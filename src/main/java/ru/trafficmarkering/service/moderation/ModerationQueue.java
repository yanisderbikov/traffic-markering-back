package ru.trafficmarkering.service.moderation;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;
import ru.trafficmarkering.config.ModerationProperties;
import ru.trafficmarkering.dto.application.ApplicationStatusUpdateRequestDTO;
import ru.trafficmarkering.model.application.Application;
import ru.trafficmarkering.model.application.ApplicationStatus;
import ru.trafficmarkering.model.application.Platform;
import ru.trafficmarkering.model.moderation.ModerationAssignment;
import ru.trafficmarkering.model.moderation.ModerationAssignment.Status;
import ru.trafficmarkering.model.moderation.Moderator;
import ru.trafficmarkering.repository.GetterApplication;
import ru.trafficmarkering.repository.ModerationAssignmentRepository;
import ru.trafficmarkering.repository.ModeratorRepository;
import ru.trafficmarkering.service.application.ApplicationService;
import ru.trafficmarkering.service.studio.telegram.TelegramClient;
import ru.trafficmarkering.service.studio.telegram.TelegramClient.TelegramException;

import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Очередь модерации: «разгребание заявок». Каждый ролик на модерации (статус PENDING) отдаётся одному
 * свободному менеджеру в его рабочие часы; у менеджера одновременно одна заявка. Не ответил за
 * {@code moderation.hold-minutes} — сообщение удаляется, ролик уходит следующему. Решили ролик мимо бота
 * (на сайте) или удалили — заявка снимается.
 */
@Slf4j
@Service
public class ModerationQueue {

    /** Заявка взята, но так и не ушла в Telegram (например, сервер перезапустился) — освобождаем */
    private static final Duration UNSENT_TIMEOUT = Duration.ofMinutes(10);

    private final ModerationProperties properties;
    private final ModerationTelegram telegram;
    private final ModerationVideoFetcher fetcher;
    private final ModeratorRepository moderators;
    private final ModerationAssignmentRepository assignments;
    private final GetterApplication applications;
    private final ApplicationService applicationService;
    private final TransactionTemplate tx;
    private final AtomicBoolean ticking = new AtomicBoolean();
    /** Пока шёл обход, попросили раздать ещё раз (менеджер освободился) — обойдём сразу же */
    private final AtomicBoolean again = new AtomicBoolean();
    private final ThreadPoolExecutor senders = new ThreadPoolExecutor(3, 3, 60, TimeUnit.SECONDS,
            new ArrayBlockingQueue<>(200), r -> {
        Thread thread = new Thread(r, "moderation-send");
        thread.setDaemon(true);
        return thread;
    });

    public ModerationQueue(ModerationProperties properties, ModerationTelegram telegram, ModerationVideoFetcher fetcher,
                           ModeratorRepository moderators, ModerationAssignmentRepository assignments,
                           GetterApplication applications, ApplicationService applicationService,
                           TransactionTemplate tx) {
        this.properties = properties;
        this.telegram = telegram;
        this.fetcher = fetcher;
        this.moderators = moderators;
        this.assignments = assignments;
        this.applications = applications;
        this.applicationService = applicationService;
        this.tx = tx;
    }

    @PreDestroy
    public void stop() {
        senders.shutdownNow();
    }

    private TelegramClient client() {
        return telegram.client();
    }

    private Duration hold() {
        return Duration.ofMinutes(Math.max(1, properties.getHoldMinutes()));
    }

    // ───────────────────────── диспетчер ─────────────────────────

    @Scheduled(fixedDelayString = "${moderation.dispatch-rate-ms:15000}", initialDelayString = "${moderation.initial-delay-ms:30000}")
    public void scheduled() {
        tick();
    }

    /** Раздать сейчас, не дожидаясь расписания: менеджер решил заявку или вернулся с паузы */
    public void kick() {
        try {
            senders.execute(this::tick);
        } catch (RejectedExecutionException e) {
            log.debug("Модерация: очередь отправки занята, раздача — по расписанию");
        }
    }

    void tick() {
        if (!telegram.enabled()) {
            return;
        }
        if (!ticking.compareAndSet(false, true)) {
            again.set(true);
            return;
        }
        try {
            do {
                again.set(false);
                dispatch();
            } while (again.get());
        } finally {
            ticking.set(false);
        }
    }

    private void dispatch() {
        Instant now = Instant.now();
        for (ModerationAssignment assignment : assignments.findByStatus(Status.ACTIVE)) {
            try {
                review(assignment.getId(), now);
            } catch (RuntimeException e) {
                log.error("Модерация: не удалось проверить заявку {}", assignment.getId(), e);
            }
        }
        for (Moderator moderator : moderators.findByEnabledTrueOrderByUsername()) {
            if (!moderator.available(now) || assignments.existsByModeratorIdAndStatus(moderator.getId(), Status.ACTIVE)) {
                continue;
            }
            try {
                claim(moderator.getId(), now).ifPresent(this::sendAsync);
            } catch (DataIntegrityViolationException e) {
                // параллельный диспетчер успел первым — следующий заход разберётся
                log.debug("Модерация: гонка при раздаче менеджеру {}", moderator.getUsername());
            } catch (RuntimeException e) {
                log.error("Модерация: не удалось выдать заявку менеджеру {}", moderator.getUsername(), e);
            }
        }
    }

    /** Что сделать с висящей заявкой после проверки в транзакции; Telegram трогаем уже вне её */
    private record Review(Status closedAs, Long chatId, Long messageId, Boolean withVideo, String note,
                          boolean paused, ModerationCard.Data card, ZoneOffset offset) {
    }

    private void review(Long assignmentId, Instant now) {
        Review review = tx.execute(status -> {
            ModerationAssignment assignment = assignments.findById(assignmentId).orElse(null);
            if (assignment == null || !assignment.active()) {
                return null;
            }
            Moderator moderator = assignment.getModerator();
            Optional<Application> application = assignment.getApplicationId() == null
                    ? Optional.empty() : applications.getById(assignment.getApplicationId());
            if (application.isEmpty() || application.get().getStatus() != ApplicationStatus.PENDING) {
                assignment.close(Status.CANCELLED, now);
                String note = application.isEmpty() ? "🗑 Криатор отозвал ролик — заявка снята."
                        : "ℹ️ Ролик уже решили на сайте — заявка снята.";
                return new Review(Status.CANCELLED, assignment.getChatId(), assignment.getMessageId(),
                        assignment.getWithVideo(), note, false,
                        application.map(ModerationCard.Data::of).orElse(null), moderator.offset());
            }
            if (assignment.getMessageId() == null) {
                if (assignment.getAssignedAt().plus(UNSENT_TIMEOUT).isBefore(now)) {
                    assignment.close(Status.FAILED, now);
                }
                return null;
            }
            if (assignment.getExpiresAt().isAfter(now)) {
                return null;
            }
            assignment.close(Status.EXPIRED, now);
            moderator.setMissedInRow(moderator.getMissedInRow() + 1);
            boolean pause = properties.getPauseAfterMissed() > 0
                    && moderator.getMissedInRow() >= properties.getPauseAfterMissed();
            if (pause) {
                moderator.setPaused(true);
            }
            String note = pause
                    ? "⚪ Пропущено заявок подряд: " + moderator.getMissedInRow() + " — вы стали <b>неактивны</b>, "
                    + "заявки больше не приходят. Готовы снова — /on"
                    : "⏰ Время на заявку <code>" + TelegramClient.escape(assignment.getApplicationPublicId())
                    + "</code> вышло — она вернулась в очередь.";
            return new Review(Status.EXPIRED, assignment.getChatId(), assignment.getMessageId(),
                    assignment.getWithVideo(), note, pause, null, moderator.offset());
        });
        if (review == null || review.chatId() == null) {
            return;
        }
        if (review.messageId() != null) {
            if (review.closedAs() == Status.EXPIRED) {
                client().deleteMessage(review.chatId(), review.messageId());
            } else if (review.card() != null) {
                closeCard(review.chatId(), review.messageId(), review.withVideo(), review.card(), review.offset(),
                        "ℹ️ Решено на сайте");
            } else {
                client().deleteMessage(review.chatId(), review.messageId());
            }
        }
        if (review.closedAs() == Status.EXPIRED || review.card() == null) {
            safeSend(review.chatId(), review.note());
        }
    }

    /** Выданная заявка: всё для отправки, без JPA-сущностей */
    record Claim(long assignmentId, long chatId, ZoneOffset offset, ModerationCard.Data card,
                 Platform platform, String videoUrl, String fileId) {
    }

    Optional<Claim> claim(Long moderatorId, Instant now) {
        return tx.execute(status -> {
            Moderator moderator = moderators.findById(moderatorId).orElse(null);
            if (moderator == null || !moderator.available(now)) {
                return Optional.<Claim>empty();
            }
            Optional<UUID> next = assignments.lockNextApplication(moderatorId);
            if (next.isEmpty()) {
                return Optional.<Claim>empty();
            }
            Application application = applications.getById(next.get()).orElseThrow();
            String fileId = assignments.findFirstByApplicationIdAndVideoFileIdNotNullOrderByIdDesc(application.getId())
                    .map(ModerationAssignment::getVideoFileId).orElse(null);
            ModerationAssignment assignment = assignments.saveAndFlush(ModerationAssignment.builder()
                    .applicationId(application.getId())
                    .applicationPublicId(application.getPublicId())
                    .moderator(moderator)
                    .status(Status.ACTIVE)
                    .chatId(moderator.getChatId())
                    .assignedAt(now)
                    .expiresAt(now.plus(hold()).plus(UNSENT_TIMEOUT))
                    .build());
            return Optional.of(new Claim(assignment.getId(), moderator.getChatId(), moderator.offset(),
                    ModerationCard.Data.of(application), application.getPlatform(), application.getVideoUrl(), fileId));
        });
    }

    private void sendAsync(Claim claim) {
        try {
            senders.execute(() -> send(claim));
        } catch (RejectedExecutionException e) {
            // не отправили — заявка освободится по UNSENT_TIMEOUT
            log.warn("Модерация: очередь отправки переполнена, заявка {} подождёт", claim.assignmentId());
        }
    }

    void send(Claim claim) {
        Instant expiresAt = Instant.now().plus(hold());
        JsonNode message = null;
        boolean withVideo = false;
        String fileId = null;
        try {
            if (claim.fileId() != null) {
                try {
                    message = client().sendVideoById(claim.chatId(), claim.fileId(),
                            ModerationCard.text(claim.card(), expiresAt, claim.offset(), true),
                            ModerationCard.decisionKeyboard(claim.assignmentId()));
                    withVideo = true;
                    fileId = claim.fileId();
                } catch (TelegramException e) {
                    if (e.getCode() == 403) {
                        throw e;
                    }
                    log.info("Модерация: file_id ролика {} не принят ({}), скачиваю заново", claim.card().publicId(), e.getMessage());
                }
            }
            if (message == null) {
                Optional<Path> file = fetcher.fetch(claim.platform(), claim.videoUrl());
                // скачивание могло занять минуты — срок отсчитываем от момента отправки
                expiresAt = Instant.now().plus(hold());
                if (file.isPresent()) {
                    try {
                        message = client().sendVideo(claim.chatId(), file.get(), claim.card().publicId() + ".mp4",
                                ModerationCard.text(claim.card(), expiresAt, claim.offset(), true),
                                ModerationCard.decisionKeyboard(claim.assignmentId()), null);
                        withVideo = true;
                        fileId = message.path("video").path("file_id").asText(null);
                        if (fileId == null) {
                            fileId = message.path("document").path("file_id").asText(null);
                        }
                    } catch (TelegramException e) {
                        if (e.getCode() == 403) {
                            throw e;
                        }
                        log.warn("Модерация: Telegram не принял видео {}: {}", claim.card().publicId(), e.getMessage());
                    } finally {
                        fetcher.cleanup(file.get());
                    }
                }
            }
            if (message == null) {
                message = client().sendMessage(claim.chatId(),
                        ModerationCard.text(claim.card(), expiresAt, claim.offset(), false),
                        ModerationCard.decisionKeyboard(claim.assignmentId()), null);
            }
        } catch (TelegramException e) {
            fail(claim, e);
            return;
        } catch (RuntimeException e) {
            log.error("Модерация: заявка {} не отправлена", claim.assignmentId(), e);
            tx.executeWithoutResult(status -> assignments.findById(claim.assignmentId())
                    .filter(ModerationAssignment::active)
                    .ifPresent(a -> a.close(Status.FAILED, Instant.now())));
            return;
        }
        long messageId = message.path("message_id").asLong();
        boolean video = withVideo;
        String storedFileId = fileId;
        Instant deadline = expiresAt;
        Boolean stillActive = tx.execute(status -> assignments.findById(claim.assignmentId()).map(assignment -> {
            if (!assignment.active()) {
                return false;
            }
            assignment.setMessageId(messageId);
            assignment.setChatId(claim.chatId());
            assignment.setWithVideo(video);
            assignment.setVideoFileId(storedFileId);
            assignment.setExpiresAt(deadline);
            return true;
        }).orElse(false));
        if (!Boolean.TRUE.equals(stillActive)) {
            // пока качали видео, ролик решили на сайте или менеджера убрали из списка
            client().deleteMessage(claim.chatId(), messageId);
        }
    }

    /** 403 — менеджер заблокировал бота: снимаем заявку и ставим его на паузу, ролик уйдёт другим */
    private void fail(Claim claim, TelegramException e) {
        log.warn("Модерация: заявка {} не доставлена в чат {}: {}", claim.assignmentId(), claim.chatId(), e.getMessage());
        tx.executeWithoutResult(status -> assignments.findById(claim.assignmentId()).ifPresent(assignment -> {
            if (assignment.active()) {
                assignment.close(Status.FAILED, Instant.now());
            }
            if (e.getCode() == 403) {
                assignment.getModerator().setPaused(true);
            }
        }));
    }

    // ───────────────────────── решения менеджера ─────────────────────────

    /** Итог нажатия: текст всплывашки в Telegram */
    public record Outcome(boolean done, String toast) {
        static Outcome fail(String toast) {
            return new Outcome(false, toast);
        }
    }

    private record Decided(long chatId, Long messageId, Boolean withVideo, ModerationCard.Data card, ZoneOffset offset) {
    }

    /** Принять или отклонить. telegramId сверяется с менеджером заявки — чужую кнопку не нажать */
    public Outcome decide(long assignmentId, long telegramId, ApplicationStatus decision, String reason) {
        Instant now = Instant.now();
        Decided decided;
        try {
            decided = tx.execute(status -> {
                ModerationAssignment assignment = ownActive(assignmentId, telegramId);
                if (assignment == null) {
                    return null;
                }
                applicationService.moderateByManager(assignment.getApplicationId(),
                        new ApplicationStatusUpdateRequestDTO(decision, reason), assignment.getModerator());
                assignment.close(Status.DECIDED, now);
                assignment.setDecision(decision);
                assignment.setReason(decision == ApplicationStatus.REJECTED ? reason : null);
                Moderator moderator = assignment.getModerator();
                moderator.setMissedInRow(0);
                moderator.setLastSeenAt(now);
                Application application = applications.getById(assignment.getApplicationId()).orElseThrow();
                return new Decided(assignment.getChatId(), assignment.getMessageId(), assignment.getWithVideo(),
                        ModerationCard.Data.of(application), moderator.offset());
            });
        } catch (ResponseStatusException e) {
            // ролик решили на сайте или отозвали между показом и нажатием
            kick();
            return Outcome.fail(e.getReason() != null ? e.getReason() : "Ролик уже не ждёт модерации");
        }
        if (decided == null) {
            return Outcome.fail("Заявка уже закрыта");
        }
        if (decided.messageId() != null) {
            closeCard(decided.chatId(), decided.messageId(), decided.withVideo(), decided.card(), decided.offset(),
                    ModerationCard.verdict(decision, reason));
        }
        kick();
        return new Outcome(true, decision == ApplicationStatus.APPROVED ? "Принято" : "Отклонено");
    }

    /** Менеджер выбрал «Своя причина» — следующее его сообщение станет причиной отказа */
    public boolean awaitReason(long assignmentId, long telegramId, boolean awaiting) {
        return Boolean.TRUE.equals(tx.execute(status -> {
            ModerationAssignment assignment = ownActive(assignmentId, telegramId);
            if (assignment == null) {
                return false;
            }
            assignment.setAwaitingReason(awaiting);
            return true;
        }));
    }

    /** Заявка, к которой менеджер пишет причину отказа */
    public Optional<Long> awaitingReason(long telegramId) {
        return tx.execute(status -> moderators.findByTelegramId(telegramId)
                .flatMap(moderator -> assignments.findFirstByModeratorIdAndStatus(moderator.getId(), Status.ACTIVE))
                .filter(ModerationAssignment::isAwaitingReason)
                .map(ModerationAssignment::getId));
    }

    public boolean ownsActive(long assignmentId, long telegramId) {
        return Boolean.TRUE.equals(tx.execute(status -> ownActive(assignmentId, telegramId) != null));
    }

    private ModerationAssignment ownActive(long assignmentId, long telegramId) {
        return assignments.findById(assignmentId)
                .filter(ModerationAssignment::active)
                .filter(a -> a.getModerator().getTelegramId() != null && a.getModerator().getTelegramId() == telegramId)
                .orElse(null);
    }

    /** Менеджера убрали из списка: его заявка возвращается в очередь */
    public void release(Long moderatorId, String note) {
        Optional<ModerationAssignment> released = tx.execute(status -> {
            Optional<ModerationAssignment> active = assignments.findFirstByModeratorIdAndStatus(moderatorId, Status.ACTIVE);
            active.ifPresent(a -> a.close(Status.CANCELLED, Instant.now()));
            return active;
        });
        if (released != null && released.isPresent() && released.get().getChatId() != null) {
            if (released.get().getMessageId() != null) {
                client().deleteMessage(released.get().getChatId(), released.get().getMessageId());
            }
            if (note != null) {
                safeSend(released.get().getChatId(), note);
            }
        }
        kick();
    }

    private void closeCard(long chatId, long messageId, Boolean withVideo, ModerationCard.Data card, ZoneOffset offset,
                           String verdict) {
        boolean video = Boolean.TRUE.equals(withVideo);
        String text = ModerationCard.closed(ModerationCard.text(card, Instant.now(), offset, video), verdict);
        if (video) {
            if (!client().editCaption(chatId, messageId, text, null)) {
                client().editMarkup(chatId, messageId, null);
            }
        } else {
            client().editMessage(chatId, messageId, text, TelegramClient.keyboard(List.of()));
        }
    }

    private void safeSend(long chatId, String html) {
        try {
            client().sendMessage(chatId, html, null, null);
        } catch (RuntimeException e) {
            log.debug("Модерация: сообщение в чат {} не ушло: {}", chatId, e.getMessage());
        }
    }
}

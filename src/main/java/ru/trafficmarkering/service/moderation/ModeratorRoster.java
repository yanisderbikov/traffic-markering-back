package ru.trafficmarkering.service.moderation;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;
import ru.trafficmarkering.config.ModerationProperties;
import ru.trafficmarkering.dto.moderation.ModerationDecisionDTO;
import ru.trafficmarkering.dto.moderation.ModeratorDTO;
import ru.trafficmarkering.model.application.Application;
import ru.trafficmarkering.repository.GetterApplication;
import ru.trafficmarkering.dto.moderation.ModeratorsDTO;
import ru.trafficmarkering.model.application.ApplicationStatus;
import ru.trafficmarkering.model.moderation.ModerationAssignment;
import ru.trafficmarkering.model.moderation.ModerationAssignment.Status;
import ru.trafficmarkering.model.moderation.Moderator;
import ru.trafficmarkering.repository.ModerationAssignmentRepository;
import ru.trafficmarkering.repository.ModeratorRepository;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Список менеджеров модерации. Супер-админ задаёт его никами Telegram; войти в бота может только тот,
 * кто в списке. Убранный из списка менеджер не удаляется — по нему остаётся история решений.
 */
@Service
public class ModeratorRoster {

    private static final Pattern USERNAME = Pattern.compile("[a-z][a-z0-9_]{3,31}");
    private static final int MAX_MODERATORS = 500;

    private final ModeratorRepository moderators;
    private final ModerationAssignmentRepository assignments;
    private final ModerationQueue queue;
    private final ModerationTelegram telegram;
    private final ModerationProperties properties;
    private final TransactionTemplate tx;
    private final GetterApplication applications;

    public ModeratorRoster(ModeratorRepository moderators, ModerationAssignmentRepository assignments,
                           ModerationQueue queue, ModerationTelegram telegram, ModerationProperties properties,
                           TransactionTemplate tx, GetterApplication applications) {
        this.applications = applications;
        this.moderators = moderators;
        this.assignments = assignments;
        this.queue = queue;
        this.telegram = telegram;
        this.properties = properties;
        this.tx = tx;
    }

    /** «@Ivan_Petrov», «t.me/ivan_petrov», «ivan_petrov» → «ivan_petrov»; мусор — null */
    public static String normalize(String raw) {
        if (raw == null) {
            return null;
        }
        String value = raw.strip().toLowerCase(Locale.ROOT)
                .replaceFirst("^(https?://)?(www\\.)?(t\\.me|telegram\\.me)/", "")
                .replaceFirst("^@", "")
                .replaceAll("[/?#].*$", "");
        return USERNAME.matcher(value).matches() ? value : null;
    }

    /** Разбирает список из текстового поля: ники через пробел, запятую или с новой строки */
    public static List<String> parse(Collection<String> raw) {
        Set<String> result = new LinkedHashSet<>();
        List<String> invalid = new ArrayList<>();
        for (String item : raw == null ? List.<String>of() : raw) {
            if (item == null) {
                continue;
            }
            for (String token : item.split("[\\s,;]+")) {
                if (token.isBlank()) {
                    continue;
                }
                String username = normalize(token);
                if (username == null) {
                    invalid.add(token.strip());
                } else {
                    result.add(username);
                }
            }
        }
        if (!invalid.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Не похоже на ник Telegram: " + String.join(", ", invalid)
                            + ". Ник — от 4 до 32 латинских букв, цифр и _, можно с @");
        }
        if (result.size() > MAX_MODERATORS) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Не больше " + MAX_MODERATORS + " менеджеров");
        }
        return List.copyOf(result);
    }

    @Transactional(readOnly = true)
    public ModeratorsDTO list() {
        Instant now = Instant.now();
        Map<Long, long[]> totals = tally(Instant.EPOCH);
        Map<Long, long[]> day = tally(now.minus(Duration.ofDays(1)));
        Map<Long, ModerationAssignment> active = new HashMap<>();
        for (ModerationAssignment assignment : assignments.findByStatus(Status.ACTIVE)) {
            active.put(assignment.getModerator().getId(), assignment);
        }
        List<ModeratorDTO> items = moderators.findAllByOrderByEnabledDescUsernameAsc().stream()
                .map(moderator -> {
                    long[] all = totals.getOrDefault(moderator.getId(), new long[3]);
                    long[] recent = day.getOrDefault(moderator.getId(), new long[3]);
                    ModerationAssignment current = active.get(moderator.getId());
                    return new ModeratorDTO(
                            moderator.getUsername(),
                            moderator.isEnabled(),
                            moderator.getTelegramId() != null,
                            moderator.getDisplayName(),
                            moderator.scheduleLabel(),
                            moderator.active(),
                            moderator.available(now),
                            current != null ? current.getApplicationPublicId() : null,
                            current != null && current.getMessageId() != null ? current.getExpiresAt() : null,
                            all[0], all[1], all[2],
                            recent[0] + recent[1],
                            moderator.getLastSeenAt());
                })
                .toList();
        return new ModeratorsDTO(telegram.enabled(), telegram.username(), properties.getHoldMinutes(),
                assignments.countWaiting(), items);
    }

    /** [принято, отклонено, пропущено] по менеджерам */
    private Map<Long, long[]> tally(Instant since) {
        Map<Long, long[]> result = new HashMap<>();
        for (ModerationAssignmentRepository.ModeratorTally row : assignments.tallySince(since)) {
            long[] counts = result.computeIfAbsent(row.getModeratorId(), id -> new long[3]);
            if (row.getStatus() == Status.DECIDED && row.getDecision() == ApplicationStatus.APPROVED) {
                counts[0] += row.getTotal();
            } else if (row.getStatus() == Status.DECIDED) {
                counts[1] += row.getTotal();
            } else if (row.getStatus() == Status.EXPIRED) {
                counts[2] += row.getTotal();
            }
        }
        return result;
    }

    /** Новый список целиком: кого нет — выключаем и возвращаем его заявку в очередь */
    public ModeratorsDTO replace(Collection<String> raw) {
        List<String> usernames = parse(raw);
        List<Long> removed = tx.execute(status -> {
            List<Long> off = new ArrayList<>();
            for (Moderator moderator : moderators.findAll()) {
                boolean listed = usernames.contains(moderator.getUsername());
                if (moderator.isEnabled() && !listed) {
                    moderator.setEnabled(false);
                    off.add(moderator.getId());
                } else if (!moderator.isEnabled() && listed) {
                    moderator.setEnabled(true);
                    moderator.setMissedInRow(0);
                }
            }
            for (String username : usernames) {
                if (moderators.findByUsername(username).isEmpty()) {
                    moderators.save(Moderator.builder().username(username).build());
                }
            }
            return off;
        });
        for (Long id : removed == null ? List.<Long>of() : removed) {
            queue.release(id, "Вас убрали из списка менеджеров модерации — заявки больше не будут приходить.");
        }
        queue.kick();
        return list();
    }

    /**
     * Кто пишет боту: ищем по Telegram ID, первый раз — по нику из списка, и привязываем ID.
     * Ник, уже привязанный к другому Telegram ID, не подходит: так ник нельзя перехватить.
     */
    public Optional<Moderator> access(long telegramId, String username, String displayName, long chatId) {
        return tx.execute(status -> {
            Optional<Moderator> found = moderators.findByTelegramId(telegramId);
            if (found.isEmpty()) {
                String normalized = normalize(username);
                found = normalized == null ? Optional.empty() : moderators.findByUsername(normalized)
                        .filter(moderator -> moderator.getTelegramId() == null);
            }
            found = found.filter(Moderator::isEnabled);
            found.ifPresent(moderator -> {
                moderator.setTelegramId(telegramId);
                moderator.setChatId(chatId);
                if (displayName != null && !displayName.isBlank()) {
                    moderator.setDisplayName(displayName.strip());
                }
                moderator.setLastSeenAt(Instant.now());
            });
            return found;
        });
    }

    public Moderator update(long moderatorId, java.util.function.Consumer<Moderator> change) {
        Moderator updated = tx.execute(status -> {
            Moderator moderator = moderators.findById(moderatorId).orElseThrow();
            change.accept(moderator);
            return moderator;
        });
        queue.kick();
        return updated;
    }

    /** [принято, отклонено, пропущено] за всё время и решено за сутки — для /stats */
    @Transactional(readOnly = true)
    public long[] stats(long moderatorId) {
        long[] all = tally(Instant.EPOCH).getOrDefault(moderatorId, new long[3]);
        long day = assignments.countByModeratorIdAndStatusInAndClosedAtGreaterThanEqual(moderatorId,
                List.of(Status.DECIDED), Instant.now().minus(Duration.ofDays(1)));
        return new long[]{all[0], all[1], all[2], day};
    }

    /**
     * Супер-админ включает или выключает менеджера. Выключенному заявки не приходят; текущую он ещё может решить.
     * Менеджеру приходит сообщение в бота.
     */
    public ModeratorsDTO setActive(String rawUsername, boolean active) {
        String username = normalize(rawUsername);
        Moderator moderator = tx.execute(status -> {
            Moderator found = username == null ? null : moderators.findByUsername(username).orElse(null);
            if (found == null) {
                throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Менеджера @" + rawUsername + " нет в списке");
            }
            found.setPaused(!active);
            found.setMissedInRow(0);
            return found;
        });
        if (moderator.getChatId() != null && telegram.enabled()) {
            try {
                telegram.client().sendMessage(moderator.getChatId(), active
                        ? "🟢 Администратор сделал вас <b>активным</b> — заявки снова будут приходить в ваши часы."
                        : "⚪ Администратор сделал вас <b>неактивным</b> — заявки больше не приходят.", null, null);
            } catch (RuntimeException ignored) {
                // менеджер мог заблокировать бота — статус всё равно сохранён
            }
        }
        queue.kick();
        return list();
    }

    /** Кто какой ролик как решил; username — только этот менеджер */
    @Transactional(readOnly = true)
    public List<ModerationDecisionDTO> decisions(String rawUsername, int limit) {
        org.springframework.data.domain.PageRequest page =
                org.springframework.data.domain.PageRequest.of(0, Math.max(1, Math.min(limit, 500)));
        String username = rawUsername == null || rawUsername.isBlank() ? null : normalize(rawUsername);
        List<ModerationAssignment> rows = username == null ? assignments.findClosed(Status.DECIDED, page)
                : assignments.findClosedBy(Status.DECIDED, username, page);
        return rows.stream().map(row -> {
            Application application = row.getApplicationId() == null ? null
                    : applications.getById(row.getApplicationId()).orElse(null);
            return new ModerationDecisionDTO(
                    row.getId(),
                    row.getApplicationId(),
                    row.getApplicationPublicId(),
                    application != null ? application.getCampaign().getTitle() : null,
                    application != null ? application.getCreator().getName() : null,
                    application != null ? application.getVideoUrl() : null,
                    application != null && application.getStatus() != null ? application.getStatus().name() : null,
                    row.getModerator().getUsername(),
                    row.getModerator().label(),
                    row.getDecision() != null ? row.getDecision().name() : null,
                    row.getReason(),
                    row.getAssignedAt(),
                    row.getClosedAt());
        }).toList();
    }

    public long waiting() {
        return assignments.countWaiting();
    }
}

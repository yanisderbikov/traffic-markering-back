package ru.trafficmarkering.service.notify;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import ru.trafficmarkering.dto.notify.BroadcastDTO;
import ru.trafficmarkering.dto.notify.BroadcastPayload;
import ru.trafficmarkering.dto.notify.BroadcastRequestDTO;
import ru.trafficmarkering.dto.notify.NotificationPayload;
import ru.trafficmarkering.dto.notify.ReachDTO;
import ru.trafficmarkering.model.Role;
import ru.trafficmarkering.model.User;
import ru.trafficmarkering.model.notify.Broadcast;
import ru.trafficmarkering.model.notify.BroadcastChannel;
import ru.trafficmarkering.model.notify.BroadcastStatus;
import ru.trafficmarkering.repository.BroadcastRepository;
import ru.trafficmarkering.repository.BroadcastRepository.Recipient;
import ru.trafficmarkering.repository.StudioProfileRepository;
import ru.trafficmarkering.service.auth.telegram.TelegramIdentity;
import ru.trafficmarkering.service.task.TaskAdder;
import ru.trafficmarkering.service.auth.CurrentUserService;

import java.time.Instant;
import java.util.List;

/**
 * Рассылки из админки. Создание только ставит таску BROADCAST; она в фоне раскладывает рассылку
 * на сообщения NOTIFICATION — по одному на человека, а отправщик считает доставленные и ошибки.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class BroadcastService {

    private final BroadcastRepository broadcasts;
    private final StudioProfileRepository profiles;
    private final CurrentUserService currentUser;
    private final TaskAdder tasks;

    /** Почта учётки или null: у учёток из Telegram её нет */
    public static String emailOf(String username) {
        if (username == null || username.startsWith(TelegramIdentity.LOGIN_PREFIX) || !username.contains("@")) {
            return null;
        }
        return username;
    }

    @Transactional(readOnly = true)
    public List<ReachDTO> reach() {
        currentUser.require(Role.SUPER_ADMIN);
        return broadcasts.reach().stream()
                .map(r -> new ReachDTO(r.getRole(), r.getTotal(), r.getTelegram(), r.getTelegramWeek(), r.getEmail(),
                        r.getTelegram() + r.getEmail() - r.getBoth()))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<BroadcastDTO> list() {
        currentUser.require(Role.SUPER_ADMIN);
        return broadcasts.findAllByOrderByCreatedAtDesc(PageRequest.of(0, 50)).stream().map(BroadcastDTO::from).toList();
    }

    @Transactional
    public BroadcastDTO create(BroadcastRequestDTO request) {
        User admin = currentUser.require(Role.SUPER_ADMIN);
        if (request.test()) {
            return sendTest(admin, request);
        }
        Broadcast broadcast = broadcasts.save(Broadcast.builder()
                .audience(request.audience())
                .channel(request.channel())
                .title(request.title().strip())
                .text(request.text().strip())
                .buttonText(blankToNull(request.buttonText()))
                .buttonUrl(blankToNull(request.buttonUrl()))
                .status(BroadcastStatus.QUEUED)
                .createdBy(admin.getId())
                .createdAt(Instant.now())
                .build());
        tasks.addTask(new BroadcastPayload(broadcast.getId()));
        log.info("Рассылка {} поставлена: {} / {}", broadcast.getId(), broadcast.getAudience(), broadcast.getChannel());
        return BroadcastDTO.from(broadcast);
    }

    /** Пробная отправка себе: по тем же правилам канала, без записи в историю */
    private BroadcastDTO sendTest(User admin, BroadcastRequestDTO request) {
        Long chatId = profiles.findById(admin.getId()).map(p -> p.getTelegramId()).orElse(null);
        NotificationPayload message = message(request.channel(), chatId, emailOf(admin.getUsername()),
                request.title().strip(), request.text().strip(), blankToNull(request.buttonText()),
                blankToNull(request.buttonUrl()), null);
        if (message == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, request.channel() == BroadcastChannel.EMAIL
                    ? "У вашей учётки нет почты" : "Привяжите Telegram к своей учётке, чтобы получить пробное сообщение");
        }
        tasks.addTask(message);
        return null;
    }

    /** Раскладывает рассылку на сообщения. Зовётся из таски BROADCAST */
    @Transactional
    public void expand(long broadcastId) {
        Broadcast broadcast = broadcasts.findById(broadcastId).orElse(null);
        if (broadcast == null || broadcast.getStatus() != BroadcastStatus.QUEUED) {
            return;
        }
        List<Recipient> recipients = broadcasts.recipients(
                broadcast.getAudience().roles().stream().map(Enum::name).toList());
        int total = 0;
        for (Recipient r : recipients) {
            NotificationPayload message = message(broadcast.getChannel(), r.getTelegramId(), emailOf(r.getUsername()),
                    broadcast.getTitle(), broadcast.getText(), broadcast.getButtonText(), broadcast.getButtonUrl(),
                    broadcast.getId());
            if (message != null) {
                tasks.addTask(message);
                total++;
            }
        }
        broadcast.setTotal(total);
        if (total == 0) {
            broadcast.setStatus(BroadcastStatus.DONE);
            broadcast.setFinishedAt(Instant.now());
        } else {
            broadcast.setStatus(BroadcastStatus.SENDING);
        }
        broadcasts.save(broadcast);
        log.info("Рассылка {}: получателей {}", broadcastId, total);
    }

    static NotificationPayload message(BroadcastChannel channel, Long chatId, String email, String title, String text,
                                       String buttonText, String buttonUrl, Long broadcastId) {
        boolean telegram = chatId != null && channel != BroadcastChannel.EMAIL;
        boolean mail = !telegram && email != null && channel != BroadcastChannel.TELEGRAM;
        if (!telegram && !mail) {
            return null;
        }
        return NotificationPayload.builder()
                .chatId(telegram ? chatId : null)
                .email(mail ? email : null)
                .title(title)
                .text(text)
                .buttonText(buttonText)
                .buttonUrl(buttonUrl)
                .broadcastId(broadcastId)
                .build();
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.strip();
    }
}

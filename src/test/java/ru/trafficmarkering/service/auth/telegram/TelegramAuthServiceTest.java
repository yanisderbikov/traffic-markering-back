package ru.trafficmarkering.service.auth.telegram;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;
import ru.trafficmarkering.model.Role;
import ru.trafficmarkering.model.User;
import ru.trafficmarkering.model.auth.TelegramAuth;
import ru.trafficmarkering.model.studio.StudioProfile;
import ru.trafficmarkering.repository.GetterCreatorProfile;
import ru.trafficmarkering.repository.GetterCustomerProfile;
import ru.trafficmarkering.repository.SaverCreatorProfile;
import ru.trafficmarkering.repository.SaverCustomerProfile;
import ru.trafficmarkering.repository.StudioProfileRepository;
import ru.trafficmarkering.repository.TelegramAuthRepository;
import ru.trafficmarkering.repository.UserRepository;
import ru.trafficmarkering.service.auth.JwtTokenService;
import ru.trafficmarkering.service.auth.telegram.TelegramAuthService.Step;
import ru.trafficmarkering.service.auth.telegram.TelegramAuthService.TgUser;
import ru.trafficmarkering.service.partner.PartnerService;
import ru.trafficmarkering.service.studio.telegram.StudioBotInfo;
import ru.trafficmarkering.service.user.AccountProvisioningService;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class TelegramAuthServiceTest {

    @Mock private TelegramAuthRepository auths;
    @Mock private UserRepository users;
    @Mock private StudioProfileRepository profiles;
    @Mock private JwtTokenService jwt;
    @Mock private AccountProvisioningService provisioning;
    @Mock private PartnerService partners;
    @Mock private GetterCreatorProfile creatorProfiles;
    @Mock private SaverCreatorProfile creatorProfileSaver;
    @Mock private GetterCustomerProfile customerProfiles;
    @Mock private SaverCustomerProfile customerProfileSaver;
    @Mock private StudioBotInfo botInfo;
    @Mock private org.springframework.context.ApplicationEventPublisher events;

    private final Map<String, TelegramAuth> authStore = new HashMap<>();
    private final Map<Long, User> userStore = new HashMap<>();
    private final Map<Long, StudioProfile> profileStore = new HashMap<>();
    private final AtomicLong ids = new AtomicLong(100);
    private final TgUser tg = new TgUser(555L, "anya", "Аня", null);

    private TelegramAuthService service;
    private TelegramIdentity identity;

    @BeforeEach
    void setUp() {
        when(auths.save(any())).thenAnswer(inv -> {
            TelegramAuth a = inv.getArgument(0);
            authStore.put(a.getId(), a);
            return a;
        });
        when(auths.lockById(anyString())).thenAnswer(inv -> Optional.ofNullable(authStore.get(inv.<String>getArgument(0))));
        when(auths.lockBySecretHash(anyString())).thenAnswer(inv -> authStore.values().stream()
                .filter(a -> a.getSecretHash().equals(inv.getArgument(0))).findFirst());
        when(users.save(any())).thenAnswer(inv -> {
            User u = inv.getArgument(0);
            if (u.getId() == null) {
                u.setId(ids.incrementAndGet());
            }
            userStore.put(u.getId(), u);
            return u;
        });
        when(users.findById(anyLong())).thenAnswer(inv -> Optional.ofNullable(userStore.get(inv.<Long>getArgument(0))));
        when(users.findByUsername(anyString())).thenAnswer(inv -> userStore.values().stream()
                .filter(u -> u.getUsername().equals(inv.getArgument(0))).findFirst());
        when(profiles.findById(anyLong())).thenAnswer(inv -> Optional.ofNullable(profileStore.get(inv.<Long>getArgument(0))));
        when(profiles.findByTelegramId(anyLong())).thenAnswer(inv -> profileStore.values().stream()
                .filter(p -> inv.getArgument(0).equals(p.getTelegramId())).findFirst());
        when(profiles.save(any())).thenAnswer(inv -> {
            StudioProfile p = inv.getArgument(0);
            profileStore.put(p.getUserId(), p);
            return p;
        });
        when(profiles.saveAndFlush(any())).thenAnswer(inv -> profiles.save(inv.getArgument(0)));
        when(botInfo.username()).thenReturn("offer_bot");
        when(jwt.createToken(anyString(), any(), any())).thenAnswer(inv -> "jwt-" + inv.getArgument(0));
        when(partners.findByCode(any())).thenReturn(Optional.empty());

        identity = new TelegramIdentity(profiles, users);
        service = new TelegramAuthService(auths, users, identity, jwt, provisioning, partners, creatorProfiles,
                creatorProfileSaver, customerProfiles, customerProfileSaver, botInfo, events);
        ReflectionTestUtils.setField(service, "siteUrl", "https://site.example/");
    }

    private User emailUser() {
        User user = users.save(User.builder().username("anya@example.com").name("Аня").role(Role.CREATOR).build());
        identity.attach(user, 555L, "anya");
        return user;
    }

    private static String idOf(String url) {
        return url.substring(url.indexOf("start=auth_") + "start=auth_".length());
    }

    @Test
    void siteLoginNeedsConfirmationAndIssuesTokenOnce() {
        emailUser();
        var start = service.start(null, null, "Chrome, macOS");
        assertThat(start.url()).startsWith("https://t.me/offer_bot?start=auth_");
        assertThat(service.poll(start.secret()).status()).isEqualTo("PENDING");

        var opened = service.openFromSite(idOf(start.url()), tg);
        assertThat(opened.step()).isEqualTo(Step.CONFIRM_LOGIN);
        assertThat(opened.clientInfo()).isEqualTo("Chrome, macOS");
        assertThat(service.poll(start.secret()).status()).isEqualTo("PENDING");

        assertThat(service.decide(opened.authId(), "ok", tg).step()).isEqualTo(Step.SITE_LOGGED_IN);
        var done = service.poll(start.secret());
        assertThat(done.status()).isEqualTo("DONE");
        assertThat(done.auth().token()).isEqualTo("jwt-anya@example.com");
        assertThat(service.poll(start.secret()).status()).isEqualTo("EXPIRED");
    }

    @Test
    void anotherTelegramCannotConfirmOrReuseTheLink() {
        emailUser();
        var start = service.start(null, null, null);
        var opened = service.openFromSite(idOf(start.url()), tg);
        TgUser stranger = new TgUser(777L, "evil", "Злодей", null);
        assertThat(service.decide(opened.authId(), "ok", stranger).step()).isEqualTo(Step.FOREIGN);
        assertThat(service.openFromSite(idOf(start.url()), stranger).step()).isEqualTo(Step.EXPIRED);
        assertThat(service.poll(start.secret()).status()).isEqualTo("PENDING");
    }

    @Test
    void cancelInBotStopsSiteLogin() {
        emailUser();
        var start = service.start(null, null, null);
        var opened = service.openFromSite(idOf(start.url()), tg);
        assertThat(service.decide(opened.authId(), "no", tg).step()).isEqualTo(Step.CANCELLED);
        assertThat(service.poll(start.secret()).status()).isEqualTo("CANCELLED");
        assertThat(service.decide(opened.authId(), "ok", tg).step()).isEqualTo(Step.EXPIRED);
    }

    @Test
    void registrationFromSiteCreatesTelegramAccountWithChosenRole() {
        var start = service.start(Role.CUSTOMER, null, null);
        var opened = service.openFromSite(idOf(start.url()), tg);
        assertThat(opened.step()).isEqualTo(Step.CHOOSE_ACCOUNT);
        assertThat(opened.presetRole()).isEqualTo(Role.CUSTOMER);

        assertThat(service.decide(opened.authId(), "cu", tg).step()).isEqualTo(Step.SITE_LOGGED_IN);
        var done = service.poll(start.secret());
        assertThat(done.status()).isEqualTo("DONE");
        assertThat(done.auth().email()).isNull();
        User created = users.findByUsername("tg:555").orElseThrow();
        assertThat(created.getRole()).isEqualTo(Role.CUSTOMER);
        assertThat(created.getName()).isEqualTo("Аня");
        assertThat(created.getVerifiedAt()).isNotNull();
        assertThat(identity.userOf(555L)).contains(created);
        verify(provisioning).provision(created);
    }

    @Test
    void existingAccountByEmailGetsTelegramAttachedInsteadOfDuplicate() {
        User owner = users.save(User.builder().username("anya@example.com").name("Аня").role(Role.CREATOR).build());
        var start = service.start(null, null, null);
        var opened = service.openFromSite(idOf(start.url()), tg);
        assertThat(service.decide(opened.authId(), "have", tg).step()).isEqualTo(Step.SITE_USE_EMAIL);

        var poll = service.poll(start.secret());
        assertThat(poll.status()).isEqualTo("NEED_EMAIL");
        assertThat(poll.telegram()).isEqualTo("@anya");

        assertThat(service.attach(start.secret(), owner)).isEqualTo("@anya");
        org.mockito.Mockito.verify(events).publishEvent(new TelegramLinkedEvent(555L, owner.getName(), owner.getUsername()));
        assertThat(identity.userOf(555L)).contains(owner);
        assertThat(users.findByUsername("tg:555")).isEmpty();
        assertThatThrownBy(() -> service.attach(start.secret(), owner)).isInstanceOf(ResponseStatusException.class);
    }

    @Test
    void botLoginGivesOneTimeSiteLink() {
        User owner = emailUser();
        var step = service.openFromBot(tg);
        assertThat(step.step()).isEqualTo(Step.LOGIN_LINK);
        assertThat(step.url()).startsWith("https://site.example/auth/telegram#");
        String secret = step.url().substring(step.url().indexOf('#') + 1);
        var done = service.poll(secret);
        assertThat(done.status()).isEqualTo("DONE");
        assertThat(done.auth().token()).isEqualTo("jwt-" + owner.getUsername());
        assertThat(service.poll(secret).status()).isEqualTo("EXPIRED");
    }

    @Test
    void botRegistrationThenLinkLogsIntoNewAccount() {
        var step = service.openFromBot(tg);
        assertThat(step.step()).isEqualTo(Step.CHOOSE_ACCOUNT);
        var created = service.decide(step.authId(), "cr", tg);
        assertThat(created.step()).isEqualTo(Step.LOGIN_LINK);
        assertThat(created.user().getUsername()).isEqualTo("tg:555");
        String secret = created.url().substring(created.url().indexOf('#') + 1);
        assertThat(service.poll(secret).status()).isEqualTo("DONE");

        // Повторная регистрация тем же Telegram не заводит вторую учётку
        var again = service.openFromBot(tg);
        assertThat(again.step()).isEqualTo(Step.LOGIN_LINK);
        assertThat(again.user().getId()).isEqualTo(created.user().getId());
    }

    @Test
    void telegramOnlyAccountKeepsItsOnlyLogin() {
        var step = service.openFromBot(tg);
        User tgOnly = service.decide(step.authId(), "cr", tg).user();
        User other = users.save(User.builder().username("boris@example.com").name("Борис").role(Role.CREATOR).build());

        assertThatThrownBy(() -> identity.attach(other, 555L, "anya"))
                .isInstanceOf(ResponseStatusException.class).hasMessageContaining("другую учётку");
        assertThatThrownBy(() -> identity.detach(tgOnly))
                .isInstanceOf(ResponseStatusException.class).hasMessageContaining("единственный вход");
        assertThat(identity.userOf(555L)).contains(tgOnly);
    }

    @Test
    void emailAccountTelegramMovesToAnotherEmailAccount() {
        User first = emailUser();
        User second = users.save(User.builder().username("boris@example.com").name("Борис").role(Role.CREATOR).build());
        identity.attach(second, 555L, "anya");
        assertThat(identity.userOf(555L)).contains(second);
        assertThat(identity.linkOf(first.getId())).isEmpty();
    }

    @Test
    void miniAppLogsInLinkedTelegramRightAway() {
        User owner = emailUser();
        var result = service.webApp(new TelegramInitData.Launch(tg, "studio"), null, null);
        assertThat(result.status()).isEqualTo("DONE");
        assertThat(result.auth().token()).isEqualTo("jwt-" + owner.getUsername());
        assertThat(result.startParam()).isEqualTo("studio");
    }

    @Test
    void miniAppAsksUnlinkedTelegramAndCreatesOneAccount() {
        var launch = new TelegramInitData.Launch(tg, null);
        assertThat(service.webApp(launch, null, null).status()).isEqualTo("NEED_ACCOUNT");
        assertThat(users.findByUsername("tg:555")).isEmpty();

        assertThat(service.webApp(launch, "cu", null).status()).isEqualTo("DONE");
        User created = users.findByUsername("tg:555").orElseThrow();
        assertThat(created.getRole()).isEqualTo(Role.CUSTOMER);
        // Повторное открытие — та же учётка, вторую не заводим
        assertThat(service.webApp(launch, "cr", null).auth().token()).isEqualTo("jwt-tg:555");
        assertThat(identity.userOf(555L)).contains(created);
    }

    @Test
    void miniAppHaveAccountAttachesAfterEmailLogin() {
        User owner = users.save(User.builder().username("boss@example.com").name("Босс").role(Role.CUSTOMER).build());
        var result = service.webApp(new TelegramInitData.Launch(tg, null), "have", null);
        assertThat(result.status()).isEqualTo("NEED_EMAIL");
        assertThat(service.attach(result.secret(), owner)).isEqualTo("@anya");
        assertThat(identity.userOf(555L)).contains(owner);
    }
}

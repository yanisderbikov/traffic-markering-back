package ru.trafficmarkering.service.profile.impl;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import ru.trafficmarkering.dto.profile.CreatorProfileRequestDTO;
import ru.trafficmarkering.model.Role;
import ru.trafficmarkering.model.User;
import ru.trafficmarkering.model.profile.CreatorProfile;
import ru.trafficmarkering.repository.GetterCreatorProfile;
import ru.trafficmarkering.repository.SaverCreatorProfile;
import ru.trafficmarkering.repository.UserRepository;
import ru.trafficmarkering.service.auth.CurrentUserService;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CreatorProfileServiceImplTest {

    @Mock
    private GetterCreatorProfile getterCreatorProfile;
    @Mock
    private SaverCreatorProfile saverCreatorProfile;
    @Mock
    private CurrentUserService currentUserService;
    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private CreatorProfileServiceImpl service;

    private CreatorProfile profile;

    @BeforeEach
    void setUp() {
        User user = User.builder().id(7L).build();
        profile = CreatorProfile.builder()
                .user(user)
                .telegram("@anya")
                .instagram("anya.insta")
                .tiktok("anya.tt")
                .youtubeShorts("anya.yt")
                .build();
        when(currentUserService.require(Role.CREATOR)).thenReturn(user);
        when(getterCreatorProfile.getByUserId(7L)).thenReturn(Optional.of(profile));
        when(saverCreatorProfile.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void profileFormKeepsContactsItDoesNotSend() {
        service.update(CreatorProfileRequestDTO.builder().displayName("Аня").bio("Снимаю").build());

        assertThat(profile.getDisplayName()).isEqualTo("Аня");
        assertThat(profile.getBio()).isEqualTo("Снимаю");
        assertThat(profile.getTelegram()).isEqualTo("@anya");
        assertThat(profile.getInstagram()).isEqualTo("anya.insta");
        assertThat(profile.getTiktok()).isEqualTo("anya.tt");
        assertThat(profile.getYoutubeShorts()).isEqualTo("anya.yt");
    }

    @Test
    void registrationStillSavesTelegram() {
        service.update(CreatorProfileRequestDTO.builder().telegram(" @new ").build());

        assertThat(profile.getTelegram()).isEqualTo("@new");
    }
}

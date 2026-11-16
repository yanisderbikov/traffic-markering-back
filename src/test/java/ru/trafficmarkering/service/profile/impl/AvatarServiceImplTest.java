package ru.trafficmarkering.service.profile.impl;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.server.ResponseStatusException;
import ru.trafficmarkering.model.User;
import ru.trafficmarkering.repository.UserRepository;
import ru.trafficmarkering.service.auth.CurrentUserService;
import ru.trafficmarkering.service.storage.FileStorage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AvatarServiceImplTest {

    private static final String KEY = "avatars/3f1c2d4e-5a6b-4c7d-8e9f-0a1b2c3d4e5f.jpg";

    @Mock
    private CurrentUserService currentUserService;
    @Mock
    private UserRepository userRepository;
    @Mock
    private FileStorage fileStorage;

    @InjectMocks
    private AvatarServiceImpl service;

    private User user;

    @BeforeEach
    void setUp() {
        user = User.builder().id(7L).build();
        when(currentUserService.require()).thenReturn(user);
    }

    @Test
    void presignAcceptsImagesOnly() {
        when(fileStorage.presignUpload("me.png", "image/png", "avatars"))
                .thenReturn(new FileStorage.PresignedUpload("https://s3/put", KEY));

        assertThat(service.presign("me.png", "image/png").key()).isEqualTo(KEY);
        assertThatThrownBy(() -> service.presign("me.svg", "image/svg+xml"))
                .isInstanceOf(ResponseStatusException.class);
    }

    @Test
    void setSavesKeyAndReturnsLink() {
        when(fileStorage.presignedUrl(KEY)).thenReturn("https://s3/get");

        assertThat(service.set(KEY).avatarUrl()).isEqualTo("https://s3/get");
        assertThat(user.getAvatarKey()).isEqualTo(KEY);
        verify(userRepository).save(user);
    }

    @Test
    void setRejectsKeysOutsideAvatarFolder() {
        assertThatThrownBy(() -> service.set("transfer-proofs/3f1c2d4e-5a6b-4c7d-8e9f-0a1b2c3d4e5f.pdf"))
                .isInstanceOf(ResponseStatusException.class);
        assertThatThrownBy(() -> service.set("avatars/../transfer-proofs/x.pdf"))
                .isInstanceOf(ResponseStatusException.class);

        assertThat(user.getAvatarKey()).isNull();
        verify(userRepository, never()).save(any());
    }

    @Test
    void removeClearsKey() {
        user.setAvatarKey(KEY);

        assertThat(service.remove().avatarUrl()).isNull();
        assertThat(user.getAvatarKey()).isNull();
    }
}

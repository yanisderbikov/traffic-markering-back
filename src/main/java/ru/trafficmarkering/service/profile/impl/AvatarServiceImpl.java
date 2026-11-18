package ru.trafficmarkering.service.profile.impl;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import ru.trafficmarkering.dto.file.PresignUploadResponseDTO;
import ru.trafficmarkering.dto.profile.AvatarDTO;
import ru.trafficmarkering.model.User;
import ru.trafficmarkering.repository.UserRepository;
import ru.trafficmarkering.service.auth.CurrentUserService;
import ru.trafficmarkering.service.profile.AvatarService;
import ru.trafficmarkering.service.storage.FileStorage;

import java.util.Set;

@Service
@RequiredArgsConstructor
class AvatarServiceImpl implements AvatarService {

    static final String AVATAR_PREFIX = "avatars";

    private static final Set<String> ALLOWED_TYPES = Set.of("image/jpeg", "image/png", "image/webp");

    private final CurrentUserService currentUserService;
    private final UserRepository userRepository;
    private final FileStorage fileStorage;

    @Override
    public PresignUploadResponseDTO presign(String filename, String contentType) {
        currentUserService.require();
        if (!ALLOWED_TYPES.contains(contentType)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Аватарка — только изображение: JPEG, PNG или WebP");
        }
        FileStorage.PresignedUpload presigned = fileStorage.presignUpload(filename, contentType, AVATAR_PREFIX);
        return new PresignUploadResponseDTO(presigned.uploadUrl(), presigned.key());
    }

    @Override
    @Transactional
    public AvatarDTO set(String key) {
        User user = currentUserService.require();
        // Ключ приходит с фронта: принимаем только то, что сами выдали под аватарки,
        // иначе в аватарку можно подставить чужой файл из хранилища и получить на него ссылку.
        if (key == null || !key.matches(AVATAR_PREFIX + "/[0-9a-f-]{36}(\\.[a-z0-9]{1,10})?")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Некорректный ключ аватарки");
        }
        user.setAvatarKey(key);
        userRepository.save(user);
        return new AvatarDTO(fileStorage.presignedUrl(key));
    }

    @Override
    @Transactional
    public AvatarDTO remove() {
        User user = currentUserService.require();
        user.setAvatarKey(null);
        userRepository.save(user);
        return new AvatarDTO(null);
    }
}

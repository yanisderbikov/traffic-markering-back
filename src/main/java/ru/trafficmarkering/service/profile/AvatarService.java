package ru.trafficmarkering.service.profile;

import ru.trafficmarkering.dto.file.PresignUploadResponseDTO;
import ru.trafficmarkering.dto.profile.AvatarDTO;

/** Аватарка учётной записи: одна на пользователя, какая бы роль у него ни была. */
public interface AvatarService {

    PresignUploadResponseDTO presign(String filename, String contentType);

    AvatarDTO set(String key);

    AvatarDTO remove();
}

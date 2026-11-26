package ru.trafficmarkering.service.studio;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectResponse;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

/** Файлы студии в общем бакете: загрузки, ассеты и готовые ролики. */
@Slf4j
@Component
@RequiredArgsConstructor
public class StudioStorage {

    public static final String UPLOADS = "studio/uploads";
    public static final String ASSETS = "studio/assets";
    public static final String RESULTS = "studio/results";

    private final S3Client s3;

    @Value("${s3.bucket}")
    private String bucket;

    public void download(String key, Path target) {
        try {
            Files.deleteIfExists(target);
            s3.getObject(GetObjectRequest.builder().bucket(bucket).key(key).build(), target);
        } catch (NoSuchKeyException e) {
            throw new StudioException("Исходный файл больше недоступен. Загрузите его ещё раз.");
        } catch (java.io.IOException e) {
            throw new IllegalStateException("download failed: " + key, e);
        }
    }

    public void upload(Path file, String key, String contentType) {
        s3.putObject(PutObjectRequest.builder().bucket(bucket).key(key).contentType(contentType).build(),
                RequestBody.fromFile(file));
    }

    public void upload(byte[] data, String key, String contentType) {
        s3.putObject(PutObjectRequest.builder().bucket(bucket).key(key).contentType(contentType).build(),
                RequestBody.fromBytes(data));
    }

    /** Размер и тип загруженного объекта; пусто — объекта нет */
    public Optional<HeadObjectResponse> head(String key) {
        try {
            return Optional.of(s3.headObject(HeadObjectRequest.builder().bucket(bucket).key(key).build()));
        } catch (NoSuchKeyException e) {
            return Optional.empty();
        } catch (S3Exception e) {
            if (e.statusCode() == 404) {
                return Optional.empty();
            }
            throw e;
        }
    }

    public void delete(String key) {
        if (key == null || key.isBlank()) {
            return;
        }
        try {
            s3.deleteObject(DeleteObjectRequest.builder().bucket(bucket).key(key).build());
        } catch (RuntimeException e) {
            log.warn("Не удалось удалить {} из хранилища: {}", key, e.getMessage());
        }
    }

    /** Ключ, выданный этому пользователю под загрузку */
    public static boolean ownedUpload(String key, Long userId) {
        return key != null && key.matches(UPLOADS + "/" + userId + "/[0-9a-f-]{36}(\\.[a-z0-9]{1,10})?");
    }

    /** Временные входные файлы задачи можно удалять; ассеты библиотеки — нет */
    public static boolean disposable(String key) {
        return key != null && key.startsWith(UPLOADS + "/");
    }
}

package ru.trafficmarkering.service.storage.impl;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import ru.trafficmarkering.service.storage.FileStorage;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;
import software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest;

import java.time.Duration;
import java.util.UUID;

@Service
@RequiredArgsConstructor
class S3FileStorage implements FileStorage {

    private static final Duration GET_PRESIGN_DURATION = Duration.ofHours(1);
    private static final Duration PUT_PRESIGN_DURATION = Duration.ofMinutes(30);

    private final S3Presigner s3Presigner;

    @Value("${s3.bucket}")
    private String bucket;

    @Override
    public PresignedUpload presignUpload(String filename, String contentType, String keyPrefix) {
        String prefix = keyPrefix == null ? "" : keyPrefix.replaceAll("^/+", "").replaceAll("/+$", "");
        String key = (prefix.isEmpty() ? "" : prefix + "/") + UUID.randomUUID() + extractExtension(filename);

        PutObjectRequest.Builder request = PutObjectRequest.builder()
                .bucket(bucket)
                .key(key);
        if (contentType != null && !contentType.isBlank()) {
            request.contentType(contentType);
        }
        String uploadUrl = s3Presigner.presignPutObject(PutObjectPresignRequest.builder()
                        .signatureDuration(PUT_PRESIGN_DURATION)
                        .putObjectRequest(request.build())
                        .build())
                .url().toString();
        return new PresignedUpload(uploadUrl, key);
    }

    @Override
    public String presignedUrl(String key) {
        if (key == null || key.isBlank()) {
            return null;
        }
        GetObjectRequest getObjectRequest = GetObjectRequest.builder()
                .bucket(bucket)
                .key(key)
                .build();
        return s3Presigner.presignGetObject(GetObjectPresignRequest.builder()
                        .getObjectRequest(getObjectRequest)
                        .signatureDuration(GET_PRESIGN_DURATION)
                        .build())
                .url().toString();
    }

    private static String extractExtension(String filename) {
        if (filename == null) {
            return "";
        }
        int dot = filename.lastIndexOf('.');
        if (dot < 0 || dot == filename.length() - 1) {
            return "";
        }
        String ext = filename.substring(dot).toLowerCase();
        return ext.matches("\\.[a-z0-9]{1,10}") ? ext : "";
    }
}

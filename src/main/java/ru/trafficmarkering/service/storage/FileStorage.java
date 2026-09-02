package ru.trafficmarkering.service.storage;

public interface FileStorage {

    record PresignedUpload(String uploadUrl, String key) {}

    PresignedUpload presignUpload(String filename, String contentType, String keyPrefix);

    String presignedUrl(String key);
}

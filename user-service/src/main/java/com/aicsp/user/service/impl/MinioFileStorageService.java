package com.aicsp.user.service.impl;

import com.aicsp.user.config.MinioProperties;
import com.aicsp.user.service.FileStorageService;
import io.minio.BucketExistsArgs;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import java.util.Locale;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

@Service
public class MinioFileStorageService implements FileStorageService {
    private final MinioClient minioClient;
    private final MinioProperties properties;

    public MinioFileStorageService(MinioClient minioClient, MinioProperties properties) {
        this.minioClient = minioClient;
        this.properties = properties;
    }

    @Override
    public String uploadAvatar(String userId, MultipartFile file) {
        if (file == null || file.isEmpty()) {
            return null;
        }
        try {
            ensureBucket();
            String objectName = "avatars/" + userId + "/" + UUID.randomUUID() + extension(file.getOriginalFilename());
            minioClient.putObject(PutObjectArgs.builder()
                    .bucket(properties.getBucket())
                    .object(objectName)
                    .stream(file.getInputStream(), file.getSize(), -1)
                    .contentType(contentType(file))
                    .build());
            return objectPath(objectName);
        } catch (Exception ex) {
            throw new IllegalStateException("Avatar upload failed", ex);
        }
    }

    private void ensureBucket() throws Exception {
        boolean exists = minioClient.bucketExists(BucketExistsArgs.builder().bucket(properties.getBucket()).build());
        if (!exists) {
            minioClient.makeBucket(MakeBucketArgs.builder().bucket(properties.getBucket()).build());
        }
    }

    private String objectPath(String objectName) {
        if (properties.getPublicUrl() == null || properties.getPublicUrl().isBlank()) {
            return properties.getBucket() + "/" + objectName;
        }
        return properties.getPublicUrl().replaceAll("/+$", "") + "/" + properties.getBucket() + "/" + objectName;
    }

    private String extension(String filename) {
        if (filename == null || !filename.contains(".")) {
            return "";
        }
        String extension = filename.substring(filename.lastIndexOf('.')).toLowerCase(Locale.ROOT);
        if (extension.matches("\\.(jpg|jpeg|png|gif|webp)")) {
            return extension;
        }
        return "";
    }

    private String contentType(MultipartFile file) {
        return file.getContentType() == null || file.getContentType().isBlank() ? "application/octet-stream" : file.getContentType();
    }
}

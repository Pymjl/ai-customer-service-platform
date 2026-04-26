package com.aicsp.biz.service.impl;

import com.aicsp.biz.config.MinioProperties;
import com.aicsp.biz.service.BizFileStorageService;
import io.minio.BucketExistsArgs;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import java.util.Locale;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

@Service
@RequiredArgsConstructor
public class MinioBizFileStorageService implements BizFileStorageService {

    private final MinioClient minioClient;
    private final MinioProperties properties;

    @Override
    public String uploadKnowledgeFile(String scope, String documentId, MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("file 不能为空");
        }
        try {
            ensureBucket();
            String objectName = "knowledge/" + scope.toLowerCase(Locale.ROOT) + "/" + documentId + "/"
                    + UUID.randomUUID() + extension(file.getOriginalFilename());
            minioClient.putObject(PutObjectArgs.builder()
                    .bucket(properties.getBucket())
                    .object(objectName)
                    .stream(file.getInputStream(), file.getSize(), -1)
                    .contentType(contentType(file))
                    .build());
            return properties.getBucket() + "/" + objectName;
        } catch (Exception e) {
            throw new IllegalStateException("知识库文件上传失败", e);
        }
    }

    private void ensureBucket() throws Exception {
        boolean exists = minioClient.bucketExists(BucketExistsArgs.builder().bucket(properties.getBucket()).build());
        if (!exists) {
            minioClient.makeBucket(MakeBucketArgs.builder().bucket(properties.getBucket()).build());
        }
    }

    private String extension(String filename) {
        if (filename == null || !filename.contains(".")) {
            return "";
        }
        return filename.substring(filename.lastIndexOf('.')).toLowerCase(Locale.ROOT);
    }

    private String contentType(MultipartFile file) {
        return file.getContentType() == null || file.getContentType().isBlank() ? "application/octet-stream" : file.getContentType();
    }
}

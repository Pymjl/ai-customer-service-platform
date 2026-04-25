package com.aicsp.user.service;

import org.springframework.web.multipart.MultipartFile;

public interface FileStorageService {
    String uploadAvatar(String userId, MultipartFile file);
}

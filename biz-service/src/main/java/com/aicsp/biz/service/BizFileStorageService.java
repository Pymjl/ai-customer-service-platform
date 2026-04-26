package com.aicsp.biz.service;

import org.springframework.web.multipart.MultipartFile;

public interface BizFileStorageService {

    String uploadKnowledgeFile(String scope, String documentId, MultipartFile file);
}

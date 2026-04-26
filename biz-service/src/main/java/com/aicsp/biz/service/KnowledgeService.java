package com.aicsp.biz.service;

import com.aicsp.biz.dto.PageResult;
import com.aicsp.biz.dto.knowledge.IngestionCallbackRequest;
import com.aicsp.biz.dto.knowledge.IngestionTaskDTO;
import com.aicsp.biz.dto.knowledge.KnowledgeCategoryDTO;
import com.aicsp.biz.dto.knowledge.KnowledgeCategoryRequest;
import com.aicsp.biz.dto.knowledge.KnowledgeDocumentDTO;
import com.aicsp.biz.dto.knowledge.KnowledgeDocumentUpdateRequest;
import com.aicsp.biz.dto.knowledge.KnowledgeSelectableResponse;
import com.aicsp.biz.dto.knowledge.KnowledgeTagDTO;
import com.aicsp.biz.dto.knowledge.RetrievalFilterRequest;
import com.aicsp.biz.dto.knowledge.RetrievalFilterResponse;
import java.util.List;
import org.springframework.web.multipart.MultipartFile;

public interface KnowledgeService {

    PageResult<KnowledgeDocumentDTO> listDocuments(String scope, int pageNo, int pageSize, String keyword,
                                                   String categoryId, String status, Boolean enabled);

    KnowledgeDocumentDTO getDocument(String scope, String documentId);

    KnowledgeDocumentDTO uploadDocument(String scope, MultipartFile file, String title, String sourceType,
                                        String categoryId, String productLine, String tags);

    KnowledgeDocumentDTO updateDocument(String scope, String documentId, KnowledgeDocumentUpdateRequest request);

    void deleteDocument(String scope, String documentId);

    void publishDocument(String scope, String documentId);

    void setEnabled(String scope, String documentId, boolean enabled);

    IngestionTaskDTO reindexDocument(String scope, String documentId);

    IngestionTaskDTO getIngestion(String documentId);

    void updateIngestion(IngestionCallbackRequest request);

    List<KnowledgeCategoryDTO> listCategories(String scope);

    KnowledgeCategoryDTO createCategory(KnowledgeCategoryRequest request);

    KnowledgeCategoryDTO updateCategory(String categoryId, KnowledgeCategoryRequest request);

    void deleteCategory(String categoryId);

    List<KnowledgeTagDTO> listTags(String keyword);

    KnowledgeSelectableResponse selectable();

    RetrievalFilterResponse resolveRetrievalFilter(RetrievalFilterRequest request);
}

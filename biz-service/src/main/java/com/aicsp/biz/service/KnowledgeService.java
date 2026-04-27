package com.aicsp.biz.service;

import com.aicsp.biz.dto.PageResult;
import com.aicsp.biz.dto.knowledge.IngestionCallbackRequest;
import com.aicsp.biz.dto.knowledge.IngestionTaskDTO;
import com.aicsp.biz.dto.knowledge.ContributionApplicationDTO;
import com.aicsp.biz.dto.knowledge.ContributionDiffDTO;
import com.aicsp.biz.dto.knowledge.ContributionRequest;
import com.aicsp.biz.dto.knowledge.ContributionReviewRequest;
import com.aicsp.biz.dto.knowledge.KnowledgeBaseDTO;
import com.aicsp.biz.dto.knowledge.KnowledgeBaseRequest;
import com.aicsp.biz.dto.knowledge.KnowledgeCategoryDTO;
import com.aicsp.biz.dto.knowledge.KnowledgeCategoryRequest;
import com.aicsp.biz.dto.knowledge.KnowledgeDocumentDTO;
import com.aicsp.biz.dto.knowledge.KnowledgeDocumentUpdateRequest;
import com.aicsp.biz.dto.knowledge.KnowledgeSelectableResponse;
import com.aicsp.biz.dto.knowledge.KnowledgeTagDTO;
import com.aicsp.biz.dto.knowledge.KnowledgeVersionDTO;
import com.aicsp.biz.dto.knowledge.RetrievalFilterRequest;
import com.aicsp.biz.dto.knowledge.RetrievalFilterResponse;
import com.aicsp.common.dto.event.KnowledgeIngestionTaskEvent;
import java.util.List;
import org.springframework.web.multipart.MultipartFile;

public interface KnowledgeService {

    PageResult<KnowledgeBaseDTO> listKnowledgeBases(String scope, int pageNo, int pageSize, String keyword);

    KnowledgeBaseDTO createKnowledgeBase(KnowledgeBaseRequest request);

    KnowledgeBaseDTO getKnowledgeBase(String kbId);

    KnowledgeBaseDTO updateKnowledgeBase(String kbId, KnowledgeBaseRequest request);

    void deleteKnowledgeBase(String kbId);

    void setKnowledgeBaseEnabled(String kbId, boolean enabled);

    List<KnowledgeVersionDTO> listVersions(String kbId);

    void activateVersion(String kbId, int versionNo);

    PageResult<KnowledgeDocumentDTO> listDocumentsByKb(String kbId, int pageNo, int pageSize, String keyword,
                                                       String categoryId, String status, Boolean enabled);

    KnowledgeDocumentDTO uploadDocumentToKb(String kbId, MultipartFile file, String title, String sourceType,
                                            String categoryId, String productLine, String tags);

    ContributionApplicationDTO contribute(String kbId, ContributionRequest request);

    ContributionApplicationDTO sync(String kbId, ContributionRequest request);

    List<ContributionApplicationDTO> myContributions();

    List<ContributionApplicationDTO> pendingContributions();

    ContributionDiffDTO contributionDiff(String applicationId);

    ContributionApplicationDTO approveContribution(String applicationId, ContributionReviewRequest request);

    ContributionApplicationDTO rejectContribution(String applicationId, ContributionReviewRequest request);

    void dispatchIngestionTask(KnowledgeIngestionTaskEvent event);

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

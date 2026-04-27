package com.aicsp.biz.controller;

import com.aicsp.biz.dto.PageResult;
import com.aicsp.biz.dto.knowledge.ContributionApplicationDTO;
import com.aicsp.biz.dto.knowledge.ContributionDiffDTO;
import com.aicsp.biz.dto.knowledge.ContributionRequest;
import com.aicsp.biz.dto.knowledge.ContributionReviewRequest;
import com.aicsp.biz.dto.knowledge.IngestionTaskDTO;
import com.aicsp.biz.dto.knowledge.KnowledgeBaseDTO;
import com.aicsp.biz.dto.knowledge.KnowledgeBaseRequest;
import com.aicsp.biz.dto.knowledge.KnowledgeCategoryDTO;
import com.aicsp.biz.dto.knowledge.KnowledgeCategoryRequest;
import com.aicsp.biz.dto.knowledge.KnowledgeDocumentDTO;
import com.aicsp.biz.dto.knowledge.KnowledgeDocumentUpdateRequest;
import com.aicsp.biz.dto.knowledge.KnowledgeSelectableResponse;
import com.aicsp.biz.dto.knowledge.KnowledgeTagDTO;
import com.aicsp.biz.service.KnowledgeService;
import com.aicsp.common.exception.BizException;
import com.aicsp.common.result.R;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/knowledge")
@RequiredArgsConstructor
public class KnowledgeController {

    private final KnowledgeService knowledgeService;

    @GetMapping("/kbs")
    public R<PageResult<KnowledgeBaseDTO>> listKnowledgeBases(@RequestParam(defaultValue = "PUBLIC") String scope,
                                                              @RequestParam(defaultValue = "1") int pageNo,
                                                              @RequestParam(defaultValue = "20") int pageSize,
                                                              @RequestParam(required = false) String keyword) {
        return R.ok(knowledgeService.listKnowledgeBases(scope, pageNo, pageSize, keyword));
    }

    @PostMapping("/kbs")
    public R<KnowledgeBaseDTO> createKnowledgeBase(@Valid @RequestBody KnowledgeBaseRequest request) {
        return R.ok(knowledgeService.createKnowledgeBase(request));
    }

    @GetMapping("/kbs/{kbId}")
    public R<KnowledgeBaseDTO> getKnowledgeBase(@PathVariable String kbId) {
        return R.ok(knowledgeService.getKnowledgeBase(kbId));
    }

    @PutMapping("/kbs/{kbId}")
    public R<KnowledgeBaseDTO> updateKnowledgeBase(@PathVariable String kbId,
                                                   @RequestBody KnowledgeBaseRequest request) {
        return R.ok(knowledgeService.updateKnowledgeBase(kbId, request));
    }

    @DeleteMapping("/kbs/{kbId}")
    public R<?> deleteKnowledgeBase(@PathVariable String kbId) {
        knowledgeService.deleteKnowledgeBase(kbId);
        return R.ok();
    }

    @PostMapping("/kbs/{kbId}/enable")
    public R<?> enableKnowledgeBase(@PathVariable String kbId) {
        knowledgeService.setKnowledgeBaseEnabled(kbId, true);
        return R.ok();
    }

    @PostMapping("/kbs/{kbId}/disable")
    public R<?> disableKnowledgeBase(@PathVariable String kbId) {
        knowledgeService.setKnowledgeBaseEnabled(kbId, false);
        return R.ok();
    }

    @GetMapping("/kbs/{kbId}/versions")
    public R<List<com.aicsp.biz.dto.knowledge.KnowledgeVersionDTO>> listVersions(@PathVariable String kbId) {
        return R.ok(knowledgeService.listVersions(kbId));
    }

    @PostMapping("/kbs/{kbId}/versions/{versionNo}/activate")
    public R<?> activateVersion(@PathVariable String kbId, @PathVariable int versionNo) {
        knowledgeService.activateVersion(kbId, versionNo);
        return R.ok();
    }

    @GetMapping("/kbs/{kbId}/documents")
    public R<PageResult<KnowledgeDocumentDTO>> listDocumentsByKb(@PathVariable String kbId,
                                                                 @RequestParam(defaultValue = "1") int pageNo,
                                                                 @RequestParam(defaultValue = "20") int pageSize,
                                                                 @RequestParam(required = false) String keyword,
                                                                 @RequestParam(required = false) String categoryId,
                                                                 @RequestParam(required = false) String status,
                                                                 @RequestParam(required = false) Boolean enabled) {
        return R.ok(knowledgeService.listDocumentsByKb(kbId, pageNo, pageSize, keyword, categoryId, status, enabled));
    }

    @PostMapping(value = "/kbs/{kbId}/documents", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public R<KnowledgeDocumentDTO> uploadDocumentToKb(@PathVariable String kbId,
                                                      @RequestPart("file") MultipartFile file,
                                                      @RequestParam String title,
                                                      @RequestParam(required = false) String sourceType,
                                                      @RequestParam(required = false) String categoryId,
                                                      @RequestParam(required = false) String productLine,
                                                      @RequestParam(required = false) String tags) {
        return R.ok(knowledgeService.uploadDocumentToKb(kbId, file, title, sourceType, categoryId, productLine, tags));
    }

    @PostMapping("/kbs/{kbId}/documents/{documentId}/enable")
    public R<?> enableKbDocument(@PathVariable String kbId, @PathVariable String documentId) {
        KnowledgeBaseDTO kb = knowledgeService.getKnowledgeBase(kbId);
        knowledgeService.setEnabled(kb.getScope().toLowerCase(), documentId, true);
        return R.ok();
    }

    @PostMapping("/kbs/{kbId}/documents/{documentId}/disable")
    public R<?> disableKbDocument(@PathVariable String kbId, @PathVariable String documentId) {
        KnowledgeBaseDTO kb = knowledgeService.getKnowledgeBase(kbId);
        knowledgeService.setEnabled(kb.getScope().toLowerCase(), documentId, false);
        return R.ok();
    }

    @PostMapping("/kbs/{kbId}/documents/{documentId}/reindex")
    public R<IngestionTaskDTO> reindexKbDocument(@PathVariable String kbId, @PathVariable String documentId) {
        KnowledgeBaseDTO kb = knowledgeService.getKnowledgeBase(kbId);
        return R.ok(knowledgeService.reindexDocument(kb.getScope().toLowerCase(), documentId));
    }

    @PostMapping("/kbs/{kbId}/contribute")
    public R<ContributionApplicationDTO> contribute(@PathVariable String kbId, @RequestBody(required = false) ContributionRequest request) {
        return R.ok(knowledgeService.contribute(kbId, request));
    }

    @PostMapping("/kbs/{kbId}/sync")
    public R<ContributionApplicationDTO> sync(@PathVariable String kbId, @RequestBody(required = false) ContributionRequest request) {
        return R.ok(knowledgeService.sync(kbId, request));
    }

    @GetMapping("/contributions/mine")
    public R<List<ContributionApplicationDTO>> myContributions() {
        return R.ok(knowledgeService.myContributions());
    }

    @GetMapping("/contributions/pending")
    public R<List<ContributionApplicationDTO>> pendingContributions() {
        return R.ok(knowledgeService.pendingContributions());
    }

    @GetMapping("/contributions/{applicationId}/diff")
    public R<ContributionDiffDTO> contributionDiff(@PathVariable String applicationId) {
        return R.ok(knowledgeService.contributionDiff(applicationId));
    }

    @PostMapping("/contributions/{applicationId}/approve")
    public R<ContributionApplicationDTO> approveContribution(@PathVariable String applicationId,
                                                             @RequestBody(required = false) ContributionReviewRequest request) {
        return R.ok(knowledgeService.approveContribution(applicationId, request));
    }

    @PostMapping("/contributions/{applicationId}/reject")
    public R<ContributionApplicationDTO> rejectContribution(@PathVariable String applicationId,
                                                            @RequestBody ContributionReviewRequest request) {
        return R.ok(knowledgeService.rejectContribution(applicationId, request));
    }

    @GetMapping("/{scope:public|personal}/documents")
    public R<PageResult<KnowledgeDocumentDTO>> listDocuments(@PathVariable String scope,
                                                             @RequestParam(defaultValue = "1") int pageNo,
                                                             @RequestParam(defaultValue = "20") int pageSize,
                                                             @RequestParam(required = false) String keyword,
                                                             @RequestParam(required = false) String categoryId,
                                                             @RequestParam(required = false) String status,
                                                             @RequestParam(required = false) Boolean enabled) {
        return R.ok(knowledgeService.listDocuments(scope, pageNo, pageSize, keyword, categoryId, status, enabled));
    }

    @GetMapping("/{scope:public|personal}/documents/{documentId}")
    public R<KnowledgeDocumentDTO> getDocument(@PathVariable String scope, @PathVariable String documentId) {
        return R.ok(knowledgeService.getDocument(scope, documentId));
    }

    @GetMapping("/{scope:public|personal}/documents/{documentId}/preview")
    public R<KnowledgeDocumentDTO> previewDocument(@PathVariable String scope, @PathVariable String documentId) {
        return R.ok(knowledgeService.getDocument(scope, documentId));
    }

    @PostMapping(value = "/{scope:public|personal}/documents", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public R<KnowledgeDocumentDTO> uploadDocument(@PathVariable String scope,
                                                  @RequestPart("file") MultipartFile file,
                                                  @RequestParam String title,
                                                  @RequestParam(required = false) String sourceType,
                                                  @RequestParam(required = false) String categoryId,
                                                  @RequestParam(required = false) String productLine,
                                                  @RequestParam(required = false) String tags) {
        return R.ok(knowledgeService.uploadDocument(scope, file, title, sourceType, categoryId, productLine, tags));
    }

    @PutMapping("/{scope:public|personal}/documents/{documentId}")
    public R<KnowledgeDocumentDTO> updateDocument(@PathVariable String scope, @PathVariable String documentId,
                                                  @RequestBody KnowledgeDocumentUpdateRequest request) {
        return R.ok(knowledgeService.updateDocument(scope, documentId, request));
    }

    @DeleteMapping("/{scope:public|personal}/documents/{documentId}")
    public R<?> deleteDocument(@PathVariable String scope, @PathVariable String documentId) {
        knowledgeService.deleteDocument(scope, documentId);
        return R.ok();
    }

    @PostMapping("/{scope:public|personal}/documents/{documentId}/publish")
    public R<?> publishDocument(@PathVariable String scope, @PathVariable String documentId) {
        knowledgeService.publishDocument(scope, documentId);
        return R.ok();
    }

    @PostMapping("/{scope:public|personal}/documents/{documentId}/enable")
    public R<?> enableDocument(@PathVariable String scope, @PathVariable String documentId) {
        knowledgeService.setEnabled(scope, documentId, true);
        return R.ok();
    }

    @PostMapping("/{scope:public|personal}/documents/{documentId}/disable")
    public R<?> disableDocument(@PathVariable String scope, @PathVariable String documentId) {
        knowledgeService.setEnabled(scope, documentId, false);
        return R.ok();
    }

    @PostMapping("/{scope:public|personal}/documents/{documentId}/reindex")
    public R<IngestionTaskDTO> reindexDocument(@PathVariable String scope, @PathVariable String documentId) {
        return R.ok(knowledgeService.reindexDocument(scope, documentId));
    }

    @GetMapping("/documents/{documentId}/ingestion")
    public R<IngestionTaskDTO> getIngestion(@PathVariable String documentId) {
        return R.ok(knowledgeService.getIngestion(documentId));
    }

    @PostMapping("/documents/{documentId}/retry")
    public R<IngestionTaskDTO> retryIngestion(@PathVariable String documentId) {
        KnowledgeDocumentDTO document;
        try {
            document = knowledgeService.getDocument("public", documentId);
        } catch (BizException e) {
            document = knowledgeService.getDocument("personal", documentId);
        }
        return R.ok(knowledgeService.reindexDocument(document.getScope(), documentId));
    }

    @GetMapping("/categories")
    public R<List<KnowledgeCategoryDTO>> listCategories(@RequestParam(defaultValue = "PUBLIC") String scope) {
        return R.ok(knowledgeService.listCategories(scope));
    }

    @PostMapping("/categories")
    public R<KnowledgeCategoryDTO> createCategory(@Valid @RequestBody KnowledgeCategoryRequest request) {
        return R.ok(knowledgeService.createCategory(request));
    }

    @PutMapping("/categories/{categoryId}")
    public R<KnowledgeCategoryDTO> updateCategory(@PathVariable String categoryId,
                                                  @Valid @RequestBody KnowledgeCategoryRequest request) {
        return R.ok(knowledgeService.updateCategory(categoryId, request));
    }

    @DeleteMapping("/categories/{categoryId}")
    public R<?> deleteCategory(@PathVariable String categoryId) {
        knowledgeService.deleteCategory(categoryId);
        return R.ok();
    }

    @GetMapping("/tags")
    public R<List<KnowledgeTagDTO>> listTags(@RequestParam(required = false) String keyword) {
        return R.ok(knowledgeService.listTags(keyword));
    }

    @GetMapping("/selectable")
    public R<KnowledgeSelectableResponse> selectable() {
        return R.ok(knowledgeService.selectable());
    }
}

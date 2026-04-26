package com.aicsp.biz.controller;

import com.aicsp.biz.dto.PageResult;
import com.aicsp.biz.dto.knowledge.IngestionTaskDTO;
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

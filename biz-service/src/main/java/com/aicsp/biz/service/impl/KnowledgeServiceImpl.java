package com.aicsp.biz.service.impl;

import com.aicsp.biz.dto.PageResult;
import com.aicsp.biz.dto.knowledge.IngestionCallbackRequest;
import com.aicsp.biz.dto.knowledge.IngestionTaskDTO;
import com.aicsp.biz.dto.knowledge.KnowledgeCategoryDTO;
import com.aicsp.biz.dto.knowledge.KnowledgeCategoryRequest;
import com.aicsp.biz.dto.knowledge.KnowledgeDocumentDTO;
import com.aicsp.biz.dto.knowledge.KnowledgeDocumentUpdateRequest;
import com.aicsp.biz.dto.knowledge.KnowledgeSelection;
import com.aicsp.biz.dto.knowledge.KnowledgeSelectableResponse;
import com.aicsp.biz.dto.knowledge.KnowledgeTagDTO;
import com.aicsp.biz.dto.knowledge.RetrievalFilterRequest;
import com.aicsp.biz.dto.knowledge.RetrievalFilterResponse;
import com.aicsp.biz.entity.KnowledgeCategory;
import com.aicsp.biz.entity.KnowledgeDocument;
import com.aicsp.biz.entity.KnowledgeIngestionTask;
import com.aicsp.biz.entity.KnowledgeTag;
import com.aicsp.biz.mapper.KnowledgeMapper;
import com.aicsp.biz.security.RequestIdentity;
import com.aicsp.biz.security.RequestIdentityProvider;
import com.aicsp.biz.service.BizFileStorageService;
import com.aicsp.biz.service.KnowledgeService;
import com.aicsp.common.exception.BizException;
import com.aicsp.common.exception.ErrorCode;
import com.aicsp.common.util.DistributedIdUtils;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

@Service
@RequiredArgsConstructor
public class KnowledgeServiceImpl implements KnowledgeService {

    private static final int MAX_PAGE_SIZE = 100;
    private static final List<String> TERMINAL_TASK_STATUSES = List.of("READY", "FAILED", "FAILED_RETRYABLE", "DELETED");

    private final KnowledgeMapper knowledgeMapper;
    private final RequestIdentityProvider identityProvider;
    private final BizFileStorageService fileStorageService;

    @Override
    public PageResult<KnowledgeDocumentDTO> listDocuments(String scope, int pageNo, int pageSize, String keyword,
                                                          String categoryId, String status, Boolean enabled) {
        RequestIdentity identity = identityProvider.current();
        String normalizedScope = normalizeScope(scope);
        assertReadable(normalizedScope, identity);
        int resolvedPageNo = Math.max(pageNo, 1);
        int resolvedPageSize = Math.min(Math.max(pageSize, 1), MAX_PAGE_SIZE);
        String ownerUserId = ownerForQuery(normalizedScope, identity);
        long total = knowledgeMapper.countDocuments(identity.tenantId(), normalizedScope, ownerUserId, keyword, categoryId, status, enabled);
        List<KnowledgeDocumentDTO> records = knowledgeMapper.listDocuments(identity.tenantId(), normalizedScope, ownerUserId, keyword,
                        categoryId, status, enabled, (resolvedPageNo - 1) * resolvedPageSize, resolvedPageSize)
                .stream().map(this::toDocumentDTO).toList();
        return PageResult.<KnowledgeDocumentDTO>builder()
                .records(records)
                .pageNo(resolvedPageNo)
                .pageSize(resolvedPageSize)
                .total(total)
                .build();
    }

    @Override
    public KnowledgeDocumentDTO getDocument(String scope, String documentId) {
        RequestIdentity identity = identityProvider.current();
        KnowledgeDocument document = loadAccessibleDocument(normalizeScope(scope), documentId, identity);
        return toDocumentDTO(document);
    }

    @Override
    @Transactional
    public KnowledgeDocumentDTO uploadDocument(String scope, MultipartFile file, String title, String sourceType,
                                               String categoryId, String productLine, String tags) {
        RequestIdentity identity = identityProvider.current();
        String normalizedScope = normalizeScope(scope);
        assertWritable(normalizedScope, identity);
        String documentId = "doc_" + UUID.randomUUID().toString().replace("-", "");
        String objectPath = fileStorageService.uploadKnowledgeFile(normalizedScope, documentId, file);
        KnowledgeDocument document = new KnowledgeDocument();
        document.setId(DistributedIdUtils.nextId());
        document.setDocumentId(documentId);
        document.setTenantId(identity.tenantId());
        document.setScope(normalizedScope);
        document.setOwnerUserId("PERSONAL".equals(normalizedScope) ? identity.userId() : null);
        document.setTitle(StringUtils.hasText(title) ? title : file.getOriginalFilename());
        document.setSourceType(StringUtils.hasText(sourceType) ? sourceType.toUpperCase(Locale.ROOT) : inferSourceType(file.getOriginalFilename()));
        document.setObjectPath(objectPath);
        document.setCategoryId(categoryId);
        document.setProductLine(productLine);
        document.setStatus("UPLOADED");
        document.setEnabled(true);
        document.setCreatedBy(0L);
        document.setUpdatedBy(0L);
        document.setDeleted(false);
        knowledgeMapper.insertDocument(document);
        replaceTags(identity.tenantId(), documentId, splitTags(tags));
        createTask(documentId, "INGEST", "SUBMITTED");
        return toDocumentDTO(document);
    }

    @Override
    @Transactional
    public KnowledgeDocumentDTO updateDocument(String scope, String documentId, KnowledgeDocumentUpdateRequest request) {
        RequestIdentity identity = identityProvider.current();
        KnowledgeDocument document = loadManageableDocument(normalizeScope(scope), documentId, identity);
        if (StringUtils.hasText(request.getTitle())) {
            document.setTitle(request.getTitle());
        }
        if (StringUtils.hasText(request.getSourceType())) {
            document.setSourceType(request.getSourceType().toUpperCase(Locale.ROOT));
        }
        document.setCategoryId(request.getCategoryId());
        document.setProductLine(request.getProductLine());
        knowledgeMapper.updateDocument(document);
        if (request.getTags() != null) {
            replaceTags(identity.tenantId(), documentId, request.getTags());
        }
        return toDocumentDTO(knowledgeMapper.findDocument(documentId));
    }

    @Override
    @Transactional
    public void deleteDocument(String scope, String documentId) {
        RequestIdentity identity = identityProvider.current();
        loadManageableDocument(normalizeScope(scope), documentId, identity);
        knowledgeMapper.updateDocumentState(documentId, "DELETED", false);
        knowledgeMapper.softDeleteDocument(documentId);
        createTask(documentId, "DELETE", "SUBMITTED");
    }

    @Override
    @Transactional
    public void publishDocument(String scope, String documentId) {
        RequestIdentity identity = identityProvider.current();
        loadManageableDocument(normalizeScope(scope), documentId, identity);
        knowledgeMapper.updateDocumentState(documentId, "READY", true);
        KnowledgeIngestionTask latestTask = knowledgeMapper.findLatestTask(documentId);
        if (latestTask != null && !TERMINAL_TASK_STATUSES.contains(latestTask.getStatus())) {
            latestTask.setStatus("READY");
            latestTask.setProgress(100);
            knowledgeMapper.updateTask(latestTask);
        }
    }

    @Override
    @Transactional
    public void setEnabled(String scope, String documentId, boolean enabled) {
        RequestIdentity identity = identityProvider.current();
        KnowledgeDocument document = loadManageableDocument(normalizeScope(scope), documentId, identity);
        if ("PUBLIC".equals(document.getScope()) && "CASE_LIBRARY".equalsIgnoreCase(document.getSourceType()) && !identity.isSuperAdmin()) {
            throw new BizException(ErrorCode.PARAM_INVALID, "客服案例库仅超级管理员可启用或禁用");
        }
        knowledgeMapper.updateDocumentState(documentId, null, enabled);
    }

    @Override
    @Transactional
    public IngestionTaskDTO reindexDocument(String scope, String documentId) {
        RequestIdentity identity = identityProvider.current();
        loadManageableDocument(normalizeScope(scope), documentId, identity);
        return toTaskDTO(createTask(documentId, "REINDEX", "SUBMITTED"));
    }

    @Override
    public IngestionTaskDTO getIngestion(String documentId) {
        RequestIdentity identity = identityProvider.current();
        KnowledgeDocument document = knowledgeMapper.findDocument(documentId);
        assertDocumentAccess(document, identity);
        KnowledgeIngestionTask task = knowledgeMapper.findLatestTask(documentId);
        if (task == null) {
            return null;
        }
        return toTaskDTO(task);
    }

    @Override
    @Transactional
    public void updateIngestion(IngestionCallbackRequest request) {
        KnowledgeIngestionTask task = knowledgeMapper.findLatestTask(request.getDocumentId());
        if (task == null || !Objects.equals(task.getTaskId(), request.getTaskId())) {
            throw new BizException(ErrorCode.PARAM_INVALID, "入库任务不存在");
        }
        task.setStatus(StringUtils.hasText(request.getStatus()) ? request.getStatus() : task.getStatus());
        task.setProgress(request.getProgress());
        task.setChunkCount(request.getChunkCount());
        task.setEmbeddingModel(request.getEmbeddingModel());
        task.setErrorMessage(request.getErrorMessage());
        knowledgeMapper.updateTask(task);
        if ("READY".equals(task.getStatus())) {
            knowledgeMapper.updateDocumentState(task.getDocumentId(), "READY", true);
        } else if ("FAILED".equals(task.getStatus()) || "FAILED_RETRYABLE".equals(task.getStatus())) {
            knowledgeMapper.updateDocumentState(task.getDocumentId(), "FAILED", null);
        }
    }

    @Override
    public List<KnowledgeCategoryDTO> listCategories(String scope) {
        RequestIdentity identity = identityProvider.current();
        String normalizedScope = normalizeScope(scope);
        return knowledgeMapper.listCategories(identity.tenantId(), normalizedScope, ownerForQuery(normalizedScope, identity))
                .stream().map(this::toCategoryDTO).toList();
    }

    @Override
    @Transactional
    public KnowledgeCategoryDTO createCategory(KnowledgeCategoryRequest request) {
        RequestIdentity identity = identityProvider.current();
        String normalizedScope = normalizeScope(request.getScope());
        assertWritable(normalizedScope, identity);
        KnowledgeCategory category = new KnowledgeCategory();
        category.setId(DistributedIdUtils.nextId());
        category.setCategoryId("cat_" + UUID.randomUUID().toString().replace("-", ""));
        category.setTenantId(identity.tenantId());
        category.setScope(normalizedScope);
        category.setOwnerUserId("PERSONAL".equals(normalizedScope) ? identity.userId() : null);
        category.setParentId(request.getParentId());
        category.setName(request.getName());
        category.setSortOrder(request.getSortOrder() == null ? 0 : request.getSortOrder());
        category.setDeleted(false);
        knowledgeMapper.insertCategory(category);
        return toCategoryDTO(category);
    }

    @Override
    @Transactional
    public KnowledgeCategoryDTO updateCategory(String categoryId, KnowledgeCategoryRequest request) {
        RequestIdentity identity = identityProvider.current();
        String normalizedScope = normalizeScope(request.getScope());
        assertWritable(normalizedScope, identity);
        KnowledgeCategory category = new KnowledgeCategory();
        category.setCategoryId(categoryId);
        category.setScope(normalizedScope);
        category.setOwnerUserId("PERSONAL".equals(normalizedScope) ? identity.userId() : null);
        category.setParentId(request.getParentId());
        category.setName(request.getName());
        category.setSortOrder(request.getSortOrder());
        int updated = knowledgeMapper.updateCategory(category);
        if (updated == 0) {
            throw new BizException(ErrorCode.PARAM_INVALID, "分类不存在或无权修改");
        }
        category.setTenantId(identity.tenantId());
        return toCategoryDTO(category);
    }

    @Override
    @Transactional
    public void deleteCategory(String categoryId) {
        RequestIdentity identity = identityProvider.current();
        KnowledgeCategory category = loadManageableCategory(categoryId, identity);
        if (knowledgeMapper.countCategoryDocuments(categoryId) > 0) {
            throw new BizException(ErrorCode.PARAM_INVALID, "分类下存在文档，不能删除");
        }
        int deleted = knowledgeMapper.deleteCategory(category.getCategoryId(), identity.tenantId(),
                category.getScope(), ownerForQuery(category.getScope(), identity));
        if (deleted == 0) {
            throw new BizException(ErrorCode.PARAM_INVALID, "分类不存在或无权删除");
        }
    }

    @Override
    public List<KnowledgeTagDTO> listTags(String keyword) {
        RequestIdentity identity = identityProvider.current();
        return knowledgeMapper.listTags(identity.tenantId(), keyword).stream().map(this::toTagDTO).toList();
    }

    @Override
    public KnowledgeSelectableResponse selectable() {
        RequestIdentity identity = identityProvider.current();
        List<KnowledgeDocument> documents = knowledgeMapper.listSelectableDocuments(identity.tenantId(), identity.userId());
        Map<String, KnowledgeSelectableResponse.CategoryOption> categories = new LinkedHashMap<>();
        for (KnowledgeDocument document : documents) {
            if (StringUtils.hasText(document.getCategoryId())) {
                categories.computeIfAbsent(document.getCategoryId(), categoryId -> KnowledgeSelectableResponse.CategoryOption.builder()
                        .categoryId(categoryId)
                        .scope(document.getScope())
                        .name(categoryId)
                        .documentCount(0L)
                        .build()).setDocumentCount(categories.getOrDefault(document.getCategoryId(),
                                KnowledgeSelectableResponse.CategoryOption.builder().documentCount(0L).build()).getDocumentCount() + 1);
            }
        }
        return KnowledgeSelectableResponse.builder()
                .defaultMode("DEFAULT")
                .scopes(List.of(
                        KnowledgeSelectableResponse.ScopeOption.builder()
                                .scope("PUBLIC").enabled(true).editable(false)
                                .documentCount(knowledgeMapper.countSelectable(identity.tenantId(), "PUBLIC", null)).build(),
                        KnowledgeSelectableResponse.ScopeOption.builder()
                                .scope("PERSONAL").enabled(true).editable(true)
                                .documentCount(knowledgeMapper.countSelectable(identity.tenantId(), "PERSONAL", identity.userId())).build()))
                .categories(new ArrayList<>(categories.values()))
                .tags(listTags(null))
                .documents(documents.stream().map(document -> KnowledgeSelectableResponse.DocumentOption.builder()
                        .documentId(document.getDocumentId())
                        .scope(document.getScope())
                        .title(document.getTitle())
                        .status(document.getStatus())
                        .categoryId(document.getCategoryId())
                        .updatedAt(document.getUpdatedAt())
                        .build()).toList())
                .build();
    }

    @Override
    public RetrievalFilterResponse resolveRetrievalFilter(RetrievalFilterRequest request) {
        KnowledgeSelection selection = request.getKnowledgeSelection();
        String mode = selection == null || !StringUtils.hasText(selection.getMode())
                ? "DEFAULT" : selection.getMode().toUpperCase(Locale.ROOT);
        boolean disabledMode = "NONE".equals(mode) || "DISABLED".equals(mode);
        List<String> allowedScopes = disabledMode ? List.of() : resolveAllowedScopes(mode, selection);
        boolean skipRetrieval = disabledMode || allowedScopes.isEmpty();
        Map<String, Object> filters = new LinkedHashMap<>();
        filters.put("status", "READY");
        filters.put("enabled", true);
        filters.put("tenantId", request.getTenantId());
        if (allowedScopes.contains("PUBLIC")) {
            filters.put("publicOwnerIsNull", true);
        }
        if (allowedScopes.contains("PERSONAL")) {
            filters.put("personalOwnerUserId", request.getUserId());
        }
        if (StringUtils.hasText(request.getProductLine())) {
            filters.put("productLine", request.getProductLine());
        }
        if (selection != null) {
            filters.put("documentIds", selection.getDocumentIds());
            filters.put("categoryIds", selection.getCategoryIds());
            filters.put("tagIds", selection.getTagIds());
        }
        return RetrievalFilterResponse.builder()
                .mode(mode)
                .skipRetrieval(skipRetrieval)
                .tenantId(request.getTenantId())
                .allowedScopes(allowedScopes)
                .filters(filters)
                .deniedCandidates(List.of())
                .build();
    }

    private List<String> resolveAllowedScopes(String mode, KnowledgeSelection selection) {
        boolean includePublic = selection == null || !Boolean.FALSE.equals(selection.getIncludePublic());
        boolean includePersonal = selection == null || !Boolean.FALSE.equals(selection.getIncludePersonal());
        if ("PUBLIC_ONLY".equals(mode)) {
            includePersonal = false;
        } else if ("PERSONAL_ONLY".equals(mode)) {
            includePublic = false;
        }

        List<String> scopes = new ArrayList<>(2);
        if (includePublic) {
            scopes.add("PUBLIC");
        }
        if (includePersonal) {
            scopes.add("PERSONAL");
        }
        return scopes;
    }

    private KnowledgeDocument loadAccessibleDocument(String scope, String documentId, RequestIdentity identity) {
        KnowledgeDocument document = knowledgeMapper.findDocument(documentId);
        assertDocumentAccess(document, identity);
        if (!scope.equals(document.getScope())) {
            throw new BizException(ErrorCode.PARAM_INVALID, "文档范围不匹配");
        }
        return document;
    }

    private KnowledgeDocument loadManageableDocument(String scope, String documentId, RequestIdentity identity) {
        KnowledgeDocument document = loadAccessibleDocument(scope, documentId, identity);
        assertWritable(document.getScope(), identity);
        return document;
    }

    private KnowledgeCategory loadManageableCategory(String categoryId, RequestIdentity identity) {
        KnowledgeCategory category = knowledgeMapper.findCategory(categoryId);
        if (category == null || Boolean.TRUE.equals(category.getDeleted()) || !identity.tenantId().equals(category.getTenantId())) {
            throw new BizException(ErrorCode.PARAM_INVALID, "分类不存在或无权访问");
        }
        assertWritable(category.getScope(), identity);
        if ("PERSONAL".equals(category.getScope()) && !identity.userId().equals(category.getOwnerUserId())) {
            throw new BizException(ErrorCode.PARAM_INVALID, "分类不存在或无权访问");
        }
        return category;
    }

    private void assertDocumentAccess(KnowledgeDocument document, RequestIdentity identity) {
        if (document == null || Boolean.TRUE.equals(document.getDeleted()) || !identity.tenantId().equals(document.getTenantId())) {
            throw new BizException(ErrorCode.PARAM_INVALID, "文档不存在或无权访问");
        }
        if ("PERSONAL".equals(document.getScope()) && !identity.userId().equals(document.getOwnerUserId())) {
            throw new BizException(ErrorCode.PARAM_INVALID, "文档不存在或无权访问");
        }
    }

    private void assertReadable(String scope, RequestIdentity identity) {
        if ("PERSONAL".equals(scope) && !StringUtils.hasText(identity.userId())) {
            throw new BizException(ErrorCode.PARAM_INVALID, "无权访问个人知识库");
        }
    }

    private void assertWritable(String scope, RequestIdentity identity) {
        if ("PUBLIC".equals(scope) && !identity.isAdmin()) {
            throw new BizException(ErrorCode.PARAM_INVALID, "无权维护公共知识库");
        }
    }

    private String ownerForQuery(String scope, RequestIdentity identity) {
        return "PERSONAL".equals(scope) ? identity.userId() : null;
    }

    private String normalizeScope(String scope) {
        if (!StringUtils.hasText(scope)) {
            return "PUBLIC";
        }
        String normalized = scope.toUpperCase(Locale.ROOT);
        if (!"PUBLIC".equals(normalized) && !"PERSONAL".equals(normalized)) {
            throw new BizException(ErrorCode.PARAM_INVALID, "scope 只能是 PUBLIC 或 PERSONAL");
        }
        return normalized;
    }

    private KnowledgeIngestionTask createTask(String documentId, String taskType, String status) {
        KnowledgeIngestionTask task = new KnowledgeIngestionTask();
        task.setTaskId("kb_task_" + UUID.randomUUID().toString().replace("-", ""));
        task.setDocumentId(documentId);
        task.setTaskType(taskType);
        task.setStatus(status);
        task.setProgress(0);
        task.setChunkCount(0);
        task.setRetryCount(0);
        knowledgeMapper.insertTask(task);
        return task;
    }

    private void replaceTags(String tenantId, String documentId, List<String> tags) {
        knowledgeMapper.deleteDocumentTags(documentId);
        for (String tagName : tags.stream().filter(StringUtils::hasText).map(String::trim).distinct().toList()) {
            KnowledgeTag tag = knowledgeMapper.findTagByName(tenantId, tagName);
            if (tag == null) {
                tag = new KnowledgeTag();
                tag.setId(DistributedIdUtils.nextId());
                tag.setTagId("tag_" + UUID.randomUUID().toString().replace("-", ""));
                tag.setTenantId(tenantId);
                tag.setName(tagName);
                tag.setDeleted(false);
                knowledgeMapper.insertTag(tag);
            }
            knowledgeMapper.insertDocumentTag(documentId, tag.getTagId());
        }
    }

    private List<String> splitTags(String tags) {
        if (!StringUtils.hasText(tags)) {
            return List.of();
        }
        return List.of(tags.split(","));
    }

    private String inferSourceType(String filename) {
        if (!StringUtils.hasText(filename) || !filename.contains(".")) {
            return "FILE";
        }
        return filename.substring(filename.lastIndexOf('.') + 1).toUpperCase(Locale.ROOT);
    }

    private KnowledgeDocumentDTO toDocumentDTO(KnowledgeDocument document) {
        KnowledgeDocumentDTO dto = new KnowledgeDocumentDTO();
        dto.setDocumentId(document.getDocumentId());
        dto.setScope(document.getScope());
        dto.setTitle(document.getTitle());
        dto.setSourceType(document.getSourceType());
        dto.setObjectPath(document.getObjectPath());
        dto.setCategoryId(document.getCategoryId());
        dto.setProductLine(document.getProductLine());
        dto.setStatus(document.getStatus());
        dto.setEnabled(document.getEnabled());
        dto.setTags(knowledgeMapper.listDocumentTags(document.getDocumentId()));
        dto.setCreatedAt(document.getCreatedAt());
        dto.setUpdatedAt(document.getUpdatedAt());
        return dto;
    }

    private KnowledgeCategoryDTO toCategoryDTO(KnowledgeCategory category) {
        KnowledgeCategoryDTO dto = new KnowledgeCategoryDTO();
        dto.setCategoryId(category.getCategoryId());
        dto.setScope(category.getScope());
        dto.setParentId(category.getParentId());
        dto.setName(category.getName());
        dto.setSortOrder(category.getSortOrder());
        dto.setDocumentCount(knowledgeMapper.countCategoryDocuments(category.getCategoryId()));
        return dto;
    }

    private KnowledgeTagDTO toTagDTO(KnowledgeTag tag) {
        KnowledgeTagDTO dto = new KnowledgeTagDTO();
        dto.setTagId(tag.getTagId());
        dto.setName(tag.getName());
        dto.setDocumentCount(knowledgeMapper.countTagDocuments(tag.getTagId()));
        return dto;
    }

    private IngestionTaskDTO toTaskDTO(KnowledgeIngestionTask task) {
        IngestionTaskDTO dto = new IngestionTaskDTO();
        dto.setDocumentId(task.getDocumentId());
        dto.setTaskId(task.getTaskId());
        dto.setStatus(task.getStatus());
        dto.setProgress(task.getProgress());
        dto.setChunkCount(task.getChunkCount());
        dto.setEmbeddingModel(task.getEmbeddingModel());
        dto.setErrorMessage(task.getErrorMessage());
        dto.setUpdatedAt(task.getUpdatedAt());
        return dto;
    }

    @SuppressWarnings("unused")
    private String hashDocumentId(String documentId) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return "sha256:" + HexFormat.of().formatHex(digest.digest(documentId.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            return "sha256:unavailable";
        }
    }
}

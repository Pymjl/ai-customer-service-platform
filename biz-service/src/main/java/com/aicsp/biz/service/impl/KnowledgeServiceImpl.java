package com.aicsp.biz.service.impl;

import com.aicsp.biz.dto.PageResult;
import com.aicsp.biz.dto.knowledge.ContributionApplicationDTO;
import com.aicsp.biz.dto.knowledge.ContributionDiffDTO;
import com.aicsp.biz.dto.knowledge.ContributionRequest;
import com.aicsp.biz.dto.knowledge.ContributionReviewRequest;
import com.aicsp.biz.dto.knowledge.IngestionCallbackRequest;
import com.aicsp.biz.dto.knowledge.IngestionTaskDTO;
import com.aicsp.biz.dto.knowledge.KnowledgeBaseDTO;
import com.aicsp.biz.dto.knowledge.KnowledgeBaseRequest;
import com.aicsp.biz.dto.knowledge.KnowledgeCategoryDTO;
import com.aicsp.biz.dto.knowledge.KnowledgeCategoryRequest;
import com.aicsp.biz.dto.knowledge.KnowledgeDocumentDTO;
import com.aicsp.biz.dto.knowledge.KnowledgeDocumentUpdateRequest;
import com.aicsp.biz.dto.knowledge.KnowledgeSelection;
import com.aicsp.biz.dto.knowledge.KnowledgeSelectableResponse;
import com.aicsp.biz.dto.knowledge.KnowledgeTagDTO;
import com.aicsp.biz.dto.knowledge.KnowledgeVersionDTO;
import com.aicsp.biz.dto.knowledge.RetrievalFilterRequest;
import com.aicsp.biz.dto.knowledge.RetrievalFilterResponse;
import com.aicsp.biz.entity.KnowledgeBase;
import com.aicsp.biz.entity.KnowledgeCategory;
import com.aicsp.biz.entity.KnowledgeContributionApplication;
import com.aicsp.biz.entity.KnowledgeDocument;
import com.aicsp.biz.entity.KnowledgeIngestionTask;
import com.aicsp.biz.entity.KnowledgeSnapshotDocument;
import com.aicsp.biz.entity.KnowledgeTag;
import com.aicsp.biz.entity.KnowledgeVersion;
import com.aicsp.biz.mapper.KnowledgeMapper;
import com.aicsp.biz.security.RequestIdentity;
import com.aicsp.biz.security.RequestIdentityProvider;
import com.aicsp.biz.service.BizFileStorageService;
import com.aicsp.biz.service.KnowledgeIngestionTaskPublisher;
import com.aicsp.biz.service.KnowledgeService;
import com.aicsp.biz.service.PythonEngineClient;
import com.aicsp.common.exception.BizException;
import com.aicsp.common.exception.ErrorCode;
import com.aicsp.common.dto.event.KnowledgeIngestionTaskEvent;
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
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

@Service
@RequiredArgsConstructor
public class KnowledgeServiceImpl implements KnowledgeService {

    private static final int MAX_PAGE_SIZE = 100;
    private static final int MAX_INGESTION_ATTEMPTS = 5;
    private static final List<String> TERMINAL_TASK_STATUSES = List.of("READY", "FAILED", "FAILED_RETRYABLE", "DELETED");

    private final KnowledgeMapper knowledgeMapper;
    private final RequestIdentityProvider identityProvider;
    private final BizFileStorageService fileStorageService;
    private final KnowledgeIngestionTaskPublisher ingestionTaskPublisher;
    private final PythonEngineClient pythonEngineClient;

    @Override
    public PageResult<KnowledgeBaseDTO> listKnowledgeBases(String scope, int pageNo, int pageSize, String keyword) {
        RequestIdentity identity = identityProvider.current();
        String normalizedScope = normalizeScope(scope);
        assertReadable(normalizedScope, identity);
        int resolvedPageNo = Math.max(pageNo, 1);
        int resolvedPageSize = Math.min(Math.max(pageSize, 1), MAX_PAGE_SIZE);
        String ownerUserId = ownerForQuery(normalizedScope, identity);
        boolean includeDisabled = !"PUBLIC".equals(normalizedScope) || identity.isAdmin();
        long total = knowledgeMapper.countKnowledgeBases(identity.tenantId(), normalizedScope, ownerUserId, keyword, includeDisabled);
        List<KnowledgeBaseDTO> records = knowledgeMapper.listKnowledgeBases(identity.tenantId(), normalizedScope,
                        ownerUserId, keyword, includeDisabled, (resolvedPageNo - 1) * resolvedPageSize, resolvedPageSize)
                .stream().map(kb -> toKnowledgeBaseDTO(kb, identity)).toList();
        return PageResult.<KnowledgeBaseDTO>builder()
                .records(records)
                .pageNo(resolvedPageNo)
                .pageSize(resolvedPageSize)
                .total(total)
                .build();
    }

    @Override
    @Transactional
    public KnowledgeBaseDTO createKnowledgeBase(KnowledgeBaseRequest request) {
        RequestIdentity identity = identityProvider.current();
        String scope = normalizeScope(request.getScope());
        assertWritable(scope, identity);
        String kbType = resolveKbType(scope, request.getKbType());
        KnowledgeBase kb = new KnowledgeBase();
        kb.setId(DistributedIdUtils.nextId());
        kb.setKbId("kb_" + UUID.randomUUID().toString().replace("-", ""));
        kb.setTenantId(identity.tenantId());
        kb.setScope(scope);
        kb.setOwnerUserId("PERSONAL".equals(scope) ? identity.userId() : null);
        kb.setName(request.getName());
        kb.setDescription(request.getDescription());
        kb.setKbType(kbType);
        kb.setCurrentVersion(1);
        kb.setEnabled(request.getEnabled() == null || request.getEnabled());
        kb.setStatus("ACTIVE");
        kb.setLocked(false);
        kb.setCreatedBy(0L);
        kb.setUpdatedBy(0L);
        kb.setDeleted(false);
        knowledgeMapper.insertKnowledgeBase(kb);
        knowledgeMapper.insertKnowledgeVersion(initialVersion(kb.getKbId(), "INITIAL", "创建知识库"));
        return toKnowledgeBaseDTO(knowledgeMapper.findKnowledgeBase(kb.getKbId()), identity);
    }

    @Override
    public KnowledgeBaseDTO getKnowledgeBase(String kbId) {
        RequestIdentity identity = identityProvider.current();
        return toKnowledgeBaseDTO(loadAccessibleKnowledgeBase(kbId, identity), identity);
    }

    @Override
    @Transactional
    public KnowledgeBaseDTO updateKnowledgeBase(String kbId, KnowledgeBaseRequest request) {
        RequestIdentity identity = identityProvider.current();
        KnowledgeBase kb = loadManageableKnowledgeBase(kbId, identity);
        if (StringUtils.hasText(request.getName())) {
            kb.setName(request.getName());
        }
        kb.setDescription(request.getDescription());
        knowledgeMapper.updateKnowledgeBase(kb);
        return toKnowledgeBaseDTO(knowledgeMapper.findKnowledgeBase(kbId), identity);
    }

    @Override
    @Transactional
    public void deleteKnowledgeBase(String kbId) {
        RequestIdentity identity = identityProvider.current();
        KnowledgeBase kb = loadManageableKnowledgeBase(kbId, identity);
        if (Boolean.TRUE.equals(kb.getLocked())) {
            throw new BizException(ErrorCode.PARAM_INVALID, "锁定知识库不能删除");
        }
        knowledgeMapper.softDeleteKnowledgeBase(kbId);
    }

    @Override
    @Transactional
    public void setKnowledgeBaseEnabled(String kbId, boolean enabled) {
        RequestIdentity identity = identityProvider.current();
        KnowledgeBase kb = loadManageableKnowledgeBase(kbId, identity);
        if ("CASE_LIBRARY".equals(kb.getKbType()) && !identity.isSuperAdmin()) {
            throw new BizException(ErrorCode.PARAM_INVALID, "客服案例库仅超级管理员可启用或禁用");
        }
        knowledgeMapper.updateKnowledgeBaseEnabled(kbId, enabled);
    }

    @Override
    public List<KnowledgeVersionDTO> listVersions(String kbId) {
        RequestIdentity identity = identityProvider.current();
        loadAccessibleKnowledgeBase(kbId, identity);
        return knowledgeMapper.listKnowledgeVersions(kbId).stream().map(this::toVersionDTO).toList();
    }

    @Override
    @Transactional
    public void activateVersion(String kbId, int versionNo) {
        RequestIdentity identity = identityProvider.current();
        loadManageableKnowledgeBase(kbId, identity);
        boolean exists = knowledgeMapper.listKnowledgeVersions(kbId).stream()
                .anyMatch(version -> version.getVersionNo() != null && version.getVersionNo() == versionNo);
        if (!exists) {
            throw new BizException(ErrorCode.PARAM_INVALID, "版本不存在");
        }
        knowledgeMapper.updateKnowledgeBaseCurrentVersion(kbId, versionNo);
    }

    @Override
    public PageResult<KnowledgeDocumentDTO> listDocumentsByKb(String kbId, int pageNo, int pageSize, String keyword,
                                                              String categoryId, String status, Boolean enabled) {
        RequestIdentity identity = identityProvider.current();
        KnowledgeBase kb = loadAccessibleKnowledgeBase(kbId, identity);
        int resolvedPageNo = Math.max(pageNo, 1);
        int resolvedPageSize = Math.min(Math.max(pageSize, 1), MAX_PAGE_SIZE);
        long total = knowledgeMapper.countDocumentsByKb(identity.tenantId(), kb.getKbId(), keyword, categoryId, status, enabled);
        List<KnowledgeDocumentDTO> records = knowledgeMapper.listDocumentsByKb(identity.tenantId(), kb.getKbId(), keyword,
                        categoryId, status, enabled, (resolvedPageNo - 1) * resolvedPageSize, resolvedPageSize)
                .stream().map(document -> toDocumentDTO(document, kb)).toList();
        return PageResult.<KnowledgeDocumentDTO>builder()
                .records(records)
                .pageNo(resolvedPageNo)
                .pageSize(resolvedPageSize)
                .total(total)
                .build();
    }

    @Override
    @Transactional
    public KnowledgeDocumentDTO uploadDocumentToKb(String kbId, MultipartFile file, String title, String sourceType,
                                                   String categoryId, String productLine, String tags) {
        RequestIdentity identity = identityProvider.current();
        KnowledgeBase kb = loadManageableKnowledgeBase(kbId, identity);
        KnowledgeDocument document = createDocument(kb, identity, file, title, sourceType, categoryId, productLine);
        knowledgeMapper.insertDocument(document);
        replaceTags(identity.tenantId(), document.getDocumentId(), splitTags(tags));
        KnowledgeIngestionTask task = createTask(document, "INGEST", "PENDING_MQ");
        publishAfterCommit(toIngestionEvent(task, document, kb));
        return toDocumentDTO(document, kb);
    }

    @Override
    @Transactional
    public ContributionApplicationDTO contribute(String kbId, ContributionRequest request) {
        RequestIdentity identity = identityProvider.current();
        KnowledgeBase kb = loadAccessibleKnowledgeBase(kbId, identity);
        if (!"PERSONAL".equals(kb.getScope()) || !identity.userId().equals(kb.getOwnerUserId())) {
            throw new BizException(ErrorCode.PARAM_INVALID, "只能贡献自己的个人知识库");
        }
        String snapshotId = createSnapshot(kb);
        KnowledgeContributionApplication application = newApplication("CONTRIBUTE", kb, snapshotId, null,
                request == null ? null : request.getReason(), identity);
        knowledgeMapper.insertContributionApplication(application);
        return toApplicationDTO(application);
    }

    @Override
    @Transactional
    public ContributionApplicationDTO sync(String kbId, ContributionRequest request) {
        RequestIdentity identity = identityProvider.current();
        KnowledgeBase kb = loadAccessibleKnowledgeBase(kbId, identity);
        if (!"PERSONAL".equals(kb.getScope()) || !identity.userId().equals(kb.getOwnerUserId())) {
            throw new BizException(ErrorCode.PARAM_INVALID, "只能同步自己的个人知识库");
        }
        if (request == null || !StringUtils.hasText(request.getTargetKbId())) {
            throw new BizException(ErrorCode.PARAM_INVALID, "同步申请必须指定目标公共知识库");
        }
        KnowledgeBase target = loadAccessibleKnowledgeBase(request.getTargetKbId(), identity);
        if (!"PUBLIC".equals(target.getScope()) || !Objects.equals(target.getSourceKbId(), kb.getKbId())) {
            throw new BizException(ErrorCode.PARAM_INVALID, "目标公共知识库不是该个人知识库的贡献快照");
        }
        String snapshotId = createSnapshot(kb);
        KnowledgeContributionApplication application = newApplication("SYNC", kb, snapshotId, target.getKbId(), request.getReason(), identity);
        knowledgeMapper.insertContributionApplication(application);
        return toApplicationDTO(application);
    }

    @Override
    public List<ContributionApplicationDTO> myContributions() {
        RequestIdentity identity = identityProvider.current();
        return knowledgeMapper.listContributionApplications(identity.tenantId(), identity.userId(), null)
                .stream().map(this::toApplicationDTO).toList();
    }

    @Override
    public List<ContributionApplicationDTO> pendingContributions() {
        RequestIdentity identity = identityProvider.current();
        if (!identity.isAdmin()) {
            throw new BizException(ErrorCode.PARAM_INVALID, "无权查看审批中心");
        }
        return knowledgeMapper.listContributionApplications(identity.tenantId(), null, "PENDING")
                .stream().map(this::toApplicationDTO).toList();
    }

    @Override
    public ContributionDiffDTO contributionDiff(String applicationId) {
        RequestIdentity identity = identityProvider.current();
        KnowledgeContributionApplication application = loadVisibleApplication(applicationId, identity);
        return buildContributionDiff(application);
    }

    @Override
    @Transactional
    public ContributionApplicationDTO approveContribution(String applicationId, ContributionReviewRequest request) {
        RequestIdentity identity = identityProvider.current();
        if (!identity.isAdmin()) {
            throw new BizException(ErrorCode.PARAM_INVALID, "无权审批申请");
        }
        KnowledgeContributionApplication application = loadPendingApplication(applicationId, identity);
        KnowledgeBase source = loadAccessibleKnowledgeBase(application.getSourceKbId(), identity);
        KnowledgeBase target = "CONTRIBUTE".equals(application.getApplicationType())
                ? createPublicSnapshotKnowledgeBase(source, application, identity)
                : loadManageableKnowledgeBase(application.getTargetKbId(), identity);
        int versionNo = "CONTRIBUTE".equals(application.getApplicationType()) ? 1 : nextVersionNo(target.getKbId());
        if (!"CONTRIBUTE".equals(application.getApplicationType())) {
            KnowledgeVersion version = initialVersion(target.getKbId(), "SYNC", "同步申请通过");
            version.setVersionNo(versionNo);
            version.setSourceKbId(source.getKbId());
            version.setSourceVersionNo(source.getCurrentVersion());
            knowledgeMapper.insertKnowledgeVersion(version);
            if (request != null && Boolean.TRUE.equals(request.getActivateVersion())) {
                knowledgeMapper.updateKnowledgeBaseCurrentVersion(target.getKbId(), versionNo);
            }
        }
        List<KnowledgeIngestionTaskEvent> events = copySnapshotDocumentsAndCreateTasks(application, target, versionNo, identity);
        application.setStatus("APPROVED");
        application.setTargetKbId(target.getKbId());
        application.setTargetVersionNo(versionNo);
        application.setReviewComment(request == null ? null : request.getComment());
        application.setReviewerUserId(identity.userId());
        application.setReviewedAt(OffsetDateTime.now());
        knowledgeMapper.updateContributionApplication(application);
        events.forEach(this::publishAfterCommit);
        return toApplicationDTO(application);
    }

    @Override
    @Transactional
    public ContributionApplicationDTO rejectContribution(String applicationId, ContributionReviewRequest request) {
        RequestIdentity identity = identityProvider.current();
        if (!identity.isAdmin()) {
            throw new BizException(ErrorCode.PARAM_INVALID, "无权审批申请");
        }
        if (request == null || !StringUtils.hasText(request.getComment())) {
            throw new BizException(ErrorCode.PARAM_INVALID, "拒绝申请必须填写意见");
        }
        KnowledgeContributionApplication application = loadPendingApplication(applicationId, identity);
        application.setStatus("REJECTED");
        application.setRejectReason(request.getComment());
        application.setReviewComment(request.getComment());
        application.setReviewerUserId(identity.userId());
        application.setReviewedAt(OffsetDateTime.now());
        knowledgeMapper.updateContributionApplication(application);
        return toApplicationDTO(application);
    }

    @Override
    public void dispatchIngestionTask(KnowledgeIngestionTaskEvent event) {
        KnowledgeIngestionTask task = knowledgeMapper.findLatestTask(event.getDocumentId());
        if (task == null || !Objects.equals(task.getTaskId(), event.getTaskId())) {
            throw new BizException(ErrorCode.PARAM_INVALID, "入库任务不存在");
        }
        try {
            task.setStatus("SUBMITTED");
            knowledgeMapper.updateTask(task);
            pythonEngineClient.submitIngestion(event);
        } catch (Exception exc) {
            int retryCount = task.getRetryCount() == null ? 1 : task.getRetryCount() + 1;
            task.setRetryCount(retryCount);
            task.setStatus(retryCount >= MAX_INGESTION_ATTEMPTS ? "FAILED" : "FAILED_RETRYABLE");
            task.setErrorMessage(exc.getMessage());
            knowledgeMapper.updateTask(task);
            if (retryCount >= MAX_INGESTION_ATTEMPTS) {
                return;
            }
            throw exc;
        }
    }

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
        KnowledgeBase kb = ensureDefaultKnowledgeBase(identity, normalizedScope);
        KnowledgeDocument document = createDocument(kb, identity, file, title, sourceType, categoryId, productLine);
        knowledgeMapper.insertDocument(document);
        replaceTags(identity.tenantId(), document.getDocumentId(), splitTags(tags));
        KnowledgeIngestionTask task = createTask(document, "INGEST", "PENDING_MQ");
        publishAfterCommit(toIngestionEvent(task, document, kb));
        return toDocumentDTO(document, kb);
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
        KnowledgeDocument document = loadManageableDocument(normalizeScope(scope), documentId, identity);
        KnowledgeBase kb = knowledgeMapper.findKnowledgeBase(document.getKbId());
        knowledgeMapper.updateDocumentState(documentId, "DELETED", false);
        knowledgeMapper.softDeleteDocument(documentId);
        KnowledgeIngestionTask task = createTask(document, "DELETE", "PENDING_MQ");
        publishAfterCommit(toIngestionEvent(task, document, kb));
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
        KnowledgeDocument document = loadManageableDocument(normalizeScope(scope), documentId, identity);
        KnowledgeBase kb = knowledgeMapper.findKnowledgeBase(document.getKbId());
        KnowledgeIngestionTask task = createTask(document, "REINDEX", "PENDING_MQ");
        publishAfterCommit(toIngestionEvent(task, document, kb));
        return toTaskDTO(task);
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
        List<KnowledgeBase> publicKbs = knowledgeMapper.listSelectableKbs(identity.tenantId(), "PUBLIC", null);
        List<KnowledgeBase> personalKbs = knowledgeMapper.listSelectableKbs(identity.tenantId(), "PERSONAL", identity.userId());
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
                .publicKbs(publicKbs.stream().map(this::toSelectableKb).toList())
                .personalKbs(personalKbs.stream().map(this::toSelectableKb).toList())
                .policy(KnowledgeSelectableResponse.Policy.builder()
                        .publicAlwaysOn(true)
                        .personalSelectable(true)
                        .build())
                .scopes(List.of(
                        KnowledgeSelectableResponse.ScopeOption.builder()
                                .scope("PUBLIC").enabled(true).editable(false)
                                .documentCount(publicKbs.stream().mapToLong(kb -> kb.getDocumentCount() == null ? 0L : kb.getDocumentCount()).sum()).build(),
                        KnowledgeSelectableResponse.ScopeOption.builder()
                                .scope("PERSONAL").enabled(true).editable(true)
                                .documentCount(personalKbs.stream().mapToLong(kb -> kb.getDocumentCount() == null ? 0L : kb.getDocumentCount()).sum()).build()))
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
        boolean includePublic = !disabledMode
                && !"PERSONAL_ONLY".equals(mode)
                && (!"DEFAULT".equals(mode) || selection == null || !Boolean.FALSE.equals(selection.getIncludePublic()));
        boolean includePersonal = !disabledMode
                && !"PUBLIC_ONLY".equals(mode)
                && (!"DEFAULT".equals(mode) || selection == null || !Boolean.FALSE.equals(selection.getIncludePersonal()));
        List<KnowledgeBase> publicKbs = includePublic
                ? knowledgeMapper.listSelectableKbs(request.getTenantId(), "PUBLIC", null)
                : List.of();
        List<String> requestedPersonalKbIds = selection == null ? List.of() : coalesceList(selection.getPersonalKbIds());
        if (requestedPersonalKbIds.isEmpty() && selection != null && selection.getKbIds() != null) {
            requestedPersonalKbIds = selection.getKbIds();
        }
        List<String> personalKbIds = !includePersonal || requestedPersonalKbIds.isEmpty()
                ? List.of()
                : knowledgeMapper.listAllowedPersonalKbIds(request.getTenantId(), request.getUserId(), requestedPersonalKbIds);
        List<String> allowedKbIds = new ArrayList<>();
        publicKbs.stream().map(KnowledgeBase::getKbId).forEach(allowedKbIds::add);
        allowedKbIds.addAll(personalKbIds);
        List<String> allowedScopes = resolveAllowedScopes(publicKbs, personalKbIds);
        boolean skipRetrieval = disabledMode || allowedKbIds.isEmpty();
        Map<String, Object> filters = new LinkedHashMap<>();
        filters.put("status", "READY");
        filters.put("docEnabled", true);
        filters.put("kbEnabled", true);
        filters.put("kbStatus", "ACTIVE");
        filters.put("tenantId", request.getTenantId());
        filters.put("allowedKbIds", allowedKbIds);
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
        Map<String, Integer> kbVersionMap = new LinkedHashMap<>();
        publicKbs.forEach(kb -> kbVersionMap.put(kb.getKbId(), kb.getCurrentVersion()));
        for (String personalKbId : personalKbIds) {
            KnowledgeBase kb = knowledgeMapper.findKnowledgeBase(personalKbId);
            if (kb != null) {
                kbVersionMap.put(kb.getKbId(), kb.getCurrentVersion());
            }
        }
        filters.put("kbVersionMap", kbVersionMap);
        return RetrievalFilterResponse.builder()
                .mode(mode)
                .skipRetrieval(skipRetrieval)
                .tenantId(request.getTenantId())
                .allowedKbIds(allowedKbIds)
                .allowedScopes(allowedScopes)
                .filters(filters)
                .deniedCandidates(List.of())
                .build();
    }

    private List<String> resolveAllowedScopes(List<KnowledgeBase> publicKbs, List<String> personalKbIds) {
        List<String> scopes = new ArrayList<>(2);
        if (!publicKbs.isEmpty()) {
            scopes.add("PUBLIC");
        }
        if (!personalKbIds.isEmpty()) {
            scopes.add("PERSONAL");
        }
        return scopes;
    }

    private String createSnapshot(KnowledgeBase source) {
        String snapshotId = "snap_" + UUID.randomUUID().toString().replace("-", "");
        int index = 0;
        int offset = 0;
        while (true) {
            List<KnowledgeDocument> documents = knowledgeMapper.listDocumentsByKb(
                    source.getTenantId(),
                    source.getKbId(),
                    null,
                    null,
                    null,
                    null,
                    offset,
                    MAX_PAGE_SIZE
            );
            if (documents.isEmpty()) {
                break;
            }
            for (KnowledgeDocument document : documents) {
                KnowledgeSnapshotDocument snapshot = new KnowledgeSnapshotDocument();
                snapshot.setId(DistributedIdUtils.nextId());
                snapshot.setSnapshotId(snapshotId);
                snapshot.setSourceKbId(source.getKbId());
                snapshot.setSourceVersion(source.getCurrentVersion());
                snapshot.setSourceDocumentId(document.getDocumentId());
                snapshot.setTitle(document.getTitle());
                snapshot.setSourceType(document.getSourceType());
                snapshot.setObjectPath(document.getObjectPath());
                snapshot.setCategoryId(document.getCategoryId());
                snapshot.setProductLine(document.getProductLine());
                snapshot.setEnabled(document.getEnabled());
                snapshot.setStatus(document.getStatus());
                snapshot.setFingerprint(fingerprint(document));
                snapshot.setSortOrder(index++);
                snapshot.setDeleted(false);
                knowledgeMapper.insertSnapshotDocument(snapshot);
            }
            if (documents.size() < MAX_PAGE_SIZE) {
                break;
            }
            offset += MAX_PAGE_SIZE;
        }
        return snapshotId;
    }

    private KnowledgeContributionApplication newApplication(
            String type,
            KnowledgeBase source,
            String snapshotId,
            String targetKbId,
            String reason,
            RequestIdentity identity
    ) {
        KnowledgeContributionApplication application = new KnowledgeContributionApplication();
        application.setId(DistributedIdUtils.nextId());
        application.setApplicationId("app_" + UUID.randomUUID().toString().replace("-", ""));
        application.setTenantId(identity.tenantId());
        application.setApplicationType(type);
        application.setSourceKbId(source.getKbId());
        application.setSourceSnapshotId(snapshotId);
        application.setTargetKbId(targetKbId);
        application.setApplicantUserId(identity.userId());
        application.setStatus("PENDING");
        application.setReason(reason);
        application.setDeleted(false);
        return application;
    }

    private KnowledgeContributionApplication loadVisibleApplication(String applicationId, RequestIdentity identity) {
        KnowledgeContributionApplication application = knowledgeMapper.findContributionApplication(applicationId);
        if (application == null || Boolean.TRUE.equals(application.getDeleted())
                || !identity.tenantId().equals(application.getTenantId())) {
            throw new BizException(ErrorCode.PARAM_INVALID, "申请不存在或无权访问");
        }
        if (!identity.isAdmin() && !identity.userId().equals(application.getApplicantUserId())) {
            throw new BizException(ErrorCode.PARAM_INVALID, "申请不存在或无权访问");
        }
        return application;
    }

    private KnowledgeContributionApplication loadPendingApplication(String applicationId, RequestIdentity identity) {
        KnowledgeContributionApplication application = loadVisibleApplication(applicationId, identity);
        if (!"PENDING".equals(application.getStatus())) {
            throw new BizException(ErrorCode.PARAM_INVALID, "只能处理待审批申请");
        }
        return application;
    }

    private ContributionDiffDTO buildContributionDiff(KnowledgeContributionApplication application) {
        List<KnowledgeSnapshotDocument> snapshots = knowledgeMapper.listSnapshotDocuments(application.getSourceSnapshotId());
        List<ContributionDiffDTO.Item> added = new ArrayList<>();
        List<ContributionDiffDTO.Item> modified = new ArrayList<>();
        Integer targetVersion = null;
        if (StringUtils.hasText(application.getTargetKbId())) {
            KnowledgeBase target = knowledgeMapper.findKnowledgeBase(application.getTargetKbId());
            targetVersion = target == null ? null : target.getCurrentVersion();
        }
        for (KnowledgeSnapshotDocument snapshot : snapshots) {
            KnowledgeDocument targetDoc = StringUtils.hasText(application.getTargetKbId())
                    ? knowledgeMapper.findDocumentBySnapshotSource(application.getTargetKbId(), snapshot.getSourceDocumentId(), targetVersion)
                    : null;
            ContributionDiffDTO.Item item = ContributionDiffDTO.Item.builder()
                    .sourceDocumentId(snapshot.getSourceDocumentId())
                    .targetDocumentId(targetDoc == null ? null : targetDoc.getDocumentId())
                    .title(snapshot.getTitle())
                    .sourceType(snapshot.getSourceType())
                    .fingerprint(snapshot.getFingerprint())
                    .previousFingerprint(targetDoc == null ? null : fingerprint(targetDoc))
                    .build();
            if (targetDoc == null) {
                added.add(item);
            } else if (!snapshot.getFingerprint().equals(fingerprint(targetDoc))) {
                modified.add(item);
            }
        }
        List<ContributionDiffDTO.Item> deleted = new ArrayList<>();
        if (StringUtils.hasText(application.getTargetKbId())) {
            List<String> snapshotSourceIds = snapshots.stream().map(KnowledgeSnapshotDocument::getSourceDocumentId).toList();
            for (KnowledgeDocument targetDoc : knowledgeMapper.listDocumentsBySourceKb(
                    application.getTenantId(), application.getTargetKbId(), targetVersion)) {
                if (StringUtils.hasText(targetDoc.getSourceDocumentId()) && !snapshotSourceIds.contains(targetDoc.getSourceDocumentId())) {
                    deleted.add(ContributionDiffDTO.Item.builder()
                            .sourceDocumentId(targetDoc.getSourceDocumentId())
                            .targetDocumentId(targetDoc.getDocumentId())
                            .title(targetDoc.getTitle())
                            .sourceType(targetDoc.getSourceType())
                            .previousFingerprint(fingerprint(targetDoc))
                            .build());
                }
            }
        }
        return ContributionDiffDTO.builder()
                .applicationId(application.getApplicationId())
                .sourceSnapshotId(application.getSourceSnapshotId())
                .targetKbId(application.getTargetKbId())
                .targetVersionNo(application.getTargetVersionNo())
                .added(added)
                .modified(modified)
                .deleted(deleted)
                .build();
    }

    private KnowledgeBase createPublicSnapshotKnowledgeBase(
            KnowledgeBase source,
            KnowledgeContributionApplication application,
            RequestIdentity identity
    ) {
        KnowledgeBase kb = new KnowledgeBase();
        kb.setId(DistributedIdUtils.nextId());
        kb.setKbId("kb_" + UUID.randomUUID().toString().replace("-", ""));
        kb.setTenantId(identity.tenantId());
        kb.setScope("PUBLIC");
        kb.setOwnerUserId(null);
        kb.setName(source.getName() + "（公共快照）");
        kb.setDescription(source.getDescription());
        kb.setKbType("GENERIC_PUBLIC");
        kb.setSourceKbId(source.getKbId());
        kb.setSourceVersion(source.getCurrentVersion());
        kb.setCurrentVersion(1);
        kb.setEnabled(false);
        kb.setStatus("ACTIVE");
        kb.setLocked(false);
        kb.setCreatedBy(0L);
        kb.setUpdatedBy(0L);
        kb.setDeleted(false);
        knowledgeMapper.insertKnowledgeBase(kb);
        KnowledgeVersion version = initialVersion(kb.getKbId(), "CONTRIBUTION", "贡献申请通过");
        version.setSourceKbId(source.getKbId());
        version.setSourceVersionNo(source.getCurrentVersion());
        knowledgeMapper.insertKnowledgeVersion(version);
        application.setTargetKbId(kb.getKbId());
        return kb;
    }

    private int nextVersionNo(String kbId) {
        return knowledgeMapper.listKnowledgeVersions(kbId).stream()
                .map(KnowledgeVersion::getVersionNo)
                .filter(Objects::nonNull)
                .max(Integer::compareTo)
                .orElse(0) + 1;
    }

    private List<KnowledgeIngestionTaskEvent> copySnapshotDocumentsAndCreateTasks(
            KnowledgeContributionApplication application,
            KnowledgeBase target,
            int versionNo,
            RequestIdentity identity
    ) {
        List<KnowledgeIngestionTaskEvent> events = new ArrayList<>();
        for (KnowledgeSnapshotDocument snapshot : knowledgeMapper.listSnapshotDocuments(application.getSourceSnapshotId())) {
            KnowledgeDocument document = new KnowledgeDocument();
            document.setId(DistributedIdUtils.nextId());
            document.setDocumentId("doc_" + UUID.randomUUID().toString().replace("-", ""));
            document.setKbId(target.getKbId());
            document.setKbVersion(versionNo);
            document.setTenantId(identity.tenantId());
            document.setScope("PUBLIC");
            document.setOwnerUserId(null);
            document.setTitle(snapshot.getTitle());
            document.setSourceType(snapshot.getSourceType());
            document.setObjectPath(snapshot.getObjectPath());
            document.setSourceDocumentId(snapshot.getSourceDocumentId());
            document.setSourceSnapshotId(snapshot.getSnapshotId());
            document.setCategoryId(snapshot.getCategoryId());
            document.setProductLine(snapshot.getProductLine());
            document.setStatus("UPLOADED");
            document.setEnabled(Boolean.TRUE.equals(snapshot.getEnabled()));
            document.setCreatedBy(0L);
            document.setUpdatedBy(0L);
            document.setDeleted(false);
            knowledgeMapper.insertDocument(document);
            KnowledgeIngestionTask task = createTask(document, "INGEST", "PENDING_MQ");
            events.add(toIngestionEvent(task, document, target));
        }
        return events;
    }

    private void publishAfterCommit(KnowledgeIngestionTaskEvent event) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            ingestionTaskPublisher.publish(event);
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                ingestionTaskPublisher.publish(event);
            }
        });
    }

    private String fingerprint(KnowledgeDocument document) {
        return sha256(String.join("|",
                nullToEmpty(document.getTitle()),
                nullToEmpty(document.getSourceType()),
                nullToEmpty(document.getObjectPath()),
                nullToEmpty(document.getCategoryId()),
                nullToEmpty(document.getProductLine()),
                String.valueOf(Boolean.TRUE.equals(document.getEnabled()))
        ));
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
        KnowledgeBase kb = knowledgeMapper.findKnowledgeBase(document.getKbId());
        if (kb != null) {
            assertManageableKnowledgeBase(kb, identity);
        } else {
            assertWritable(document.getScope(), identity);
        }
        return document;
    }

    private KnowledgeBase loadAccessibleKnowledgeBase(String kbId, RequestIdentity identity) {
        KnowledgeBase kb = knowledgeMapper.findKnowledgeBase(kbId);
        if (kb == null || Boolean.TRUE.equals(kb.getDeleted()) || !identity.tenantId().equals(kb.getTenantId())) {
            throw new BizException(ErrorCode.PARAM_INVALID, "知识库不存在或无权访问");
        }
        if ("PERSONAL".equals(kb.getScope()) && !identity.userId().equals(kb.getOwnerUserId())) {
            throw new BizException(ErrorCode.PARAM_INVALID, "知识库不存在或无权访问");
        }
        if ("PUBLIC".equals(kb.getScope()) && !identity.isAdmin()
                && (!Boolean.TRUE.equals(kb.getEnabled()) || !"ACTIVE".equals(kb.getStatus()))) {
            throw new BizException(ErrorCode.PARAM_INVALID, "知识库不存在或无权访问");
        }
        return kb;
    }

    private KnowledgeBase loadManageableKnowledgeBase(String kbId, RequestIdentity identity) {
        KnowledgeBase kb = loadAccessibleKnowledgeBase(kbId, identity);
        assertManageableKnowledgeBase(kb, identity);
        return kb;
    }

    private void assertManageableKnowledgeBase(KnowledgeBase kb, RequestIdentity identity) {
        assertWritable(kb.getScope(), identity);
        if ("PERSONAL".equals(kb.getScope()) && !identity.userId().equals(kb.getOwnerUserId())) {
            throw new BizException(ErrorCode.PARAM_INVALID, "无权维护该个人知识库");
        }
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

    private String resolveKbType(String scope, String requestedType) {
        if ("PERSONAL".equals(scope)) {
            return "PERSONAL";
        }
        if (StringUtils.hasText(requestedType) && "CASE_LIBRARY".equalsIgnoreCase(requestedType)) {
            return "CASE_LIBRARY";
        }
        return "GENERIC_PUBLIC";
    }

    private KnowledgeBase ensureDefaultKnowledgeBase(RequestIdentity identity, String scope) {
        KnowledgeBase kb = knowledgeMapper.findDefaultKnowledgeBase(identity.tenantId(), scope, ownerForQuery(scope, identity));
        if (kb != null) {
            return kb;
        }
        KnowledgeBase created = new KnowledgeBase();
        created.setId(DistributedIdUtils.nextId());
        created.setKbId(defaultKnowledgeBaseId(identity, scope));
        created.setTenantId(identity.tenantId());
        created.setScope(scope);
        created.setOwnerUserId("PERSONAL".equals(scope) ? identity.userId() : null);
        created.setName("PUBLIC".equals(scope) ? "客服案例库" : "我的默认知识库");
        created.setDescription("PUBLIC".equals(scope) ? "系统默认公共客服案例库" : "兼容旧接口自动创建的个人知识库");
        created.setKbType("PUBLIC".equals(scope) ? "CASE_LIBRARY" : "PERSONAL");
        created.setCurrentVersion(1);
        created.setEnabled(true);
        created.setStatus("ACTIVE");
        created.setLocked("PUBLIC".equals(scope));
        created.setCreatedBy(0L);
        created.setUpdatedBy(0L);
        created.setDeleted(false);
        knowledgeMapper.insertKnowledgeBase(created);
        knowledgeMapper.insertKnowledgeVersion(initialVersion(created.getKbId(), "INITIAL", "默认知识库初始化"));
        return created;
    }

    private String defaultKnowledgeBaseId(RequestIdentity identity, String scope) {
        if ("PUBLIC".equals(scope)) {
            return "kb_case_default_" + identity.tenantId();
        }
        String key = identity.tenantId() + "_" + identity.userId();
        return "kb_personal_default_" + UUID.nameUUIDFromBytes(key.getBytes(StandardCharsets.UTF_8)).toString().replace("-", "");
    }

    private KnowledgeVersion initialVersion(String kbId, String versionType, String note) {
        KnowledgeVersion version = new KnowledgeVersion();
        version.setId(DistributedIdUtils.nextId());
        version.setVersionId("ver_" + UUID.randomUUID().toString().replace("-", ""));
        version.setKbId(kbId);
        version.setVersionNo(1);
        version.setVersionType(versionType);
        version.setNote(note);
        version.setCreatedBy(0L);
        version.setDeleted(false);
        return version;
    }

    private KnowledgeDocument createDocument(KnowledgeBase kb, RequestIdentity identity, MultipartFile file,
                                             String title, String sourceType, String categoryId, String productLine) {
        String documentId = "doc_" + UUID.randomUUID().toString().replace("-", "");
        String objectPath = fileStorageService.uploadKnowledgeFile(kb.getScope(), documentId, file);
        KnowledgeDocument document = new KnowledgeDocument();
        document.setId(DistributedIdUtils.nextId());
        document.setDocumentId(documentId);
        document.setKbId(kb.getKbId());
        document.setKbVersion(kb.getCurrentVersion());
        document.setTenantId(identity.tenantId());
        document.setScope(kb.getScope());
        document.setOwnerUserId("PERSONAL".equals(kb.getScope()) ? identity.userId() : null);
        document.setTitle(StringUtils.hasText(title) ? title : file.getOriginalFilename());
        document.setSourceType("CASE_LIBRARY".equals(kb.getKbType())
                ? "CASE_LIBRARY"
                : StringUtils.hasText(sourceType) ? sourceType.toUpperCase(Locale.ROOT) : inferSourceType(file.getOriginalFilename()));
        document.setObjectPath(objectPath);
        document.setCategoryId(categoryId);
        document.setProductLine(productLine);
        document.setStatus("UPLOADED");
        document.setEnabled(true);
        document.setCreatedBy(0L);
        document.setUpdatedBy(0L);
        document.setDeleted(false);
        return document;
    }

    private KnowledgeIngestionTask createTask(KnowledgeDocument document, String taskType, String status) {
        KnowledgeIngestionTask task = new KnowledgeIngestionTask();
        task.setTaskId("kb_task_" + UUID.randomUUID().toString().replace("-", ""));
        task.setDocumentId(document.getDocumentId());
        task.setKbId(document.getKbId());
        task.setKbVersion(document.getKbVersion());
        task.setTaskType(taskType);
        task.setStatus(status);
        task.setProgress(0);
        task.setChunkCount(0);
        task.setRetryCount(0);
        knowledgeMapper.insertTask(task);
        return task;
    }

    private List<String> coalesceList(List<String> values) {
        return values == null ? List.of() : values;
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
        KnowledgeBase kb = StringUtils.hasText(document.getKbId()) ? knowledgeMapper.findKnowledgeBase(document.getKbId()) : null;
        return toDocumentDTO(document, kb);
    }

    private KnowledgeDocumentDTO toDocumentDTO(KnowledgeDocument document, KnowledgeBase kb) {
        KnowledgeDocumentDTO dto = new KnowledgeDocumentDTO();
        dto.setDocumentId(document.getDocumentId());
        dto.setKbId(document.getKbId());
        dto.setKbVersion(document.getKbVersion());
        if (kb != null) {
            dto.setKbName(kb.getName());
            dto.setKbType(kb.getKbType());
        }
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

    private KnowledgeBaseDTO toKnowledgeBaseDTO(KnowledgeBase kb, RequestIdentity identity) {
        KnowledgeBaseDTO dto = new KnowledgeBaseDTO();
        dto.setKbId(kb.getKbId());
        dto.setScope(kb.getScope());
        dto.setName(kb.getName());
        dto.setDescription(kb.getDescription());
        dto.setKbType(kb.getKbType());
        dto.setSourceKbId(kb.getSourceKbId());
        dto.setSourceVersion(kb.getSourceVersion());
        dto.setCurrentVersion(kb.getCurrentVersion());
        dto.setEnabled(kb.getEnabled());
        dto.setStatus(kb.getStatus());
        dto.setLocked(kb.getLocked());
        dto.setDocumentCount(kb.getDocumentCount() == null ? 0L : kb.getDocumentCount());
        dto.setManageable(canManage(kb, identity));
        dto.setHasPublicSnapshot(false);
        dto.setCreatedAt(kb.getCreatedAt());
        dto.setUpdatedAt(kb.getUpdatedAt());
        return dto;
    }

    private KnowledgeSelectableResponse.KbOption toSelectableKb(KnowledgeBase kb) {
        return KnowledgeSelectableResponse.KbOption.builder()
                .kbId(kb.getKbId())
                .name(kb.getName())
                .scope(kb.getScope())
                .kbType(kb.getKbType())
                .enabled(kb.getEnabled())
                .locked(kb.getLocked())
                .documentCount(kb.getDocumentCount() == null ? 0L : kb.getDocumentCount())
                .currentVersion(kb.getCurrentVersion())
                .hasPublicSnapshot(false)
                .build();
    }

    private boolean canManage(KnowledgeBase kb, RequestIdentity identity) {
        if ("PUBLIC".equals(kb.getScope())) {
            return identity.isAdmin();
        }
        return identity.userId().equals(kb.getOwnerUserId());
    }

    private KnowledgeVersionDTO toVersionDTO(KnowledgeVersion version) {
        KnowledgeVersionDTO dto = new KnowledgeVersionDTO();
        dto.setVersionId(version.getVersionId());
        dto.setKbId(version.getKbId());
        dto.setVersionNo(version.getVersionNo());
        dto.setVersionType(version.getVersionType());
        dto.setSourceKbId(version.getSourceKbId());
        dto.setSourceVersionNo(version.getSourceVersionNo());
        dto.setNote(version.getNote());
        dto.setCreatedAt(version.getCreatedAt());
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
        dto.setKbId(task.getKbId());
        dto.setKbVersion(task.getKbVersion());
        dto.setTaskId(task.getTaskId());
        dto.setStatus(task.getStatus());
        dto.setProgress(task.getProgress());
        dto.setChunkCount(task.getChunkCount());
        dto.setEmbeddingModel(task.getEmbeddingModel());
        dto.setErrorMessage(task.getErrorMessage());
        dto.setUpdatedAt(task.getUpdatedAt());
        return dto;
    }

    private ContributionApplicationDTO toApplicationDTO(KnowledgeContributionApplication application) {
        return ContributionApplicationDTO.builder()
                .applicationId(application.getApplicationId())
                .applicationType(application.getApplicationType())
                .sourceKbId(application.getSourceKbId())
                .targetKbId(application.getTargetKbId())
                .sourceSnapshotId(application.getSourceSnapshotId())
                .status(application.getStatus())
                .reason(application.getReason())
                .reviewComment(StringUtils.hasText(application.getReviewComment())
                        ? application.getReviewComment()
                        : application.getRejectReason())
                .createdAt(application.getCreatedAt())
                .reviewedAt(application.getReviewedAt())
                .build();
    }

    private KnowledgeIngestionTaskEvent toIngestionEvent(
            KnowledgeIngestionTask task,
            KnowledgeDocument document,
            KnowledgeBase kb
    ) {
        KnowledgeIngestionTaskEvent event = new KnowledgeIngestionTaskEvent();
        event.setTaskId(task.getTaskId());
        event.setTaskType(task.getTaskType());
        event.setDocumentId(document.getDocumentId());
        event.setKbId(document.getKbId());
        event.setKbVersion(document.getKbVersion());
        event.setKbType(kb == null ? document.getSourceType() : kb.getKbType());
        event.setKbName(kb == null ? null : kb.getName());
        event.setTenantId(document.getTenantId());
        event.setScope(document.getScope());
        event.setOwnerUserId(document.getOwnerUserId());
        event.setObjectPath(document.getObjectPath());
        event.setTitle(document.getTitle());
        event.setSourceType(document.getSourceType());
        event.setCategoryId(document.getCategoryId());
        event.setProductLine(document.getProductLine());
        event.setEnabled(document.getEnabled());
        return event;
    }

    private String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return "sha256:" + HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            return "sha256:unavailable";
        }
    }

    private String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    @SuppressWarnings("unused")
    private String hashDocumentId(String documentId) {
        return sha256(documentId);
    }
}

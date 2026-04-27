package com.aicsp.biz.mapper;

import com.aicsp.biz.entity.KnowledgeCategory;
import com.aicsp.biz.entity.KnowledgeBase;
import com.aicsp.biz.entity.KnowledgeDocument;
import com.aicsp.biz.entity.KnowledgeContributionApplication;
import com.aicsp.biz.entity.KnowledgeIngestionTask;
import com.aicsp.biz.entity.KnowledgeSnapshotDocument;
import com.aicsp.biz.entity.KnowledgeTag;
import com.aicsp.biz.entity.KnowledgeVersion;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface KnowledgeMapper {

    List<KnowledgeBase> listKnowledgeBases(@Param("tenantId") String tenantId, @Param("scope") String scope,
                                           @Param("ownerUserId") String ownerUserId, @Param("keyword") String keyword,
                                           @Param("includeDisabled") boolean includeDisabled,
                                           @Param("offset") int offset, @Param("limit") int limit);

    long countKnowledgeBases(@Param("tenantId") String tenantId, @Param("scope") String scope,
                             @Param("ownerUserId") String ownerUserId, @Param("keyword") String keyword,
                             @Param("includeDisabled") boolean includeDisabled);

    KnowledgeBase findKnowledgeBase(@Param("kbId") String kbId);

    KnowledgeBase findCaseLibrary(@Param("tenantId") String tenantId);

    KnowledgeBase findDefaultKnowledgeBase(@Param("tenantId") String tenantId, @Param("scope") String scope,
                                           @Param("ownerUserId") String ownerUserId);

    void insertKnowledgeBase(KnowledgeBase knowledgeBase);

    int updateKnowledgeBase(KnowledgeBase knowledgeBase);

    int updateKnowledgeBaseEnabled(@Param("kbId") String kbId, @Param("enabled") boolean enabled);

    int updateKnowledgeBaseCurrentVersion(@Param("kbId") String kbId, @Param("versionNo") int versionNo);

    int softDeleteKnowledgeBase(@Param("kbId") String kbId);

    void insertKnowledgeVersion(KnowledgeVersion version);

    List<KnowledgeVersion> listKnowledgeVersions(@Param("kbId") String kbId);

    List<KnowledgeBase> listSelectableKbs(@Param("tenantId") String tenantId, @Param("scope") String scope,
                                          @Param("ownerUserId") String ownerUserId);

    List<String> listAllowedPersonalKbIds(@Param("tenantId") String tenantId, @Param("ownerUserId") String ownerUserId,
                                          @Param("kbIds") List<String> kbIds);

    void insertContributionApplication(KnowledgeContributionApplication application);

    KnowledgeContributionApplication findContributionApplication(@Param("applicationId") String applicationId);

    List<KnowledgeContributionApplication> listContributionApplications(@Param("tenantId") String tenantId,
                                                                        @Param("applicantUserId") String applicantUserId,
                                                                        @Param("status") String status);

    int updateContributionApplication(KnowledgeContributionApplication application);

    void insertSnapshotDocument(KnowledgeSnapshotDocument document);

    List<KnowledgeSnapshotDocument> listSnapshotDocuments(@Param("snapshotId") String snapshotId);

    List<KnowledgeDocument> listDocumentsBySourceKb(@Param("tenantId") String tenantId,
                                                    @Param("kbId") String kbId,
                                                    @Param("kbVersion") Integer kbVersion);

    KnowledgeDocument findDocumentBySnapshotSource(@Param("targetKbId") String targetKbId,
                                                   @Param("sourceDocumentId") String sourceDocumentId,
                                                   @Param("kbVersion") Integer kbVersion);

    List<KnowledgeDocument> listDocuments(@Param("tenantId") String tenantId, @Param("scope") String scope,
                                          @Param("ownerUserId") String ownerUserId, @Param("keyword") String keyword,
                                          @Param("categoryId") String categoryId, @Param("status") String status,
                                          @Param("enabled") Boolean enabled, @Param("offset") int offset,
                                          @Param("limit") int limit);

    List<KnowledgeDocument> listDocumentsByKb(@Param("tenantId") String tenantId, @Param("kbId") String kbId,
                                              @Param("keyword") String keyword, @Param("categoryId") String categoryId,
                                              @Param("status") String status, @Param("enabled") Boolean enabled,
                                              @Param("offset") int offset, @Param("limit") int limit);

    long countDocuments(@Param("tenantId") String tenantId, @Param("scope") String scope,
                        @Param("ownerUserId") String ownerUserId, @Param("keyword") String keyword,
                        @Param("categoryId") String categoryId, @Param("status") String status,
                        @Param("enabled") Boolean enabled);

    long countDocumentsByKb(@Param("tenantId") String tenantId, @Param("kbId") String kbId,
                            @Param("keyword") String keyword, @Param("categoryId") String categoryId,
                            @Param("status") String status, @Param("enabled") Boolean enabled);

    KnowledgeDocument findDocument(@Param("documentId") String documentId);

    int insertDocument(KnowledgeDocument document);

    int updateDocument(KnowledgeDocument document);

    int updateDocumentState(@Param("documentId") String documentId, @Param("status") String status,
                            @Param("enabled") Boolean enabled);

    int softDeleteDocument(@Param("documentId") String documentId);

    List<String> listDocumentTags(@Param("documentId") String documentId);

    void deleteDocumentTags(@Param("documentId") String documentId);

    KnowledgeTag findTagByName(@Param("tenantId") String tenantId, @Param("name") String name);

    void insertTag(KnowledgeTag tag);

    void insertDocumentTag(@Param("documentId") String documentId, @Param("tagId") String tagId);

    List<KnowledgeCategory> listCategories(@Param("tenantId") String tenantId, @Param("scope") String scope,
                                           @Param("ownerUserId") String ownerUserId);

    KnowledgeCategory findCategory(@Param("categoryId") String categoryId);

    void insertCategory(KnowledgeCategory category);

    int updateCategory(KnowledgeCategory category);

    int deleteCategory(@Param("categoryId") String categoryId, @Param("tenantId") String tenantId,
                       @Param("scope") String scope, @Param("ownerUserId") String ownerUserId);

    long countCategoryDocuments(@Param("categoryId") String categoryId);

    List<KnowledgeTag> listTags(@Param("tenantId") String tenantId, @Param("keyword") String keyword);

    long countTagDocuments(@Param("tagId") String tagId);

    void insertTask(KnowledgeIngestionTask task);

    KnowledgeIngestionTask findLatestTask(@Param("documentId") String documentId);

    int updateTask(KnowledgeIngestionTask task);

    long countSelectable(@Param("tenantId") String tenantId, @Param("scope") String scope,
                         @Param("ownerUserId") String ownerUserId);

    List<KnowledgeDocument> listSelectableDocuments(@Param("tenantId") String tenantId,
                                                    @Param("ownerUserId") String ownerUserId);
}

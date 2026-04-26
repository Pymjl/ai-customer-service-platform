package com.aicsp.biz.mapper;

import com.aicsp.biz.entity.KnowledgeCategory;
import com.aicsp.biz.entity.KnowledgeDocument;
import com.aicsp.biz.entity.KnowledgeIngestionTask;
import com.aicsp.biz.entity.KnowledgeTag;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface KnowledgeMapper {

    List<KnowledgeDocument> listDocuments(@Param("tenantId") String tenantId, @Param("scope") String scope,
                                          @Param("ownerUserId") String ownerUserId, @Param("keyword") String keyword,
                                          @Param("categoryId") String categoryId, @Param("status") String status,
                                          @Param("enabled") Boolean enabled, @Param("offset") int offset,
                                          @Param("limit") int limit);

    long countDocuments(@Param("tenantId") String tenantId, @Param("scope") String scope,
                        @Param("ownerUserId") String ownerUserId, @Param("keyword") String keyword,
                        @Param("categoryId") String categoryId, @Param("status") String status,
                        @Param("enabled") Boolean enabled);

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

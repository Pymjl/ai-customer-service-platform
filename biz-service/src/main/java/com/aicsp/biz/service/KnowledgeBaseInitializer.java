package com.aicsp.biz.service;

import com.aicsp.biz.entity.KnowledgeBase;
import com.aicsp.biz.entity.KnowledgeVersion;
import com.aicsp.biz.mapper.KnowledgeMapper;
import com.aicsp.common.util.DistributedIdUtils;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
public class KnowledgeBaseInitializer implements ApplicationRunner {

    private static final String DEFAULT_TENANT_ID = "default";

    private final KnowledgeMapper knowledgeMapper;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        KnowledgeBase existing = knowledgeMapper.findCaseLibrary(DEFAULT_TENANT_ID);
        if (existing == null) {
            KnowledgeBase kb = new KnowledgeBase();
            kb.setId(DistributedIdUtils.nextId());
            kb.setKbId("kb_case_default_" + DEFAULT_TENANT_ID);
            kb.setTenantId(DEFAULT_TENANT_ID);
            kb.setScope("PUBLIC");
            kb.setOwnerUserId(null);
            kb.setName("客服案例库");
            kb.setDescription("系统默认公共客服案例库");
            kb.setKbType("CASE_LIBRARY");
            kb.setCurrentVersion(1);
            kb.setEnabled(true);
            kb.setStatus("ACTIVE");
            kb.setLocked(true);
            kb.setCreatedBy(0L);
            kb.setUpdatedBy(0L);
            kb.setDeleted(false);
            knowledgeMapper.insertKnowledgeBase(kb);
            knowledgeMapper.insertKnowledgeVersion(initialVersion(kb.getKbId()));
            return;
        }
        if (!Boolean.TRUE.equals(existing.getLocked())) {
            existing.setName(existing.getName());
            existing.setDescription(existing.getDescription());
            existing.setLocked(true);
            knowledgeMapper.updateKnowledgeBase(existing);
        }
    }

    private KnowledgeVersion initialVersion(String kbId) {
        KnowledgeVersion version = new KnowledgeVersion();
        version.setId(DistributedIdUtils.nextId());
        version.setVersionId("ver_" + UUID.randomUUID().toString().replace("-", ""));
        version.setKbId(kbId);
        version.setVersionNo(1);
        version.setVersionType("INITIAL");
        version.setNote("客服案例库默认初始化");
        version.setCreatedBy(0L);
        version.setDeleted(false);
        return version;
    }
}

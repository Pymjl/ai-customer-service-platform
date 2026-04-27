package com.aicsp.biz.service;

import com.aicsp.biz.config.PythonEngineProperties;
import com.aicsp.common.constant.HeaderConstants;
import com.aicsp.common.dto.event.KnowledgeIngestionTaskEvent;
import com.aicsp.common.exception.BizException;
import com.aicsp.common.exception.ErrorCode;
import java.util.LinkedHashMap;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
@RequiredArgsConstructor
public class PythonEngineClient {

    private final PythonEngineProperties properties;

    public void submitIngestion(KnowledgeIngestionTaskEvent event) {
        String path = resolvePath(event);
        try {
            RestClient.create(properties.getBaseUrl())
                    .post()
                    .uri(path)
                    .contentType(MediaType.APPLICATION_JSON)
                    .header(HeaderConstants.X_INTERNAL_TOKEN, properties.getInternalToken())
                    .body(buildBody(event))
                    .retrieve()
                    .toBodilessEntity();
        } catch (Exception e) {
            throw new BizException(ErrorCode.PARAM_INVALID, "提交 Python 引擎入库任务失败");
        }
    }

    private String resolvePath(KnowledgeIngestionTaskEvent event) {
        if ("DELETE".equalsIgnoreCase(event.getTaskType())) {
            return "/internal/rag/delete";
        }
        if ("REINDEX".equalsIgnoreCase(event.getTaskType())) {
            return "/internal/rag/reindex";
        }
        return "/internal/rag/ingest";
    }

    private Map<String, Object> buildBody(KnowledgeIngestionTaskEvent event) {
        Map<String, Object> body = new LinkedHashMap<>();
        put(body, "taskId", event.getTaskId());
        put(body, "documentId", event.getDocumentId());
        put(body, "tenantId", event.getTenantId());
        put(body, "kbId", event.getKbId());
        put(body, "kbVersion", event.getKbVersion());
        put(body, "traceId", event.getTraceId());
        if ("DELETE".equalsIgnoreCase(event.getTaskType())) {
            return body;
        }
        put(body, "kbType", event.getKbType());
        put(body, "kbName", event.getKbName());
        put(body, "scope", event.getScope());
        put(body, "ownerUserId", event.getOwnerUserId());
        put(body, "objectPath", event.getObjectPath());
        put(body, "title", event.getTitle());
        put(body, "sourceType", event.getSourceType());
        put(body, "categoryId", event.getCategoryId());
        put(body, "productLine", event.getProductLine());
        put(body, "enabled", event.getEnabled());
        return body;
    }

    private void put(Map<String, Object> body, String key, Object value) {
        if (value != null) {
            body.put(key, value);
        }
    }
}

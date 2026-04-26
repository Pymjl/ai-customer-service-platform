package com.aicsp.biz.controller;

import com.aicsp.biz.dto.knowledge.IngestionCallbackRequest;
import com.aicsp.biz.dto.knowledge.RetrievalFilterRequest;
import com.aicsp.biz.dto.knowledge.RetrievalFilterResponse;
import com.aicsp.biz.service.KnowledgeService;
import com.aicsp.common.result.R;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/internal/knowledge")
@RequiredArgsConstructor
public class InternalKnowledgeController {

    private final KnowledgeService knowledgeService;

    @PostMapping("/retrieval-filter")
    public R<RetrievalFilterResponse> retrievalFilter(@RequestBody RetrievalFilterRequest request) {
        return R.ok(knowledgeService.resolveRetrievalFilter(request));
    }

    @PostMapping("/ingestion-callback")
    public R<?> ingestionCallback(@RequestBody IngestionCallbackRequest request) {
        knowledgeService.updateIngestion(request);
        return R.ok();
    }
}

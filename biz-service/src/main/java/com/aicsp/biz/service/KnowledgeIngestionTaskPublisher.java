package com.aicsp.biz.service;

import com.aicsp.common.constant.MQTopicConstants;
import com.aicsp.common.dto.event.KnowledgeIngestionTaskEvent;
import lombok.RequiredArgsConstructor;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class KnowledgeIngestionTaskPublisher {

    private final ObjectProvider<RocketMQTemplate> rocketMQTemplateProvider;
    private final PythonEngineClient pythonEngineClient;

    public void publish(KnowledgeIngestionTaskEvent event) {
        RocketMQTemplate template = rocketMQTemplateProvider.getIfAvailable();
        if (template == null) {
            pythonEngineClient.submitIngestion(event);
            return;
        }
        template.convertAndSend(MQTopicConstants.TOPIC_KNOWLEDGE_INGESTION_TASK, event);
    }
}

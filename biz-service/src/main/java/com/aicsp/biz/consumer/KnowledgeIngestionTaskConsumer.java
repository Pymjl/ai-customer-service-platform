package com.aicsp.biz.consumer;

import com.aicsp.biz.service.KnowledgeService;
import com.aicsp.common.constant.MQTopicConstants;
import com.aicsp.common.dto.event.KnowledgeIngestionTaskEvent;
import lombok.RequiredArgsConstructor;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.springframework.stereotype.Component;

@Component
@RocketMQMessageListener(
        topic = MQTopicConstants.TOPIC_KNOWLEDGE_INGESTION_TASK,
        consumerGroup = "biz-service-knowledge-ingestion-consumer",
        maxReconsumeTimes = 5
)
@RequiredArgsConstructor
public class KnowledgeIngestionTaskConsumer implements RocketMQListener<KnowledgeIngestionTaskEvent> {

    private final KnowledgeService knowledgeService;

    @Override
    public void onMessage(KnowledgeIngestionTaskEvent event) {
        knowledgeService.dispatchIngestionTask(event);
    }
}

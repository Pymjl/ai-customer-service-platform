package com.aicsp.biz.consumer;

import com.aicsp.biz.service.MessageService;
import com.aicsp.biz.service.SessionService;
import com.aicsp.common.constant.MQTopicConstants;
import com.aicsp.common.dto.event.MessageCompletedEvent;
import lombok.RequiredArgsConstructor;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.springframework.stereotype.Component;

@Component
@RocketMQMessageListener(
        topic = MQTopicConstants.TOPIC_CHAT_MESSAGE_COMPLETED,
        consumerGroup = "biz-service-consumer"
)
@RequiredArgsConstructor
public class MessageCompletedConsumer implements RocketMQListener<MessageCompletedEvent> {

    private final SessionService sessionService;
    private final MessageService messageService;

    @Override
    public void onMessage(MessageCompletedEvent event) {
        sessionService.ensureSession(event.getSessionId(), event.getUserId(), event.getTenantId());
        messageService.saveCompletedMessage(event);
    }
}

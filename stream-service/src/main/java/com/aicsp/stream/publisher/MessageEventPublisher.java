package com.aicsp.stream.publisher;

import com.aicsp.common.constant.MQTopicConstants;
import com.aicsp.common.constant.MessageStatusConstants;
import com.aicsp.common.dto.event.MessageCompletedEvent;
import lombok.RequiredArgsConstructor;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

@Component
@RequiredArgsConstructor
public class MessageEventPublisher {

    private static final int AI_REPLY_MAX_LENGTH = 65536;

    private final RocketMQTemplate rocketMQTemplate;

    public Mono<Void> publishCompleted(MessageCompletedEvent event) {
        return Mono.fromRunnable(() -> {
            if (event.getAiReply() != null && event.getAiReply().length() > AI_REPLY_MAX_LENGTH) {
                event.setAiReply(event.getAiReply().substring(0, AI_REPLY_MAX_LENGTH));
                event.setStatus(MessageStatusConstants.INTERRUPTED);
            }
            rocketMQTemplate.convertAndSend(MQTopicConstants.TOPIC_CHAT_MESSAGE_COMPLETED, event);
        }).subscribeOn(Schedulers.boundedElastic()).then();
    }
}

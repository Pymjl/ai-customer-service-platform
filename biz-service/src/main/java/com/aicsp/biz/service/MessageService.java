package com.aicsp.biz.service;

import com.aicsp.biz.dto.response.MessageDTO;
import com.aicsp.common.dto.event.MessageCompletedEvent;
import java.util.List;

public interface MessageService {

    List<MessageDTO> listMessages(String sessionId);

    MessageDTO getMessage(String messageId);

    void deleteMessage(String messageId);

    void saveCompletedMessage(MessageCompletedEvent event);
}

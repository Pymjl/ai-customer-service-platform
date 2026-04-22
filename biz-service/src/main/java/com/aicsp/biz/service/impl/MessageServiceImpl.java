package com.aicsp.biz.service.impl;

import com.aicsp.biz.dto.response.MessageDTO;
import com.aicsp.biz.mapper.MessageMapper;
import com.aicsp.biz.mapper.converter.MessageConverter;
import com.aicsp.biz.service.MessageService;
import com.aicsp.common.dto.event.MessageCompletedEvent;
import java.util.Collections;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class MessageServiceImpl implements MessageService {

    private final MessageMapper messageMapper;
    private final MessageConverter messageConverter;

    @Override
    public List<MessageDTO> listMessages(String sessionId) {
        return Collections.emptyList();
    }

    @Override
    public void saveCompletedMessage(MessageCompletedEvent event) {
        messageConverter.fromEvent(event);
    }
}

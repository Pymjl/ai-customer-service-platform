package com.aicsp.biz.service.impl;

import com.aicsp.biz.dto.response.MessageDTO;
import com.aicsp.biz.entity.Message;
import com.aicsp.biz.mapper.MessageMapper;
import com.aicsp.biz.mapper.converter.MessageConverter;
import com.aicsp.biz.security.RequestIdentity;
import com.aicsp.biz.security.RequestIdentityProvider;
import com.aicsp.biz.service.MessageService;
import com.aicsp.common.dto.event.MessageCompletedEvent;
import com.aicsp.common.exception.BizException;
import com.aicsp.common.exception.ErrorCode;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
public class MessageServiceImpl implements MessageService {

    private final MessageMapper messageMapper;
    private final MessageConverter messageConverter;
    private final RequestIdentityProvider identityProvider;

    @Override
    public List<MessageDTO> listMessages(String sessionId) {
        RequestIdentity identity = identityProvider.current();
        return messageConverter.toDTOList(messageMapper.listByOwner(sessionId, identity.userId(), identity.tenantId()));
    }

    @Override
    public MessageDTO getMessage(String messageId) {
        RequestIdentity identity = identityProvider.current();
        Message message = messageMapper.findByOwner(messageId, identity.userId(), identity.tenantId());
        if (message == null) {
            throw new BizException(ErrorCode.PARAM_INVALID, "消息不存在或无权访问");
        }
        return messageConverter.toDTO(message);
    }

    @Override
    @Transactional
    public void deleteMessage(String messageId) {
        RequestIdentity identity = identityProvider.current();
        int deleted = messageMapper.softDeleteByOwner(messageId, identity.userId(), identity.tenantId());
        if (deleted == 0) {
            throw new BizException(ErrorCode.PARAM_INVALID, "消息不存在或无权访问");
        }
    }

    @Override
    @Transactional
    public void saveCompletedMessage(MessageCompletedEvent event) {
        if (!StringUtils.hasText(event.getMessageId())) {
            throw new BizException(ErrorCode.PARAM_INVALID, "messageId 不能为空");
        }
        Message message = messageConverter.fromEvent(event);
        if (!StringUtils.hasText(message.getStatus())) {
            message.setStatus("COMPLETED");
        }
        if (!StringUtils.hasText(message.getTraceId())) {
            message.setTraceId("");
        }
        messageMapper.insertIgnore(message);
    }
}

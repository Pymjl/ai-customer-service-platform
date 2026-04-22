package com.aicsp.biz.mapper.converter;

import com.aicsp.biz.dto.response.MessageDTO;
import com.aicsp.biz.entity.Message;
import com.aicsp.common.dto.event.MessageCompletedEvent;
import java.util.List;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface MessageConverter {

    MessageDTO toDTO(Message message);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(source = "userMessage", target = "userMsg")
    Message fromEvent(MessageCompletedEvent event);

    List<MessageDTO> toDTOList(List<Message> messages);
}

package com.aicsp.biz.mapper.converter;

import com.aicsp.biz.dto.request.SessionCreateRequest;
import com.aicsp.biz.dto.response.SessionDTO;
import com.aicsp.biz.entity.Session;
import java.util.List;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface SessionConverter {

    SessionDTO toDTO(Session session);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "status", expression = "java(1)")
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    Session toEntity(SessionCreateRequest request);

    List<SessionDTO> toDTOList(List<Session> sessions);
}

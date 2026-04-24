package com.aicsp.user.mapper.converter;

import com.aicsp.user.dto.request.UserCreateRequest;
import com.aicsp.user.dto.response.UserDTO;
import com.aicsp.user.entity.User;
import java.util.List;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface UserConverter {

    UserDTO toDTO(User user);

    @Mapping(target = "id", expression = "java(com.aicsp.common.util.DistributedIdUtils.nextId())")
    @Mapping(target = "userId", expression = "java(java.util.UUID.randomUUID().toString())")
    @Mapping(target = "status", expression = "java(1)")
    @Mapping(target = "createdBy", expression = "java(0L)")
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedBy", expression = "java(0L)")
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "deleted", expression = "java(false)")
    @Mapping(target = "deletedAt", ignore = true)
    User toEntity(UserCreateRequest request);

    List<UserDTO> toDTOList(List<User> users);
}

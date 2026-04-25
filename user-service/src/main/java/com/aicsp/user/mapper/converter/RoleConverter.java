package com.aicsp.user.mapper.converter;

import com.aicsp.user.dto.request.RoleCreateRequest;
import com.aicsp.user.dto.response.RoleDTO;
import com.aicsp.user.entity.Role;
import java.util.List;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface RoleConverter {

    RoleDTO toDTO(Role role);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "createdBy", expression = "java(0L)")
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedBy", expression = "java(0L)")
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "deleted", expression = "java(false)")
    Role toEntity(RoleCreateRequest request);

    List<RoleDTO> toDTOList(List<Role> roles);
}

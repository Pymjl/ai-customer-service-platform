package com.aicsp.user.mapper.converter;

import com.aicsp.user.dto.response.PermissionDTO;
import com.aicsp.user.entity.Permission;
import java.util.List;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface PermissionConverter {

    PermissionDTO toDTO(Permission permission);

    List<PermissionDTO> toDTOList(List<Permission> permissions);
}

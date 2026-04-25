package com.aicsp.user.mapper;

import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface RolePermissionMapper {
    int deleteByRoleId(@Param("roleId") Long roleId);
    int insert(@Param("id") Long id, @Param("roleId") Long roleId, @Param("permissionId") Long permissionId);
}

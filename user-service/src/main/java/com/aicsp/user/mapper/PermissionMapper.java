package com.aicsp.user.mapper;

import com.aicsp.user.entity.Permission;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface PermissionMapper {
    List<Permission> selectAll();
}

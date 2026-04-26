package com.aicsp.user.mapper;

import com.aicsp.user.entity.Role;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface RoleMapper {
    List<Role> selectAll();
    Role selectById(@Param("id") Long id);
    Role selectByCode(@Param("roleCode") String roleCode);
    List<Role> selectByUserId(@Param("userId") String userId);
    int insert(Role role);
    int update(Role role);
    int deleteById(@Param("id") Long id);
}

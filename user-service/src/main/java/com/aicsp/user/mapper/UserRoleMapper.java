package com.aicsp.user.mapper;

import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface UserRoleMapper {
    int deleteByUserId(@Param("userId") String userId);
    int insert(@Param("id") Long id, @Param("userId") String userId, @Param("roleId") Long roleId);
    List<Long> selectRoleIdsByUserId(@Param("userId") String userId);
}

package com.aicsp.user.mapper;

import com.aicsp.user.entity.ApiResource;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface ApiResourceMapper {
    List<ApiResource> selectAll();
    List<ApiResource> selectByRoleIds(@Param("roleIds") List<Long> roleIds);
    ApiResource selectById(@Param("id") Long id);
    ApiResource selectByCode(@Param("resourceCode") String resourceCode);
    int insert(ApiResource resource);
    int update(ApiResource resource);
    int updateEnabled(@Param("id") Long id, @Param("enabled") Boolean enabled);
    int deleteRoleResources(@Param("roleId") Long roleId);
    int insertRoleResource(@Param("id") Long id, @Param("roleId") Long roleId, @Param("resourceId") Long resourceId);
    List<Long> selectResourceIdsByRoleId(@Param("roleId") Long roleId);
}

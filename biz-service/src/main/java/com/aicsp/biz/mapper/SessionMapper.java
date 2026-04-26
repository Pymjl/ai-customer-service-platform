package com.aicsp.biz.mapper;

import com.aicsp.biz.entity.Session;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface SessionMapper {

    List<Session> listByOwner(@Param("userId") String userId, @Param("tenantId") String tenantId);

    Session findBySessionId(@Param("sessionId") String sessionId);

    int insertIgnore(Session session);

    int updateByOwner(@Param("sessionId") String sessionId, @Param("userId") String userId,
                      @Param("tenantId") String tenantId, @Param("title") String title,
                      @Param("status") Integer status);

    int softDeleteByOwner(@Param("sessionId") String sessionId, @Param("userId") String userId,
                          @Param("tenantId") String tenantId);
}

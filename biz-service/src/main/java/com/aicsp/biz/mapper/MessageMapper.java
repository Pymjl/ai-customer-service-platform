package com.aicsp.biz.mapper;

import com.aicsp.biz.entity.Message;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface MessageMapper {

    List<Message> listByOwner(@Param("sessionId") String sessionId, @Param("userId") String userId,
                              @Param("tenantId") String tenantId);

    Message findByMessageId(@Param("messageId") String messageId);

    Message findByOwner(@Param("messageId") String messageId, @Param("userId") String userId,
                        @Param("tenantId") String tenantId);

    int insertIgnore(Message message);

    int softDeleteByOwner(@Param("messageId") String messageId, @Param("userId") String userId,
                          @Param("tenantId") String tenantId);
}

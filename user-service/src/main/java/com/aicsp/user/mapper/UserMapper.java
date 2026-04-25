package com.aicsp.user.mapper;

import com.aicsp.user.entity.User;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface UserMapper {
    List<User> selectAll();
    User selectByUsername(@Param("tenantId") String tenantId, @Param("username") String username);
    User selectByUserId(@Param("userId") String userId);
    int insert(User user);
    int updateProfile(User user);
    int updateAvatar(@Param("userId") String userId, @Param("avatarPath") String avatarPath);
    int updatePassword(@Param("userId") String userId, @Param("password") String password);
    int updateStatus(@Param("userId") String userId, @Param("status") Integer status);
}

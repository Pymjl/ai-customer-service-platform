package com.aicsp.user.service;

import com.aicsp.user.dto.request.UserCreateRequest;
import com.aicsp.user.dto.request.UserUpdateRequest;
import com.aicsp.user.dto.response.UserDTO;
import java.util.List;
import org.springframework.web.multipart.MultipartFile;

public interface UserService {

    List<UserDTO> listUsers();

    void createUser(UserCreateRequest request);

    UserDTO getUser(String userId);

    void updateUser(String userId, UserUpdateRequest request);

    String updateAvatar(String userId, MultipartFile avatar);
}

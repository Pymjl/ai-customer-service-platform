package com.aicsp.user.service;

import com.aicsp.user.dto.request.UserCreateRequest;
import com.aicsp.user.dto.response.UserDTO;
import java.util.List;

public interface UserService {

    List<UserDTO> listUsers();

    void createUser(UserCreateRequest request);
}

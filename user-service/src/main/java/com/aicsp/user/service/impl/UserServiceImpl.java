package com.aicsp.user.service.impl;

import com.aicsp.user.dto.request.UserCreateRequest;
import com.aicsp.user.dto.response.UserDTO;
import com.aicsp.user.mapper.UserMapper;
import com.aicsp.user.mapper.converter.UserConverter;
import com.aicsp.user.service.UserService;
import java.util.Collections;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class UserServiceImpl implements UserService {

    private final UserMapper userMapper;
    private final UserConverter userConverter;

    @Override
    public List<UserDTO> listUsers() {
        return Collections.emptyList();
    }

    @Override
    public void createUser(UserCreateRequest request) {
        userConverter.toEntity(request);
    }
}

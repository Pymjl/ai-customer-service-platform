package com.aicsp.user.service.impl;

import com.aicsp.common.util.DistributedIdUtils;
import com.aicsp.user.dto.request.UserCreateRequest;
import com.aicsp.user.dto.request.UserUpdateRequest;
import com.aicsp.user.dto.response.UserDTO;
import com.aicsp.user.entity.User;
import com.aicsp.user.mapper.UserMapper;
import com.aicsp.user.mapper.converter.UserConverter;
import com.aicsp.user.service.FileStorageService;
import com.aicsp.user.service.UserService;
import java.util.List;
import java.util.Locale;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

@Service
@RequiredArgsConstructor
public class UserServiceImpl implements UserService {
    private final UserMapper userMapper;
    private final UserConverter userConverter;
    private final PasswordEncoder passwordEncoder;
    private final FileStorageService fileStorageService;

    @Override
    public List<UserDTO> listUsers() {
        return userMapper.selectAll().stream()
                .map(userConverter::toDTO)
                .toList();
    }

    @Override
    public void createUser(UserCreateRequest request) {
        String username = normalizeUsername(request.getUsername());
        if (userMapper.selectByUsername(request.getTenantId(), username) != null) {
            throw new IllegalArgumentException("账户名已存在");
        }
        User user = userConverter.toEntity(request);
        user.setId(DistributedIdUtils.nextId());
        user.setUserId("U" + user.getId());
        user.setUsername(username);
        user.setPassword(passwordEncoder.encode(request.getPassword()));
        user.setRealName(request.getRealName().trim());
        user.setEmail(trimToNull(request.getEmail()));
        user.setPhone(trimToNull(request.getPhone()));
        user.setAddress(trimToNull(request.getAddress()));
        user.setStatus(1);
        userMapper.insert(user);
    }

    @Override
    public UserDTO getUser(String userId) {
        User user = userMapper.selectByUserId(userId);
        if (user == null) {
            throw new IllegalArgumentException("user not found");
        }
        return userConverter.toDTO(user);
    }

    @Override
    public void updateUser(String userId, UserUpdateRequest request) {
        User user = userMapper.selectByUserId(userId);
        if (user == null) {
            throw new IllegalArgumentException("user not found");
        }
        user.setGender(request.getGender());
        user.setRealName(request.getRealName().trim());
        user.setAge(request.getAge());
        user.setEmail(trimToNull(request.getEmail()));
        user.setPhone(trimToNull(request.getPhone()));
        user.setAddress(trimToNull(request.getAddress()));
        user.setStatus(request.getStatus() == null ? user.getStatus() : request.getStatus());
        userMapper.updateProfile(user);
    }

    @Override
    public String updateAvatar(String userId, MultipartFile avatar) {
        User user = userMapper.selectByUserId(userId);
        if (user == null) {
            throw new IllegalArgumentException("user not found");
        }
        String avatarPath = fileStorageService.uploadAvatar(userId, avatar);
        userMapper.updateAvatar(userId, avatarPath);
        return avatarPath;
    }

    private String normalizeUsername(String username) {
        return username.trim().toLowerCase(Locale.ROOT);
    }

    private String trimToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }
}

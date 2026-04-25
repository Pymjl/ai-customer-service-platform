package com.aicsp.user.controller;

import com.aicsp.common.result.R;
import com.aicsp.user.dto.request.UserCreateRequest;
import com.aicsp.user.dto.request.UserUpdateRequest;
import com.aicsp.user.dto.rbac.AssignUserRolesRequest;
import com.aicsp.user.dto.response.UserDTO;
import com.aicsp.user.service.RbacService;
import com.aicsp.user.service.UserService;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class UserController {
    private final UserService userService;
    private final RbacService rbacService;

    @GetMapping
    public R<List<UserDTO>> listUsers() {
        return R.ok(userService.listUsers());
    }

    @PostMapping
    public R<Void> createUser(@Valid @RequestBody UserCreateRequest request) {
        userService.createUser(request);
        return R.ok();
    }

    @GetMapping("/{userId}")
    public R<UserDTO> getUser(@PathVariable("userId") String userId) {
        return R.ok(userService.getUser(userId));
    }

    @PutMapping("/{userId}")
    public R<Void> updateUser(@PathVariable("userId") String userId, @Valid @RequestBody UserUpdateRequest request) {
        userService.updateUser(userId, request);
        return R.ok();
    }

    @PutMapping(value = "/{userId}/avatar", consumes = "multipart/form-data")
    public R<String> updateAvatar(@PathVariable("userId") String userId, @RequestPart("avatar") MultipartFile avatar) {
        return R.ok(userService.updateAvatar(userId, avatar));
    }

    @GetMapping("/{userId}/roles")
    public R<List<Long>> userRoles(@PathVariable("userId") String userId) {
        return R.ok(rbacService.userRoleIds(userId));
    }

    @PutMapping("/{userId}/roles")
    public R<Void> assignUserRoles(@PathVariable("userId") String userId, @RequestBody AssignUserRolesRequest request) {
        rbacService.assignUserRoles(userId, request.getRoleIds());
        return R.ok();
    }
}

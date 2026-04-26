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

    /**
     * 用途：查询用户列表。
     *
     * @return 用户列表，包含账号、租户、资料、状态和创建时间
     */
    @GetMapping
    public R<List<UserDTO>> listUsers() {
        return R.ok(userService.listUsers());
    }

    /**
     * 用途：创建后台用户。
     *
     * @param request 用户创建请求，包含账号、密码、租户、资料和状态
     * @return 空结果，表示创建成功
     */
    @PostMapping
    public R<Void> createUser(@Valid @RequestBody UserCreateRequest request) {
        userService.createUser(request);
        return R.ok();
    }

    /**
     * 用途：查询指定用户详情。
     *
     * @param userId 用户业务 ID
     * @return 用户详情，包含账号、租户、资料、状态和角色信息
     */
    @GetMapping("/{userId}")
    public R<UserDTO> getUser(@PathVariable("userId") String userId) {
        return R.ok(userService.getUser(userId));
    }

    /**
     * 用途：更新指定用户资料和状态。
     *
     * @param userId 用户业务 ID
     * @param request 用户更新请求，包含可修改的资料字段和状态
     * @return 空结果，表示更新成功
     */
    @PutMapping("/{userId}")
    public R<Void> updateUser(@PathVariable("userId") String userId, @Valid @RequestBody UserUpdateRequest request) {
        userService.updateUser(userId, request);
        return R.ok();
    }

    /**
     * 用途：更新指定用户头像。
     *
     * @param userId 用户业务 ID
     * @param avatar 头像文件
     * @return 头像访问路径
     */
    @PutMapping(value = "/{userId}/avatar", consumes = "multipart/form-data")
    public R<String> updateAvatar(@PathVariable("userId") String userId, @RequestPart("avatar") MultipartFile avatar) {
        return R.ok(userService.updateAvatar(userId, avatar));
    }

    /**
     * 用途：查询指定用户已分配的启用角色。
     *
     * @param userId 用户业务 ID
     * @return 角色 ID 列表，仅包含启用角色
     */
    @GetMapping("/{userId}/roles")
    public R<List<Long>> userRoles(@PathVariable("userId") String userId) {
        return R.ok(rbacService.userRoleIds(userId));
    }

    /**
     * 用途：为指定用户分配角色，停用角色会被忽略。
     *
     * @param userId 用户业务 ID
     * @param request 用户角色授权请求，包含角色 ID 列表
     * @return 空结果，表示保存成功
     */
    @PutMapping("/{userId}/roles")
    public R<Void> assignUserRoles(@PathVariable("userId") String userId, @RequestBody AssignUserRolesRequest request) {
        rbacService.assignUserRoles(userId, request.getRoleIds());
        return R.ok();
    }
}

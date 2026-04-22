package com.aicsp.user.controller;

import com.aicsp.common.result.R;
import com.aicsp.user.dto.request.UserCreateRequest;
import com.aicsp.user.dto.response.UserDTO;
import com.aicsp.user.service.UserService;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    @GetMapping
    public R<List<UserDTO>> listUsers() {
        return R.ok(userService.listUsers());
    }

    @PostMapping
    public R<?> createUser(@Valid @RequestBody UserCreateRequest request) {
        userService.createUser(request);
        return R.ok();
    }
}

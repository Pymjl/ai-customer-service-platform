package com.aicsp.user.controller;

import com.aicsp.common.result.R;
import com.aicsp.user.dto.request.RoleCreateRequest;
import com.aicsp.user.dto.response.RoleDTO;
import com.aicsp.user.service.RoleService;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/roles")
@RequiredArgsConstructor
public class RoleController {

    private final RoleService roleService;

    @GetMapping
    public R<List<RoleDTO>> listRoles() {
        return R.ok(roleService.listRoles());
    }

    @PostMapping
    public R<?> createRole(@Valid @RequestBody RoleCreateRequest request) {
        roleService.createRole(request);
        return R.ok();
    }
}

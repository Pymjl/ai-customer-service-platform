package com.aicsp.biz.controller;

import com.aicsp.common.result.R;
import com.aicsp.biz.dto.request.SessionCreateRequest;
import com.aicsp.biz.dto.response.SessionDTO;
import com.aicsp.biz.service.SessionService;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/sessions")
@RequiredArgsConstructor
public class SessionController {

    private final SessionService sessionService;

    @GetMapping
    public R<List<SessionDTO>> listSessions() {
        return R.ok(sessionService.listSessions());
    }

    @PostMapping
    public R<?> createSession(@Valid @RequestBody SessionCreateRequest request) {
        sessionService.createSession(request);
        return R.ok();
    }
}

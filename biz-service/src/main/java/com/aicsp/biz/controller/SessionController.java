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

    /**
     * 用途：查询客服会话列表。
     *
     * @return 会话列表，包含会话 ID、用户、租户、标题、状态和创建时间
     */
    @GetMapping
    public R<List<SessionDTO>> listSessions() {
        return R.ok(sessionService.listSessions());
    }

    /**
     * 用途：创建新的客服会话。
     *
     * @param request 会话创建请求，包含用户、租户和标题等信息
     * @return 空结果，表示创建成功
     */
    @PostMapping
    public R<?> createSession(@Valid @RequestBody SessionCreateRequest request) {
        sessionService.createSession(request);
        return R.ok();
    }
}

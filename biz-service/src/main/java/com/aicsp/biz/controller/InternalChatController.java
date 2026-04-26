package com.aicsp.biz.controller;

import com.aicsp.biz.service.MessageService;
import com.aicsp.biz.service.SessionService;
import com.aicsp.common.dto.event.MessageCompletedEvent;
import com.aicsp.common.result.R;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/internal/chat")
@RequiredArgsConstructor
public class InternalChatController {

    private final SessionService sessionService;
    private final MessageService messageService;

    @PostMapping("/message-completed")
    public R<?> messageCompleted(@Valid @RequestBody MessageCompletedEvent event) {
        sessionService.ensureSession(event.getSessionId(), event.getUserId(), event.getTenantId());
        messageService.saveCompletedMessage(event);
        return R.ok();
    }
}

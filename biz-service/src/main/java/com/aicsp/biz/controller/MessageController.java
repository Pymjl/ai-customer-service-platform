package com.aicsp.biz.controller;

import com.aicsp.common.result.R;
import com.aicsp.biz.dto.response.MessageDTO;
import com.aicsp.biz.service.MessageService;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/messages")
@RequiredArgsConstructor
public class MessageController {

    private final MessageService messageService;

    @GetMapping
    public R<List<MessageDTO>> listMessages(@RequestParam(required = false) String sessionId) {
        return R.ok(messageService.listMessages(sessionId));
    }
}

package com.aicsp.biz.controller;

import com.aicsp.common.result.R;
import com.aicsp.biz.dto.response.MessageDTO;
import com.aicsp.biz.service.MessageService;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/messages")
@RequiredArgsConstructor
public class MessageController {

    private final MessageService messageService;

    /**
     * 用途：查询消息列表，可按会话过滤。
     *
     * @param sessionId 会话 ID，可为空；为空时查询全部可见消息
     * @return 消息列表，包含会话 ID、发送方、内容和创建时间
     */
    @GetMapping
    public R<List<MessageDTO>> listMessages(@RequestParam(required = false) String sessionId) {
        return R.ok(messageService.listMessages(sessionId));
    }

    @GetMapping("/{messageId}")
    public R<MessageDTO> getMessage(@PathVariable String messageId) {
        return R.ok(messageService.getMessage(messageId));
    }

    @DeleteMapping("/{messageId}")
    public R<?> deleteMessage(@PathVariable String messageId) {
        messageService.deleteMessage(messageId);
        return R.ok();
    }
}

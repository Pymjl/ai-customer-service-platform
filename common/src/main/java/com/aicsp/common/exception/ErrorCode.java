package com.aicsp.common.exception;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum ErrorCode {

    USER_NOT_FOUND(1001, "用户不存在"),
    SESSION_NOT_EXIST(2001, "会话不存在"),
    ENGINE_TIMEOUT(3001, "AI 引擎响应超时"),
    INTERNAL_TOKEN_INVALID(4001, "内部调用 Token 无效"),
    PARAM_INVALID(400, "请求参数错误"),
    JSON_PROCESS_ERROR(5001, "JSON 处理失败");

    private final Integer code;
    private final String message;
}

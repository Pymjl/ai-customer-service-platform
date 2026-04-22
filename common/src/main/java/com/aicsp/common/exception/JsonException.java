package com.aicsp.common.exception;

import lombok.Getter;

@Getter
public class JsonException extends RuntimeException {

    private final Integer code = ErrorCode.JSON_PROCESS_ERROR.getCode();
    private final String bizMessage;

    public JsonException(String detail, Throwable cause) {
        super(detail, cause);
        this.bizMessage = detail;
    }
}

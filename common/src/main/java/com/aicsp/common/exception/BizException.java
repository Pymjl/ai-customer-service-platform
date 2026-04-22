package com.aicsp.common.exception;

import lombok.Getter;

@Getter
public class BizException extends RuntimeException {

    private final Integer code;
    private final String bizMessage;

    public BizException(ErrorCode errorCode) {
        super(errorCode.getMessage());
        this.code = errorCode.getCode();
        this.bizMessage = errorCode.getMessage();
    }

    public BizException(ErrorCode errorCode, String detail) {
        super(detail);
        this.code = errorCode.getCode();
        this.bizMessage = detail;
    }
}

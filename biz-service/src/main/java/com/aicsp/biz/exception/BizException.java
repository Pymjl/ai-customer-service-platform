package com.aicsp.biz.exception;

public class BizException extends RuntimeException {

    private final int code;
    private final String bizMessage;

    public BizException(int code, String bizMessage) {
        super(bizMessage);
        this.code = code;
        this.bizMessage = bizMessage;
    }

    public int getCode() {
        return code;
    }

    public String getBizMessage() {
        return bizMessage;
    }
}

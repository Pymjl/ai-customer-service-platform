package com.aicsp.user.exception;

public class JsonException extends RuntimeException {

    private final int code;
    private final String bizMessage;

    public JsonException(int code, String bizMessage) {
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

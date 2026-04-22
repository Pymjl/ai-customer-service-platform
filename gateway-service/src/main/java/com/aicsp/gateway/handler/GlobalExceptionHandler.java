package com.aicsp.gateway.handler;

import com.aicsp.common.exception.BizException;
import com.aicsp.common.exception.JsonException;
import com.aicsp.common.result.R;
import com.aicsp.common.result.ResultCode;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.bind.support.WebExchangeBindException;
import reactor.core.publisher.Mono;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(BizException.class)
    public Mono<R<?>> handleBizException(BizException e) {
        return Mono.just(R.fail(e.getCode(), e.getBizMessage()));
    }

    @ExceptionHandler(JsonException.class)
    public Mono<R<?>> handleJsonException(JsonException e) {
        return Mono.just(R.fail(e.getCode(), e.getBizMessage()));
    }

    @ExceptionHandler(WebExchangeBindException.class)
    public Mono<R<?>> handleBindException(WebExchangeBindException e) {
        return Mono.just(R.fail(ResultCode.BAD_REQUEST.getCode(), e.getMessage()));
    }

    @ExceptionHandler(Exception.class)
    public Mono<R<?>> handleException(Exception e) {
        return Mono.just(R.fail(ResultCode.SYSTEM_ERROR));
    }
}

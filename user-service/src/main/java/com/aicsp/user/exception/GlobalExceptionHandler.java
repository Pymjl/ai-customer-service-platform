package com.aicsp.user.exception;

import com.aicsp.common.exception.BizException;
import com.aicsp.common.exception.JsonException;
import com.aicsp.common.result.R;
import com.aicsp.common.result.ResultCode;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {
    @ExceptionHandler(BizException.class)
    public R<?> handleBizException(BizException exception) {
        return R.fail(exception.getCode(), exception.getBizMessage());
    }

    @ExceptionHandler(JsonException.class)
    public R<?> handleJsonException(JsonException exception) {
        return R.fail(exception.getCode(), exception.getBizMessage());
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public R<?> handleIllegalArgumentException(IllegalArgumentException exception) {
        return R.fail(ResultCode.BAD_REQUEST.getCode(), exception.getMessage());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public R<?> handleValidException(MethodArgumentNotValidException exception) {
        String message = exception.getBindingResult().getFieldErrors().stream()
                .map(fieldError -> fieldError.getField() + ": " + fieldError.getDefaultMessage())
                .collect(Collectors.joining("; "));
        return R.fail(ResultCode.BAD_REQUEST.getCode(), message);
    }

    @ExceptionHandler(Exception.class)
    public R<?> handleException(Exception exception) {
        log.error("Unhandled user-service exception", exception);
        return R.fail(ResultCode.SYSTEM_ERROR);
    }
}

package com.aicsp.user.exception;

import com.aicsp.common.exception.BizException;
import com.aicsp.common.exception.JsonException;
import com.aicsp.common.result.R;
import com.aicsp.common.result.ResultCode;
import java.util.stream.Collectors;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(BizException.class)
    public R<?> handleBizException(BizException e) {
        return R.fail(e.getCode(), e.getBizMessage());
    }

    @ExceptionHandler(JsonException.class)
    public R<?> handleJsonException(JsonException e) {
        return R.fail(e.getCode(), e.getBizMessage());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public R<?> handleValidException(MethodArgumentNotValidException e) {
        String message = e.getBindingResult().getFieldErrors().stream()
                .map(fieldError -> fieldError.getField() + ": " + fieldError.getDefaultMessage())
                .collect(Collectors.joining("; "));
        return R.fail(ResultCode.BAD_REQUEST.getCode(), message);
    }

    @ExceptionHandler(Exception.class)
    public R<?> handleException(Exception e) {
        return R.fail(ResultCode.SYSTEM_ERROR);
    }
}

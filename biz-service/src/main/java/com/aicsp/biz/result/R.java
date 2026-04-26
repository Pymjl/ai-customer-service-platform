package com.aicsp.biz.result;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class R<T> {

    private Boolean succeed;
    private String message;
    private T data;

    public static <T> R<T> ok(T data) {
        return new R<>(true, ResultCode.SUCCESS.getMessage(), data);
    }

    public static R<Void> ok() {
        return new R<>(true, ResultCode.SUCCESS.getMessage(), null);
    }

    public static <T> R<T> fail(ResultCode resultCode) {
        return new R<>(false, resultCode.getMessage(), null);
    }

    public static <T> R<T> fail(int code, String message) {
        return new R<>(false, message, null);
    }
}

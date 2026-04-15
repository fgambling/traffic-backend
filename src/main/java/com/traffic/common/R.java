package com.traffic.common;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;

/**
 * 统一API响应体
 * code=0 表示成功，非0表示失败
 */
@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class R<T> {

    private int code;
    private String message;
    private T data;

    private R(int code, String message, T data) {
        this.code = code;
        this.message = message;
        this.data = data;
    }

    /** 成功，无data */
    public static <T> R<T> ok() {
        return new R<>(0, "success", null);
    }

    /** 成功，带data */
    public static <T> R<T> ok(T data) {
        return new R<>(0, "success", data);
    }

    /** 成功，自定义message */
    public static <T> R<T> ok(String message, T data) {
        return new R<>(0, message, data);
    }

    /** 失败，仅message */
    public static <T> R<T> fail(String message) {
        return new R<>(500, message, null);
    }

    /** 失败，指定错误码 */
    public static <T> R<T> fail(int code, String message) {
        return new R<>(code, message, null);
    }

    /** 失败，使用ErrorCode枚举 */
    public static <T> R<T> fail(ErrorCode errorCode) {
        return new R<>(errorCode.getCode(), errorCode.getMessage(), null);
    }
}

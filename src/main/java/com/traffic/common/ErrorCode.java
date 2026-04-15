package com.traffic.common;

import lombok.Getter;

/**
 * 统一错误码枚举
 */
@Getter
public enum ErrorCode {

    // 成功
    SUCCESS(0, "success"),

    // 4xx 客户端错误
    BAD_REQUEST(400, "请求参数错误"),
    UNAUTHORIZED(401, "未登录或登录已过期"),
    FORBIDDEN(403, "无权限访问"),
    NOT_FOUND(404, "资源不存在"),

    // 业务错误码 (1xxx)
    USER_NOT_REGISTERED(1001, "用户未注册"),
    MERCHANT_NOT_FOUND(1002, "商家不存在"),
    DEVICE_BIND_NOT_FOUND(1003, "设备绑定关系不存在，请检查bindId"),
    INVALID_ATTRIBUTES_LENGTH(1004, "attributes属性数组长度必须为26"),
    WX_LOGIN_FAILED(1005, "微信登录失败"),

    // 5xx 服务端错误
    INTERNAL_ERROR(500, "服务器内部错误");

    private final int code;
    private final String message;

    ErrorCode(int code, String message) {
        this.code = code;
        this.message = message;
    }
}

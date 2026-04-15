package com.traffic.auth.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

/**
 * 登录成功响应
 */
@Data
@AllArgsConstructor
public class LoginResponse {

    /** JWT Token（有效期7天） */
    private String token;

    /** 角色：merchant / salesman */
    private String role;

    /** 用户ID */
    private Integer userId;

    /** 商家ID（role=merchant时有值） */
    private Integer merchantId;

    /** 用户昵称/名称 */
    private String name;
}

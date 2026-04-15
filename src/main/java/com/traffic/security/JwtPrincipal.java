package com.traffic.security;

import lombok.AllArgsConstructor;
import lombok.Data;

/**
 * JWT认证主体，存储在SecurityContext中
 */
@Data
@AllArgsConstructor
public class JwtPrincipal {

    /** 用户ID（merchant.id 或 salesman.id） */
    private Integer userId;

    /** 角色：merchant / salesman */
    private String role;

    /** 商家ID（role=merchant时有值） */
    private Integer merchantId;
}

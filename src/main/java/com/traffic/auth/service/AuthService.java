package com.traffic.auth.service;

import com.traffic.auth.dto.LoginResponse;
import com.traffic.auth.dto.WxLoginRequest;

/**
 * 认证服务接口
 */
public interface AuthService {

    /**
     * 微信小程序登录
     * @param request 包含code和role
     * @return 登录成功则返回JWT及用户信息
     */
    LoginResponse wxLogin(WxLoginRequest request);
}

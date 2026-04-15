package com.traffic.auth.controller;

import com.traffic.auth.dto.LoginResponse;
import com.traffic.auth.dto.WxLoginRequest;
import com.traffic.auth.service.AuthService;
import com.traffic.common.R;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/**
 * 登录鉴权接口
 */
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    /**
     * 微信小程序登录
     * POST /api/auth/wx-login
     */
    @PostMapping("/wx-login")
    public R<LoginResponse> wxLogin(@Valid @RequestBody WxLoginRequest request) {
        LoginResponse response = authService.wxLogin(request);
        return R.ok(response);
    }
}

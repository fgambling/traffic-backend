package com.traffic.admin.controller;

import com.traffic.auth.util.JwtUtil;
import com.traffic.common.BusinessException;
import com.traffic.common.R;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * 后台管理登录接口
 * POST /api/admin/login  { username, password }
 * 开发阶段固定账号 admin / admin123
 */
@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
public class AdminAuthController {

    private final JwtUtil jwtUtil;

    @PostMapping("/login")
    public R<Map<String, Object>> login(@RequestBody Map<String, String> body) {
        String username = body.getOrDefault("username", "");
        String password = body.getOrDefault("password", "");

        // 开发阶段：硬编码管理员账号
        if (!"admin".equals(username) || !"admin123".equals(password)) {
            throw new BusinessException(401, "账号或密码错误");
        }

        String token = jwtUtil.generateToken(-1, "admin", null);
        return R.ok(Map.of("token", token, "name", "超级管理员"));
    }
}

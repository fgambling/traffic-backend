package com.traffic.admin.controller;

import com.traffic.admin.entity.AdminUser;
import com.traffic.admin.mapper.AdminUserMapper;
import com.traffic.auth.util.JwtUtil;
import com.traffic.common.BusinessException;
import com.traffic.common.R;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * 后台管理登录接口
 * POST /api/admin/login  { username, password }
 */
@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
public class AdminAuthController {

    private final JwtUtil jwtUtil;
    private final AdminUserMapper adminUserMapper;
    private final PasswordEncoder passwordEncoder;

    @PostMapping("/login")
    public R<Map<String, Object>> login(@RequestBody Map<String, String> body) {
        String username = body.getOrDefault("username", "");
        String password = body.getOrDefault("password", "");

        AdminUser admin = adminUserMapper.findByUsername(username);
        if (admin == null || !passwordEncoder.matches(password, admin.getPassword())) {
            throw new BusinessException(401, "账号或密码错误");
        }

        String token = jwtUtil.generateToken(admin.getId().intValue(), "admin", null);
        return R.ok(Map.of("token", token, "name", StringUtils.hasText(admin.getName()) ? admin.getName() : admin.getUsername()));
    }
}

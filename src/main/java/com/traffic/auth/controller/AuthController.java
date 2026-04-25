package com.traffic.auth.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.traffic.auth.dto.LoginResponse;
import com.traffic.auth.dto.WxLoginRequest;
import com.traffic.auth.service.AuthService;
import com.traffic.auth.util.JwtUtil;
import com.traffic.common.BusinessException;
import com.traffic.common.R;
import com.traffic.merchant.entity.Merchant;
import com.traffic.merchant.mapper.MerchantMapper;
import com.traffic.salesman.entity.Salesman;
import com.traffic.salesman.mapper.SalesmanMapper;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * 登录鉴权接口
 */
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;
    private final SalesmanMapper salesmanMapper;
    private final MerchantMapper merchantMapper;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;

    /**
     * 微信小程序登录（商家端）
     * POST /api/auth/wx-login
     */
    @PostMapping("/wx-login")
    public R<LoginResponse> wxLogin(@Valid @RequestBody WxLoginRequest request) {
        LoginResponse response = authService.wxLogin(request);
        return R.ok(response);
    }

    /**
     * 业务员手机号+密码登录
     * POST /api/auth/salesman-login
     * body: { "phone": "138xxx", "password": "123456" }
     */
    @PostMapping("/salesman-login")
    public R<LoginResponse> salesmanLogin(@RequestBody Map<String, String> body) {
        String phone    = body.get("phone");
        String password = body.get("password");
        if (!StringUtils.hasText(phone) || !StringUtils.hasText(password)) {
            throw new BusinessException(400, "手机号和密码不能为空");
        }

        Salesman s = salesmanMapper.selectOne(
                new LambdaQueryWrapper<Salesman>()
                        .eq(Salesman::getPhone, phone)
                        .last("LIMIT 1"));

        if (s == null || !passwordEncoder.matches(password, s.getPassword())) {
            throw new BusinessException(401, "手机号或密码错误");
        }
        if (s.getStatus() != 1) {
            throw new BusinessException(403, "账号已禁用，请联系管理员");
        }

        String token = jwtUtil.generateToken(s.getId(), "salesman", null);
        return R.ok(new LoginResponse(token, "salesman", s.getId(), null, s.getName()));
    }

    /**
     * 商家手机号+密码登录
     * POST /api/auth/merchant-login
     * body: { "phone": "138xxx", "password": "123456" }
     */
    @PostMapping("/merchant-login")
    public R<LoginResponse> merchantLogin(@RequestBody Map<String, String> body) {
        String phone    = body.get("phone");
        String password = body.get("password");
        if (!StringUtils.hasText(phone) || !StringUtils.hasText(password)) {
            throw new BusinessException(400, "手机号和密码不能为空");
        }

        Merchant m = merchantMapper.selectOne(
                new LambdaQueryWrapper<Merchant>()
                        .eq(Merchant::getContactPhone, phone)
                        .last("LIMIT 1"));

        if (m == null || !StringUtils.hasText(m.getPassword())
                || !passwordEncoder.matches(password, m.getPassword())) {
            throw new BusinessException(401, "手机号或密码错误");
        }
        if (m.getStatus() == 2) {
            throw new BusinessException(403, "账号已禁用，请联系管理员");
        }

        String token = jwtUtil.generateToken(m.getId(), "merchant", m.getId());
        return R.ok(new LoginResponse(token, "merchant", m.getId(), m.getId(), m.getName()));
    }
}

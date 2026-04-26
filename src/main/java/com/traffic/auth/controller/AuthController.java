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

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

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
     * 商家手机号+密码登录（第一步）
     * POST /api/auth/merchant-login
     * body: { "phone": "138xxx", "password": "123456" }
     * 返回：单商家直接返回 token；多商家返回 { needSelect: true, merchants: [...] }
     */
    @PostMapping("/merchant-login")
    public R<Map<String, Object>> merchantLogin(@RequestBody Map<String, String> body) {
        String phone    = body.get("phone");
        String password = body.get("password");
        if (!StringUtils.hasText(phone) || !StringUtils.hasText(password)) {
            throw new BusinessException(400, "手机号和密码不能为空");
        }

        // 查该手机号下所有未禁用商家
        List<Merchant> all = merchantMapper.selectList(
                new LambdaQueryWrapper<Merchant>()
                        .eq(Merchant::getContactPhone, phone)
                        .ne(Merchant::getStatus, 2));

        if (all.isEmpty()) throw new BusinessException(401, "手机号或密码错误");

        // 找出密码匹配的商家（同一老板不同店密码可能不同）
        List<Merchant> owned = all.stream()
                .filter(m -> StringUtils.hasText(m.getPassword())
                        && passwordEncoder.matches(password, m.getPassword()))
                .collect(Collectors.toList());

        if (owned.isEmpty()) throw new BusinessException(401, "手机号或密码错误");

        // 只有一家店，直接登录
        if (owned.size() == 1) {
            Merchant m = owned.get(0);
            String token = jwtUtil.generateToken(m.getId(), "merchant", m.getId());
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("needSelect", false);
            result.put("token",      token);
            result.put("role",       "merchant");
            result.put("userId",     m.getId());
            result.put("merchantId", m.getId());
            result.put("name",       m.getName());
            return R.ok(result);
        }

        // 多家店，返回列表让前端选择
        List<Map<String, Object>> merchantList = owned.stream().map(m -> {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("merchantId", m.getId());
            item.put("name",       m.getName());
            item.put("address",    m.getAddress());
            item.put("licenseNo",  m.getLicenseNo());
            return item;
        }).collect(Collectors.toList());

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("needSelect", true);
        result.put("phone",      phone);
        result.put("merchants",  merchantList);
        return R.ok(result);
    }

    /**
     * 商家选择店铺登录（第二步，仅多商家时调用）
     * POST /api/auth/merchant-select
     * body: { "phone": "138xxx", "merchantId": 3 }
     */
    @PostMapping("/merchant-select")
    public R<Map<String, Object>> merchantSelect(@RequestBody Map<String, Object> body) {
        String phone      = (String) body.get("phone");
        Integer merchantId = ((Number) body.get("merchantId")).intValue();

        // 验证该 merchantId 确实属于这个手机号（防篡改）
        Merchant m = merchantMapper.selectOne(
                new LambdaQueryWrapper<Merchant>()
                        .eq(Merchant::getId, merchantId)
                        .eq(Merchant::getContactPhone, phone)
                        .ne(Merchant::getStatus, 2));
        if (m == null) throw new BusinessException(403, "非法请求");

        String token = jwtUtil.generateToken(m.getId(), "merchant", m.getId());
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("token",      token);
        result.put("role",       "merchant");
        result.put("userId",     m.getId());
        result.put("merchantId", m.getId());
        result.put("name",       m.getName());
        return R.ok(result);
    }
}

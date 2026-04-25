package com.traffic.auth.controller;

import com.traffic.auth.dto.LoginResponse;
import com.traffic.auth.util.JwtUtil;
import com.traffic.common.BusinessException;
import com.traffic.common.ErrorCode;
import com.traffic.common.R;
import com.traffic.merchant.entity.Merchant;
import com.traffic.merchant.mapper.MerchantMapper;
import com.traffic.salesman.entity.Salesman;
import com.traffic.salesman.mapper.SalesmanMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.*;

/**
 * 开发/测试专用登录接口
 *
 * ⚠️  仅在 spring.profiles.active=dev 时生效，生产环境不会注册此 Bean。
 *
 * 使用方式（在 application.yml 或启动参数中激活 dev profile）：
 *   spring:
 *     profiles:
 *       active: dev
 *
 * 然后调用：
 *   POST /api/auth/dev-login?openid=test_openid_merchant_001
 *   → 返回商家 JWT，可直接用于后续接口测试
 */
@Profile("dev")
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class DevAuthController {

    private final MerchantMapper merchantMapper;
    private final SalesmanMapper salesmanMapper;
    private final JwtUtil jwtUtil;

    /**
     * 开发环境：商家 JWT（跳过微信验证）
     * POST /api/auth/dev-login?openid=test_openid_merchant_001
     */
    @PostMapping("/dev-login")
    public R<LoginResponse> devLogin(@RequestParam String openid) {
        Merchant merchant = merchantMapper.findByOpenid(openid);
        if (merchant == null) throw new BusinessException(ErrorCode.MERCHANT_NOT_FOUND);
        String token = jwtUtil.generateToken(merchant.getId(), "merchant", merchant.getId());
        return R.ok(new LoginResponse(token, "merchant", merchant.getId(), merchant.getId(), merchant.getName()));
    }

    /**
     * 开发环境：业务员 JWT（跳过微信验证）
     * POST /api/auth/dev-salesman-login?openid=test_openid_salesman_001
     */
    @PostMapping("/dev-salesman-login")
    public R<LoginResponse> devSalesmanLogin(@RequestParam String openid) {
        Salesman salesman = salesmanMapper.findByOpenid(openid);
        if (salesman == null) throw new BusinessException(ErrorCode.NOT_FOUND);
        String token = jwtUtil.generateToken(salesman.getId(), "salesman", null);
        return R.ok(new LoginResponse(token, "salesman", salesman.getId(), null, salesman.getName()));
    }
}

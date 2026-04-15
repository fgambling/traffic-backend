package com.traffic.merchant.controller;

import com.traffic.common.BusinessException;
import com.traffic.common.ErrorCode;
import com.traffic.common.R;
import com.traffic.merchant.dto.DashboardResponse;
import com.traffic.merchant.service.MerchantService;
import com.traffic.security.JwtPrincipal;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

/**
 * 商家端接口
 * 所有接口需要JWT认证（role=merchant）
 */
@RestController
@RequestMapping("/api/merchant")
@RequiredArgsConstructor
public class MerchantController {

    private final MerchantService merchantService;

    /**
     * 获取今日客流看板
     * GET /api/merchant/dashboard
     */
    @GetMapping("/dashboard")
    public R<DashboardResponse> dashboard(@AuthenticationPrincipal JwtPrincipal principal) {
        if (!"merchant".equals(principal.getRole())) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }
        DashboardResponse data = merchantService.getDashboard(principal.getMerchantId());
        return R.ok(data);
    }
}

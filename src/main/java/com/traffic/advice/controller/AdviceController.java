package com.traffic.advice.controller;

import com.traffic.advice.dto.AdvicePageResult;
import com.traffic.advice.dto.FeedbackRequest;
import com.traffic.advice.service.AdviceService;
import com.traffic.common.BusinessException;
import com.traffic.common.ErrorCode;
import com.traffic.common.R;
import com.traffic.security.JwtPrincipal;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

/**
 * 商家端 AI 经营建议接口
 */
@RestController
@RequestMapping("/api/merchant/advice")
@RequiredArgsConstructor
public class AdviceController {

    private final AdviceService adviceService;

    /**
     * 分页查询建议列表
     * GET /api/merchant/advice/list?page=1&size=10&type=营销
     */
    @GetMapping("/list")
    public R<AdvicePageResult> list(
            @AuthenticationPrincipal JwtPrincipal principal,
            @RequestParam(required = false) String type,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size) {

        checkMerchant(principal);
        AdvicePageResult result = adviceService.listAdvice(principal.getMerchantId(), type, page, size);
        return R.ok(result);
    }

    /**
     * 生成一条建议（规则引擎）
     * POST /api/merchant/advice/generate
     */
    @PostMapping("/generate")
    public R<Void> generate(@AuthenticationPrincipal JwtPrincipal principal) {
        checkMerchant(principal);
        adviceService.generateAdvice(principal.getMerchantId());
        return R.ok(null);
    }

    /**
     * 提交建议反馈
     * POST /api/merchant/advice/feedback
     */
    @PostMapping("/feedback")
    public R<Void> feedback(
            @AuthenticationPrincipal JwtPrincipal principal,
            @RequestBody FeedbackRequest req) {

        checkMerchant(principal);
        adviceService.saveFeedback(req.getId(), req.getFeedback());
        return R.ok(null);
    }

    private void checkMerchant(JwtPrincipal principal) {
        if (!"merchant".equals(principal.getRole())) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }
    }
}

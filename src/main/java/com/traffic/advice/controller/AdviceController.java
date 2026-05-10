package com.traffic.advice.controller;

import com.traffic.admin.mapper.SystemConfigMapper;
import com.traffic.advice.dto.AdvicePageResult;
import com.traffic.advice.dto.FeedbackRequest;
import com.traffic.advice.mapper.AiAdviceMapper;
import com.traffic.advice.service.AdviceService;
import com.traffic.common.BusinessException;
import com.traffic.common.ErrorCode;
import com.traffic.common.R;
import com.traffic.security.JwtPrincipal;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Map;

/**
 * 商家端 AI 经营建议接口
 */
@RestController
@RequestMapping("/api/merchant/advice")
@RequiredArgsConstructor
public class AdviceController {

    private final AdviceService adviceService;
    private final SystemConfigMapper systemConfigMapper;
    private final AiAdviceMapper aiAdviceMapper;

    /**
     * 分页查询建议列表
     * GET /api/merchant/advice/list?page=1&size=10&type=营销
     */
    @GetMapping("/list")
    public R<AdvicePageResult> list(
            @AuthenticationPrincipal JwtPrincipal principal,
            @RequestParam(required = false) String type,
            @RequestParam(required = false) Integer source,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size) {

        checkMerchant(principal);
        AdvicePageResult result = adviceService.listAdvice(principal.getMerchantId(), type, source, page, size);
        return R.ok(result);
    }

    /**
     * 检查大模型是否已配置可用
     * GET /api/merchant/advice/llm-available
     */
    @GetMapping("/llm-available")
    public R<Boolean> llmAvailable(@AuthenticationPrincipal JwtPrincipal principal) {
        checkMerchant(principal);
        String apiKey = systemConfigMapper.getValue("ai.apiKey");
        return R.ok(StringUtils.hasText(apiKey));
    }

    /**
     * 检查今日 / 过去一小时是否有客流数据
     * GET /api/merchant/advice/data-check
     */
    @GetMapping("/data-check")
    public R<java.util.Map<String, Boolean>> dataCheck(@AuthenticationPrincipal JwtPrincipal principal) {
        checkMerchant(principal);
        return R.ok(adviceService.checkDataAvailability(principal.getMerchantId()));
    }

    /**
     * 查询今日剩余调用次数
     * GET /api/merchant/advice/daily-remain
     * 返回：{ limit: 5, used: 2, remain: 3 }
     *       limit=0 表示不限制，remain=-1 表示无限制
     */
    @GetMapping("/daily-remain")
    public R<Map<String, Integer>> dailyRemain(@AuthenticationPrincipal JwtPrincipal principal) {
        checkMerchant(principal);
        int limit = 0;
        try {
            String v = systemConfigMapper.getValue("ai.dailyLimit");
            if (StringUtils.hasText(v)) limit = Integer.parseInt(v.trim());
        } catch (Exception ignored) {}

        if (limit <= 0) {
            return R.ok(Map.of("limit", 0, "used", 0, "remain", -1));
        }

        LocalDateTime todayStart = LocalDate.now(ZoneId.of("Asia/Shanghai")).atStartOfDay();
        int used   = (int) aiAdviceMapper.countTodayCalls(principal.getMerchantId(), todayStart);
        int remain = Math.max(0, limit - used);
        return R.ok(Map.of("limit", limit, "used", used, "remain", remain));
    }

    /**
     * 生成建议
     * POST /api/merchant/advice/generate?mode=today|lastHour
     */
    @PostMapping("/generate")
    public R<Void> generate(
            @AuthenticationPrincipal JwtPrincipal principal,
            @RequestParam(defaultValue = "today") String mode) {
        checkMerchant(principal);
        adviceService.generateAdvice(principal.getMerchantId(), mode);
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

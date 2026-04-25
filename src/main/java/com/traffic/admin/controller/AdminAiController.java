package com.traffic.admin.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.traffic.admin.entity.SystemConfig;
import com.traffic.admin.mapper.SystemConfigMapper;
import com.traffic.advice.entity.AiAdvice;
import com.traffic.advice.mapper.AiAdviceMapper;
import com.traffic.common.BusinessException;
import com.traffic.common.R;
import lombok.RequiredArgsConstructor;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

import java.util.*;

/**
 * 后台 AI 规则 & 费用 & 配置 & 建议审核接口
 */
@RestController
@RequestMapping("/api/admin/ai")
@RequiredArgsConstructor
public class AdminAiController {

    private final AiAdviceMapper aiAdviceMapper;
    private final SystemConfigMapper systemConfigMapper;

    // ── 内置规则列表（开发阶段内存存储）────────────────────────
    private static final List<Map<String, Object>> BUILT_IN_RULES = new java.util.concurrent.CopyOnWriteArrayList<>(List.of(
        rule("R001", "营销", "今日进店 = 0",               "今日尚无客流，建议通过发券等方式引流。",             1, true),
        rule("R002", "排班", "峰值时段在 10 点前或 16 点后", "峰值出现在 {peakHour}，建议增派服务人员。",         2, true),
        rule("R003", "营销", "客均停留 < 60 秒",             "客均停留仅 {avgStay} 秒，转化率偏低，建议优化陈列。", 3, true),
        rule("R004", "备货", "今日进店 ≥ 50",               "今日进店已达 {totalEnter} 人，建议核查热销库存。",    4, true),
        rule("R005", "营销", "兜底规则",                     "今日共 {totalEnter} 人进店，可推出会员积分活动。",    5, true)
    ));

    private static Map<String, Object> rule(String id, String type, String cond, String tmpl, int pri, boolean enabled) {
        Map<String, Object> m = new HashMap<>();
        m.put("ruleId", id); m.put("adviceType", type); m.put("condition", cond);
        m.put("template", tmpl); m.put("priority", pri); m.put("enabled", enabled);
        return m;
    }

    // ── 规则管理 ──────────────────────────────────────────────

    @GetMapping("/rules")
    public R<List<Map<String, Object>>> getRules() {
        return R.ok(BUILT_IN_RULES);
    }

    @PostMapping("/rules")
    public R<Void> saveRule(@RequestBody Map<String, Object> body) {
        String ruleId = (String) body.get("ruleId");
        BUILT_IN_RULES.removeIf(r -> ruleId.equals(r.get("ruleId")));
        BUILT_IN_RULES.add(body);
        BUILT_IN_RULES.sort((a, b) -> {
            int pa = a.get("priority") instanceof Number ? ((Number) a.get("priority")).intValue() : 99;
            int pb = b.get("priority") instanceof Number ? ((Number) b.get("priority")).intValue() : 99;
            return pa - pb;
        });
        return R.ok(null);
    }

    @DeleteMapping("/rules/{ruleId}")
    public R<Void> deleteRule(@PathVariable String ruleId) {
        BUILT_IN_RULES.removeIf(r -> ruleId.equals(r.get("ruleId")));
        return R.ok(null);
    }

    // ── AI 模型配置 ───────────────────────────────────────────

    private static final List<String> CONFIG_KEYS = List.of(
            "ai.model", "ai.apiKey", "ai.promptTemplate", "ai.dailyLimit"
    );

    /**
     * GET /api/admin/ai/config — 读取AI模型配置
     */
    @GetMapping("/config")
    public R<Map<String, String>> getConfig() {
        Map<String, String> result = new LinkedHashMap<>();
        for (String key : CONFIG_KEYS) {
            String val = systemConfigMapper.getValue(key);
            // 对 apiKey 做脱敏
            if ("ai.apiKey".equals(key) && val != null && val.length() > 8) {
                val = val.substring(0, 4) + "****" + val.substring(val.length() - 4);
            }
            result.put(key, val != null ? val : "");
        }
        return R.ok(result);
    }

    /**
     * POST /api/admin/ai/config — 保存AI模型配置
     * body: { "ai.model": "gpt-4o", "ai.apiKey": "sk-...", ... }
     */
    @PostMapping("/config")
    public R<Void> saveConfig(@RequestBody Map<String, String> body) {
        for (Map.Entry<String, String> entry : body.entrySet()) {
            if (CONFIG_KEYS.contains(entry.getKey())) {
                // 如果 apiKey 是脱敏占位符则跳过（不覆盖真实值）
                if ("ai.apiKey".equals(entry.getKey()) && entry.getValue().contains("****")) continue;
                systemConfigMapper.setValue(entry.getKey(), entry.getValue());
            }
        }
        AdminSystemController.writeLog("admin", "ai", "更新AI模型配置", "admin");
        return R.ok(null);
    }

    // ── 建议审核 ──────────────────────────────────────────────

    /**
     * GET /api/admin/ai/advice?merchantId=&reviewStatus=&source=&page=1&size=20
     * 管理员审核 AI/规则建议列表
     */
    @GetMapping("/advice")
    public R<Map<String, Object>> adviceList(
            @RequestParam(required = false) Integer merchantId,
            @RequestParam(required = false) Integer reviewStatus,
            @RequestParam(required = false) Integer source,
            @RequestParam(defaultValue = "1")  int page,
            @RequestParam(defaultValue = "20") int size) {

        Page<AiAdvice> pageObj = new Page<>(page, size);
        LambdaQueryWrapper<AiAdvice> wrapper = new LambdaQueryWrapper<AiAdvice>()
                .eq(merchantId != null,    AiAdvice::getMerchantId,   merchantId)
                .eq(reviewStatus != null,  AiAdvice::getReviewStatus, reviewStatus)
                .eq(source != null,        AiAdvice::getSource,       source)
                .orderByDesc(AiAdvice::getCreatedAt);
        aiAdviceMapper.selectPage(pageObj, wrapper);

        return R.ok(Map.of("list", pageObj.getRecords(), "total", pageObj.getTotal()));
    }

    /**
     * PUT /api/admin/ai/advice/{id}/review — 标记审核结果
     * body: { "reviewStatus": 1|2, "adminNote": "..." }
     */
    @PutMapping("/advice/{id}/review")
    public R<Void> reviewAdvice(@PathVariable Long id,
                                @RequestBody Map<String, Object> body) {
        AiAdvice advice = aiAdviceMapper.selectById(id);
        if (advice == null) throw new BusinessException(404, "建议不存在");

        Integer status = body.get("reviewStatus") instanceof Number
                ? ((Number) body.get("reviewStatus")).intValue() : null;
        String note = (String) body.get("adminNote");

        LambdaUpdateWrapper<AiAdvice> wrapper = new LambdaUpdateWrapper<AiAdvice>()
                .eq(AiAdvice::getId, id);
        if (status != null) wrapper.set(AiAdvice::getReviewStatus, status);
        if (StringUtils.hasText(note)) wrapper.set(AiAdvice::getAdminNote, note);
        aiAdviceMapper.update(null, wrapper);

        String action = status != null && status == 1 ? "采纳" : "标记待优化";
        AdminSystemController.writeLog("admin", "ai", "建议审核[" + action + "] id=" + id, "admin");
        return R.ok(null);
    }

    // ── 费用 & 统计 ──────────────────────────────────────────

    /**
     * GET /api/admin/ai/cost?page=1&size=15 — 建议生成记录汇总
     */
    @GetMapping("/cost")
    public R<Map<String, Object>> cost(
            @RequestParam(defaultValue = "1")  int page,
            @RequestParam(defaultValue = "15") int size) {

        Page<AiAdvice> pageObj = new Page<>(page, size);
        LambdaQueryWrapper<AiAdvice> wrapper = new LambdaQueryWrapper<AiAdvice>()
                .orderByDesc(AiAdvice::getCreatedAt);
        aiAdviceMapper.selectPage(pageObj, wrapper);

        long total     = pageObj.getTotal();
        long ruleCount = pageObj.getRecords().stream()
                .filter(a -> a.getSource() == null || a.getSource() == 1).count();
        long aiCount   = total - ruleCount;
        long useful    = pageObj.getRecords().stream()
                .filter(a -> a.getFeedback() != null && a.getFeedback() == 1).count();
        double usefulRate = total > 0 ? Math.round(useful * 1000.0 / total) / 10.0 : 0.0;

        Map<String, Object> summary = Map.of(
            "totalGenerated", total,
            "ruleCount",      ruleCount,
            "aiCount",        aiCount,
            "usefulRate",     usefulRate
        );

        return R.ok(Map.of("list", pageObj.getRecords(), "total", total, "summary", summary));
    }

    /**
     * GET /api/admin/ai/cost/by-merchant — 按商家聚合建议成本明细
     */
    @GetMapping("/cost/by-merchant")
    public R<List<Map<String, Object>>> costByMerchant() {
        List<Map<String, Object>> rows = aiAdviceMapper.costByMerchant();
        // 补充 usefulRate 字段
        for (Map<String, Object> row : rows) {
            long totalCount  = toLong(row.get("totalCount"));
            long usefulCount = toLong(row.get("usefulCount"));
            double rate = totalCount > 0 ? Math.round(usefulCount * 1000.0 / totalCount) / 10.0 : 0.0;
            row.put("usefulRate", rate);
        }
        return R.ok(rows);
    }

    private long toLong(Object v) {
        if (v == null) return 0L;
        if (v instanceof Number) return ((Number) v).longValue();
        try { return Long.parseLong(v.toString()); } catch (Exception e) { return 0L; }
    }
}

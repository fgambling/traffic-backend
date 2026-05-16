package com.traffic.admin.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.traffic.admin.entity.SystemConfig;
import com.traffic.admin.mapper.SystemConfigMapper;
import com.traffic.advice.entity.AiAdvice;
import com.traffic.advice.mapper.AiAdviceMapper;
import com.traffic.advice.service.LlmService;
import com.traffic.common.BusinessException;
import com.traffic.merchant.entity.Merchant;
import com.traffic.merchant.mapper.MerchantMapper;
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
    private final MerchantMapper merchantMapper;
    private final LlmService llmService;

    // ── 内置规则列表（与 BuiltinRule 枚举保持一致，共 10 条）───
    private static final List<Map<String, Object>> BUILT_IN_RULES = new java.util.concurrent.CopyOnWriteArrayList<>(List.of(
        rule("R001", "营销", "过去1小时女性客流占比 > 70%",
             "当前时段女性顾客比例较高，建议推送甜品/低卡饮品优惠。", 1, true),
        rule("R002", "营销", "过去1小时男性客流占比 > 70%",
             "男性顾客占比较高，可推荐啤酒、下酒菜或运动直播。", 2, true),
        rule("R003", "营销", "成年人（18-60岁）占比 > 80%",
             "主力消费人群为成年人，可主推套餐或会员充值活动。", 3, true),
        rule("R004", "营销", "未成年人（<18岁）占比 > 30%",
             "学生群体增多，可推出学生证折扣或小份餐品。", 4, true),
        rule("R005", "营销", "戴眼镜顾客比例 > 60%",
             "戴眼镜顾客多，可能为上班族或学生，可提供免费WIFI、充电插座。", 5, true),
        rule("R006", "营销", "背包/手提包顾客比例 > 50%",
             "携带包袋顾客多，建议提供储物篮或挂钩。", 6, true),
        rule("R007", "排班", "平均停留时长较昨日同时段下降 > 30%",
             "顾客停留时间明显缩短，请检查服务速度或产品吸引力。", 7, true),
        rule("R008", "备货", "当前时段进店人数超过历史同期均值的 1.5 倍",
             "客流暴增！建议增加收银人手，提前备餐。", 8, true),
        rule("R009", "营销", "连续3天同一时段客流下降超过 20%",
             "客流持续下降，建议检查周边竞争情况或推出限时优惠。", 9, true),
        rule("R010", "营销", "手持物品顾客比例 > 40%",
             "很多顾客手持物品，可增加购物篮或打包台。", 10, true)
    ));

    private static Map<String, Object> rule(String id, String type, String cond, String tmpl, int pri, boolean enabled) {
        Map<String, Object> m = new HashMap<>();
        m.put("ruleId", id); m.put("adviceType", type); m.put("condition", cond);
        m.put("template", tmpl); m.put("priority", pri); m.put("enabled", enabled);
        return m;
    }

    // ── 规则管理 ──────────────────────────────────────────────

    private static final String GLOBAL_RULE_CONFIG_KEY = "global_rule_config";

    /** 从 system_config 读取全局规则启用状态，覆盖内存列表 */
    private void syncEnabledFromDb() {
        String json = systemConfigMapper.getValue(GLOBAL_RULE_CONFIG_KEY);
        if (json == null || json.isBlank()) return;
        try {
            com.fasterxml.jackson.databind.ObjectMapper om = new com.fasterxml.jackson.databind.ObjectMapper();
            Map<String, Boolean> map = om.readValue(json, new com.fasterxml.jackson.core.type.TypeReference<>() {});
            BUILT_IN_RULES.forEach(r -> {
                Boolean v = map.get(r.get("ruleId"));
                if (v != null) r.put("enabled", v);
            });
        } catch (Exception ignored) {}
    }

    /** 将当前内存列表的启用状态持久化到 system_config */
    private void persistEnabledToDb() {
        try {
            Map<String, Boolean> map = new LinkedHashMap<>();
            BUILT_IN_RULES.forEach(r -> map.put((String) r.get("ruleId"),
                    Boolean.TRUE.equals(r.get("enabled"))));
            com.fasterxml.jackson.databind.ObjectMapper om = new com.fasterxml.jackson.databind.ObjectMapper();
            systemConfigMapper.setValue(GLOBAL_RULE_CONFIG_KEY, om.writeValueAsString(map));
        } catch (Exception ignored) {}
    }

    @GetMapping("/rules")
    public R<List<Map<String, Object>>> getRules() {
        syncEnabledFromDb();
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
        persistEnabledToDb();
        return R.ok(null);
    }

    @DeleteMapping("/rules/{ruleId}")
    public R<Void> deleteRule(@PathVariable String ruleId) {
        BUILT_IN_RULES.removeIf(r -> ruleId.equals(r.get("ruleId")));
        persistEnabledToDb();
        return R.ok(null);
    }

    // ── AI 模型配置 ───────────────────────────────────────────

    private static final List<String> CONFIG_KEYS = List.of(
            "ai.provider", "ai.model", "ai.apiKey", "ai.promptTemplate", "ai.dailyLimit"
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
        AdminSystemController.writeLog(AdminSystemController.currentAdminName(), "ai", "更新AI模型配置");
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

        // 批量查商家名称
        List<AiAdvice> records = pageObj.getRecords();
        Set<Integer> mids = new HashSet<>();
        records.forEach(a -> { if (a.getMerchantId() != null) mids.add(a.getMerchantId()); });
        Map<Integer, String> nameMap = new HashMap<>();
        if (!mids.isEmpty()) {
            merchantMapper.selectBatchIds(mids)
                    .forEach(m -> nameMap.put(m.getId(), m.getName()));
        }

        List<Map<String, Object>> enriched = records.stream().map(a -> {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("id",           a.getId());
            row.put("merchantId",   a.getMerchantId());
            row.put("merchantName", nameMap.getOrDefault(a.getMerchantId(), "--"));
            row.put("adviceType",   a.getAdviceType());
            row.put("content",      a.getContent());
            row.put("feedback",     a.getFeedback());
            row.put("reviewStatus", a.getReviewStatus());
            row.put("adminNote",    a.getAdminNote());
            row.put("createdAt",    a.getCreatedAt());
            return row;
        }).toList();

        return R.ok(Map.of("list", enriched, "total", pageObj.getTotal()));
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
        AdminSystemController.writeLog(AdminSystemController.currentAdminName(), "ai", "建议审核[" + action + "] id=" + id);
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

    // ── 模型单价表（元/千token） [input, output] ─────────────────
    // 单位：元/千 token（输入价, 输出价），参考各官网公开定价
    private static final Map<String, double[]> MODEL_PRICE = Map.ofEntries(
        // OpenAI（按美元 × 7.2 汇率估算）
        Map.entry("gpt-4o",             new double[]{0.036,  0.108}),
        Map.entry("gpt-4-turbo",        new double[]{0.072,  0.216}),
        Map.entry("gpt-3.5-turbo",      new double[]{0.004,  0.012}),
        // 文心一言（百度千帆官网定价）
        Map.entry("ernie-4.0-8k",       new double[]{0.12,   0.12 }),
        Map.entry("ernie-3.5-8k",       new double[]{0.012,  0.012}),
        Map.entry("ernie-speed-128k",   new double[]{0.0,    0.0  }),
        // 通义千问（阿里云官网定价）
        Map.entry("qwen-max",           new double[]{0.04,   0.12 }),
        Map.entry("qwen-plus",          new double[]{0.008,  0.024}),
        Map.entry("qwen-turbo",         new double[]{0.003,  0.009}),
        // DeepSeek（官网定价）
        Map.entry("deepseek-chat",      new double[]{0.001,  0.002}),
        Map.entry("deepseek-reasoner",  new double[]{0.004,  0.016})
    );
    private static final int    INPUT_TOKENS_PER_CALL = 600;   // prompt 固定估算
    private static final double CHARS_PER_TOKEN       = 1.5;   // 中文约1.5字/token

    /**
     * GET /api/admin/ai/cost/by-merchant — 按商家聚合，附精确历史成本
     * 成本按每条记录的 model_used 字段查价格，旧记录（model_used 为空）回退当前配置模型
     */
    @GetMapping("/cost/by-merchant")
    public R<List<Map<String, Object>>> costByMerchant() {
        String currentModel = systemConfigMapper.getValue("ai.model");

        // 按 merchant + model_used 分组，方便精确计费
        List<Map<String, Object>> rawRows = aiAdviceMapper.costByMerchantModel();

        // 按 merchantId 聚合
        Map<Long, List<Map<String, Object>>> byMerchant = new LinkedHashMap<>();
        for (Map<String, Object> row : rawRows) {
            Long mid = toLong(row.get("merchantId"));
            byMerchant.computeIfAbsent(mid, k -> new ArrayList<>()).add(row);
        }

        List<Map<String, Object>> result = new ArrayList<>();
        for (List<Map<String, Object>> group : byMerchant.values()) {
            Map<String, Object> first = group.get(0);
            long totalAiCount = 0, todayAiCount = 0, totalContentLen = 0, todayContentLen = 0;
            Object lastGenAt = null;
            double estCost = 0, estTodayCost = 0;

            for (Map<String, Object> row : group) {
                long count      = toLong(row.get("totalAiCount"));
                long today      = toLong(row.get("todayAiCount"));
                long cLen       = toLong(row.get("totalContentLen"));
                long tLen       = toLong(row.get("todayContentLen"));
                // 按实际记录的模型查价；空值回退当前配置模型
                String model = (String) row.get("modelUsed");
                if (!StringUtils.hasText(model)) model = currentModel;
                double[] price  = MODEL_PRICE.getOrDefault(model, new double[]{0.01, 0.01});

                totalAiCount    += count;
                todayAiCount    += today;
                totalContentLen += cLen;
                todayContentLen += tLen;
                estCost         += calcCost(count, cLen,  price[0], price[1]);
                estTodayCost    += calcCost(today, tLen,  price[0], price[1]);
                if (row.get("lastGenAt") != null) lastGenAt = row.get("lastGenAt");
            }

            Map<String, Object> out = new LinkedHashMap<>();
            out.put("merchantId",       first.get("merchantId"));
            out.put("merchantName",     first.get("merchantName"));
            out.put("totalAiCount",     totalAiCount);
            out.put("todayAiCount",     todayAiCount);
            out.put("totalContentLen",  totalContentLen);
            out.put("todayContentLen",  todayContentLen);
            out.put("lastGenAt",        lastGenAt);
            out.put("estCostYuan",      Math.round(estCost      * 100.0) / 100.0);
            out.put("estTodayCostYuan", Math.round(estTodayCost * 100.0) / 100.0);
            result.add(out);
        }
        result.sort((a, b) -> Long.compare(toLong(b.get("totalAiCount")), toLong(a.get("totalAiCount"))));
        return R.ok(result);
    }

    /**
     * POST /api/admin/ai/config/test — 测试 LLM 连接
     * body: { provider, model, apiKey }
     */
    @PostMapping("/config/test")
    public R<Map<String, Object>> testConnection(@RequestBody Map<String, String> body) {
        Map<String, Object> result = llmService.testConnection(
                body.get("provider"), body.get("model"), body.get("apiKey"));
        return R.ok(result);
    }

    private double calcCost(long count, long contentLen, double inPrice, double outPrice) {
        if (count == 0) return 0.0;
        double outTokens = contentLen / CHARS_PER_TOKEN;
        return (count * INPUT_TOKENS_PER_CALL * inPrice + outTokens * outPrice) / 1000.0;
    }

    private long toLong(Object v) {
        if (v == null) return 0L;
        if (v instanceof Number) return ((Number) v).longValue();
        try { return Long.parseLong(v.toString()); } catch (Exception e) { return 0L; }
    }
}

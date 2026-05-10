package com.traffic.advice.scheduler;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.traffic.advice.entity.AiAdvice;
import com.traffic.advice.mapper.AiAdviceMapper;
import com.traffic.advice.service.LlmService;
import com.traffic.admin.mapper.SystemConfigMapper;
import com.traffic.device.entity.TrafficFact;
import com.traffic.device.mapper.TrafficFactMapper;
import com.traffic.device.service.impl.DeviceServiceImpl;
import com.traffic.merchant.entity.Merchant;
import com.traffic.merchant.entity.MerchantBusinessInfo;
import com.traffic.merchant.mapper.MerchantBusinessInfoMapper;
import com.traffic.merchant.mapper.MerchantMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.*;

/**
 * 凌晨定时任务：为高级版商家调用 LLM 生成经营建议
 * 分析昨日完整数据 + 近7天趋势，每天凌晨1点执行
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class LlmAdviceScheduler {

    private static final String DEFAULT_PROMPT_TEMPLATE = com.traffic.advice.util.LlmConstants.DEFAULT_PROMPT_TEMPLATE;

    private final MerchantMapper            merchantMapper;
    private final TrafficFactMapper         trafficFactMapper;
    private final MerchantBusinessInfoMapper businessInfoMapper;
    private final AiAdviceMapper            aiAdviceMapper;
    private final LlmService               llmService;
    private final SystemConfigMapper        systemConfigMapper;
    private final ObjectMapper              objectMapper;

    @Scheduled(cron = "0 0 1 * * ?", zone = "Asia/Shanghai")   // 每天凌晨 1:00（北京时间）
    public void runNightlyLlmAdvice() {
        log.info("凌晨 LLM 建议任务开始: {}", LocalDateTime.now());

        int dailyLimit = getDailyLimit();
        String modelUsed;
        try { modelUsed = systemConfigMapper.getValue("ai.model"); } catch (Exception e) { modelUsed = ""; }
        final String mu = org.springframework.util.StringUtils.hasText(modelUsed) ? modelUsed : "";

        List<Merchant> merchants = merchantMapper.selectList(
                new LambdaQueryWrapper<Merchant>()
                        .eq(Merchant::getStatus, 1)
                        .eq(Merchant::getPackageType, 3));

        log.info("高级版商家数: {}", merchants.size());

        for (Merchant merchant : merchants) {
            try {
                processOneMerchant(merchant, dailyLimit, mu);
            } catch (Exception e) {
                log.error("LLM 建议生成异常 merchantId={}: {}", merchant.getId(), e.getMessage(), e);
            }
        }

        log.info("凌晨 LLM 建议任务完成: {}", LocalDateTime.now());
    }

    private void processOneMerchant(Merchant merchant, int dailyLimit, String modelUsed) {
        Integer merchantId = merchant.getId();

        // 检查今日 LLM 调用次数是否达上限（按 call_id 去重）
        if (dailyLimit > 0) {
            LocalDateTime todayStart = LocalDate.now(ZoneId.of("Asia/Shanghai")).atStartOfDay();
            long todayCalls = aiAdviceMapper.countTodayCalls(merchantId, todayStart);
            if (todayCalls >= dailyLimit) {
                log.info("merchantId={} 今日调用次数已达上限 {}，跳过", merchantId, dailyLimit);
                return;
            }
        }

        ZoneId tz = ZoneId.of("Asia/Shanghai");
        LocalDate today     = LocalDate.now(tz);
        LocalDate yesterday = today.minusDays(1);

        // ── 昨日数据 ──────────────────────────────────────────
        LocalDateTime yStart = yesterday.atStartOfDay();
        LocalDateTime yEnd   = today.atStartOfDay();
        List<TrafficFact> yFacts = trafficFactMapper.findTodayByMerchant(merchantId, yStart, yEnd);
        Map<String, Object> ySum = DeviceServiceImpl.aggregateTodaySummary(yFacts);

        int totalEnter  = toInt(ySum.get("totalEnter"));
        int avgStay     = toInt(ySum.get("avgStaySeconds"));
        int male        = toInt(ySum.get("genderMale"));
        int female      = toInt(ySum.get("genderFemale"));
        double femaleRatio = (male + female) > 0 ? female * 100.0 / (male + female) : 0;

        // 昨日高峰时段
        Map<Integer, Integer> hourMap = new TreeMap<>();
        for (TrafficFact f : yFacts) {
            if (f.getTimeBucket() != null)
                hourMap.merge(f.getTimeBucket().getHour(), f.getEnterCount(), Integer::sum);
        }
        int peakHour = -1, peakCount = 0;
        for (var e : hourMap.entrySet()) {
            if (e.getValue() > peakCount) { peakCount = e.getValue(); peakHour = e.getKey(); }
        }

        // ── 近7天趋势摘要 ──────────────────────────────────────
        LocalDateTime weekStart = today.minusDays(7).atStartOfDay();
        List<TrafficFact> weekFacts = trafficFactMapper.findTodayByMerchant(merchantId, weekStart, yStart);
        String weekSummary = buildWeekSummary(weekFacts, yesterday);

        // ── 构建 Prompt 并调用 LLM ────────────────────────────
        MerchantBusinessInfo info = businessInfoMapper.findByMerchant(merchantId);
        String prompt = buildPrompt(merchant.getName(), totalEnter, avgStay, femaleRatio,
                                    peakHour, peakCount, weekSummary, info);

        log.info("凌晨 LLM 调用 merchantId={}", merchantId);
        String callId = java.util.UUID.randomUUID().toString();
        String reply = llmService.call(prompt);

        if (!StringUtils.hasText(reply)) {
            log.warn("LLM 返回为空，跳过 merchantId={}", merchantId);
            return;
        }

        // ── 解析 JSON 并存库 ──────────────────────────────────
        List<Map<String, String>> items = parseLlmJson(reply);
        String snapshot = buildSnapshot(totalEnter, avgStay, male, female, peakHour, peakCount);

        if (items.isEmpty()) {
            aiAdviceMapper.insert(new AiAdvice()
                    .setMerchantId(merchantId).setSource(2).setAdviceType("营销")
                    .setContent(reply).setDataSnapshot(snapshot).setFeedback(0)
                    .setCallId(callId).setModelUsed(modelUsed));
        } else {
            for (Map<String, String> item : items) {
                aiAdviceMapper.insert(new AiAdvice()
                        .setMerchantId(merchantId).setSource(2)
                        .setAdviceType(item.getOrDefault("type", "营销"))
                        .setContent(item.getOrDefault("content", ""))
                        .setConfidence(item.getOrDefault("confidence", "中"))
                        .setDataSnapshot(snapshot).setFeedback(0)
                        .setCallId(callId).setModelUsed(modelUsed));
            }
        }
        log.info("凌晨建议已写入 {} 条，merchantId={}", items.isEmpty() ? 1 : items.size(), merchantId);
    }

    /** 生成近7天每日进店人数摘要字符串 */
    private String buildWeekSummary(List<TrafficFact> facts, LocalDate yesterday) {
        // 按日期聚合进店人数
        Map<LocalDate, Integer> dailyMap = new TreeMap<>();
        for (TrafficFact f : facts) {
            if (f.getTimeBucket() == null) continue;
            LocalDate d = f.getTimeBucket().toLocalDate();
            dailyMap.merge(d, f.getEnterCount(), Integer::sum);
        }
        if (dailyMap.isEmpty()) return null;

        StringBuilder sb = new StringBuilder("近7天每日进店（");
        int total = 0;
        for (var e : dailyMap.entrySet()) {
            sb.append(e.getKey().getMonthValue()).append("/").append(e.getKey().getDayOfMonth())
              .append(":").append(e.getValue()).append("人,");
            total += e.getValue();
        }
        if (sb.charAt(sb.length() - 1) == ',') sb.deleteCharAt(sb.length() - 1);
        sb.append("），7日合计 ").append(total).append(" 人，日均 ")
          .append(total / Math.max(dailyMap.size(), 1)).append(" 人");
        return sb.toString();
    }

    private String buildPrompt(String name, int enter, int avgStay, double femaleRatio,
                               int peakHour, int peakCount, String weekSummary,
                               MerchantBusinessInfo info) {
        String dataBlock = buildDataBlock(name, enter, avgStay, femaleRatio, peakHour, peakCount, weekSummary, info);
        return getPromptTemplate().replace("{{data}}", dataBlock);
    }

    private String buildDataBlock(String name, int enter, int avgStay, double femaleRatio,
                                  int peakHour, int peakCount, String weekSummary,
                                  MerchantBusinessInfo info) {
        StringBuilder sb = new StringBuilder();
        sb.append("门店：").append(name).append("\n");
        sb.append("昨日数据：进店 ").append(enter).append(" 人");
        if (peakHour >= 0)
            sb.append("，高峰时段 ").append(peakHour).append(":00（").append(peakCount).append(" 人）");
        sb.append("，平均停留 ").append(avgStay).append(" 秒");
        sb.append("，女性占比 ").append(String.format("%.0f", femaleRatio)).append("%");

        if (StringUtils.hasText(weekSummary))
            sb.append("\n近7日趋势：").append(weekSummary);

        if (info != null) {
            if (StringUtils.hasText(info.getBusinessType()))
                sb.append("\n店铺业态：").append(info.getBusinessType());
            if (StringUtils.hasText(info.getMenu()))
                sb.append("\n门店菜单/商品：").append(info.getMenu());
            if (StringUtils.hasText(info.getPromotions()))
                sb.append("\n当期促销活动：").append(info.getPromotions());
            if (StringUtils.hasText(info.getTargetAudience()))
                sb.append("\n目标客群：").append(info.getTargetAudience());
            if (StringUtils.hasText(info.getBusinessHours()))
                sb.append("\n营业时段：").append(info.getBusinessHours());
        }
        return sb.toString();
    }

    private String getPromptTemplate() {
        try {
            String v = systemConfigMapper.getValue("ai.promptTemplate");
            return StringUtils.hasText(v) ? v : DEFAULT_PROMPT_TEMPLATE;
        } catch (Exception e) {
            return DEFAULT_PROMPT_TEMPLATE;
        }
    }

    private List<Map<String, String>> parseLlmJson(String reply) {
        String text = reply.trim();
        if (text.startsWith("```"))
            text = text.replaceAll("(?s)```[a-z]*\\n?", "").replaceAll("```", "").trim();
        int s = text.indexOf('['), e = text.lastIndexOf(']');
        if (s == -1 || e <= s) return List.of();
        try {
            return objectMapper.readValue(text.substring(s, e + 1),
                    new com.fasterxml.jackson.core.type.TypeReference<List<Map<String, String>>>() {});
        } catch (Exception ex) {
            log.warn("LLM JSON 解析失败: {}", ex.getMessage());
            return List.of();
        }
    }

    private String buildSnapshot(int enter, int avgStay, int male, int female,
                                  int peakHour, int peakCount) {
        try {
            Map<String, Object> snap = new LinkedHashMap<>();
            snap.put("totalEnter", enter);
            snap.put("avgStaySeconds", avgStay);
            snap.put("genderMale", male);
            snap.put("genderFemale", female);
            if (peakHour >= 0) { snap.put("peakHour", peakHour + ":00"); snap.put("peakCount", peakCount); }
            return objectMapper.writeValueAsString(snap);
        } catch (Exception e) { return "{}"; }
    }

    private int getDailyLimit() {
        try {
            String v = systemConfigMapper.getValue("ai.dailyLimit");
            return v != null ? Integer.parseInt(v.trim()) : 0;
        } catch (Exception e) { return 0; }
    }

    private int toInt(Object val) {
        if (val == null) return 0;
        if (val instanceof Number) return ((Number) val).intValue();
        try { return Integer.parseInt(val.toString()); } catch (Exception e) { return 0; }
    }
}

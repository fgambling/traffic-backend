package com.traffic.rule;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.traffic.advice.mapper.AiAdviceMapper;
import com.traffic.device.mapper.TrafficFactMapper;
import com.traffic.merchant.mapper.MerchantConfigMapper;
import com.traffic.rule.dto.HourlyStatsDTO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * 规则引擎核心
 *
 * 职责：
 *  1. 为指定商家构建 RuleContext
 *  2. 对内置规则 + 自定义规则逐条求值
 *  3. 去重（同规则1小时内不重复触发）
 *  4. 返回最终触发结果列表（由调度器负责落库和推送Redis）
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RuleEngine {

    private final TrafficFactMapper trafficFactMapper;
    private final AiAdviceMapper aiAdviceMapper;
    private final MerchantConfigMapper merchantConfigMapper;
    private final CustomRuleParser customRuleParser;
    private final ObjectMapper objectMapper;

    /** 统计窗口：过去1小时 */
    private static final int STATS_WINDOW_HOURS = 1;
    /** 历史均值参考：过去4周 */
    private static final int HISTORY_WEEKS = 4;
    /** 去重窗口：同规则1小时内不重复 */
    private static final int DEDUP_HOURS = 1;

    /**
     * 对单个商家执行全量规则评估
     *
     * @param merchantId 商家ID
     * @return 触发的规则结果列表（已去重）
     */
    public List<RuleResult> evaluate(Integer merchantId) {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime fromTime = now.minusHours(STATS_WINDOW_HOURS);

        // 1. 构建 RuleContext
        RuleContext ctx = buildContext(merchantId, fromTime, now);
        if (ctx.getTotalEnterCount() == 0) {
            log.debug("商家{}过去1小时无客流数据，跳过规则评估", merchantId);
            return Collections.emptyList();
        }

        // 2. 获取该商家的内置规则启用配置（默认全部启用）
        Set<String> enabledRules = loadEnabledRules(merchantId);

        List<RuleResult> triggered = new ArrayList<>();
        LocalDateTime dedupSince = now.minusHours(DEDUP_HOURS);

        // 3. 评估内置规则
        for (BuiltinRule rule : BuiltinRule.values()) {
            if (!enabledRules.contains(rule.getRuleId())) continue;
            if (!rule.evaluate(ctx)) continue;
            if (isDuplicate(merchantId, rule.getRuleId(), dedupSince)) {
                log.debug("规则{}在{}已触发，跳过去重", rule.getRuleId(), merchantId);
                continue;
            }
            triggered.add(rule.buildResult(ctx));
            log.info("规则触发: merchantId={}, ruleId={}", merchantId, rule.getRuleId());
        }

        // 4. 评估自定义规则
        var customConfig = merchantConfigMapper.findByKey(merchantId, "custom_rules");
        if (customConfig != null) {
            List<RuleResult> customResults =
                    customRuleParser.evaluate(customConfig.getConfigValue(), ctx);
            for (RuleResult r : customResults) {
                if (isDuplicate(merchantId, r.getRuleId(), dedupSince)) continue;
                triggered.add(r);
                log.info("自定义规则触发: merchantId={}, ruleId={}", merchantId, r.getRuleId());
            }
        }

        return triggered;
    }

    /** 构建规则上下文（聚合过去1小时数据 + 历史对比数据） */
    private RuleContext buildContext(Integer merchantId,
                                    LocalDateTime fromTime, LocalDateTime toTime) {
        // 当前时段聚合
        HourlyStatsDTO stats = trafficFactMapper.aggregateHourlyStats(merchantId, fromTime, toTime);
        if (stats == null) stats = new HourlyStatsDTO();

        int total = stats.getTotalEnterCount();
        int hour  = toTime.getHour();

        // 历史均值（过去4周同星期同时段）
        LocalDateTime historyStart = toTime.minusWeeks(HISTORY_WEEKS);
        Double historyAvg = trafficFactMapper.queryHistoryAvgCount(
                merchantId, hour, toTime, historyStart);

        // 昨日同时段停留时长
        LocalDateTime yesterdayFrom = fromTime.minusDays(1);
        LocalDateTime yesterdayTo   = toTime.minusDays(1);
        Double yesterdayAvgStay = trafficFactMapper.queryAvgStaySeconds(
                merchantId, yesterdayFrom, yesterdayTo);

        // 连续3天下降检测
        boolean consecutiveDecline = detectConsecutiveDecline(merchantId, hour, toTime);

        // 计算各比例（total=0时各比例为0）
        double femaleRatio   = ratio(stats.getTotalFemale(), total);
        double maleRatio     = ratio(stats.getTotalMale(), total);
        double under18Ratio  = ratio(stats.getTotalUnder18(), total);
        double age1860Ratio  = ratio(stats.getTotal1860(), total);
        double over60Ratio   = ratio(stats.getTotalOver60(), total);
        double glassesRatio  = ratio(stats.getTotalGlasses(), total);
        double bagRatio      = ratio(stats.getTotalBag(), total);
        double holdItemRatio = ratio(stats.getTotalHoldItem(), total);
        double avgStay       = stats.getStayCount() > 0
                ? (double) stats.getTotalStaySeconds() / stats.getStayCount() : 0;

        return RuleContext.builder()
                .merchantId(merchantId)
                .totalEnterCount(total)
                .femaleRatio(femaleRatio)
                .maleRatio(maleRatio)
                .ageUnder18Ratio(under18Ratio)
                .age1860Ratio(age1860Ratio)
                .ageOver60Ratio(over60Ratio)
                .glassesRatio(glassesRatio)
                .bagRatio(bagRatio)
                .holdItemRatio(holdItemRatio)
                .avgStaySeconds(avgStay)
                .avgStayYesterdaySameHour(yesterdayAvgStay != null ? yesterdayAvgStay : 0)
                .historyAvgCount(historyAvg != null ? historyAvg : 0)
                .consecutiveDecline(consecutiveDecline)
                .build();
    }

    /**
     * 连续3天同时段下降 >20% 检测
     * 取最近3天（不含今天）同小时数据，判断是否连续递减且降幅>20%
     */
    private boolean detectConsecutiveDecline(Integer merchantId, int hour,
                                             LocalDateTime now) {
        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("yyyy-MM-dd");
        String day1 = now.minusDays(1).format(fmt); // 昨天
        String day3 = now.minusDays(3).format(fmt); // 3天前

        List<Map<String, Object>> rows =
                trafficFactMapper.queryRecentDailyCount(merchantId, hour, day1, day3);
        if (rows.size() < 3) return false;

        // rows 按日期升序，index 0=最早，index 2=昨天
        double d0 = toDouble(rows.get(0).get("count"));
        double d1 = toDouble(rows.get(1).get("count"));
        double d2 = toDouble(rows.get(2).get("count"));

        // 每天比前一天下降 >20%
        boolean decline01 = d0 > 0 && d1 < d0 * 0.80;
        boolean decline12 = d1 > 0 && d2 < d1 * 0.80;
        return decline01 && decline12;
    }

    /**
     * 加载该商家内置规则启用状态
     * config_key='builtin_rule_config'，格式：{"R001":true,"R002":false,...}
     * 若无配置则默认全部启用
     */
    private Set<String> loadEnabledRules(Integer merchantId) {
        Set<String> enabled = new HashSet<>();
        // 默认全部启用
        for (BuiltinRule r : BuiltinRule.values()) enabled.add(r.getRuleId());

        var config = merchantConfigMapper.findByKey(merchantId, "builtin_rule_config");
        if (config == null) return enabled;

        try {
            JsonNode node = objectMapper.readTree(config.getConfigValue());
            enabled.clear();
            node.fields().forEachRemaining(e -> {
                if (e.getValue().asBoolean(true)) {
                    enabled.add(e.getKey());
                }
            });
        } catch (Exception e) {
            log.warn("解析 builtin_rule_config 失败，使用默认全部启用: {}", e.getMessage());
            for (BuiltinRule r : BuiltinRule.values()) enabled.add(r.getRuleId());
        }
        return enabled;
    }

    /** 检查该规则最近是否已触发（去重） */
    private boolean isDuplicate(Integer merchantId, String ruleId, LocalDateTime since) {
        return aiAdviceMapper.countRecentByRule(merchantId, ruleId, since) > 0;
    }

    private double ratio(int part, int total) {
        return total > 0 ? (double) part / total : 0.0;
    }

    private double toDouble(Object val) {
        if (val == null) return 0;
        if (val instanceof Number) return ((Number) val).doubleValue();
        try { return Double.parseDouble(val.toString()); } catch (Exception e) { return 0; }
    }
}

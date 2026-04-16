package com.traffic.rule;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * 自定义规则解析器
 *
 * 自定义规则JSON格式（存于 merchant_config.config_value，config_key='custom_rules'）：
 * {
 *   "rules": [
 *     {
 *       "ruleId": "custom_001",
 *       "name": "周五晚高峰甜点提醒",
 *       "metric": "femaleRatio",
 *       "operator": ">",
 *       "threshold": 0.6,
 *       "adviceType": "备货",
 *       "content": "今晚女性顾客多，甜点备货双倍"
 *     }
 *   ]
 * }
 *
 * 支持的 metric：femaleRatio / maleRatio / ageUnder18Ratio / age1860Ratio /
 *               ageOver60Ratio / totalEnterCount / glassesRatio / bagRatio /
 *               holdItemRatio / avgStaySeconds
 * 支持的 operator：> / < / >= / <= / =
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CustomRuleParser {

    private final ObjectMapper objectMapper;

    /**
     * 解析自定义规则JSON并对当前上下文逐条求值
     *
     * @param customRulesJson  merchant_config.config_value 的 JSON 字符串
     * @param ctx              当前规则上下文
     * @return 触发的自定义规则结果列表
     */
    public List<RuleResult> evaluate(String customRulesJson, RuleContext ctx) {
        List<RuleResult> results = new ArrayList<>();
        if (customRulesJson == null || customRulesJson.isBlank()) {
            return results;
        }

        try {
            JsonNode root = objectMapper.readTree(customRulesJson);
            JsonNode rulesNode = root.path("rules");
            if (!rulesNode.isArray()) return results;

            for (JsonNode ruleNode : rulesNode) {
                try {
                    String ruleId    = ruleNode.path("ruleId").asText();
                    String metric    = ruleNode.path("metric").asText();
                    String operator  = ruleNode.path("operator").asText();
                    double threshold = ruleNode.path("threshold").asDouble();
                    String adviceType = ruleNode.path("adviceType").asText("营销");
                    String content   = ruleNode.path("content").asText();

                    double actual = extractMetric(ctx, metric);
                    if (compare(actual, operator, threshold)) {
                        String snapshot = String.format(
                            "{\"metric\":\"%s\",\"operator\":\"%s\",\"threshold\":%.4f,\"actual\":%.4f}",
                            metric, operator, threshold, actual);
                        results.add(new RuleResult(ruleId, adviceType, content, snapshot));
                    }
                } catch (Exception e) {
                    log.warn("自定义规则解析失败: ruleId={}, error={}",
                             ruleNode.path("ruleId").asText("?"), e.getMessage());
                }
            }
        } catch (Exception e) {
            log.error("自定义规则JSON解析异常: {}", e.getMessage());
        }

        return results;
    }

    /**
     * 从 RuleContext 中取指定 metric 字段的值
     * 使用 switch 而非反射，保持类型安全
     */
    private double extractMetric(RuleContext ctx, String metric) {
        return switch (metric) {
            case "femaleRatio"      -> ctx.getFemaleRatio();
            case "maleRatio"        -> ctx.getMaleRatio();
            case "ageUnder18Ratio"  -> ctx.getAgeUnder18Ratio();
            case "age1860Ratio"     -> ctx.getAge1860Ratio();
            case "ageOver60Ratio"   -> ctx.getAgeOver60Ratio();
            case "totalEnterCount"  -> ctx.getTotalEnterCount();
            case "glassesRatio"     -> ctx.getGlassesRatio();
            case "bagRatio"         -> ctx.getBagRatio();
            case "holdItemRatio"    -> ctx.getHoldItemRatio();
            case "avgStaySeconds"   -> ctx.getAvgStaySeconds();
            default -> throw new IllegalArgumentException("不支持的 metric: " + metric);
        };
    }

    /** 执行比较运算 */
    private boolean compare(double actual, String operator, double threshold) {
        return switch (operator) {
            case ">"  -> actual > threshold;
            case "<"  -> actual < threshold;
            case ">=" -> actual >= threshold;
            case "<=" -> actual <= threshold;
            case "="  -> Math.abs(actual - threshold) < 1e-9;
            default   -> throw new IllegalArgumentException("不支持的 operator: " + operator);
        };
    }
}

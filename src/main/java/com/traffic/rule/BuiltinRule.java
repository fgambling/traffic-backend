package com.traffic.rule;

import lombok.Getter;

import java.util.Arrays;
import java.util.Optional;
import java.util.function.Predicate;

/**
 * 内置规则枚举（10条）
 * 每条规则包含：ruleId、adviceType、触发条件谓词、建议内容
 *
 * 使用方式：
 *   BuiltinRule.values() 遍历所有规则
 *   rule.evaluate(ctx)   判断是否触发
 *   rule.buildResult(ctx) 生成 RuleResult
 */
@Getter
public enum BuiltinRule {

    /**
     * R001: 女性顾客占比 > 70%
     */
    R001("R001", "营销",
         ctx -> ctx.getFemaleRatio() > 0.70,
         "当前时段女性顾客比例较高，建议推送甜品/低卡饮品优惠。"),

    /**
     * R002: 男性顾客占比 > 70%
     */
    R002("R002", "营销",
         ctx -> ctx.getMaleRatio() > 0.70,
         "男性顾客占比较高，可推荐啤酒、下酒菜或运动直播。"),

    /**
     * R003: 成年人(18-60岁)占比 > 80%
     */
    R003("R003", "营销",
         ctx -> ctx.getAge1860Ratio() > 0.80,
         "主力消费人群为成年人，可主推套餐或会员充值活动。"),

    /**
     * R004: 未成年占比 > 30%（学生群体）
     */
    R004("R004", "营销",
         ctx -> ctx.getAgeUnder18Ratio() > 0.30,
         "学生群体增多，可推出学生证折扣或小份餐品。"),

    /**
     * R005: 戴眼镜顾客占比 > 60%（上班族/学生）
     */
    R005("R005", "营销",
         ctx -> ctx.getGlassesRatio() > 0.60,
         "戴眼镜顾客多，可能为上班族或学生，可提供免费WIFI、充电插座。"),

    /**
     * R006: 携带包袋顾客占比 > 50%
     */
    R006("R006", "营销",
         ctx -> ctx.getBagRatio() > 0.50,
         "携带包袋顾客多，建议提供储物篮或挂钩。"),

    /**
     * R007: 平均停留时长 < 昨日同时段 70%（明显缩短）
     */
    R007("R007", "排班",
         ctx -> ctx.getAvgStayYesterdaySameHour() > 0
              && ctx.getAvgStaySeconds() < ctx.getAvgStayYesterdaySameHour() * 0.70,
         "顾客停留时间明显缩短，请检查服务速度或产品吸引力。"),

    /**
     * R008: 当前客流 > 历史同期均值 150%（客流暴增）
     */
    R008("R008", "备货",
         ctx -> ctx.getHistoryAvgCount() > 0
              && ctx.getTotalEnterCount() > ctx.getHistoryAvgCount() * 1.5,
         "客流暴增！建议增加收银人手，提前备餐。"),

    /**
     * R009: 连续3天同时段客流下降 > 20%
     */
    R009("R009", "营销",
         RuleContext::isConsecutiveDecline,
         "客流持续下降，建议检查周边竞争情况或推出限时优惠。"),

    /**
     * R010: 手持物品顾客占比 > 40%
     */
    R010("R010", "营销",
         ctx -> ctx.getHoldItemRatio() > 0.40,
         "很多顾客手持物品，可增加购物篮或打包台。");

    private final String ruleId;
    private final String adviceType;
    private final Predicate<RuleContext> condition;
    private final String adviceContent;

    BuiltinRule(String ruleId, String adviceType,
                Predicate<RuleContext> condition, String adviceContent) {
        this.ruleId = ruleId;
        this.adviceType = adviceType;
        this.condition = condition;
        this.adviceContent = adviceContent;
    }

    /** 判断当前上下文是否触发该规则 */
    public boolean evaluate(RuleContext ctx) {
        return condition.test(ctx);
    }

    /** 构建规则结果（含数据快照） */
    public RuleResult buildResult(RuleContext ctx) {
        String snapshot = buildSnapshot(ctx);
        return new RuleResult(ruleId, adviceType, adviceContent, snapshot);
    }

    /** 根据 ruleId 字符串查找枚举 */
    public static Optional<BuiltinRule> findById(String ruleId) {
        return Arrays.stream(values())
                .filter(r -> r.getRuleId().equals(ruleId))
                .findFirst();
    }

    /** 构建触发时的数据快照（JSON字符串），便于溯源 */
    private String buildSnapshot(RuleContext ctx) {
        return String.format(
            "{\"totalEnterCount\":%d,\"femaleRatio\":%.2f,\"maleRatio\":%.2f," +
            "\"ageUnder18Ratio\":%.2f,\"age1860Ratio\":%.2f,\"ageOver60Ratio\":%.2f," +
            "\"glassesRatio\":%.2f,\"bagRatio\":%.2f,\"holdItemRatio\":%.2f," +
            "\"avgStaySeconds\":%.1f,\"avgStayYesterdaySameHour\":%.1f," +
            "\"historyAvgCount\":%.1f,\"consecutiveDecline\":%b}",
            ctx.getTotalEnterCount(),
            ctx.getFemaleRatio(), ctx.getMaleRatio(),
            ctx.getAgeUnder18Ratio(), ctx.getAge1860Ratio(), ctx.getAgeOver60Ratio(),
            ctx.getGlassesRatio(), ctx.getBagRatio(), ctx.getHoldItemRatio(),
            ctx.getAvgStaySeconds(), ctx.getAvgStayYesterdaySameHour(),
            ctx.getHistoryAvgCount(), ctx.isConsecutiveDecline()
        );
    }
}

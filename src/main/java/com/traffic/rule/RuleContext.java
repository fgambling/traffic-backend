package com.traffic.rule;

import lombok.Builder;
import lombok.Data;

/**
 * 规则执行上下文
 * 由过去1小时 traffic_fact 数据聚合计算得到，作为规则引擎的输入
 */
@Data
@Builder
public class RuleContext {

    private Integer merchantId;

    // -------- 基础流量 --------
    /** 过去1小时总进店人数 */
    private int totalEnterCount;

    // -------- 性别占比 --------
    /** 女性占比 [0,1] */
    private double femaleRatio;
    /** 男性占比 [0,1] */
    private double maleRatio;

    // -------- 年龄占比 --------
    /** 未成年人(<18岁)占比 */
    private double ageUnder18Ratio;
    /** 成年人(18-60岁)占比 */
    private double age1860Ratio;
    /** 老年人(>60岁)占比 */
    private double ageOver60Ratio;

    // -------- 特征占比 --------
    /** 戴眼镜占比 */
    private double glassesRatio;
    /** 携带包袋占比（手提包+单肩包+背包之和/总人数） */
    private double bagRatio;
    /** 手持物品占比 */
    private double holdItemRatio;

    // -------- 停留时长 --------
    /** 过去1小时平均停留时长（秒） */
    private double avgStaySeconds;
    /** 昨日同时段平均停留时长（秒），用于环比 */
    private double avgStayYesterdaySameHour;

    // -------- 历史对比 --------
    /** 历史同期均值：过去4周同星期、同时段进店均值 */
    private double historyAvgCount;
    /** 连续3天同时段客流下降>20% */
    private boolean consecutiveDecline;
}

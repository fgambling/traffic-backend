package com.traffic.rule.dto;

import lombok.Data;

/**
 * 单时间窗口的客流属性聚合结果
 * 用于规则引擎构建 RuleContext
 */
@Data
public class HourlyStatsDTO {

    private int totalEnterCount;
    private int totalMale;
    private int totalFemale;
    private int totalUnder18;
    /** age_18_60 总人数（列别名 total1860，避免下划线混淆） */
    private int total1860;
    private int totalOver60;
    private int totalGlasses;
    /** handbag + shoulder + backpack 合计 */
    private int totalBag;
    private int totalHoldItem;
    private int totalStaySeconds;
    private int stayCount;
}

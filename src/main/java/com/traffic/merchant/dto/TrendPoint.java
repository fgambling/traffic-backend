package com.traffic.merchant.dto;

import lombok.Data;

/**
 * 客流趋势单个数据点
 * 对应 /api/merchant/trend 接口返回列表中的每个元素
 */
@Data
public class TrendPoint {

    /** 时间标签，格式因粒度而异：
     *  hour  → "2026-04-17 14:00:00"
     *  day   → "2026-04-17"
     *  week  → "2026-04-14"（本周一日期）
     *  month → "2026-04"
     */
    private String timeLabel;

    /** 进店人数 */
    private int enterCount;

    /** 穿行人数 */
    private int passCount;

    /** 男性人数 */
    private int genderMale;

    /** 女性人数 */
    private int genderFemale;

    /** <18岁 */
    private int ageUnder18;

    /** 18-60岁 */
    private int age1860;

    /** >60岁 */
    private int ageOver60;
}

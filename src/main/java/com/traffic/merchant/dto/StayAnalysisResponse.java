package com.traffic.merchant.dto;

import lombok.Data;

import java.util.List;

/**
 * 停留时长分析响应
 * 对应 GET /api/merchant/stay 接口
 */
@Data
public class StayAnalysisResponse {

    /** 当前时段平均停留秒数 */
    private int avgStaySeconds;

    /** 前一期平均停留秒数（用于与昨日/上期对比） */
    private int prevAvgStaySeconds;

    /** 当前时段有效停留人次 */
    private int stayCount;

    /** <5分钟人次 */
    private int under5Count;

    /** 5~15分钟人次 */
    private int mid5to15Count;

    /** >15分钟人次 */
    private int over15Count;

    /** 按粒度的趋势数据（小时/日/周） */
    private List<StayTrendPoint> trend;
}

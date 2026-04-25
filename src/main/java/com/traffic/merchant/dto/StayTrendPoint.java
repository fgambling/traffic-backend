package com.traffic.merchant.dto;

import lombok.Data;

/**
 * 停留时长趋势单个数据点
 */
@Data
public class StayTrendPoint {
    private String timeLabel;
    private int stayCount;
    private int under5Count;
    private int mid5to15Count;
    private int over15Count;
    /** 该时段平均停留秒数 */
    private double avgStaySeconds;
}

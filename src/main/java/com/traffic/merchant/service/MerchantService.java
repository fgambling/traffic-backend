package com.traffic.merchant.service;

import com.traffic.merchant.dto.DashboardResponse;
import com.traffic.merchant.dto.ProfileResponse;
import com.traffic.merchant.dto.StayAnalysisResponse;
import com.traffic.merchant.dto.TrendPoint;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 商家业务服务接口
 */
public interface MerchantService {

    /**
     * 获取今日看板数据
     * 优先读Redis缓存，缓存不存在则查MySQL聚合
     */
    DashboardResponse getDashboard(Integer merchantId);

    /**
     * 获取客流趋势
     *
     * @param merchantId 商家ID
     * @param type       粒度：hour / day / week / month
     * @param start      开始日期（含），null 时按 type 给默认值
     * @param end        结束日期（含），null 时为今天
     */
    List<TrendPoint> getTrend(Integer merchantId, String type, LocalDate start, LocalDate end,
                              LocalDateTime startDt, LocalDateTime endDt);

    /**
     * 获取用户画像详情（指定时段内各属性维度聚合）
     *
     * @param merchantId 商家ID
     * @param start      开始日期（含），null 时默认今天
     * @param end        结束日期（含），null 时默认今天
     */
    /**
     * @param startDt 精确开始时间（用于小时级查询），优先于 start/end
     * @param endDt   精确结束时间
     */
    ProfileResponse getProfile(Integer merchantId, LocalDate start, LocalDate end,
                               LocalDateTime startDt, LocalDateTime endDt);

    /**
     * 获取停留时长分析（分布 + 趋势 + 与前期对比）
     *
     * @param type    粒度：hour / day / week
     * @param start   开始日期，null 时按 type 给默认值
     * @param end     结束日期，null 时为今天
     * @param startDt 精确开始时间（小时级下钻用），优先于 start/end
     * @param endDt   精确结束时间
     */
    StayAnalysisResponse getStayAnalysis(Integer merchantId, String type,
                                         LocalDate start, LocalDate end,
                                         Integer hour,
                                         LocalDateTime startDt, LocalDateTime endDt);
}

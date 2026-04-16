package com.traffic.device.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.traffic.device.entity.TrafficFact;
import com.traffic.rule.dto.HourlyStatsDTO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 客流数据Mapper
 */
@Mapper
public interface TrafficFactMapper extends BaseMapper<TrafficFact> {

    /**
     * 查询某商家今日所有分钟桶数据（用于看板）
     */
    @Select("SELECT * FROM traffic_fact " +
            "WHERE merchant_id = #{merchantId} " +
            "AND time_bucket >= #{startTime} " +
            "AND time_bucket < #{endTime} " +
            "ORDER BY time_bucket")
    List<TrafficFact> findTodayByMerchant(@Param("merchantId") Integer merchantId,
                                           @Param("startTime") LocalDateTime startTime,
                                           @Param("endTime") LocalDateTime endTime);

    /**
     * 过去N小时各属性聚合统计（给规则引擎构建 RuleContext 用）
     * 列别名采用下划线格式，MyBatis 自动映射到 HourlyStatsDTO 的驼峰字段。
     * total1860 直接用字段名保证 age_18_60 列能正确映射。
     */
    @Select("SELECT " +
            "  COALESCE(SUM(enter_count),0)                       AS total_enter_count, " +
            "  COALESCE(SUM(gender_male),0)                       AS total_male, " +
            "  COALESCE(SUM(gender_female),0)                     AS total_female, " +
            "  COALESCE(SUM(age_under18),0)                       AS total_under18, " +
            "  COALESCE(SUM(age_18_60),0)                         AS total1860, " +
            "  COALESCE(SUM(age_over60),0)                        AS total_over60, " +
            "  COALESCE(SUM(accessory_glasses),0)                 AS total_glasses, " +
            "  COALESCE(SUM(bag_handbag+bag_shoulder+bag_backpack),0) AS total_bag, " +
            "  COALESCE(SUM(hold_item),0)                         AS total_hold_item, " +
            "  COALESCE(SUM(total_stay_seconds),0)                AS total_stay_seconds, " +
            "  COALESCE(SUM(stay_count),0)                        AS stay_count " +
            "FROM traffic_fact " +
            "WHERE merchant_id = #{merchantId} " +
            "AND time_bucket >= #{fromTime} " +
            "AND time_bucket < #{toTime}")
    HourlyStatsDTO aggregateHourlyStats(@Param("merchantId") Integer merchantId,
                                        @Param("fromTime") LocalDateTime fromTime,
                                        @Param("toTime") LocalDateTime toTime);

    /**
     * 历史同期均值：过去4周同星期、同小时段的进店总数均值
     * 用于判断客流暴增（R008）
     */
    @Select("SELECT COALESCE(AVG(daily_count), 0) FROM (" +
            "  SELECT DATE(time_bucket) AS d, SUM(enter_count) AS daily_count " +
            "  FROM traffic_fact " +
            "  WHERE merchant_id = #{merchantId} " +
            "  AND HOUR(time_bucket) = #{hour} " +
            "  AND DAYOFWEEK(time_bucket) = DAYOFWEEK(#{refDate}) " +
            "  AND time_bucket >= #{startDate} " +
            "  AND time_bucket < #{refDate} " +
            "  GROUP BY DATE(time_bucket)" +
            ") t")
    Double queryHistoryAvgCount(@Param("merchantId") Integer merchantId,
                                @Param("hour") int hour,
                                @Param("refDate") LocalDateTime refDate,
                                @Param("startDate") LocalDateTime startDate);

    /**
     * 昨日同时段平均停留时长（秒）
     */
    @Select("SELECT COALESCE(SUM(total_stay_seconds) / NULLIF(SUM(stay_count), 0), 0) " +
            "FROM traffic_fact " +
            "WHERE merchant_id = #{merchantId} " +
            "AND time_bucket >= #{fromTime} " +
            "AND time_bucket < #{toTime}")
    Double queryAvgStaySeconds(@Param("merchantId") Integer merchantId,
                               @Param("fromTime") LocalDateTime fromTime,
                               @Param("toTime") LocalDateTime toTime);

    /**
     * 连续3天同时段下降检测
     * 返回最近3天（不含今天）同小时段每日进店量，按日期升序
     * 格式：[{"day":"2026-04-13","count":120}, ...]
     */
    @Select("SELECT DATE(time_bucket) AS day, SUM(enter_count) AS count " +
            "FROM traffic_fact " +
            "WHERE merchant_id = #{merchantId} " +
            "AND HOUR(time_bucket) = #{hour} " +
            "AND DATE(time_bucket) BETWEEN #{day3} AND #{day1} " +
            "GROUP BY DATE(time_bucket) " +
            "ORDER BY day ASC")
    List<Map<String, Object>> queryRecentDailyCount(@Param("merchantId") Integer merchantId,
                                                    @Param("hour") int hour,
                                                    @Param("day1") String day1,
                                                    @Param("day3") String day3);
}

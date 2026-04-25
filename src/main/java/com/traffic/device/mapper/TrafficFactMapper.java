package com.traffic.device.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.traffic.device.entity.TrafficFact;
import com.traffic.merchant.dto.ProfileResponse;
import com.traffic.merchant.dto.StayTrendPoint;
import com.traffic.merchant.dto.TrendPoint;
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
     * 指定时段内进店总人数
     */
    @Select("SELECT COALESCE(SUM(enter_count), 0) FROM traffic_fact " +
            "WHERE merchant_id = #{merchantId} " +
            "AND time_bucket >= #{startTime} " +
            "AND time_bucket < #{endTime}")
    int sumEnterCount(@Param("merchantId") Integer merchantId,
                      @Param("startTime") LocalDateTime startTime,
                      @Param("endTime") LocalDateTime endTime);

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
     * 客流趋势查询（按粒度聚合）
     * type: hour / day / week / month
     * 注意：script 标签内 < 须转义为 &lt;
     */
    @Select("<script>" +
            "SELECT " +
            "<choose>" +
            "  <when test=\"type == 'minute'\">DATE_FORMAT(time_bucket, '%Y-%m-%d %H:%i:00')</when>" +
            "  <when test=\"type == 'hour'\">DATE_FORMAT(time_bucket, '%Y-%m-%d %H:00:00')</when>" +
            "  <when test=\"type == 'day'\">DATE_FORMAT(time_bucket, '%Y-%m-%d')</when>" +
            "  <when test=\"type == 'week'\">DATE_FORMAT(DATE_SUB(time_bucket, INTERVAL WEEKDAY(time_bucket) DAY), '%Y-%m-%d')</when>" +
            "  <otherwise>DATE_FORMAT(time_bucket, '%Y-%m')</otherwise>" +
            "</choose> AS time_label, " +
            "COALESCE(SUM(enter_count), 0)   AS enter_count, " +
            "COALESCE(SUM(pass_count), 0)    AS pass_count, " +
            "COALESCE(SUM(gender_male), 0)   AS gender_male, " +
            "COALESCE(SUM(gender_female), 0) AS gender_female, " +
            "COALESCE(SUM(age_under18), 0)   AS age_under18, " +
            "COALESCE(SUM(age_18_60), 0)     AS age1860, " +
            "COALESCE(SUM(age_over60), 0)    AS age_over60 " +
            "FROM traffic_fact " +
            "WHERE merchant_id = #{merchantId} " +
            "AND time_bucket >= #{startTime} " +
            "AND time_bucket &lt; #{endTime} " +
            "GROUP BY " +
            "<choose>" +
            "  <when test=\"type == 'minute'\">DATE_FORMAT(time_bucket, '%Y-%m-%d %H:%i:00')</when>" +
            "  <when test=\"type == 'hour'\">DATE_FORMAT(time_bucket, '%Y-%m-%d %H:00:00')</when>" +
            "  <when test=\"type == 'day'\">DATE_FORMAT(time_bucket, '%Y-%m-%d')</when>" +
            "  <when test=\"type == 'week'\">DATE_FORMAT(DATE_SUB(time_bucket, INTERVAL WEEKDAY(time_bucket) DAY), '%Y-%m-%d')</when>" +
            "  <otherwise>DATE_FORMAT(time_bucket, '%Y-%m')</otherwise>" +
            "</choose> " +
            "ORDER BY time_label ASC" +
            "</script>")
    List<TrendPoint> queryTrend(@Param("merchantId") Integer merchantId,
                                @Param("type") String type,
                                @Param("startTime") LocalDateTime startTime,
                                @Param("endTime") LocalDateTime endTime);

    /**
     * 用户画像聚合查询：指定时段内所有属性维度的汇总
     */
    @Select("SELECT " +
            "COALESCE(SUM(enter_count), 0)           AS total_enter, " +
            "COALESCE(SUM(pass_count), 0)            AS total_pass, " +
            "COALESCE(SUM(gender_male), 0)           AS gender_male, " +
            "COALESCE(SUM(gender_female), 0)         AS gender_female, " +
            "COALESCE(SUM(age_under18), 0)           AS age_under18, " +
            "COALESCE(SUM(age_18_60), 0)             AS age1860, " +
            "COALESCE(SUM(age_over60), 0)            AS age_over60, " +
            "COALESCE(SUM(accessory_glasses), 0)     AS accessory_glasses, " +
            "COALESCE(SUM(accessory_hat), 0)         AS accessory_hat, " +
            "COALESCE(SUM(accessory_boots), 0)       AS accessory_boots, " +
            "COALESCE(SUM(bag_handbag), 0)           AS bag_handbag, " +
            "COALESCE(SUM(bag_shoulder), 0)          AS bag_shoulder, " +
            "COALESCE(SUM(bag_backpack), 0)          AS bag_backpack, " +
            "COALESCE(SUM(hold_item), 0)             AS hold_item, " +
            "COALESCE(SUM(upper_short), 0)           AS upper_short, " +
            "COALESCE(SUM(upper_long), 0)            AS upper_long, " +
            "COALESCE(SUM(upper_coat), 0)            AS upper_coat, " +
            "COALESCE(SUM(upper_style_stripe), 0)    AS upper_style_stripe, " +
            "COALESCE(SUM(upper_style_logo), 0)      AS upper_style_logo, " +
            "COALESCE(SUM(upper_style_plaid), 0)     AS upper_style_plaid, " +
            "COALESCE(SUM(upper_style_splice), 0)    AS upper_style_splice, " +
            "COALESCE(SUM(lower_trousers), 0)        AS lower_trousers, " +
            "COALESCE(SUM(lower_shorts), 0)          AS lower_shorts, " +
            "COALESCE(SUM(lower_skirt), 0)           AS lower_skirt, " +
            "COALESCE(SUM(lower_style_stripe), 0)    AS lower_style_stripe, " +
            "COALESCE(SUM(lower_style_pattern), 0)   AS lower_style_pattern, " +
            "COALESCE(SUM(total_stay_seconds), 0)    AS total_stay_seconds, " +
            "COALESCE(SUM(stay_count), 0)            AS stay_count, " +
            "COALESCE(SUM(new_customer_count), 0)    AS new_customer_count, " +
            "COALESCE(SUM(returning_customer_count), 0) AS returning_customer_count " +
            "FROM traffic_fact " +
            "WHERE merchant_id = #{merchantId} " +
            "AND time_bucket >= #{startTime} " +
            "AND time_bucket < #{endTime}")
    ProfileResponse queryProfile(@Param("merchantId") Integer merchantId,
                                 @Param("startTime") LocalDateTime startTime,
                                 @Param("endTime") LocalDateTime endTime);

    /**
     * 停留时长分析：按粒度聚合分桶数据 + 平均停留秒数趋势
     * type: hour / day / week
     */
    @Select("<script>" +
            "SELECT " +
            "<choose>" +
            "  <when test=\"type == 'minute'\">DATE_FORMAT(time_bucket, '%Y-%m-%d %H:%i:00')</when>" +
            "  <when test=\"type == 'hour'\">DATE_FORMAT(time_bucket, '%Y-%m-%d %H:00:00')</when>" +
            "  <when test=\"type == 'week'\">DATE_FORMAT(DATE_SUB(time_bucket, INTERVAL WEEKDAY(time_bucket) DAY), '%Y-%m-%d')</when>" +
            "  <otherwise>DATE_FORMAT(time_bucket, '%Y-%m-%d')</otherwise>" +
            "</choose> AS time_label, " +
            "COALESCE(SUM(stay_count), 0)                                            AS stay_count, " +
            "COALESCE(SUM(stay_under5min), 0)                                        AS under5_count, " +
            "COALESCE(SUM(stay_5to15min), 0)                                         AS mid5to15_count, " +
            "COALESCE(SUM(stay_over15min), 0)                                        AS over15_count, " +
            "COALESCE(SUM(total_stay_seconds) / NULLIF(SUM(stay_count), 0), 0)       AS avg_stay_seconds " +
            "FROM traffic_fact " +
            "WHERE merchant_id = #{merchantId} " +
            "AND time_bucket >= #{startTime} " +
            "AND time_bucket &lt; #{endTime} " +
            "GROUP BY " +
            "<choose>" +
            "  <when test=\"type == 'minute'\">DATE_FORMAT(time_bucket, '%Y-%m-%d %H:%i:00')</when>" +
            "  <when test=\"type == 'hour'\">DATE_FORMAT(time_bucket, '%Y-%m-%d %H:00:00')</when>" +
            "  <when test=\"type == 'week'\">DATE_FORMAT(DATE_SUB(time_bucket, INTERVAL WEEKDAY(time_bucket) DAY), '%Y-%m-%d')</when>" +
            "  <otherwise>DATE_FORMAT(time_bucket, '%Y-%m-%d')</otherwise>" +
            "</choose> " +
            "ORDER BY time_label ASC" +
            "</script>")
    List<StayTrendPoint> queryStayTrend(@Param("merchantId") Integer merchantId,
                                        @Param("type") String type,
                                        @Param("startTime") LocalDateTime startTime,
                                        @Param("endTime") LocalDateTime endTime);

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

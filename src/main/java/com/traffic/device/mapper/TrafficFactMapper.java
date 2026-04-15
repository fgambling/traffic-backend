package com.traffic.device.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.traffic.device.entity.TrafficFact;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 客流数据Mapper
 * 核心操作：按 merchant_id + device_id + time_bucket 做 UPSERT
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
}

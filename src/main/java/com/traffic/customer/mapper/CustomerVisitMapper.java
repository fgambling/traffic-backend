package com.traffic.customer.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.traffic.customer.entity.CustomerVisit;
import org.apache.ibatis.annotations.*;

import java.time.LocalDate;

/**
 * 新老客Mapper
 */
@Mapper
public interface CustomerVisitMapper extends BaseMapper<CustomerVisit> {

    /**
     * 高效UPSERT：
     * - 首次：INSERT，visit_count=1
     * - 今日已记录：last_visit_date=today，不改 visit_count（避免重复计日）
     * - 历史老客今日再来：last_visit_date 更新，visit_count+1
     */
    @Insert("INSERT INTO customer_visit " +
            "(merchant_id, person_hash, first_visit_date, last_visit_date, visit_count) " +
            "VALUES (#{merchantId}, #{personHash}, #{today}, #{today}, 1) " +
            "ON DUPLICATE KEY UPDATE " +
            "visit_count = IF(last_visit_date = #{today}, visit_count, visit_count + 1), " +
            "last_visit_date = #{today}")
    int upsert(@Param("merchantId") Integer merchantId,
               @Param("personHash") String personHash,
               @Param("today") LocalDate today);

    /**
     * 查询单条记录（upsert 后用于判断新老客状态）
     */
    @Select("SELECT * FROM customer_visit " +
            "WHERE merchant_id = #{merchantId} AND person_hash = #{personHash}")
    CustomerVisit findByHash(@Param("merchantId") Integer merchantId,
                             @Param("personHash") String personHash);

    /**
     * 查询常客数量（过去30天内到访>=4次）
     */
    @Select("SELECT COUNT(1) FROM customer_visit " +
            "WHERE merchant_id = #{merchantId} " +
            "AND visit_count >= 4 " +
            "AND last_visit_date >= #{since}")
    int countFrequent(@Param("merchantId") Integer merchantId,
                      @Param("since") LocalDate since);
}

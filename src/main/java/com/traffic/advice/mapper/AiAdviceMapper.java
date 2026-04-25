package com.traffic.advice.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.traffic.advice.entity.AiAdvice;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * AI建议Mapper
 */
@Mapper
public interface AiAdviceMapper extends BaseMapper<AiAdvice> {

    /**
     * 查询某商家某规则在指定时间之后是否已有记录（去重用）
     */
    @Select("SELECT COUNT(1) FROM ai_advice " +
            "WHERE merchant_id = #{merchantId} " +
            "AND trigger_rule_id = #{ruleId} " +
            "AND created_at >= #{fromTime}")
    int countRecentByRule(@Param("merchantId") Integer merchantId,
                          @Param("ruleId") String ruleId,
                          @Param("fromTime") LocalDateTime fromTime);

    /**
     * 分页查询建议列表（可按 source 过滤）
     * source=null 时查全部
     */
    @Select("<script>" +
            "SELECT * FROM ai_advice " +
            "WHERE merchant_id = #{merchantId} " +
            "<if test='adviceType != null'> AND advice_type = #{adviceType} </if>" +
            "<if test='source != null'> AND source = #{source} </if>" +
            "ORDER BY created_at DESC" +
            "</script>")
    IPage<AiAdvice> pageByMerchant(Page<AiAdvice> page,
                                   @Param("merchantId") Integer merchantId,
                                   @Param("adviceType") String adviceType,
                                   @Param("source") Integer source);

    /**
     * 按商家聚合建议数量和有用率（用于费用/成本按商家明细）
     */
    @Select("SELECT merchant_id AS merchantId, COUNT(1) AS totalCount, " +
            "SUM(CASE WHEN source = 2 THEN 1 ELSE 0 END) AS aiCount, " +
            "SUM(CASE WHEN feedback = 1 THEN 1 ELSE 0 END) AS usefulCount " +
            "FROM ai_advice GROUP BY merchant_id ORDER BY totalCount DESC")
    List<Map<String, Object>> costByMerchant();
}

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
            "AND (review_status IS NULL OR review_status != 2) " +
            "<if test='adviceType != null'> AND advice_type = #{adviceType} </if>" +
            "<if test='source != null'> AND source = #{source} </if>" +
            "ORDER BY created_at DESC" +
            "</script>")
    IPage<AiAdvice> pageByMerchant(Page<AiAdvice> page,
                                   @Param("merchantId") Integer merchantId,
                                   @Param("adviceType") String adviceType,
                                   @Param("source") Integer source);

    /**
     * 统计某商家今日 LLM 调用次数（按 call_id 去重）
     */
    @Select("SELECT COUNT(DISTINCT call_id) FROM ai_advice " +
            "WHERE merchant_id = #{merchantId} AND source = 2 " +
            "AND call_id IS NOT NULL AND created_at >= #{fromTime}")
    long countTodayCalls(@Param("merchantId") Integer merchantId,
                         @Param("fromTime") LocalDateTime fromTime);

    /**
     * 按商家 + 模型聚合 AI 大模型建议统计（仅 source=2）
     * 用于按各记录实际使用模型计算精确历史成本
     */
    @Select("SELECT m.id AS merchantId, " +
            "  m.name AS merchantName, " +
            "  COALESCE(a.model_used, '') AS modelUsed, " +
            "  COUNT(a.id) AS totalAiCount, " +
            "  SUM(CASE WHEN DATE(a.created_at) = CURDATE() THEN 1 ELSE 0 END) AS todayAiCount, " +
            "  MAX(a.created_at) AS lastGenAt, " +
            "  COALESCE(SUM(LENGTH(a.content)), 0) AS totalContentLen, " +
            "  COALESCE(SUM(CASE WHEN DATE(a.created_at) = CURDATE() THEN LENGTH(a.content) ELSE 0 END), 0) AS todayContentLen " +
            "FROM merchant m " +
            "LEFT JOIN ai_advice a ON a.merchant_id = m.id AND a.source = 2 " +
            "WHERE m.package_type = 3 AND m.status = 1 " +
            "GROUP BY m.id, m.name, a.model_used " +
            "ORDER BY m.id ASC")
    List<Map<String, Object>> costByMerchantModel();
}

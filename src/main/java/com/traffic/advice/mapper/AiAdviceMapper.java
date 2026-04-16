package com.traffic.advice.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.traffic.advice.entity.AiAdvice;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDateTime;

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
}

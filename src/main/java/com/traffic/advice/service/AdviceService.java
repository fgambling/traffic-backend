package com.traffic.advice.service;

import com.traffic.advice.dto.AdvicePageResult;

/**
 * AI/规则建议服务接口
 */
public interface AdviceService {

    /**
     * 分页查询建议列表
     *
     * @param merchantId 商家ID
     * @param adviceType 类型过滤（null=全部）
     * @param page       页码（1起）
     * @param size       每页条数
     */
    AdvicePageResult listAdvice(Integer merchantId, String adviceType, Integer source, int page, int size);

    /**
     * 规则引擎触发：生成一条建议并保存
     *
     * @param merchantId 商家ID
     */
    void generateAdvice(Integer merchantId, String mode);

    /**
     * 保存用户反馈
     *
     * @param id       建议ID
     * @param feedback 1=有用 2=无用
     */
    void saveFeedback(Long id, Integer feedback);

    /**
     * 检查今日 / 过去一小时是否有客流数据
     * @return { "today": true/false, "lastHour": true/false }
     */
    java.util.Map<String, Boolean> checkDataAvailability(Integer merchantId);
}

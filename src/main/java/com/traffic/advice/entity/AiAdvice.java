package com.traffic.advice.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import lombok.experimental.Accessors;

import java.time.LocalDateTime;

/**
 * AI/规则引擎建议实体
 * 对应 ai_advice 表（含V2新增字段）
 */
@Data
@Accessors(chain = true)
@TableName("ai_advice")
public class AiAdvice {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Integer merchantId;

    /** 触发规则ID（如 R001、custom_001），AI生成时为null */
    private String triggerRuleId;

    /** 来源：1=规则引擎，2=AI大模型 */
    private Integer source;

    /** 建议类型（营销/排班/备货） */
    private String adviceType;

    /** 建议正文 */
    private String content;

    /** 触发时的关键数据快照（JSON字符串） */
    private String dataSnapshot;

    /** 置信度：高/中/低（AI大模型生成时填写） */
    private String confidence;

    /** LLM调用批次ID（同一次API调用的多条建议共享同一UUID） */
    private String callId;

    /** 生成建议时实际使用的LLM模型（source=2时记录，用于精确成本核算） */
    private String modelUsed;

    /** 用户反馈：0未反馈 1有用 2无用 */
    private Integer feedback;

    /** 管理员审核：0未审核 1已采纳 2标记待优化 */
    private Integer reviewStatus;

    /** 管理员备注 */
    private String adminNote;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}

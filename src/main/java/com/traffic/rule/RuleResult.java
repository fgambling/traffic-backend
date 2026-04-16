package com.traffic.rule;

import lombok.AllArgsConstructor;
import lombok.Data;

/**
 * 单条规则的匹配结果
 */
@Data
@AllArgsConstructor
public class RuleResult {

    /** 规则ID（如 R001 / custom_001） */
    private String ruleId;

    /** 建议类型（营销/排班/备货） */
    private String adviceType;

    /** 建议正文 */
    private String content;

    /** 触发时的数据快照（JSON字符串，存入 ai_advice.data_snapshot） */
    private String dataSnapshot;
}

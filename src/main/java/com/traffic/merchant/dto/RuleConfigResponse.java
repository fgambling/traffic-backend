package com.traffic.merchant.dto;

import com.traffic.rule.BuiltinRule;
import lombok.Data;

import java.util.List;

/**
 * 商家规则配置响应体
 */
@Data
public class RuleConfigResponse {

    /** 内置规则列表（含当前启用状态） */
    private List<BuiltinRuleDTO> builtinRules;

    /** 商家自定义规则列表 */
    private List<CustomRuleDTO> customRules;

    @Data
    public static class BuiltinRuleDTO {
        private String ruleId;
        private String adviceType;
        private String description;
        private boolean enabled;

        public static BuiltinRuleDTO from(BuiltinRule rule, boolean enabled) {
            BuiltinRuleDTO dto = new BuiltinRuleDTO();
            dto.setRuleId(rule.getRuleId());
            dto.setAdviceType(rule.getAdviceType());
            dto.setDescription(rule.getAdviceContent());
            dto.setEnabled(enabled);
            return dto;
        }
    }

    @Data
    public static class CustomRuleDTO {
        private String ruleId;
        private String name;
        private String metric;
        private String operator;
        private double threshold;
        private String adviceType;
        private String content;
    }
}

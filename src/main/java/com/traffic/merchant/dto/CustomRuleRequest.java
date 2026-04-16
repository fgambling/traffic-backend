package com.traffic.merchant.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 新增自定义规则请求体
 */
@Data
public class CustomRuleRequest {

    @NotBlank(message = "ruleId不能为空")
    private String ruleId;

    @NotBlank(message = "规则名称不能为空")
    private String name;

    /** 指标字段，见 CustomRuleParser 支持列表 */
    @NotBlank(message = "metric不能为空")
    private String metric;

    /** 比较运算符：> < >= <= = */
    @NotBlank(message = "operator不能为空")
    private String operator;

    @NotNull(message = "threshold不能为空")
    private Double threshold;

    private String adviceType = "营销";

    @NotBlank(message = "建议内容不能为空")
    private String content;
}

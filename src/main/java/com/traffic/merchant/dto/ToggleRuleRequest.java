package com.traffic.merchant.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 内置规则启用/禁用请求
 */
@Data
public class ToggleRuleRequest {

    @NotBlank(message = "ruleId不能为空")
    private String ruleId;

    private boolean enabled;
}

package com.traffic.merchant.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 商家配置保存请求
 */
@Data
public class MerchantConfigRequest {

    @NotBlank(message = "configKey不能为空")
    private String configKey;

    @NotNull(message = "configValue不能为空")
    private Object configValue;
}

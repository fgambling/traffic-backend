package com.traffic.merchant.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import lombok.experimental.Accessors;

import java.time.LocalDateTime;

/**
 * 商家配置实体
 * config_key 常量：
 *   builtin_rule_config  — 内置规则启用状态 JSON  {"R001":true,"R002":false,...}
 *   custom_rules         — 自定义规则列表 JSON    {"rules":[...]}
 *   menu_categories      — 菜单分类
 *   stock_threshold      — 备货阈值
 */
@Data
@Accessors(chain = true)
@TableName("merchant_config")
public class MerchantConfig {

    @TableId(type = IdType.AUTO)
    private Integer id;

    private Integer merchantId;

    private String configKey;

    /** 存储JSON字符串 */
    private String configValue;

    private LocalDateTime updatedAt;
}

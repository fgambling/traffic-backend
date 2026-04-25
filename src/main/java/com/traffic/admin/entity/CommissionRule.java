package com.traffic.admin.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("commission_rule")
public class CommissionRule {
    @TableId(type = IdType.AUTO)
    private Integer id;
    /** 套餐类型 1基础版 2高级版 */
    private Integer packageType;
    private String name;
    /** 佣金比例（与 fixedAmount 二选一） */
    private BigDecimal rate;
    /** 固定佣金金额 */
    private BigDecimal fixedAmount;
    private String description;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}

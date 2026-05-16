package com.traffic.salesman.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 提现申请
 */
@Data
@TableName("withdraw_apply")
public class WithdrawApply {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 业务员ID */
    private Integer salesmanId;

    /** 提现金额 */
    private BigDecimal amount;

    /** 提现方式: 1微信 2银行卡 */
    private Integer way;

    /** 收款账号 */
    private String account;

    /** 状态: 0审核中 1已打款 2驳回 */
    private Integer status;

    /** 备注 */
    private String remark;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(insertStrategy = FieldStrategy.NOT_NULL, updateStrategy = FieldStrategy.NOT_NULL)
    private LocalDateTime updatedAt;
}

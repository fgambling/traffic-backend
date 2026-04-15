package com.traffic.salesman.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 业务员实体
 */
@Data
@TableName("salesman")
public class Salesman {

    @TableId(type = IdType.AUTO)
    private Integer id;

    /** 姓名 */
    private String name;

    /** 手机号 */
    private String phone;

    /** 密码（BCrypt加密） */
    private String password;

    /** 微信openid */
    private String openid;

    /** 累计佣金 */
    private BigDecimal totalCommission;

    /** 可提现余额 */
    private BigDecimal balance;

    /** 状态: 0禁用 1正常 */
    private Integer status;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}

package com.traffic.salesman.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 商家跟进记录
 */
@Data
@TableName("merchant_follow")
public class MerchantFollow {

    @TableId(type = IdType.AUTO)
    private Integer id;

    /** 业务员ID */
    private Integer salesmanId;

    /** 商家ID */
    private Integer merchantId;

    /** 状态: 1接洽中 2已合作 3已流失 */
    private Integer status;

    /** 跟进备注（最新一条，历史见 follow_record 表） */
    private String followRecord;

    /** 合作凭证图片URL */
    private String voucherUrl;

    /** 合作时间（状态变为2时记录） */
    private LocalDateTime cooperationTime;

    /** 合作金额（业务员申请时填写） */
    private BigDecimal commission;

    /** 实际到账佣金（审批通过时写入，联合跟进为均分后的份额） */
    private BigDecimal earnedCommission;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.UPDATE)
    private LocalDateTime updatedAt;
}

package com.traffic.salesman.dto;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class UpdateFollowRequest {
    /** 1接洽中 2已合作 3已流失 */
    private Integer status;
    private String followRecord;
    /** 合作凭证图片URL（状态变为2时可上传） */
    private String voucherUrl;
    /** 状态变更备注（可选） */
    private String remark;
    /** 合作金额（选择已合作时必填，存入 commission 字段供管理员审批） */
    private BigDecimal amount;
}

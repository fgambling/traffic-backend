package com.traffic.salesman.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 跟进记录视图对象（JOIN 商家表后返回给前端）
 */
@Data
public class FollowVO {

    private Integer id;
    private Integer merchantId;

    /** 来自 merchant 表 */
    private String merchantName;
    private String contactPerson;
    private String contactPhone;
    private String licenseNo;
    private String address;

    /** 1接洽中 2已合作 3已流失 */
    private Integer status;

    private String followRecord;
    private String voucherUrl;
    private BigDecimal commission;
    private BigDecimal earnedCommission;
    private LocalDateTime cooperationTime;
    private LocalDateTime updatedAt;

    /** 联合跟进的其他业务员数量（>0 表示联合跟进） */
    private Integer coFollowCount;
}

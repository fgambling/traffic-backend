package com.traffic.salesman.dto;

import lombok.Data;

@Data
public class AddFollowRequest {
    /** 商家名称 */
    private String name;
    private String contactPerson;
    private String contactPhone;
    private String licenseNo;
    /** 商家地址（必填） */
    private String address;
    /** 初次跟进备注（选填） */
    private String remark;
    /** 初始状态，默认 1（接洽中） */
    private Integer status = 1;
}

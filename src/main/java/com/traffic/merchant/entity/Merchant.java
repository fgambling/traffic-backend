package com.traffic.merchant.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 商家实体
 */
@Data
@TableName("merchant")
public class Merchant {

    @TableId(type = IdType.AUTO)
    private Integer id;

    /** 商家名称 */
    private String name;

    /** 营业执照号 */
    private String licenseNo;

    /** 联系人 */
    private String contactPerson;

    /** 联系电话 */
    private String contactPhone;

    /** 登录密码（BCrypt，后台创建时设置） */
    private String password;

    /** 地址 */
    private String address;

    /** 套餐类型: 1普通 2中级 3高级 */
    private Integer packageType;

    /** 状态: 0待激活 1正常 2禁用 */
    private Integer status;

    /** 关联设备bindId */
    private String bindId;

    /** 微信openid（商家端登录用） */
    private String openid;

    /** 是否为业务员跟进线索：1=线索（不显示在商家管理），0=正式商家 */
    private Integer isLead;

    /** 套餐到期日（普通版为 null 表示永久） */
    private LocalDate packageExpireAt;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}

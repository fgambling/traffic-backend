package com.traffic.merchant.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

@Data
@TableName("merchant_business_info")
public class MerchantBusinessInfo {

    @TableId(type = IdType.AUTO)
    private Integer id;

    private Integer merchantId;

    /** 店铺业态，如：餐饮、商超、服装、美妆、书店、健身等 */
    private String businessType;

    private String menu;

    private String promotions;

    private String businessHours;

    private String targetAudience;

    @TableField(insertStrategy = FieldStrategy.NEVER, updateStrategy = FieldStrategy.NEVER)
    private String updatedAt;
}

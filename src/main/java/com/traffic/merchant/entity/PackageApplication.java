package com.traffic.merchant.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import lombok.experimental.Accessors;

import java.time.LocalDateTime;

@Data
@Accessors(chain = true)
@TableName("package_application")
public class PackageApplication {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Integer merchantId;

    /** 目标套餐：2=中级版 3=高级版 */
    private Integer targetPkg;

    private String remark;

    private String imageUrl;

    /** 0=待处理 1=已通过 2=已拒绝 */
    private Integer status;

    private String adminNote;

    @TableField(insertStrategy = FieldStrategy.NOT_NULL)
    private LocalDateTime createdAt;

    @TableField(insertStrategy = FieldStrategy.NOT_NULL, updateStrategy = FieldStrategy.NOT_NULL)
    private LocalDateTime updatedAt;
}

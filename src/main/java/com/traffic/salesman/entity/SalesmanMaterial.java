package com.traffic.salesman.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 业务员营销素材
 */
@Data
@TableName("salesman_material")
public class SalesmanMaterial {

    @TableId(type = IdType.AUTO)
    private Integer id;

    private Integer salesmanId;

    /** 素材标题 */
    private String title;

    /** 类型：image / video / doc */
    private String type;

    /** 文件URL */
    private String url;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}

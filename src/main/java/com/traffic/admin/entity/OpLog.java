package com.traffic.admin.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import lombok.experimental.Accessors;

import java.time.LocalDateTime;

@Data
@Accessors(chain = true)
@TableName("op_log")
public class OpLog {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String operator;
    private String module;
    private String action;

    @TableField(insertStrategy = FieldStrategy.NOT_NULL)
    private LocalDateTime createdAt;
}

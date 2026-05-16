package com.traffic.admin.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import lombok.experimental.Accessors;

import java.time.LocalDateTime;

@Data
@Accessors(chain = true)
@TableName("admin_user")
public class AdminUser {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String username;

    private String name;

    @TableField(insertStrategy = FieldStrategy.NOT_NULL)
    private String password;

    @TableField(insertStrategy = FieldStrategy.NOT_NULL)
    private LocalDateTime createdAt;
}

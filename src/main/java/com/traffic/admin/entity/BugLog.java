package com.traffic.admin.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("bug_log")
public class BugLog {
    @TableId(type = IdType.AUTO)
    private Long id;
    /** error / warn / info */
    private String level;
    private String module;
    private String message;
    private String stackTrace;
    private Integer userId;
    private String userRole;
    /** 0未处理 1已解决 */
    private Integer resolved;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}

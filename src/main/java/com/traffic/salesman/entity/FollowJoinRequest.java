package com.traffic.salesman.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 联合跟进申请
 */
@Data
@TableName("follow_join_request")
public class FollowJoinRequest {

    @TableId(type = IdType.AUTO)
    private Integer id;

    /** 被申请联合跟进的 merchant_follow.id */
    private Integer followId;

    /** 发起申请的业务员 ID */
    private Integer requesterId;

    /** 0=待处理 1=已同意 2=已拒绝 */
    private Integer status;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}

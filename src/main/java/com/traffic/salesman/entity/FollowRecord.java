package com.traffic.salesman.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 跟进历史记录（每次保存备注都追加一条，不覆盖）
 */
@Data
@TableName("follow_record")
public class FollowRecord {

    @TableId(type = IdType.AUTO)
    private Integer id;

    /** 关联 merchant_follow.id */
    private Integer followId;

    /** 记录类型: note=文字记录, status=状态变更 */
    private String type;

    /** 跟进内容 */
    private String content;

    /** 图片附件 URL（可选） */
    private String imageUrl;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}

package com.traffic.device.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.time.Instant;
import java.util.List;

/**
 * 单条行人检测记录
 */
@Data
public class PersonDetection {

    /** 记录ID */
    private Long id;

    /** 行人唯一标识 */
    private String personId;

    /** 摄像头设备ID */
    private String deviceId;

    /** 进入时间（ISO8601，UTC） */
    private Instant entryTime;

    /** 离开时间（ISO8601，UTC） */
    private Instant exitTime;

    /** 进入计数 */
    private Integer entryCount;

    /** 离开计数 */
    private Integer exitCount;

    /**
     * 是否穿行（true=路过未停留，不计入有效分析）
     */
    @JsonProperty("isPassThrough")
    private boolean passThrough;

    private Instant lastSeenAt;
    private Instant createdAt;

    /**
     * 26维行人属性置信度数组
     * 索引定义（详见AttributeParser）:
     *  0: 帽子   1: 眼镜
     *  2: 短袖   3: 长袖   4: 上衣条纹  5: 上衣Logo  6: 上衣格子  7: 上衣拼接
     *  8: 下装条纹  9: 下装图案
     * 10: 长外套  11: 长裤  12: 短裤  13: 裙子
     * 14: 靴子  15: 手提包  16: 单肩包  17: 背包  18: 手持物品
     * 19: <18岁  20: 18-60岁  21: >60岁  22: 女性（>0.5则女）
     * 23-25: 预留
     */
    private List<Double> attributes;
}

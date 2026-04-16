package com.traffic.device.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import lombok.experimental.Accessors;

import java.time.LocalDateTime;

/**
 * 客流聚合宽表实体
 * 每条记录对应某商家某设备某分钟的聚合数据
 */
@Data
@Accessors(chain = true)
@TableName("traffic_fact")
public class TrafficFact {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Integer merchantId;
    private String deviceId;

    /** 分钟时间戳（秒位清零） */
    private LocalDateTime timeBucket;

    // ---------- 人流量 ----------
    private Integer enterCount;    // 进店
    private Integer passCount;     // 穿行

    // ---------- 性别 ----------
    private Integer genderMale;
    private Integer genderFemale;

    // ---------- 年龄段 ----------
    private Integer ageUnder18;
    @TableField("age_18_60")   // 纯数字段名无法由驼峰自动推导，需显式指定
    private Integer age1860;
    private Integer ageOver60;

    // ---------- 配饰 ----------
    private Integer accessoryGlasses;
    private Integer accessoryHat;
    private Integer accessoryBoots;

    // ---------- 包袋 ----------
    private Integer bagHandbag;
    private Integer bagShoulder;
    private Integer bagBackpack;
    private Integer holdItem;

    // ---------- 上衣类型 ----------
    private Integer upperShort;
    private Integer upperLong;
    private Integer upperCoat;

    // ---------- 上衣风格 ----------
    private Integer upperStyleStripe;
    private Integer upperStyleLogo;
    private Integer upperStylePlaid;
    private Integer upperStyleSplice;

    // ---------- 下装类型 ----------
    private Integer lowerTrousers;
    private Integer lowerShorts;
    private Integer lowerSkirt;

    // ---------- 下装风格 ----------
    private Integer lowerStyleStripe;
    private Integer lowerStylePattern;

    // ---------- 停留时长 ----------
    /** 总停留秒数（穿行不计入） */
    private Integer totalStaySeconds;
    /** 有效停留人次 */
    private Integer stayCount;

    // ---------- 新老客（V2新增字段） ----------
    /** 新客人数（first_visit_date == today） */
    private Integer newCustomerCount;
    /** 老客人数（visit_count >= 2） */
    private Integer returningCustomerCount;
}

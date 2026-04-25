package com.traffic.merchant.dto;

import lombok.Data;

/**
 * 用户画像详情响应
 * 对应 GET /api/merchant/profile 接口
 * 聚合指定时段内所有属性维度的总量
 */
@Data
public class ProfileResponse {

    // ---------- 汇总 ----------
    private int totalEnter;
    private int totalPass;

    // ---------- 性别 ----------
    private int genderMale;
    private int genderFemale;

    // ---------- 年龄段 ----------
    private int ageUnder18;
    private int age1860;
    private int ageOver60;

    // ---------- 配饰 ----------
    private int accessoryGlasses;
    private int accessoryHat;
    private int accessoryBoots;

    // ---------- 包袋 ----------
    private int bagHandbag;
    private int bagShoulder;
    private int bagBackpack;
    private int holdItem;

    // ---------- 上衣类型 ----------
    private int upperShort;
    private int upperLong;
    private int upperCoat;

    // ---------- 上衣风格 ----------
    private int upperStyleStripe;
    private int upperStyleLogo;
    private int upperStylePlaid;
    private int upperStyleSplice;

    // ---------- 下装类型 ----------
    private int lowerTrousers;
    private int lowerShorts;
    private int lowerSkirt;

    // ---------- 下装风格 ----------
    private int lowerStyleStripe;
    private int lowerStylePattern;

    // ---------- 停留时长 ----------
    /** 总停留秒数 */
    private int totalStaySeconds;
    /** 有效停留人次 */
    private int stayCount;
    /** 平均停留时长（秒），stayCount=0时为0 */
    private int avgStaySeconds;

    // ---------- 新老客 ----------
    private int newCustomerCount;
    private int returningCustomerCount;

    // ---------- 查询时段 ----------
    private String startTime;
    private String endTime;
}

package com.traffic.merchant.dto;

import lombok.Data;

/**
 * 商家看板响应数据
 */
@Data
public class DashboardResponse {

    /** 今日进店总人数 */
    private int totalEnter;

    /** 今日穿行人数（未进店） */
    private int totalPass;

    /** 当前在店人数（预估：最近10分钟进店 - 已离开） */
    private int currentInStore;

    // ---------- 性别分布 ----------
    private int genderMale;
    private int genderFemale;
    /** 女性占比（百分比，保留1位小数） */
    private double femaleRatio;

    // ---------- 年龄分布 ----------
    private int ageUnder18;
    private int age1860;
    private int ageOver60;

    // ---------- 停留时长 ----------
    /** 平均停留时长（秒） */
    private int avgStaySeconds;
    /** 有效停留人次 */
    private int stayCount;

    /** 数据来源：cache=缓存，db=数据库 */
    private String dataSource;
}

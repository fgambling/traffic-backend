package com.traffic.merchant.service;

import com.traffic.merchant.dto.DashboardResponse;

/**
 * 商家业务服务接口
 */
public interface MerchantService {

    /**
     * 获取今日看板数据
     * 优先读Redis缓存，缓存不存在则查MySQL聚合
     */
    DashboardResponse getDashboard(Integer merchantId);
}

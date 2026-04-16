package com.traffic.customer.service;

/**
 * 新老客识别服务接口
 */
public interface CustomerVisitService {

    /**
     * 新老客识别结果
     */
    record VisitStatus(boolean isNew, boolean isReturning) {}

    /**
     * 记录一次到访，返回该顾客的新老客状态
     *
     * @param merchantId 商家ID
     * @param personId   设备返回的行人ID
     * @return VisitStatus（isNew=true表示今日首次来/历史首次；isReturning=true表示老客）
     */
    VisitStatus recordVisit(Integer merchantId, String personId);
}

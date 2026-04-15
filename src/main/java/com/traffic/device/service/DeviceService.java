package com.traffic.device.service;

import com.traffic.device.dto.DeviceUploadRequest;

/**
 * 设备数据接入服务接口
 */
public interface DeviceService {

    /**
     * 处理设备上传的行人检测数据
     * 解析26维属性 → 按分钟聚合 → 写入MySQL → 更新Redis缓存
     */
    void processUpload(DeviceUploadRequest request);
}

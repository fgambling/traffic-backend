package com.traffic.device.controller;

import com.traffic.common.R;
import com.traffic.device.dto.DeviceUploadRequest;
import com.traffic.device.service.DeviceService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

/**
 * 设备数据接入接口
 * 不需要JWT验证（硬件设备直接调用）
 */
@Slf4j
@RestController
@RequestMapping("/api/device")
@RequiredArgsConstructor
public class DeviceController {

    private final DeviceService deviceService;

    /**
     * 接收摄像头上传的行人属性识别数据
     * POST /api/device/upload
     */
    @PostMapping("/upload")
    public R<Void> upload(@Valid @RequestBody DeviceUploadRequest request) {
        log.info("收到设备上传: systemId={}, bindId={}, records={}",
                request.getSystemId(), request.getBindId(),
                request.getPersonDetections().size());
        deviceService.processUpload(request);
        return R.ok();
    }
}

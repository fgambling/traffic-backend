package com.traffic.device.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import lombok.Data;

import java.util.List;

/**
 * 设备上传请求体
 */
@Data
public class DeviceUploadRequest {

    /** 系统ID，如 "EA-01234567" */
    @NotBlank(message = "systemId不能为空")
    private String systemId;

    /** 系统名称 */
    private String systemName;

    /** 设备绑定ID，用于关联商家 */
    @NotBlank(message = "bindId不能为空")
    private String bindId;

    /** 行人检测记录列表 */
    @NotEmpty(message = "personDetections不能为空")
    @Valid
    private List<PersonDetection> personDetections;
}

package com.traffic.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

/**
 * 微信登录请求
 */
@Data
public class WxLoginRequest {

    /** 微信授权code（wx.login()返回） */
    @NotBlank(message = "code不能为空")
    private String code;

    /** 登录角色：merchant=商家，salesman=业务员 */
    @NotBlank(message = "role不能为空")
    @Pattern(regexp = "merchant|salesman", message = "role只能是 merchant 或 salesman")
    private String role;
}

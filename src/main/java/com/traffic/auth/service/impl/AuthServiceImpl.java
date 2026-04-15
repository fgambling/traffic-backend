package com.traffic.auth.service.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.traffic.auth.dto.LoginResponse;
import com.traffic.auth.dto.WxLoginRequest;
import com.traffic.auth.service.AuthService;
import com.traffic.auth.util.JwtUtil;
import com.traffic.common.BusinessException;
import com.traffic.common.ErrorCode;
import com.traffic.merchant.entity.Merchant;
import com.traffic.merchant.mapper.MerchantMapper;
import com.traffic.salesman.entity.Salesman;
import com.traffic.salesman.mapper.SalesmanMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

/**
 * 认证服务实现
 * 通过微信code换取openid，再查库匹配用户
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AuthServiceImpl implements AuthService {

    private final MerchantMapper merchantMapper;
    private final SalesmanMapper salesmanMapper;
    private final JwtUtil jwtUtil;
    private final WebClient.Builder webClientBuilder;
    private final ObjectMapper objectMapper;

    @Value("${wx.app-id}")
    private String appId;

    @Value("${wx.app-secret}")
    private String appSecret;

    @Value("${wx.jscode2session-url}")
    private String jscode2sessionUrl;

    @Override
    public LoginResponse wxLogin(WxLoginRequest request) {
        // 1. 用code换取微信openid
        String openid = fetchOpenid(request.getCode());
        log.info("微信登录: role={}, openid={}", request.getRole(), openid);

        // 2. 根据角色查对应表
        String role = request.getRole();
        if ("merchant".equals(role)) {
            return loginAsMerchant(openid);
        } else {
            return loginAsSalesman(openid);
        }
    }

    /**
     * 调用微信 jscode2session 接口换取openid
     * 文档: https://developers.weixin.qq.com/miniprogram/dev/api-backend/open-api/login/auth.code2Session.html
     */
    private String fetchOpenid(String code) {
        try {
            String url = jscode2sessionUrl
                    + "?appid=" + appId
                    + "&secret=" + appSecret
                    + "&js_code=" + code
                    + "&grant_type=authorization_code";

            String responseBody = webClientBuilder.build()
                    .get()
                    .uri(url)
                    .retrieve()
                    .bodyToMono(String.class)
                    .block();

            log.debug("微信jscode2session响应: {}", responseBody);
            JsonNode json = objectMapper.readTree(responseBody);

            // 微信返回errcode非0时表示失败
            if (json.has("errcode") && json.get("errcode").asInt() != 0) {
                log.error("微信jscode2session失败: errcode={}, errmsg={}",
                        json.get("errcode"), json.get("errmsg"));
                throw new BusinessException(ErrorCode.WX_LOGIN_FAILED);
            }

            String openid = json.path("openid").asText();
            if (openid.isBlank()) {
                throw new BusinessException(ErrorCode.WX_LOGIN_FAILED);
            }
            return openid;

        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("调用微信API异常", e);
            throw new BusinessException(ErrorCode.WX_LOGIN_FAILED);
        }
    }

    /** 商家端登录 */
    private LoginResponse loginAsMerchant(String openid) {
        Merchant merchant = merchantMapper.findByOpenid(openid);
        if (merchant == null) {
            throw new BusinessException(ErrorCode.USER_NOT_REGISTERED);
        }
        String token = jwtUtil.generateToken(merchant.getId(), "merchant", merchant.getId());
        return new LoginResponse(token, "merchant", merchant.getId(), merchant.getId(), merchant.getName());
    }

    /** 业务员端登录 */
    private LoginResponse loginAsSalesman(String openid) {
        Salesman salesman = salesmanMapper.findByOpenid(openid);
        if (salesman == null) {
            throw new BusinessException(ErrorCode.USER_NOT_REGISTERED);
        }
        String token = jwtUtil.generateToken(salesman.getId(), "salesman", null);
        return new LoginResponse(token, "salesman", salesman.getId(), null, salesman.getName());
    }
}

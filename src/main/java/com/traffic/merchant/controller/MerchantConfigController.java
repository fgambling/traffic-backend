package com.traffic.merchant.controller;

import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.traffic.common.BusinessException;
import com.traffic.common.ErrorCode;
import com.traffic.common.R;
import com.traffic.merchant.dto.MerchantConfigRequest;
import com.traffic.merchant.entity.MerchantConfig;
import com.traffic.merchant.mapper.MerchantConfigMapper;
import com.traffic.security.JwtPrincipal;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

/**
 * 商家配置接口（菜单分类、促销、备货阈值等）
 */
@RestController
@RequestMapping("/api/merchant/config")
@RequiredArgsConstructor
public class MerchantConfigController {

    private final MerchantConfigMapper merchantConfigMapper;
    private final ObjectMapper objectMapper;

    /**
     * 查询指定配置项
     * GET /api/merchant/config?key=menu_categories
     */
    @GetMapping
    public R<Object> getConfig(@AuthenticationPrincipal JwtPrincipal principal,
                               @RequestParam String key) {
        assertMerchant(principal);
        MerchantConfig config = merchantConfigMapper.findByKey(principal.getMerchantId(), key);
        if (config == null) return R.ok(null);
        try {
            // 将存储的JSON字符串解析为对象再返回（避免双重转义）
            Object parsed = objectMapper.readValue(config.getConfigValue(), Object.class);
            return R.ok(parsed);
        } catch (Exception e) {
            return R.ok(config.getConfigValue());
        }
    }

    /**
     * 保存/更新配置项（UPSERT）
     * POST /api/merchant/config
     */
    @PostMapping
    public R<Void> saveConfig(@AuthenticationPrincipal JwtPrincipal principal,
                              @Valid @RequestBody MerchantConfigRequest req) {
        assertMerchant(principal);
        Integer merchantId = principal.getMerchantId();

        try {
            String json = objectMapper.writeValueAsString(req.getConfigValue());
            MerchantConfig existing = merchantConfigMapper.findByKey(merchantId, req.getConfigKey());
            if (existing == null) {
                merchantConfigMapper.insert(new MerchantConfig()
                        .setMerchantId(merchantId)
                        .setConfigKey(req.getConfigKey())
                        .setConfigValue(json));
            } else {
                merchantConfigMapper.update(null,
                        new LambdaUpdateWrapper<MerchantConfig>()
                                .eq(MerchantConfig::getId, existing.getId())
                                .set(MerchantConfig::getConfigValue, json));
            }
        } catch (Exception e) {
            throw new BusinessException(500, "保存配置失败: " + e.getMessage());
        }
        return R.ok();
    }

    private void assertMerchant(JwtPrincipal principal) {
        if (!"merchant".equals(principal.getRole())) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }
    }
}

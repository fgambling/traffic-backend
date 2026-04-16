package com.traffic.merchant.controller;

import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.traffic.common.BusinessException;
import com.traffic.common.ErrorCode;
import com.traffic.common.R;
import com.traffic.merchant.dto.CustomRuleRequest;
import com.traffic.merchant.dto.RuleConfigResponse;
import com.traffic.merchant.dto.RuleConfigResponse.BuiltinRuleDTO;
import com.traffic.merchant.dto.RuleConfigResponse.CustomRuleDTO;
import com.traffic.merchant.dto.ToggleRuleRequest;
import com.traffic.merchant.entity.Merchant;
import com.traffic.merchant.entity.MerchantConfig;
import com.traffic.merchant.mapper.MerchantConfigMapper;
import com.traffic.merchant.mapper.MerchantMapper;
import com.traffic.rule.BuiltinRule;
import com.traffic.security.JwtPrincipal;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.*;

/**
 * 商家规则配置接口（中级版及以上）
 */
@Slf4j
@RestController
@RequestMapping("/api/merchant/rules")
@RequiredArgsConstructor
public class MerchantRuleController {

    private final MerchantMapper merchantMapper;
    private final MerchantConfigMapper merchantConfigMapper;
    private final ObjectMapper objectMapper;

    /**
     * 获取内置规则列表（含启用状态）+ 自定义规则列表
     * GET /api/merchant/rules
     */
    @GetMapping
    public R<RuleConfigResponse> listRules(@AuthenticationPrincipal JwtPrincipal principal) {
        Integer merchantId = assertMerchantAndPackage(principal);

        // 内置规则启用状态
        Map<String, Boolean> enabledMap = loadBuiltinRuleConfig(merchantId);
        List<BuiltinRuleDTO> builtinRules = Arrays.stream(BuiltinRule.values())
                .map(r -> BuiltinRuleDTO.from(r, enabledMap.getOrDefault(r.getRuleId(), true)))
                .toList();

        // 自定义规则
        List<CustomRuleDTO> customRules = loadCustomRules(merchantId);

        RuleConfigResponse resp = new RuleConfigResponse();
        resp.setBuiltinRules(builtinRules);
        resp.setCustomRules(customRules);
        return R.ok(resp);
    }

    /**
     * 启用/禁用内置规则
     * POST /api/merchant/rules/builtin/toggle
     */
    @PostMapping("/builtin/toggle")
    public R<Void> toggleBuiltinRule(@AuthenticationPrincipal JwtPrincipal principal,
                                     @Valid @RequestBody ToggleRuleRequest req) {
        Integer merchantId = assertMerchantAndPackage(principal);

        // 校验 ruleId 是否合法
        BuiltinRule.findById(req.getRuleId())
                .orElseThrow(() -> new BusinessException(400, "无效的 ruleId: " + req.getRuleId()));

        Map<String, Boolean> enabledMap = loadBuiltinRuleConfig(merchantId);
        enabledMap.put(req.getRuleId(), req.isEnabled());
        saveConfig(merchantId, "builtin_rule_config", enabledMap);

        return R.ok();
    }

    /**
     * 新增一条自定义规则
     * POST /api/merchant/rules/custom
     */
    @PostMapping("/custom")
    public R<Void> addCustomRule(@AuthenticationPrincipal JwtPrincipal principal,
                                 @Valid @RequestBody CustomRuleRequest req) {
        Integer merchantId = assertMerchantAndPackage(principal);

        try {
            // 读取现有自定义规则列表
            MerchantConfig config = merchantConfigMapper.findByKey(merchantId, "custom_rules");
            ObjectNode root;
            if (config == null) {
                root = objectMapper.createObjectNode();
                root.putArray("rules");
            } else {
                root = (ObjectNode) objectMapper.readTree(config.getConfigValue());
            }

            // 检查 ruleId 是否重复
            ArrayNode rulesNode = (ArrayNode) root.get("rules");
            for (JsonNode existing : rulesNode) {
                if (req.getRuleId().equals(existing.path("ruleId").asText())) {
                    throw new BusinessException(400, "ruleId 已存在: " + req.getRuleId());
                }
            }

            // 追加新规则
            ObjectNode newRule = objectMapper.createObjectNode();
            newRule.put("ruleId",     req.getRuleId());
            newRule.put("name",       req.getName());
            newRule.put("metric",     req.getMetric());
            newRule.put("operator",   req.getOperator());
            newRule.put("threshold",  req.getThreshold());
            newRule.put("adviceType", req.getAdviceType());
            newRule.put("content",    req.getContent());
            rulesNode.add(newRule);

            saveConfig(merchantId, "custom_rules", root);
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            throw new BusinessException(500, "保存自定义规则失败: " + e.getMessage());
        }
        return R.ok();
    }

    /**
     * 删除指定自定义规则
     * DELETE /api/merchant/rules/custom/{ruleId}
     */
    @DeleteMapping("/custom/{ruleId}")
    public R<Void> deleteCustomRule(@AuthenticationPrincipal JwtPrincipal principal,
                                    @PathVariable String ruleId) {
        Integer merchantId = assertMerchantAndPackage(principal);

        try {
            MerchantConfig config = merchantConfigMapper.findByKey(merchantId, "custom_rules");
            if (config == null) return R.ok();

            ObjectNode root = (ObjectNode) objectMapper.readTree(config.getConfigValue());
            ArrayNode rulesNode = (ArrayNode) root.get("rules");
            ArrayNode filtered = objectMapper.createArrayNode();
            for (JsonNode node : rulesNode) {
                if (!ruleId.equals(node.path("ruleId").asText())) {
                    filtered.add(node);
                }
            }
            root.set("rules", filtered);
            saveConfig(merchantId, "custom_rules", root);
        } catch (Exception e) {
            throw new BusinessException(500, "删除自定义规则失败: " + e.getMessage());
        }
        return R.ok();
    }

    // -------- 私有工具方法 --------

    /** 校验JWT为商家角色且套餐>=2，返回merchantId */
    private Integer assertMerchantAndPackage(JwtPrincipal principal) {
        if (!"merchant".equals(principal.getRole())) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }
        Integer merchantId = principal.getMerchantId();
        Merchant merchant = merchantMapper.selectById(merchantId);
        if (merchant == null || merchant.getPackageType() < 2) {
            throw new BusinessException(403, "该功能仅限中级版及以上商家使用");
        }
        return merchantId;
    }

    /** 加载内置规则启用状态Map，默认全部true */
    private Map<String, Boolean> loadBuiltinRuleConfig(Integer merchantId) {
        Map<String, Boolean> map = new LinkedHashMap<>();
        for (BuiltinRule r : BuiltinRule.values()) map.put(r.getRuleId(), true);

        MerchantConfig config = merchantConfigMapper.findByKey(merchantId, "builtin_rule_config");
        if (config != null) {
            try {
                Map<String, Boolean> stored = objectMapper.readValue(
                        config.getConfigValue(), new TypeReference<>() {});
                map.putAll(stored);
            } catch (Exception ignored) {}
        }
        return map;
    }

    /** 加载自定义规则列表 */
    private List<CustomRuleDTO> loadCustomRules(Integer merchantId) {
        MerchantConfig config = merchantConfigMapper.findByKey(merchantId, "custom_rules");
        if (config == null) return Collections.emptyList();
        try {
            JsonNode root = objectMapper.readTree(config.getConfigValue());
            List<CustomRuleDTO> list = new ArrayList<>();
            for (JsonNode n : root.path("rules")) {
                CustomRuleDTO dto = new CustomRuleDTO();
                dto.setRuleId(n.path("ruleId").asText());
                dto.setName(n.path("name").asText());
                dto.setMetric(n.path("metric").asText());
                dto.setOperator(n.path("operator").asText());
                dto.setThreshold(n.path("threshold").asDouble());
                dto.setAdviceType(n.path("adviceType").asText("营销"));
                dto.setContent(n.path("content").asText());
                list.add(dto);
            }
            return list;
        } catch (Exception e) {
            return Collections.emptyList();
        }
    }

    /** UPSERT 一条 merchant_config 记录 */
    private void saveConfig(Integer merchantId, String key, Object value) {
        try {
            String json = objectMapper.writeValueAsString(value);
            MerchantConfig existing = merchantConfigMapper.findByKey(merchantId, key);
            if (existing == null) {
                MerchantConfig config = new MerchantConfig()
                        .setMerchantId(merchantId)
                        .setConfigKey(key)
                        .setConfigValue(json);
                merchantConfigMapper.insert(config);
            } else {
                merchantConfigMapper.update(null,
                        new LambdaUpdateWrapper<MerchantConfig>()
                                .eq(MerchantConfig::getId, existing.getId())
                                .set(MerchantConfig::getConfigValue, json));
            }
        } catch (Exception e) {
            throw new BusinessException(500, "保存配置失败: " + e.getMessage());
        }
    }
}

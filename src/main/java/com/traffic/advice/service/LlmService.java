package com.traffic.advice.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.traffic.admin.mapper.SystemConfigMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.Duration;
import java.util.List;
import java.util.Map;

/**
 * LLM 调用服务（OpenAI 兼容协议，支持 OpenAI / 文心一言 / 通义千问 / DeepSeek）
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class LlmService {

    private final SystemConfigMapper configMapper;
    private final ObjectMapper objectMapper;

    private static final Map<String, String> BASE_URLS = Map.of(
        "openai",   "https://api.openai.com/v1",
        "ernie",    "https://qianfan.baidubce.com/v2",
        "qwen",     "https://dashscope.aliyuncs.com/compatible-mode/v1",
        "deepseek", "https://api.deepseek.com/v1"
    );

    /**
     * 调用 LLM 并返回文本回复
     * @param prompt 完整 prompt（已含数据上下文）
     * @return 模型回复文本，失败返回 null
     */
    public String call(String prompt) {
        String provider = configMapper.getValue("ai.provider");
        String apiKey   = configMapper.getValue("ai.apiKey");
        String model    = configMapper.getValue("ai.model");

        if (!StringUtils.hasText(apiKey) || !StringUtils.hasText(model)) {
            log.warn("LLM 配置未完成（apiKey 或 model 为空），跳过 LLM 调用");
            return null;
        }
        final String p = StringUtils.hasText(provider) ? provider : "deepseek";

        String baseUrl = BASE_URLS.getOrDefault(p, BASE_URLS.get("deepseek"));

        try {
            Map<String, Object> body = Map.of(
                "model",    model,
                "messages", List.of(
                    Map.of("role", "system", "content",
                        "你是一位专业的零售门店运营顾问，请用简洁中文给出具体可执行的经营建议。"),
                    Map.of("role", "user", "content", prompt)
                ),
                "max_tokens",  800,
                "temperature", 0.7
            );

            String resp = WebClient.builder()
                    .baseUrl(baseUrl)
                    .defaultHeader("Authorization", "Bearer " + apiKey)
                    .defaultHeader("Content-Type", "application/json")
                    .build()
                    .post()
                    .uri("/chat/completions")
                    .bodyValue(body)
                    .retrieve()
                    .onStatus(status -> !status.is2xxSuccessful(), clientResp ->
                        clientResp.bodyToMono(String.class).map(errBody -> {
                            log.error("LLM HTTP {} provider={} model={} body={}",
                                    clientResp.statusCode().value(), p, model, errBody);
                            return new RuntimeException(clientResp.statusCode() + " " + errBody);
                        }))
                    .bodyToMono(String.class)
                    .timeout(Duration.ofSeconds(90))
                    .block();

            JsonNode root = objectMapper.readTree(resp);
            return root.path("choices").path(0).path("message").path("content").asText(null);

        } catch (Exception e) {
            log.error("LLM 调用失败 provider={} model={}: {}", provider, model, e.getMessage());
            return null;
        }
    }

    /** 测试连接：发送简单 ping 并返回延迟 */
    public Map<String, Object> testConnection(String provider, String model, String apiKey) {
        if (!StringUtils.hasText(provider)) provider = "deepseek";
        if (!StringUtils.hasText(model))    model    = "deepseek-chat";
        // 脱敏占位符或空值时回退到数据库中保存的真实 Key
        if (!StringUtils.hasText(apiKey) || apiKey.contains("****"))
            apiKey = configMapper.getValue("ai.apiKey");
        if (!StringUtils.hasText(apiKey))   return Map.of("ok", false, "error", "API Key 未配置");

        String baseUrl = BASE_URLS.getOrDefault(provider, BASE_URLS.get("deepseek"));
        long start = System.currentTimeMillis();
        try {
            Map<String, Object> body = Map.of(
                "model",    model,
                "messages", List.of(Map.of("role", "user", "content", "hi")),
                "max_tokens", 5
            );
            String resp = WebClient.builder()
                    .baseUrl(baseUrl)
                    .defaultHeader("Authorization", "Bearer " + apiKey)
                    .defaultHeader("Content-Type", "application/json")
                    .build()
                    .post().uri("/chat/completions")
                    .bodyValue(body)
                    .retrieve()
                    .onStatus(status -> !status.is2xxSuccessful(), clientResp ->
                        clientResp.bodyToMono(String.class).map(errBody ->
                            new RuntimeException(clientResp.statusCode() + ": " + errBody)))
                    .bodyToMono(String.class)
                    .timeout(Duration.ofSeconds(15))
                    .block();

            JsonNode root = objectMapper.readTree(resp);
            String reply = root.path("choices").path(0).path("message").path("content").asText("");
            return Map.of("ok", true, "latencyMs", System.currentTimeMillis() - start, "reply", reply);
        } catch (Exception e) {
            return Map.of("ok", false, "error", e.getMessage(), "latencyMs", System.currentTimeMillis() - start);
        }
    }
}

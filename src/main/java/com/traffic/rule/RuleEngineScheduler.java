package com.traffic.rule;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.traffic.advice.entity.AiAdvice;
import com.traffic.advice.mapper.AiAdviceMapper;
import com.traffic.merchant.entity.Merchant;
import com.traffic.merchant.mapper.MerchantMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 规则引擎定时调度器
 * 每15分钟扫描一次所有中级版及以上商家，执行规则评估并落库推送
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RuleEngineScheduler {

    private final MerchantMapper merchantMapper;
    private final AiAdviceMapper aiAdviceMapper;
    private final RuleEngine ruleEngine;
    private final RedisTemplate<String, Object> redisTemplate;

    /** Redis建议缓存 key 前缀 */
    private static final String REDIS_ADVICE_KEY = "advice:latest:";
    /** 缓存最新建议条数 */
    private static final int MAX_CACHED_ADVICE = 5;
    /** Redis TTL：2小时 */
    private static final Duration REDIS_TTL = Duration.ofHours(2);

    /**
     * 每15分钟执行一次
     * fixedRate=900000ms（15分钟），initialDelay=30000ms（启动30秒后首次执行）
     */
    @Scheduled(fixedRate = 900_000, initialDelay = 30_000)
    public void runRuleEngine() {
        log.info("规则引擎开始执行: {}", LocalDateTime.now());

        // 查询所有正常状态且为中级版及以上的商家
        List<Merchant> merchants = merchantMapper.selectList(
                new LambdaQueryWrapper<Merchant>()
                        .eq(Merchant::getStatus, 1)
                        .ge(Merchant::getPackageType, 2)
        );

        log.info("本次扫描商家数: {}", merchants.size());

        for (Merchant merchant : merchants) {
            try {
                processOneMerchant(merchant);
            } catch (Exception e) {
                // 单个商家失败不影响其他商家
                log.error("规则引擎处理商家{}异常: {}", merchant.getId(), e.getMessage(), e);
            }
        }

        log.info("规则引擎执行完毕: {}", LocalDateTime.now());
    }

    /** 处理单个商家 */
    private void processOneMerchant(Merchant merchant) {
        Integer merchantId = merchant.getId();

        // 1. 执行规则评估（内置 + 自定义，含去重）
        List<RuleResult> results = ruleEngine.evaluate(merchantId);
        if (results.isEmpty()) {
            log.debug("商家{}本轮无规则触发", merchantId);
            return;
        }

        log.info("商家{}触发规则数: {}", merchantId, results.size());

        // 2. 批量写入 ai_advice 表
        List<AiAdvice> adviceList = results.stream()
                .map(r -> new AiAdvice()
                        .setMerchantId(merchantId)
                        .setTriggerRuleId(r.getRuleId())
                        .setSource(1)                   // 1=规则引擎
                        .setAdviceType(r.getAdviceType())
                        .setContent(r.getContent())
                        .setDataSnapshot(r.getDataSnapshot())
                        .setFeedback(0))
                .toList();

        adviceList.forEach(aiAdviceMapper::insert);

        // 3. 更新 Redis 最新建议缓存
        updateRedisAdviceCache(merchantId);
    }

    /**
     * 更新 Redis 建议缓存
     * key: advice:latest:{merchantId}，存最新5条，TTL 2小时
     */
    private void updateRedisAdviceCache(Integer merchantId) {
        try {
            // 查最新5条建议
            com.baomidou.mybatisplus.extension.plugins.pagination.Page<AiAdvice> page =
                    new com.baomidou.mybatisplus.extension.plugins.pagination.Page<>(1, MAX_CACHED_ADVICE);
            var pageResult = aiAdviceMapper.pageByMerchant(page, merchantId, null, null);

            String key = REDIS_ADVICE_KEY + merchantId;
            redisTemplate.opsForValue().set(key, pageResult.getRecords(), REDIS_TTL);
            log.debug("Redis建议缓存已更新: key={}, size={}", key, pageResult.getRecords().size());
        } catch (Exception e) {
            log.error("更新Redis建议缓存失败, merchantId={}: {}", merchantId, e.getMessage());
        }
    }
}

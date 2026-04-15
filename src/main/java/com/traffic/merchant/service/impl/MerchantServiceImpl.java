package com.traffic.merchant.service.impl;

import com.traffic.device.entity.TrafficFact;
import com.traffic.device.mapper.TrafficFactMapper;
import com.traffic.device.service.impl.DeviceServiceImpl;
import com.traffic.merchant.dto.DashboardResponse;
import com.traffic.merchant.service.MerchantService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;

/**
 * 商家服务实现
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MerchantServiceImpl implements MerchantService {

    private final TrafficFactMapper trafficFactMapper;
    private final RedisTemplate<String, Object> redisTemplate;

    private static final String REDIS_KEY_PREFIX = "traffic:realtime:";

    @Override
    public DashboardResponse getDashboard(Integer merchantId) {
        String redisKey = REDIS_KEY_PREFIX + merchantId;

        // 1. 优先读Redis缓存
        Object cached = redisTemplate.opsForValue().get(redisKey);
        if (cached instanceof Map) {
            @SuppressWarnings("unchecked")
            Map<String, Object> summary = (Map<String, Object>) cached;
            log.debug("从Redis缓存读取看板数据: merchantId={}", merchantId);
            return buildFromCache(summary, "cache");
        }

        // 2. 缓存未命中，查MySQL今日数据聚合
        log.info("Redis缓存未命中，查MySQL: merchantId={}", merchantId);
        LocalDate today = LocalDate.now(ZoneId.of("Asia/Shanghai"));
        LocalDateTime startOfDay = today.atStartOfDay();
        LocalDateTime endOfDay = today.plusDays(1).atStartOfDay();

        List<TrafficFact> todayFacts = trafficFactMapper.findTodayByMerchant(
                merchantId, startOfDay, endOfDay);

        Map<String, Object> summary = DeviceServiceImpl.aggregateTodaySummary(todayFacts);
        return buildFromCache(summary, "db");
    }

    /** 将Map汇总数据转为DashboardResponse */
    private DashboardResponse buildFromCache(Map<String, Object> summary, String source) {
        DashboardResponse resp = new DashboardResponse();
        resp.setTotalEnter(toInt(summary.get("totalEnter")));
        resp.setTotalPass(toInt(summary.get("totalPass")));
        resp.setGenderMale(toInt(summary.get("genderMale")));
        resp.setGenderFemale(toInt(summary.get("genderFemale")));
        resp.setAgeUnder18(toInt(summary.get("ageUnder18")));
        resp.setAge1860(toInt(summary.get("age1860")));
        resp.setAgeOver60(toInt(summary.get("ageOver60")));
        resp.setAvgStaySeconds(toInt(summary.get("avgStaySeconds")));
        resp.setStayCount(toInt(summary.get("stayCount")));
        resp.setDataSource(source);

        // 女性占比
        int total = resp.getGenderMale() + resp.getGenderFemale();
        resp.setFemaleRatio(total > 0
                ? Math.round(resp.getGenderFemale() * 1000.0 / total) / 10.0
                : 0.0);

        // 当前在店预估：简单用总进店数的10%作为近似（实际可用最近10分钟数据计算）
        // 此处预留逻辑，实际项目可结合实时推流完善
        resp.setCurrentInStore((int) Math.max(0, resp.getTotalEnter() * 0.1));

        return resp;
    }

    private int toInt(Object val) {
        if (val == null) return 0;
        if (val instanceof Number) return ((Number) val).intValue();
        try { return Integer.parseInt(val.toString()); } catch (Exception e) { return 0; }
    }
}

package com.traffic.merchant.service.impl;

import com.traffic.device.entity.TrafficFact;
import com.traffic.device.mapper.TrafficFactMapper;
import com.traffic.device.service.impl.DeviceServiceImpl;
import com.traffic.merchant.dto.DashboardResponse;
import com.traffic.merchant.dto.ProfileResponse;
import com.traffic.merchant.dto.StayAnalysisResponse;
import com.traffic.merchant.dto.StayTrendPoint;
import com.traffic.merchant.dto.TrendPoint;
import com.traffic.merchant.entity.Merchant;
import com.traffic.merchant.mapper.MerchantMapper;
import com.traffic.merchant.service.MerchantService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
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
    private final MerchantMapper merchantMapper;
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
            DashboardResponse resp = buildFromCache(summary, "cache");
            Merchant merchant = merchantMapper.selectById(merchantId);
            if (merchant != null) {
                resp.setMerchantName(merchant.getName());
                resp.setPackageType(merchant.getPackageType() != null ? merchant.getPackageType() : 1);
            }
            resp.setCurrentInStore(calcCurrentInStore(merchantId, resp.getAvgStaySeconds()));
            fillDelta(resp, merchantId, LocalDate.now(ZoneId.of("Asia/Shanghai")));
            return resp;
        }

        // 2. 缓存未命中，查MySQL今日数据聚合
        log.info("Redis缓存未命中，查MySQL: merchantId={}", merchantId);
        LocalDate today = LocalDate.now(ZoneId.of("Asia/Shanghai"));
        LocalDateTime startOfDay = today.atStartOfDay();
        LocalDateTime endOfDay = today.plusDays(1).atStartOfDay();

        List<TrafficFact> todayFacts = trafficFactMapper.findTodayByMerchant(
                merchantId, startOfDay, endOfDay);

        Map<String, Object> summary = DeviceServiceImpl.aggregateTodaySummary(todayFacts);
        // 回写 Redis：TTL 设到当天午夜，避免每次缓存未命中都查 MySQL
        try {
            Duration ttl = Duration.between(
                LocalDateTime.now(ZoneId.of("Asia/Shanghai")),
                today.plusDays(1).atStartOfDay());
            if (!ttl.isNegative() && !ttl.isZero()) {
                redisTemplate.opsForValue().set(redisKey, summary, ttl);
            }
        } catch (Exception e) {
            log.warn("回写 Redis 缓存失败 merchantId={}: {}", merchantId, e.getMessage());
        }
        DashboardResponse resp = buildFromCache(summary, "db");
        Merchant merchant = merchantMapper.selectById(merchantId);
        if (merchant != null) {
            resp.setMerchantName(merchant.getName());
            resp.setPackageType(merchant.getPackageType() != null ? merchant.getPackageType() : 1);
        }
        resp.setCurrentInStore(calcCurrentInStore(merchantId, resp.getAvgStaySeconds()));
        fillDelta(resp, merchantId, today);
        return resp;
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

        return resp;
    }

    /**
     * 当前在店预估：统计最近 N 分钟进店人数（N 取今日平均停留时长，最少 30 分钟，最多 90 分钟）。
     * 非营业时间无新数据则自然返回 0。
     */
    private int calcCurrentInStore(Integer merchantId, int avgStaySeconds) {
        int windowMinutes = Math.min(90, Math.max(30, avgStaySeconds / 60));
        LocalDateTime now   = LocalDateTime.now(ZoneId.of("Asia/Shanghai"));
        LocalDateTime since = now.minusMinutes(windowMinutes);
        return trafficFactMapper.sumEnterCount(merchantId, since, now);
    }

    /** 计算较昨日进店增减百分比并写入 resp */
    private void fillDelta(DashboardResponse resp, Integer merchantId, LocalDate today) {
        int yesterday = trafficFactMapper.sumEnterCount(
                merchantId, today.minusDays(1).atStartOfDay(), today.atStartOfDay());
        resp.setYesterdayCount(yesterday);
        double delta = yesterday > 0
                ? Math.round((resp.getTotalEnter() - yesterday) * 1000.0 / yesterday) / 10.0
                : (resp.getTotalEnter() > 0 ? 100.0 : 0.0);
        resp.setDeltaPercent(delta);
    }

    @Override
    public List<TrendPoint> getTrend(Integer merchantId, String type,
                                     LocalDate start, LocalDate end,
                                     LocalDateTime startDt, LocalDateTime endDt) {
        // 校验并规范 type
        String granularity = switch (type == null ? "" : type.toLowerCase()) {
            case "day"    -> "day";
            case "week"   -> "week";
            case "month"  -> "month";
            case "minute" -> "minute";
            default       -> "hour";
        };

        // minute 粒度：必须使用精确 startDt/endDt（对应某一小时窗口）
        if ("minute".equals(granularity) && startDt != null && endDt != null) {
            return trafficFactMapper.queryTrend(merchantId, granularity, startDt, endDt);
        }

        LocalDate today = LocalDate.now(ZoneId.of("Asia/Shanghai"));

        // end 未传默认今天（含），转为明天凌晨作为排他上界
        LocalDate endDate  = (end  != null) ? end  : today;
        LocalDateTime endTime = endDate.plusDays(1).atStartOfDay();

        // start 未传时按粒度给默认回溯
        LocalDate startDate = (start != null) ? start : switch (granularity) {
            case "day"   -> today.minusDays(6);          // 近 7 天
            case "week"  -> today.minusWeeks(7);         // 近 8 周
            case "month" -> today.minusMonths(11);       // 近 12 个月
            default      -> today;                       // 今天
        };
        LocalDateTime startTime = startDate.atStartOfDay();

        return trafficFactMapper.queryTrend(merchantId, granularity, startTime, endTime);
    }

    @Override
    public ProfileResponse getProfile(Integer merchantId, LocalDate start, LocalDate end,
                                      LocalDateTime startDt, LocalDateTime endDt) {
        LocalDate today     = LocalDate.now(ZoneId.of("Asia/Shanghai"));
        LocalDate startDate = (start != null) ? start : today;
        LocalDate endDate   = (end   != null) ? end   : today;

        LocalDateTime startTime, endTime;
        if (startDt != null && endDt != null) {
            // 精确时间段（小时级点击下钻）
            startTime = startDt;
            endTime   = endDt;
            // 用 startDt 的日期回填 startDate/endDate 用于响应展示
            startDate = startDt.toLocalDate();
            endDate   = endDt.toLocalDate();
        } else {
            startTime = startDate.atStartOfDay();
            endTime   = endDate.plusDays(1).atStartOfDay();
        }

        ProfileResponse resp = trafficFactMapper.queryProfile(merchantId, startTime, endTime);
        if (resp == null) {
            resp = new ProfileResponse();
        }

        // 计算平均停留时长
        resp.setAvgStaySeconds(resp.getStayCount() > 0
                ? resp.getTotalStaySeconds() / resp.getStayCount()
                : 0);

        // 记录查询时段
        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("yyyy-MM-dd");
        resp.setStartTime(startDate.format(fmt));
        resp.setEndTime(endDate.format(fmt));

        return resp;
    }

    @Override
    public StayAnalysisResponse getStayAnalysis(Integer merchantId, String type,
                                                LocalDate start, LocalDate end,
                                                Integer hour,
                                                LocalDateTime startDt, LocalDateTime endDt) {
        String granularity = switch (type == null ? "" : type.toLowerCase()) {
            case "week"  -> "week";
            case "hour"  -> "hour";
            default      -> "day";
        };

        LocalDateTime startTime, endTime;
        if (hour != null) {
            // 小时 Tab 折线点击：用 5 分钟粒度展开该小时内趋势（共 12 个点）
            granularity = "minute";
            LocalDate date = (start != null) ? start : LocalDate.now(ZoneId.of("Asia/Shanghai"));
            startTime = date.atTime(hour, 0, 0);
            endTime   = date.atTime(hour + 1, 0, 0);
        } else if (startDt != null && endDt != null) {
            // 精确时段下钻（兼容旧调用）
            granularity = "hour";
            startTime = startDt;
            endTime   = endDt;
        } else {
            LocalDate today = LocalDate.now(ZoneId.of("Asia/Shanghai"));
            LocalDate endDate   = (end   != null) ? end   : today;
            LocalDate startDate = (start != null) ? start : switch (granularity) {
                case "week"  -> today.minusWeeks(7);
                case "hour"  -> today;
                default      -> today.minusDays(6);
            };
            startTime = startDate.atStartOfDay();
            endTime   = endDate.plusDays(1).atStartOfDay();
        }

        // 查当前时段趋势（若表结构未升级，SQL 会报错，这里捕获以便前端仍能显示空壳）
        List<StayTrendPoint> trend;
        try {
            trend = trafficFactMapper.queryStayTrend(merchantId, granularity, startTime, endTime);
        } catch (Exception e) {
            log.warn("queryStayTrend 失败（可能 traffic_fact 未升级到 v3）: {}", e.getMessage());
            trend = java.util.Collections.emptyList();
        }

        // 汇总当前时段
        int totalStay = 0, stayCount = 0, under5 = 0, mid = 0, over15 = 0;
        for (StayTrendPoint p : trend) {
            stayCount += p.getStayCount();
            under5    += p.getUnder5Count();
            mid       += p.getMid5to15Count();
            over15    += p.getOver15Count();
            totalStay += (int)(p.getAvgStaySeconds() * p.getStayCount());
        }
        int avgStay = stayCount > 0 ? totalStay / stayCount : 0;

        // 查前一期（等长时段）用于对比
        long rangeSecs = java.time.Duration.between(startTime, endTime).getSeconds();
        LocalDateTime prevEnd   = startTime;
        LocalDateTime prevStart = startTime.minusSeconds(rangeSecs);
        Double prevAvgRaw = trafficFactMapper.queryAvgStaySeconds(merchantId, prevStart, prevEnd);
        int prevAvg = prevAvgRaw != null ? prevAvgRaw.intValue() : 0;

        StayAnalysisResponse resp = new StayAnalysisResponse();
        resp.setAvgStaySeconds(avgStay);
        resp.setPrevAvgStaySeconds(prevAvg);
        resp.setStayCount(stayCount);
        resp.setUnder5Count(under5);
        resp.setMid5to15Count(mid);
        resp.setOver15Count(over15);
        resp.setTrend(trend);
        return resp;
    }

    private int toInt(Object val) {
        if (val == null) return 0;
        if (val instanceof Number) return ((Number) val).intValue();
        try { return Integer.parseInt(val.toString()); } catch (Exception e) { return 0; }
    }
}

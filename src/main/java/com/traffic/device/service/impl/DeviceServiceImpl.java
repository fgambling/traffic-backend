package com.traffic.device.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.traffic.common.BusinessException;
import com.traffic.common.ErrorCode;
import com.traffic.device.dto.DeviceUploadRequest;
import com.traffic.device.dto.PersonDetection;
import com.traffic.device.entity.TrafficFact;
import com.traffic.device.mapper.TrafficFactMapper;
import com.traffic.device.service.AttributeParser;
import com.traffic.device.service.DeviceService;
import com.traffic.merchant.entity.Merchant;
import com.traffic.merchant.mapper.MerchantMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.*;

/**
 * 设备数据接入服务实现
 *
 * 核心流程：
 *  1. 通过bindId查商家
 *  2. 遍历personDetections，解析26维属性，按 (merchantId, deviceId, 分钟桶) 聚合到Map
 *  3. 对每个聚合桶做 INSERT ... ON DUPLICATE KEY UPDATE（MySQL UPSERT）
 *  4. 更新Redis今日累计缓存
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DeviceServiceImpl implements DeviceService {

    private final MerchantMapper merchantMapper;
    private final TrafficFactMapper trafficFactMapper;
    private final AttributeParser attributeParser;
    private final RedisTemplate<String, Object> redisTemplate;

    /** Redis key前缀 */
    private static final String REDIS_KEY_PREFIX = "traffic:realtime:";

    /** Redis缓存TTL：25小时，保证跨零点仍可读到昨日数据 */
    private static final Duration REDIS_TTL = Duration.ofHours(25);

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void processUpload(DeviceUploadRequest request) {
        String bindId = request.getBindId();

        // 1. 根据bindId查找商家
        Merchant merchant = merchantMapper.findByBindId(bindId);
        if (merchant == null) {
            log.warn("找不到bindId对应的商家: {}", bindId);
            throw new BusinessException(ErrorCode.DEVICE_BIND_NOT_FOUND);
        }
        Integer merchantId = merchant.getId();
        log.info("设备上传处理: systemId={}, bindId={}, merchantId={}, records={}",
                request.getSystemId(), bindId, merchantId, request.getPersonDetections().size());

        // 2. 遍历行人检测记录，按 (deviceId, 分钟桶) 聚合
        // key格式: "deviceId|yyyy-MM-ddTHH:mm"
        Map<String, TrafficFact> bucketMap = new LinkedHashMap<>();

        for (PersonDetection detection : request.getPersonDetections()) {
            if (detection.getAttributes() == null || detection.getAttributes().size() != 26) {
                log.warn("personId={} 的attributes长度不为26，跳过", detection.getPersonId());
                throw new BusinessException(ErrorCode.INVALID_ATTRIBUTES_LENGTH);
            }

            // 使用entryTime计算所在分钟桶（UTC转北京时间）
            LocalDateTime entryLdt = detection.getEntryTime() != null
                    ? LocalDateTime.ofInstant(detection.getEntryTime(), ZoneId.of("Asia/Shanghai"))
                    : LocalDateTime.now(ZoneId.of("Asia/Shanghai"));

            LocalDateTime timeBucket = entryLdt.withSecond(0).withNano(0);
            String bucketKey = detection.getDeviceId() + "|" + timeBucket;

            // 获取或初始化聚合桶
            TrafficFact fact = bucketMap.computeIfAbsent(bucketKey, k -> {
                TrafficFact f = new TrafficFact();
                f.setMerchantId(merchantId);
                f.setDeviceId(detection.getDeviceId());
                f.setTimeBucket(timeBucket);
                // 初始化所有计数字段为0
                initZero(f);
                return f;
            });

            // 3. 解析26维属性并累加到聚合桶
            attributeParser.parseAndAccumulate(detection, fact);
        }

        // 4. 将聚合结果写入MySQL（UPSERT: 存在则累加，不存在则插入）
        for (TrafficFact fact : bucketMap.values()) {
            upsertTrafficFact(fact);
        }

        // 5. 更新Redis今日累计缓存
        updateRedisCache(merchantId);
    }

    /**
     * MySQL UPSERT：
     * 若 (merchant_id, device_id, time_bucket) 不存在则INSERT，
     * 若存在则对所有计数字段做累加（UPDATE col = col + delta）
     */
    private void upsertTrafficFact(TrafficFact fact) {
        // 查找是否已存在同一聚合键的记录
        TrafficFact existing = trafficFactMapper.selectOne(
                new LambdaQueryWrapper<TrafficFact>()
                        .eq(TrafficFact::getMerchantId, fact.getMerchantId())
                        .eq(TrafficFact::getDeviceId, fact.getDeviceId())
                        .eq(TrafficFact::getTimeBucket, fact.getTimeBucket())
        );

        if (existing == null) {
            // 不存在，直接插入
            trafficFactMapper.insert(fact);
        } else {
            // 存在，累加所有字段
            trafficFactMapper.update(null,
                new LambdaUpdateWrapper<TrafficFact>()
                    .eq(TrafficFact::getId, existing.getId())
                    .setSql("enter_count = enter_count + " + fact.getEnterCount())
                    .setSql("pass_count = pass_count + " + fact.getPassCount())
                    .setSql("gender_male = gender_male + " + fact.getGenderMale())
                    .setSql("gender_female = gender_female + " + fact.getGenderFemale())
                    .setSql("age_under18 = age_under18 + " + fact.getAgeUnder18())
                    .setSql("age_18_60 = age_18_60 + " + fact.getAge1860())
                    .setSql("age_over60 = age_over60 + " + fact.getAgeOver60())
                    .setSql("accessory_glasses = accessory_glasses + " + fact.getAccessoryGlasses())
                    .setSql("accessory_hat = accessory_hat + " + fact.getAccessoryHat())
                    .setSql("accessory_boots = accessory_boots + " + fact.getAccessoryBoots())
                    .setSql("bag_handbag = bag_handbag + " + fact.getBagHandbag())
                    .setSql("bag_shoulder = bag_shoulder + " + fact.getBagShoulder())
                    .setSql("bag_backpack = bag_backpack + " + fact.getBagBackpack())
                    .setSql("hold_item = hold_item + " + fact.getHoldItem())
                    .setSql("upper_short = upper_short + " + fact.getUpperShort())
                    .setSql("upper_long = upper_long + " + fact.getUpperLong())
                    .setSql("upper_coat = upper_coat + " + fact.getUpperCoat())
                    .setSql("upper_style_stripe = upper_style_stripe + " + fact.getUpperStyleStripe())
                    .setSql("upper_style_logo = upper_style_logo + " + fact.getUpperStyleLogo())
                    .setSql("upper_style_plaid = upper_style_plaid + " + fact.getUpperStylePlaid())
                    .setSql("upper_style_splice = upper_style_splice + " + fact.getUpperStyleSplice())
                    .setSql("lower_trousers = lower_trousers + " + fact.getLowerTrousers())
                    .setSql("lower_shorts = lower_shorts + " + fact.getLowerShorts())
                    .setSql("lower_skirt = lower_skirt + " + fact.getLowerSkirt())
                    .setSql("lower_style_stripe = lower_style_stripe + " + fact.getLowerStyleStripe())
                    .setSql("lower_style_pattern = lower_style_pattern + " + fact.getLowerStylePattern())
                    .setSql("total_stay_seconds = total_stay_seconds + " + fact.getTotalStaySeconds())
                    .setSql("stay_count = stay_count + " + fact.getStayCount())
            );
        }
    }

    /**
     * 将今日全量聚合数据写入Redis缓存
     * key: traffic:realtime:{merchantId}
     * TTL: 25小时
     */
    private void updateRedisCache(Integer merchantId) {
        try {
            LocalDate today = LocalDate.now(ZoneId.of("Asia/Shanghai"));
            LocalDateTime startOfDay = today.atStartOfDay();
            LocalDateTime endOfDay = today.plusDays(1).atStartOfDay();

            List<TrafficFact> todayData = trafficFactMapper.findTodayByMerchant(
                    merchantId, startOfDay, endOfDay);

            // 汇总今日累计数据
            Map<String, Object> summary = aggregateTodaySummary(todayData);
            String redisKey = REDIS_KEY_PREFIX + merchantId;
            redisTemplate.opsForValue().set(redisKey, summary, REDIS_TTL);
            log.debug("Redis缓存已更新: key={}", redisKey);

        } catch (Exception e) {
            // Redis缓存更新失败不影响主流程
            log.error("更新Redis缓存失败，merchantId={}", merchantId, e);
        }
    }

    /**
     * 将多条分钟桶数据汇总为今日累计Map
     */
    public static Map<String, Object> aggregateTodaySummary(List<TrafficFact> facts) {
        Map<String, Object> summary = new LinkedHashMap<>();
        int totalEnter = 0, totalPass = 0;
        int genderMale = 0, genderFemale = 0;
        int ageUnder18 = 0, age1860 = 0, ageOver60 = 0;
        int totalStaySeconds = 0, stayCount = 0;

        for (TrafficFact f : facts) {
            totalEnter += f.getEnterCount();
            totalPass += f.getPassCount();
            genderMale += f.getGenderMale();
            genderFemale += f.getGenderFemale();
            ageUnder18 += f.getAgeUnder18();
            age1860 += f.getAge1860();
            ageOver60 += f.getAgeOver60();
            totalStaySeconds += f.getTotalStaySeconds();
            stayCount += f.getStayCount();
        }

        summary.put("totalEnter", totalEnter);
        summary.put("totalPass", totalPass);
        summary.put("genderMale", genderMale);
        summary.put("genderFemale", genderFemale);
        summary.put("ageUnder18", ageUnder18);
        summary.put("age1860", age1860);
        summary.put("ageOver60", ageOver60);
        summary.put("avgStaySeconds", stayCount > 0 ? totalStaySeconds / stayCount : 0);
        summary.put("stayCount", stayCount);
        return summary;
    }

    /** 将TrafficFact所有计数字段初始化为0 */
    private void initZero(TrafficFact f) {
        f.setEnterCount(0); f.setPassCount(0);
        f.setGenderMale(0); f.setGenderFemale(0);
        f.setAgeUnder18(0); f.setAge1860(0); f.setAgeOver60(0);
        f.setAccessoryGlasses(0); f.setAccessoryHat(0); f.setAccessoryBoots(0);
        f.setBagHandbag(0); f.setBagShoulder(0); f.setBagBackpack(0); f.setHoldItem(0);
        f.setUpperShort(0); f.setUpperLong(0); f.setUpperCoat(0);
        f.setUpperStyleStripe(0); f.setUpperStyleLogo(0);
        f.setUpperStylePlaid(0); f.setUpperStyleSplice(0);
        f.setLowerTrousers(0); f.setLowerShorts(0); f.setLowerSkirt(0);
        f.setLowerStyleStripe(0); f.setLowerStylePattern(0);
        f.setTotalStaySeconds(0); f.setStayCount(0);
    }
}

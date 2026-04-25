package com.traffic.device.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.traffic.common.BusinessException;
import com.traffic.common.ErrorCode;
import com.traffic.customer.service.CustomerVisitService;
import com.traffic.customer.service.CustomerVisitService.VisitStatus;
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
 *  2. 遍历personDetections：
 *     a. 解析26维属性，按 (merchantId, deviceId, 分钟桶) 聚合到Map
 *     b. 非穿行人员：调用 CustomerVisitService 记录新老客状态，累加到聚合桶
 *  3. 对每个聚合桶做 UPSERT（SELECT → INSERT or UPDATE）
 *  4. 更新Redis今日累计缓存
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DeviceServiceImpl implements DeviceService {

    private final MerchantMapper merchantMapper;
    private final TrafficFactMapper trafficFactMapper;
    private final AttributeParser attributeParser;
    private final CustomerVisitService customerVisitService;
    private final RedisTemplate<String, Object> redisTemplate;

    private static final String REDIS_KEY_PREFIX = "traffic:realtime:";
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
        Map<String, TrafficFact> bucketMap = new LinkedHashMap<>();

        for (PersonDetection detection : request.getPersonDetections()) {
            if (detection.getAttributes() == null || detection.getAttributes().size() != 26) {
                throw new BusinessException(ErrorCode.INVALID_ATTRIBUTES_LENGTH);
            }

            // 计算分钟级时间桶（UTC → 北京时间，秒位清零）
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
                initZero(f);
                return f;
            });

            // 解析26维属性并累加到聚合桶
            attributeParser.parseAndAccumulate(detection, fact);

            // 非穿行：记录新老客状态
            if (!detection.isPassThrough() && detection.getPersonId() != null) {
                try {
                    VisitStatus status = customerVisitService.recordVisit(
                            merchantId, detection.getPersonId());
                    if (status.isNew()) {
                        fact.setNewCustomerCount(fact.getNewCustomerCount() + 1);
                    }
                    if (status.isReturning()) {
                        fact.setReturningCustomerCount(fact.getReturningCustomerCount() + 1);
                    }
                } catch (Exception e) {
                    // 新老客记录失败不影响主流程
                    log.warn("新老客记录失败: personId={}, err={}", detection.getPersonId(), e.getMessage());
                }
            }
        }

        // 3. 将聚合结果 UPSERT 到 MySQL
        for (TrafficFact fact : bucketMap.values()) {
            upsertTrafficFact(fact);
        }

        // 4. 更新Redis今日累计缓存
        updateRedisCache(merchantId);
    }

    /**
     * UPSERT：不存在则INSERT，存在则对所有字段累加
     */
    private void upsertTrafficFact(TrafficFact fact) {
        TrafficFact existing = trafficFactMapper.selectOne(
                new LambdaQueryWrapper<TrafficFact>()
                        .eq(TrafficFact::getMerchantId, fact.getMerchantId())
                        .eq(TrafficFact::getDeviceId, fact.getDeviceId())
                        .eq(TrafficFact::getTimeBucket, fact.getTimeBucket())
        );

        if (existing == null) {
            trafficFactMapper.insert(fact);
        } else {
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
                    .setSql("stay_under5min = stay_under5min + " + fact.getStayUnder5min())
                    .setSql("stay_5to15min = stay_5to15min + " + fact.getStay5To15min())
                    .setSql("stay_over15min = stay_over15min + " + fact.getStayOver15min())
                    .setSql("new_customer_count = new_customer_count + " + fact.getNewCustomerCount())
                    .setSql("returning_customer_count = returning_customer_count + " + fact.getReturningCustomerCount())
            );
        }
    }

    /** 将今日数据汇总写入Redis（供看板接口使用） */
    private void updateRedisCache(Integer merchantId) {
        try {
            LocalDate today = LocalDate.now(ZoneId.of("Asia/Shanghai"));
            List<TrafficFact> todayData = trafficFactMapper.findTodayByMerchant(
                    merchantId, today.atStartOfDay(), today.plusDays(1).atStartOfDay());
            Map<String, Object> summary = aggregateTodaySummary(todayData);
            redisTemplate.opsForValue().set(REDIS_KEY_PREFIX + merchantId, summary, REDIS_TTL);
        } catch (Exception e) {
            log.error("更新Redis缓存失败，merchantId={}", merchantId, e);
        }
    }

    /** 将多条分钟桶数据汇总为今日累计 Map（供看板使用） */
    public static Map<String, Object> aggregateTodaySummary(List<TrafficFact> facts) {
        int totalEnter = 0, totalPass = 0, genderMale = 0, genderFemale = 0;
        int ageUnder18 = 0, age1860 = 0, ageOver60 = 0;
        int totalStaySeconds = 0, stayCount = 0;
        int newCustomer = 0, returningCustomer = 0;

        for (TrafficFact f : facts) {
            totalEnter        += f.getEnterCount();
            totalPass         += f.getPassCount();
            genderMale        += f.getGenderMale();
            genderFemale      += f.getGenderFemale();
            ageUnder18        += f.getAgeUnder18();
            age1860           += f.getAge1860();
            ageOver60         += f.getAgeOver60();
            totalStaySeconds  += f.getTotalStaySeconds();
            stayCount         += f.getStayCount();
            if (f.getNewCustomerCount() != null)       newCustomer       += f.getNewCustomerCount();
            if (f.getReturningCustomerCount() != null) returningCustomer += f.getReturningCustomerCount();
        }

        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("totalEnter", totalEnter);
        summary.put("totalPass", totalPass);
        summary.put("genderMale", genderMale);
        summary.put("genderFemale", genderFemale);
        summary.put("ageUnder18", ageUnder18);
        summary.put("age1860", age1860);
        summary.put("ageOver60", ageOver60);
        summary.put("avgStaySeconds", stayCount > 0 ? totalStaySeconds / stayCount : 0);
        summary.put("stayCount", stayCount);
        summary.put("newCustomerCount", newCustomer);
        summary.put("returningCustomerCount", returningCustomer);
        return summary;
    }

    /** 初始化 TrafficFact 所有计数字段为 0 */
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
        f.setStayUnder5min(0); f.setStay5To15min(0); f.setStayOver15min(0);
        f.setNewCustomerCount(0); f.setReturningCustomerCount(0);
    }
}

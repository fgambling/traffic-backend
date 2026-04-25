package com.traffic.advice.service.impl;

import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.traffic.advice.dto.AdvicePageResult;
import com.traffic.advice.entity.AiAdvice;
import com.traffic.advice.mapper.AiAdviceMapper;
import com.traffic.advice.service.AdviceService;
import com.traffic.device.entity.TrafficFact;
import com.traffic.device.mapper.TrafficFactMapper;
import com.traffic.device.service.impl.DeviceServiceImpl;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.*;

/**
 * 建议服务实现（规则引擎版）
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AdviceServiceImpl implements AdviceService {

  private final AiAdviceMapper aiAdviceMapper;
  private final TrafficFactMapper trafficFactMapper;
  private final ObjectMapper objectMapper;

  @Override
  public AdvicePageResult listAdvice(Integer merchantId, String adviceType, int page, int size) {
    Page<AiAdvice> pageObj = new Page<>(page, size);
    var result = aiAdviceMapper.pageByMerchant(pageObj, merchantId, adviceType, null);
    return new AdvicePageResult(result.getRecords(), result.getTotal());
  }

  @Override
  public void generateAdvice(Integer merchantId) {
    LocalDate today = LocalDate.now(ZoneId.of("Asia/Shanghai"));
    LocalDateTime start = today.atStartOfDay();
    LocalDateTime end = today.plusDays(1).atStartOfDay();

    List<TrafficFact> facts = trafficFactMapper.findTodayByMerchant(merchantId, start, end);
    Map<String, Object> summary = DeviceServiceImpl.aggregateTodaySummary(facts);

    int totalEnter = toInt(summary.get("totalEnter"));
    int avgStay = toInt(summary.get("avgStaySeconds"));

    // 按小时统计，找峰值
    Map<Integer, Integer> hourlyMap = new TreeMap<>();
    for (TrafficFact f : facts) {
      if (f.getTimeBucket() == null)
        continue;
      int h = f.getTimeBucket().getHour();
      hourlyMap.merge(h, f.getEnterCount(), Integer::sum);
    }

    int peakHour = -1;
    int peakCount = 0;
    for (var e : hourlyMap.entrySet()) {
      if (e.getValue() > peakCount) {
        peakCount = e.getValue();
        peakHour = e.getKey();
      }
    }

    // 规则判断：按优先级选择建议类型
    String ruleId;
    String adviceType;
    String content;

    if (totalEnter == 0) {
      ruleId = "R001";
      adviceType = "营销";
      content = "今日尚无客流记录，建议通过线上发券、朋友圈推广等方式引流，提升到店率。";
    } else if (peakHour >= 0 && peakCount > 10 && (peakHour < 10 || peakHour > 16)) {
      // 峰值在非常规时段（早10点前或晚4点后）
      ruleId = "R002";
      adviceType = "排班";
      content = String.format(
          "今日客流峰值出现在 %d:00 前后（共 %d 人），建议在该时段增派1-2名服务人员，提升客户体验。",
          peakHour, peakCount);
    } else if (avgStay > 0 && avgStay < 60) {
      // 停留时间过短（< 1分钟），购买转化低
      ruleId = "R003";
      adviceType = "营销";
      content = String.format(
          "今日客均停留仅 %d 秒，转化率偏低。建议在入口处设置引流展示或推出限时特惠，延长顾客停留时间。",
          avgStay);
    } else if (totalEnter >= 50) {
      // 高客流 → 备货提醒
      ruleId = "R004";
      adviceType = "备货";
      content = String.format(
          "今日进店人数已达 %d 人，高于平日水平。建议提前核查热销商品库存，避免断货影响销售。",
          totalEnter);
    } else {
      // 默认：给出综合建议
      ruleId = "R005";
      adviceType = "营销";
      content = String.format(
          "今日共有 %d 人进店，整体客流平稳。可考虑推出会员积分活动或满减优惠，刺激复购。",
          totalEnter);
    }

    // 防重：同一规则当天只生成一次
    int recent = aiAdviceMapper.countRecentByRule(merchantId, ruleId, start);
    if (recent > 0) {
      log.info("规则 {} 今日已生成，跳过: merchantId={}", ruleId, merchantId);
      return;
    }

    // 构建数据快照
    String snapshot = buildSnapshot(summary, peakHour, peakCount);

    AiAdvice advice = new AiAdvice()
        .setMerchantId(merchantId)
        .setTriggerRuleId(ruleId)
        .setSource(1)
        .setAdviceType(adviceType)
        .setContent(content)
        .setDataSnapshot(snapshot)
        .setFeedback(0);

    aiAdviceMapper.insert(advice);
    log.info("已生成建议 ruleId={} merchantId={} type={}", ruleId, merchantId, adviceType);
  }

  @Override
  public void saveFeedback(Long id, Integer feedback) {
    aiAdviceMapper.update(null,
        new LambdaUpdateWrapper<AiAdvice>()
            .eq(AiAdvice::getId, id)
            .set(AiAdvice::getFeedback, feedback));
  }

  private String buildSnapshot(Map<String, Object> summary, int peakHour, int peakCount) {
    try {
      Map<String, Object> snap = new LinkedHashMap<>();
      snap.put("totalEnter", summary.get("totalEnter"));
      snap.put("avgStaySeconds", summary.get("avgStaySeconds"));
      snap.put("genderMale", summary.get("genderMale"));
      snap.put("genderFemale", summary.get("genderFemale"));
      if (peakHour >= 0) {
        snap.put("peakHour", peakHour + ":00");
        snap.put("peakCount", peakCount);
      }
      return objectMapper.writeValueAsString(snap);
    } catch (Exception e) {
      return "{}";
    }
  }

  private int toInt(Object val) {
    if (val == null)
      return 0;
    if (val instanceof Number)
      return ((Number) val).intValue();
    try {
      return Integer.parseInt(val.toString());
    } catch (Exception e) {
      return 0;
    }
  }
}

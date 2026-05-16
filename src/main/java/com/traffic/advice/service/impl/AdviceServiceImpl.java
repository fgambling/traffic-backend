package com.traffic.advice.service.impl;

import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.traffic.advice.dto.AdvicePageResult;
import com.traffic.advice.entity.AiAdvice;
import com.traffic.advice.mapper.AiAdviceMapper;
import com.traffic.advice.service.AdviceService;
import com.traffic.advice.service.LlmService;
import com.traffic.device.entity.TrafficFact;
import com.traffic.device.mapper.TrafficFactMapper;
import com.traffic.device.service.impl.DeviceServiceImpl;
import com.traffic.merchant.entity.Merchant;
import com.traffic.merchant.entity.MerchantBusinessInfo;
import com.traffic.merchant.mapper.MerchantBusinessInfoMapper;
import com.traffic.admin.mapper.SystemConfigMapper;
import com.traffic.merchant.mapper.MerchantMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.*;

/**
 * 建议服务实现（规则引擎 + LLM 高级版）
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AdviceServiceImpl implements AdviceService {

  private static final String DEFAULT_PROMPT_TEMPLATE = com.traffic.advice.util.LlmConstants.DEFAULT_PROMPT_TEMPLATE;

  private final AiAdviceMapper aiAdviceMapper;
  private final TrafficFactMapper trafficFactMapper;
  private final MerchantMapper merchantMapper;
  private final MerchantBusinessInfoMapper businessInfoMapper;
  private final LlmService llmService;
  private final ObjectMapper objectMapper;
  private final SystemConfigMapper systemConfigMapper;

  @Override
  public AdvicePageResult listAdvice(Integer merchantId, String adviceType, Integer source, int page, int size) {
    Page<AiAdvice> pageObj = new Page<>(page, size);
    var result = aiAdviceMapper.pageByMerchant(pageObj, merchantId, adviceType, source);
    return new AdvicePageResult(result.getRecords(), result.getTotal());
  }

  @Override
  public void generateAdvice(Integer merchantId, String mode) {
    ZoneId tz = ZoneId.of("Asia/Shanghai");
    LocalDateTime start, end;

    if ("lastHour".equals(mode)) {
      end   = LocalDateTime.now(tz);
      start = end.minusHours(1);
    } else {
      LocalDate today = LocalDate.now(tz);
      start = today.atStartOfDay();
      end   = today.plusDays(1).atStartOfDay();
    }

    List<TrafficFact> facts     = trafficFactMapper.findTodayByMerchant(merchantId, start, end);
    Map<String, Object> summary = DeviceServiceImpl.aggregateTodaySummary(facts);

    int totalEnter = toInt(summary.get("totalEnter"));
    int avgStay    = toInt(summary.get("avgStaySeconds"));
    int male       = toInt(summary.get("genderMale"));
    int female     = toInt(summary.get("genderFemale"));
    double femaleRatio = (male + female) > 0 ? female * 100.0 / (male + female) : 0;

    Map<Integer, Integer> hourlyMap = new TreeMap<>();
    for (TrafficFact f : facts) {
      if (f.getTimeBucket() != null)
        hourlyMap.merge(f.getTimeBucket().getHour(), f.getEnterCount(), Integer::sum);
    }
    int peakHour = -1, peakCount = 0;
    for (var e : hourlyMap.entrySet()) {
      if (e.getValue() > peakCount) { peakCount = e.getValue(); peakHour = e.getKey(); }
    }

    String snapshot = buildSnapshot(summary, peakHour, peakCount);

    Merchant merchant = merchantMapper.selectById(merchantId);
    if (merchant != null) {
      // 套餐到期检查
      if (merchant.getPackageExpireAt() != null &&
          merchant.getPackageExpireAt().isBefore(java.time.LocalDate.now(ZoneId.of("Asia/Shanghai")))) {
        throw new com.traffic.common.BusinessException(403, "套餐已到期，请联系管理员续费");
      }
      if (merchant.getPackageType() != null && merchant.getPackageType() >= 3) {
        generateByLlm(merchantId, merchant.getName(), totalEnter, avgStay, femaleRatio, peakHour, peakCount, snapshot);
        return;
      }
    }

    generateByRule(merchantId, totalEnter, avgStay, peakHour, peakCount, start, snapshot);
  }

  /** 规则引擎生成建议 */
  private void generateByRule(Integer merchantId, int totalEnter, int avgStay,
                              int peakHour, int peakCount,
                              LocalDateTime start, String snapshot) {
    String ruleId, adviceType, content;
    if (totalEnter == 0) {
      ruleId = "R001"; adviceType = "营销";
      content = "今日尚无客流记录，建议通过线上发券、朋友圈推广等方式引流，提升到店率。";
    } else if (peakHour >= 0 && peakCount > 10 && (peakHour < 10 || peakHour > 16)) {
      ruleId = "R002"; adviceType = "排班";
      content = String.format("今日客流峰值出现在 %d:00（共 %d 人），建议该时段增派 1-2 名服务人员。", peakHour, peakCount);
    } else if (avgStay > 0 && avgStay < 60) {
      ruleId = "R003"; adviceType = "营销";
      content = String.format("今日客均停留仅 %d 秒，转化率偏低，建议优化陈列或推出限时特惠延长停留。", avgStay);
    } else if (totalEnter >= 50) {
      ruleId = "R004"; adviceType = "备货";
      content = String.format("今日进店 %d 人，高于平日水平，建议提前核查热销商品库存，避免断货。", totalEnter);
    } else {
      ruleId = "R005"; adviceType = "营销";
      content = String.format("今日共 %d 人进店，整体平稳，可推出会员积分或满减活动刺激复购。", totalEnter);
    }

    if (aiAdviceMapper.countRecentByRule(merchantId, ruleId, start) > 0) {
      log.info("规则 {} 今日已生成，跳过 merchantId={}", ruleId, merchantId);
      return;
    }
    aiAdviceMapper.insert(new AiAdvice()
        .setMerchantId(merchantId).setTriggerRuleId(ruleId).setSource(1)
        .setAdviceType(adviceType).setContent(content).setDataSnapshot(snapshot).setFeedback(0));
    log.info("规则建议已生成 ruleId={} merchantId={}", ruleId, merchantId);
  }

  /** 高级版：调用 LLM 生成建议，结果解析为多条结构化记录 */
  private void generateByLlm(Integer merchantId, String merchantName,
                             int totalEnter, int avgStay, double femaleRatio,
                             int peakHour, int peakCount, String snapshot) {
    MerchantBusinessInfo info = businessInfoMapper.findByMerchant(merchantId);
    String prompt = buildLlmPrompt(merchantName, totalEnter, avgStay, femaleRatio,
                                   peakHour, peakCount, null, info);
    log.info("调用 LLM 生成建议 merchantId={}", merchantId);
    String callId = java.util.UUID.randomUUID().toString();
    String mu;
    try { mu = systemConfigMapper.getValue("ai.model"); } catch (Exception e) { mu = ""; }
    final String modelUsed = StringUtils.hasText(mu) ? mu : "";
    String reply = llmService.call(prompt);

    if (!StringUtils.hasText(reply)) {
      throw new com.traffic.common.BusinessException("AI 大模型调用失败，请检查管理员配置（服务商、模型与 API Key 是否匹配）");
    }

    List<Map<String, String>> items = parseLlmJson(reply);
    if (items.isEmpty()) {
      aiAdviceMapper.insert(new AiAdvice()
          .setMerchantId(merchantId).setSource(2).setAdviceType("营销")
          .setContent(reply).setDataSnapshot(snapshot).setFeedback(0)
          .setCallId(callId).setModelUsed(modelUsed));
    } else {
      for (Map<String, String> item : items) {
        aiAdviceMapper.insert(new AiAdvice()
            .setMerchantId(merchantId).setSource(2)
            .setAdviceType(item.getOrDefault("type", "营销"))
            .setContent(item.getOrDefault("content", ""))
            .setConfidence(item.getOrDefault("confidence", "中"))
            .setDataSnapshot(snapshot).setFeedback(0)
            .setCallId(callId).setModelUsed(modelUsed));
      }
    }
    log.info("LLM 建议已生成 {} 条，merchantId={}", items.isEmpty() ? 1 : items.size(), merchantId);
  }

  /**
   * 解析 LLM 返回的 JSON 建议数组
   * 兼容 LLM 可能在 JSON 外包裹 markdown 代码块的情况
   */
  private List<Map<String, String>> parseLlmJson(String reply) {
    String text = reply.trim();
    // 去掉 markdown 代码块
    if (text.startsWith("```")) {
      text = text.replaceAll("(?s)```[a-z]*\\n?", "").replaceAll("```", "").trim();
    }
    int start = text.indexOf('[');
    int end   = text.lastIndexOf(']');
    if (start == -1 || end <= start) return List.of();
    try {
      return objectMapper.readValue(text.substring(start, end + 1),
          new com.fasterxml.jackson.core.type.TypeReference<List<Map<String, String>>>() {});
    } catch (Exception e) {
      log.warn("LLM JSON 解析失败: {}", e.getMessage());
      return List.of();
    }
  }

  /** 构建 LLM Prompt：将动态数据块注入模板 */
  private String buildLlmPrompt(String name, int enter, int avgStay, double femaleRatio,
                                int peakHour, int peakCount,
                                String weekSummary, MerchantBusinessInfo info) {
    String dataBlock = buildDataBlock(name, enter, avgStay, femaleRatio, peakHour, peakCount, weekSummary, info);
    return getPromptTemplate().replace("{{data}}", dataBlock);
  }

  private String buildDataBlock(String name, int enter, int avgStay, double femaleRatio,
                                int peakHour, int peakCount,
                                String weekSummary, MerchantBusinessInfo info) {
    StringBuilder sb = new StringBuilder();
    sb.append("门店：").append(name).append("\n");
    sb.append("数据：进店 ").append(enter).append(" 人");
    if (peakHour >= 0) sb.append("，高峰时段 ").append(peakHour).append(":00（").append(peakCount).append(" 人）");
    sb.append("，平均停留 ").append(avgStay).append(" 秒");
    sb.append("，女性占比 ").append(String.format("%.0f", femaleRatio)).append("%");

    if (StringUtils.hasText(weekSummary))
      sb.append("\n近7日趋势：").append(weekSummary);

    if (info != null) {
      if (StringUtils.hasText(info.getBusinessType()))
        sb.append("\n店铺业态：").append(info.getBusinessType());
      if (StringUtils.hasText(info.getMenu()))
        sb.append("\n门店菜单/商品：").append(info.getMenu());
      if (StringUtils.hasText(info.getPromotions()))
        sb.append("\n当期促销活动：").append(info.getPromotions());
      if (StringUtils.hasText(info.getTargetAudience()))
        sb.append("\n目标客群：").append(info.getTargetAudience());
      if (StringUtils.hasText(info.getBusinessHours()))
        sb.append("\n营业时段：").append(info.getBusinessHours());
    }
    return sb.toString();
  }

  private String getPromptTemplate() {
    try {
      String v = systemConfigMapper.getValue("ai.promptTemplate");
      return StringUtils.hasText(v) ? v : DEFAULT_PROMPT_TEMPLATE;
    } catch (Exception e) {
      return DEFAULT_PROMPT_TEMPLATE;
    }
  }

  @Override
  public java.util.Map<String, Boolean> checkDataAvailability(Integer merchantId) {
    ZoneId tz  = ZoneId.of("Asia/Shanghai");
    LocalDateTime now       = LocalDateTime.now(tz);
    LocalDateTime todayStart = LocalDate.now(tz).atStartOfDay();
    boolean hasToday    = trafficFactMapper.sumEnterCount(merchantId, todayStart, now) > 0;
    boolean hasLastHour = trafficFactMapper.sumEnterCount(merchantId, now.minusHours(1), now) > 0;
    return java.util.Map.of("today", hasToday, "lastHour", hasLastHour);
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

package com.traffic.merchant.controller;

import com.traffic.common.BusinessException;
import com.traffic.common.ErrorCode;
import com.traffic.common.R;
import com.traffic.merchant.dto.DashboardResponse;
import com.traffic.merchant.dto.ProfileResponse;
import com.traffic.merchant.dto.StayAnalysisResponse;
import com.traffic.merchant.dto.TrendPoint;
import com.traffic.merchant.service.MerchantService;
import com.traffic.security.JwtPrincipal;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 商家端接口
 * 所有接口需要JWT认证（role=merchant）
 */
@RestController
@RequestMapping("/api/merchant")
@RequiredArgsConstructor
public class MerchantController {

  private final MerchantService merchantService;

  /**
   * 获取今日客流看板
   * GET /api/merchant/dashboard
   */
  @GetMapping("/dashboard")
  public R<DashboardResponse> dashboard(@AuthenticationPrincipal JwtPrincipal principal) {
    if (!"merchant".equals(principal.getRole())) {
      throw new BusinessException(ErrorCode.FORBIDDEN);
    }
    DashboardResponse data = merchantService.getDashboard(principal.getMerchantId());
    return R.ok(data);
  }

  /**
   * 获取客流趋势
   * GET /api/merchant/trend?type=hour&start=2026-04-17&end=2026-04-17
   *
   * @param type  粒度：hour（默认）/ day / week / month
   * @param start 开始日期，格式 yyyy-MM-dd，不传时按 type 取默认回溯
   * @param end   结束日期，格式 yyyy-MM-dd，不传时为今天
   */
  @GetMapping("/trend")
  public R<List<TrendPoint>> trend(
      @AuthenticationPrincipal JwtPrincipal principal,
      @RequestParam(defaultValue = "hour") String type,
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate start,
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate end,
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startDt,
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endDt,
      @RequestParam(required = false) String date, // minute粒度：YYYY-MM-DD，避免ISO时间戳URL编码问题
      @RequestParam(required = false) Integer hour) { // minute粒度：0-23

    if (!"merchant".equals(principal.getRole())) {
      throw new BusinessException(ErrorCode.FORBIDDEN);
    }

    // type=minute 优先使用 date+hour 参数（简单安全，无需编码），
    // 也兼容 startDt/endDt（直接传入时使用）
    if ("minute".equals(type) && date != null && hour != null) {
      LocalDate queryDate = LocalDate.parse(date);
      startDt = queryDate.atTime(hour, 0, 0);
      endDt = queryDate.atTime(hour, 59, 59);
    }

    List<TrendPoint> data = merchantService.getTrend(principal.getMerchantId(), type, start, end, startDt, endDt);
    return R.ok(data);
  }

  /**
   * 获取停留时长分析（分布 + 趋势 + 与前期对比）
   * GET /api/merchant/stay?type=day&start=2026-04-15&end=2026-04-21
   *
   * @param type  粒度：hour / day / week
   * @param start 开始日期，不传时按 type 给默认回溯
   * @param end   结束日期，不传时为今天
   */
  @GetMapping("/stay")
  public R<StayAnalysisResponse> stayAnalysis(
      @AuthenticationPrincipal JwtPrincipal principal,
      @RequestParam(defaultValue = "day") String type,
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate start,
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate end,
      @RequestParam(required = false) Integer hour,
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startDt,
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endDt) {

    if (!"merchant".equals(principal.getRole())) {
      throw new BusinessException(ErrorCode.FORBIDDEN);
    }
    StayAnalysisResponse data = merchantService.getStayAnalysis(
        principal.getMerchantId(), type, start, end, hour, startDt, endDt);
    return R.ok(data);
  }

  /**
   * 获取用户画像详情
   * GET /api/merchant/profile?start=2026-04-17&end=2026-04-17
   *
   * @param start 开始日期，格式 yyyy-MM-dd，不传时默认今天
   * @param end   结束日期，格式 yyyy-MM-dd，不传时默认今天
   */
  @GetMapping("/profile")
  public R<ProfileResponse> profile(
      @AuthenticationPrincipal JwtPrincipal principal,
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate start,
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate end,
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startDt,
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endDt) {

    if (!"merchant".equals(principal.getRole())) {
      throw new BusinessException(ErrorCode.FORBIDDEN);
    }
    ProfileResponse data = merchantService.getProfile(principal.getMerchantId(), start, end, startDt, endDt);
    return R.ok(data);
  }
}

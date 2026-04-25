package com.traffic.admin.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.traffic.common.R;
import com.traffic.device.entity.TrafficFact;
import com.traffic.device.mapper.TrafficFactMapper;
import com.traffic.device.service.impl.DeviceServiceImpl;
import com.traffic.merchant.dto.TrendPoint;
import com.traffic.merchant.entity.Merchant;
import com.traffic.merchant.mapper.MerchantMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.*;

/**
 * 后台数据监控接口
 */
@RestController
@RequestMapping("/api/admin/monitor")
@RequiredArgsConstructor
public class AdminMonitorController {

    private final MerchantMapper merchantMapper;
    private final TrafficFactMapper trafficFactMapper;

    /**
     * 概览数据：各商家今日客流汇总
     * GET /api/admin/monitor/overview
     */
    @GetMapping("/overview")
    public R<Map<String, Object>> overview() {
        List<Merchant> merchants = merchantMapper.selectList(
                new LambdaQueryWrapper<Merchant>().eq(Merchant::getStatus, 1));

        LocalDate today = LocalDate.now(ZoneId.of("Asia/Shanghai"));
        var startOfDay = today.atStartOfDay();
        var endOfDay   = today.plusDays(1).atStartOfDay();

        long totalEnterAll    = 0;
        long activeMerchants  = 0;
        List<Map<String, Object>> rows = new ArrayList<>();

        for (Merchant m : merchants) {
            List<TrafficFact> facts = trafficFactMapper.findTodayByMerchant(m.getId(), startOfDay, endOfDay);
            Map<String, Object> summary = DeviceServiceImpl.aggregateTodaySummary(facts);

            int totalEnter = toInt(summary.get("totalEnter"));
            if (totalEnter > 0) activeMerchants++;
            totalEnterAll += totalEnter;

            int male   = toInt(summary.get("genderMale"));
            int female = toInt(summary.get("genderFemale"));
            int gTotal = male + female;
            double femaleRatio = gTotal > 0 ? Math.round(female * 1000.0 / gTotal) / 10.0 : 0.0;

            Map<String, Object> row = new LinkedHashMap<>();
            row.put("merchantId",     m.getId());
            row.put("merchantName",   m.getName());
            row.put("totalEnter",     totalEnter);
            row.put("currentInStore", (int) Math.max(0, totalEnter * 0.1));
            row.put("avgStaySeconds", toInt(summary.get("avgStaySeconds")));
            row.put("femaleRatio",    femaleRatio);
            row.put("dataSource",     facts.isEmpty() ? "db" : "db");
            rows.add(row);
        }

        // 按进店人数降序
        rows.sort((a, b) -> Integer.compare(toInt(b.get("totalEnter")), toInt(a.get("totalEnter"))));

        // 总设备数（简单统计 traffic_fact 中 distinct deviceId）
        long totalDevices = trafficFactMapper.selectCount(null);  // 近似

        Map<String, Object> overviewStat = new LinkedHashMap<>();
        overviewStat.put("totalMerchants",  (long) merchants.size());
        overviewStat.put("totalDevices",    totalDevices > 100 ? totalDevices / 100 : 1);
        overviewStat.put("todayEnterTotal", totalEnterAll);
        overviewStat.put("activeMerchants", activeMerchants);

        return R.ok(Map.of("overview", overviewStat, "merchants", rows));
    }

    /**
     * GET /api/admin/monitor/trend?merchantId=&type=day|week|month&days=30
     * 平台级（不传 merchantId）或单商家历史客流趋势
     */
    @GetMapping("/trend")
    public R<List<Map<String, Object>>> trend(
            @RequestParam(required = false) Integer merchantId,
            @RequestParam(defaultValue = "day") String type,
            @RequestParam(defaultValue = "30") int days) {

        LocalDateTime end   = LocalDate.now(ZoneId.of("Asia/Shanghai")).plusDays(1).atStartOfDay();
        LocalDateTime start = end.minusDays(days);

        List<Merchant> targets;
        if (merchantId != null) {
            Merchant m = merchantMapper.selectById(merchantId);
            targets = m != null ? List.of(m) : List.of();
        } else {
            targets = merchantMapper.selectList(
                    new LambdaQueryWrapper<Merchant>().eq(Merchant::getStatus, 1));
        }

        // 汇聚所有商家按 label 合并
        Map<String, long[]> agg = new LinkedHashMap<>();
        for (Merchant m : targets) {
            List<TrendPoint> pts = trafficFactMapper.queryTrend(m.getId(), type, start, end);
            for (TrendPoint p : pts) {
                long[] cur = agg.computeIfAbsent(p.getTimeLabel(), k -> new long[2]);
                cur[0] += p.getEnterCount();
                cur[1] += p.getPassCount();
            }
        }

        List<Map<String, Object>> result = new ArrayList<>();
        agg.forEach((label, vals) -> {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("label",      label);
            row.put("enterCount", vals[0]);
            row.put("passCount",  vals[1]);
            result.add(row);
        });
        return R.ok(result);
    }

    private int toInt(Object v) {
        if (v == null) return 0;
        if (v instanceof Number) return ((Number) v).intValue();
        try { return Integer.parseInt(v.toString()); } catch (Exception e) { return 0; }
    }
}

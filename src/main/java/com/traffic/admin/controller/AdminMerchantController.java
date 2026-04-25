package com.traffic.admin.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.traffic.common.BusinessException;
import com.traffic.common.R;
import com.traffic.device.entity.TrafficFact;
import com.traffic.device.mapper.TrafficFactMapper;
import com.traffic.device.service.impl.DeviceServiceImpl;
import com.traffic.merchant.entity.Merchant;
import com.traffic.merchant.mapper.MerchantMapper;
import com.traffic.salesman.entity.MerchantFollow;
import com.traffic.salesman.entity.Salesman;
import com.traffic.salesman.mapper.MerchantFollowMapper;
import com.traffic.salesman.mapper.SalesmanMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 后台商家管理接口
 */
@RestController
@RequestMapping("/api/admin/merchants")
@RequiredArgsConstructor
public class AdminMerchantController {

    private final MerchantMapper merchantMapper;
    private final TrafficFactMapper trafficFactMapper;
    private final MerchantFollowMapper followMapper;
    private final SalesmanMapper salesmanMapper;
    private final PasswordEncoder passwordEncoder;

    /** 商家列表（分页+搜索） */
    @GetMapping
    public R<Map<String, Object>> list(
            @RequestParam(required = false) String name,
            @RequestParam(required = false) Integer status,
            @RequestParam(required = false) Integer packageType,
            @RequestParam(defaultValue = "1")  int page,
            @RequestParam(defaultValue = "15") int size) {

        Page<Merchant> pageObj = new Page<>(page, size);
        LambdaQueryWrapper<Merchant> wrapper = new LambdaQueryWrapper<Merchant>()
                .eq(Merchant::getIsLead, 0)
                .like(StringUtils.hasText(name), Merchant::getName, name)
                .eq(status != null, Merchant::getStatus, status)
                .eq(packageType != null, Merchant::getPackageType, packageType)
                .orderByDesc(Merchant::getId);

        merchantMapper.selectPage(pageObj, wrapper);
        return R.ok(Map.of("list", pageObj.getRecords(), "total", pageObj.getTotal()));
    }

    /** 商家详情（含今日快照） */
    @GetMapping("/{id}")
    public R<Map<String, Object>> detail(@PathVariable Integer id) {
        Merchant m = merchantMapper.selectById(id);
        if (m == null) throw new com.traffic.common.BusinessException(404, "商家不存在");

        // 今日数据快照
        LocalDate today = LocalDate.now(ZoneId.of("Asia/Shanghai"));
        List<TrafficFact> facts = trafficFactMapper.findTodayByMerchant(
                id, today.atStartOfDay(), today.plusDays(1).atStartOfDay());
        Map<String, Object> snapshot = DeviceServiceImpl.aggregateTodaySummary(facts);

        // 计算 femaleRatio
        int male   = toInt(snapshot.get("genderMale"));
        int female = toInt(snapshot.get("genderFemale"));
        int total  = male + female;
        double femaleRatio = total > 0 ? Math.round(female * 1000.0 / total) / 10.0 : 0.0;
        snapshot.put("femaleRatio", femaleRatio);
        snapshot.put("currentInStore", (int) Math.max(0, toInt(snapshot.get("totalEnter")) * 0.1));

        Map<String, Object> result = new HashMap<>();
        result.put("id",            m.getId());
        result.put("name",          m.getName());
        result.put("licenseNo",     m.getLicenseNo());
        result.put("contactPerson", m.getContactPerson());
        result.put("contactPhone",  m.getContactPhone());
        result.put("address",       m.getAddress());
        result.put("packageType",   m.getPackageType());
        result.put("status",        m.getStatus());
        result.put("createdAt",     m.getCreatedAt());
        result.put("todaySnapshot", snapshot);
        return R.ok(result);
    }

    /** 编辑商家信息 */
    @PutMapping("/{id}")
    public R<Void> update(@PathVariable Integer id, @RequestBody Map<String, Object> body) {
        LambdaUpdateWrapper<Merchant> wrapper = new LambdaUpdateWrapper<Merchant>().eq(Merchant::getId, id);
        if (body.containsKey("name"))          wrapper.set(Merchant::getName,          body.get("name"));
        if (body.containsKey("licenseNo"))     wrapper.set(Merchant::getLicenseNo,     body.get("licenseNo"));
        if (body.containsKey("contactPerson")) wrapper.set(Merchant::getContactPerson, body.get("contactPerson"));
        if (body.containsKey("contactPhone"))  wrapper.set(Merchant::getContactPhone,  body.get("contactPhone"));
        if (body.containsKey("address"))       wrapper.set(Merchant::getAddress,       body.get("address"));
        if (body.containsKey("packageType"))   wrapper.set(Merchant::getPackageType,   ((Number) body.get("packageType")).intValue());
        if (body.containsKey("password") && StringUtils.hasText((String) body.get("password")))
            wrapper.set(Merchant::getPassword, passwordEncoder.encode((String) body.get("password")));
        merchantMapper.update(null, wrapper);
        return R.ok(null);
    }

    /** 启用/禁用商家 */
    @PutMapping("/{id}/status")
    public R<Void> toggleStatus(@PathVariable Integer id, @RequestBody Map<String, Integer> body) {
        merchantMapper.update(null,
                new LambdaUpdateWrapper<Merchant>()
                        .eq(Merchant::getId, id)
                        .set(Merchant::getStatus, body.get("status")));
        return R.ok(null);
    }

    /**
     * PUT /api/admin/merchants/{id}/approve
     * 审批通过商家注册（status 0 → 1）
     */
    @PutMapping("/{id}/approve")
    public R<Void> approve(@PathVariable Integer id) {
        Merchant m = merchantMapper.selectById(id);
        if (m == null) throw new com.traffic.common.BusinessException(404, "商家不存在");
        if (m.getStatus() != 0) throw new com.traffic.common.BusinessException(400, "该商家已激活");
        merchantMapper.update(null,
                new LambdaUpdateWrapper<Merchant>()
                        .eq(Merchant::getId, id)
                        .set(Merchant::getStatus, 1));
        AdminSystemController.writeLog("admin", "merchant", "审批通过商家「" + m.getName() + "」", "admin");
        return R.ok(null);
    }

    /**
     * GET /api/admin/merchants/{merchantId}/follows
     * 查询该商家所有跟进记录（含业务员信息）
     */
    @GetMapping("/{merchantId}/follows")
    public R<List<Map<String, Object>>> getFollows(@PathVariable Integer merchantId) {
        List<MerchantFollow> follows = followMapper.selectList(
                new LambdaQueryWrapper<MerchantFollow>()
                        .eq(MerchantFollow::getMerchantId, merchantId)
                        .orderByDesc(MerchantFollow::getUpdatedAt));

        List<Map<String, Object>> result = new java.util.ArrayList<>();
        for (MerchantFollow f : follows) {
            Map<String, Object> item = new java.util.LinkedHashMap<>();
            item.put("followId",         f.getId());
            item.put("salesmanId",       f.getSalesmanId());
            item.put("status",           f.getStatus());
            item.put("commission",       f.getCommission());
            item.put("cooperationTime",  f.getCooperationTime());
            Salesman s = salesmanMapper.selectById(f.getSalesmanId());
            item.put("salesmanName",  s != null ? s.getName()  : "--");
            item.put("salesmanPhone", s != null ? s.getPhone() : "--");
            result.add(item);
        }
        return R.ok(result);
    }

    /**
     * POST /api/admin/merchants/{merchantId}/follows/{followId}/commission
     * 为指定跟进记录设置佣金，并自动计入业务员账户
     * body: { "amount": 500.00 }
     */
    @PostMapping("/{merchantId}/follows/{followId}/commission")
    public R<Void> creditCommission(@PathVariable Integer merchantId,
                                    @PathVariable Integer followId,
                                    @RequestBody Map<String, Object> body) {
        MerchantFollow follow = followMapper.selectById(followId);
        if (follow == null || !merchantId.equals(follow.getMerchantId()))
            throw new BusinessException(404, "跟进记录不存在");

        BigDecimal amount = new BigDecimal(body.get("amount").toString());
        if (amount.compareTo(BigDecimal.ZERO) <= 0)
            throw new BusinessException(400, "佣金金额必须大于0");

        // 更新跟进记录的佣金
        followMapper.update(null, new LambdaUpdateWrapper<MerchantFollow>()
                .eq(MerchantFollow::getId, followId)
                .set(MerchantFollow::getCommission, amount));

        // 计入业务员余额和累计佣金
        salesmanMapper.update(null, new LambdaUpdateWrapper<Salesman>()
                .eq(Salesman::getId, follow.getSalesmanId())
                .setSql("balance = balance + " + amount)
                .setSql("total_commission = total_commission + " + amount));

        Merchant m = merchantMapper.selectById(merchantId);
        String merchantName = m != null ? m.getName() : String.valueOf(merchantId);
        AdminSystemController.writeLog("admin", "merchant",
                String.format("为商家「%s」设置佣金 ¥%s → 业务员 id=%d", merchantName, amount.toPlainString(), follow.getSalesmanId()),
                "admin");
        return R.ok(null);
    }

    private int toInt(Object v) {
        if (v == null) return 0;
        if (v instanceof Number) return ((Number) v).intValue();
        try { return Integer.parseInt(v.toString()); } catch (Exception e) { return 0; }
    }
}

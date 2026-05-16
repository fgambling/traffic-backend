package com.traffic.admin.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.traffic.admin.entity.CommissionRule;
import com.traffic.admin.mapper.CommissionRuleMapper;
import com.traffic.common.BusinessException;
import com.traffic.common.R;
import com.traffic.merchant.entity.Merchant;
import com.traffic.merchant.mapper.MerchantMapper;
import com.traffic.salesman.entity.FollowRecord;
import com.traffic.salesman.entity.MerchantFollow;
import com.traffic.salesman.mapper.FollowRecordMapper;
import com.traffic.salesman.mapper.MerchantFollowMapper;
import com.traffic.salesman.entity.Salesman;
import com.traffic.salesman.mapper.SalesmanMapper;
import com.traffic.salesman.mapper.WithdrawApplyMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 后台业务员管理 + 佣金规则接口
 */
@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
public class AdminSalesmanController {

    private final SalesmanMapper salesmanMapper;
    private final CommissionRuleMapper commissionRuleMapper;
    private final PasswordEncoder passwordEncoder;
    private final MerchantFollowMapper followMapper;
    private final FollowRecordMapper followRecordMapper;
    private final MerchantMapper merchantMapper;
    private final WithdrawApplyMapper withdrawMapper;

    // ─── 业务员管理 ───────────────────────────────────────────

    /** GET /api/admin/salesmen?name=&status=&page=1&size=15 */
    @GetMapping("/salesmen")
    public R<Map<String, Object>> salesmanList(
            @RequestParam(required = false) String name,
            @RequestParam(required = false) Integer status,
            @RequestParam(defaultValue = "1")  int page,
            @RequestParam(defaultValue = "15") int size) {

        Page<Salesman> pageObj = new Page<>(page, size);
        LambdaQueryWrapper<Salesman> wrapper = new LambdaQueryWrapper<Salesman>()
                .like(StringUtils.hasText(name), Salesman::getName, name)
                .eq(status != null, Salesman::getStatus, status)
                .orderByDesc(Salesman::getId);
        salesmanMapper.selectPage(pageObj, wrapper);

        List<Map<String, Object>> enriched = pageObj.getRecords().stream().map(s -> {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("id",              s.getId());
            item.put("name",            s.getName());
            item.put("phone",           s.getPhone());
            item.put("status",          s.getStatus());
            item.put("totalCommission", s.getTotalCommission());
            item.put("balance",         s.getBalance());
            item.put("withdrawnAmount", withdrawMapper.sumApprovedWithdraw(s.getId()));
            item.put("createdAt",       s.getCreatedAt());
            return item;
        }).toList();
        return R.ok(Map.of("list", enriched, "total", pageObj.getTotal()));
    }

    /** POST /api/admin/salesmen — 新增业务员 */
    @PostMapping("/salesmen")
    public R<Void> addSalesman(@RequestBody Map<String, Object> body) {
        String phone = (String) body.get("phone");
        if (!StringUtils.hasText(phone)) throw new BusinessException(400, "手机号不能为空");

        // 重复检测
        if (salesmanMapper.selectCount(new LambdaQueryWrapper<Salesman>()
                .eq(Salesman::getPhone, phone)) > 0) {
            throw new BusinessException(409, "该手机号已存在");
        }

        Salesman s = new Salesman();
        s.setName((String) body.getOrDefault("name", ""));
        s.setPhone(phone);
        String rawPwd = (String) body.getOrDefault("password", "123456");
        s.setPassword(passwordEncoder.encode(rawPwd));
        s.setStatus(1);
        s.setTotalCommission(BigDecimal.ZERO);
        s.setBalance(BigDecimal.ZERO);
        salesmanMapper.insert(s);

        AdminSystemController.writeLog(AdminSystemController.currentAdminName(), "业务员管理", "新增业务员「" + s.getName() + "」");
        return R.ok(null);
    }

    /** PUT /api/admin/salesmen/{id} — 编辑业务员 */
    @PutMapping("/salesmen/{id}")
    public R<Void> updateSalesman(@PathVariable Integer id, @RequestBody Map<String, Object> body) {
        LambdaUpdateWrapper<Salesman> w = new LambdaUpdateWrapper<Salesman>().eq(Salesman::getId, id);
        if (body.containsKey("name"))  w.set(Salesman::getName,  body.get("name"));
        if (body.containsKey("phone")) w.set(Salesman::getPhone, body.get("phone"));
        if (body.containsKey("password") && StringUtils.hasText((String) body.get("password"))) {
            w.set(Salesman::getPassword, passwordEncoder.encode((String) body.get("password")));
        }
        salesmanMapper.update(null, w);
        return R.ok(null);
    }

    /** PUT /api/admin/salesmen/{id}/status — 启用/禁用 */
    @PutMapping("/salesmen/{id}/status")
    public R<Void> toggleSalesmanStatus(@PathVariable Integer id,
                                        @RequestBody Map<String, Integer> body) {
        salesmanMapper.update(null, new LambdaUpdateWrapper<Salesman>()
                .eq(Salesman::getId, id)
                .set(Salesman::getStatus, body.get("status")));
        return R.ok(null);
    }

    /** POST /api/admin/salesmen/batch-import — 批量导入（JSON 数组） */
    @PostMapping("/salesmen/batch-import")
    public R<Map<String, Object>> batchImport(@RequestBody List<Map<String, Object>> rows) {
        int success = 0, skip = 0;
        for (Map<String, Object> row : rows) {
            String phone = (String) row.get("phone");
            if (!StringUtils.hasText(phone)) { skip++; continue; }
            if (salesmanMapper.selectCount(new LambdaQueryWrapper<Salesman>()
                    .eq(Salesman::getPhone, phone)) > 0) { skip++; continue; }
            Salesman s = new Salesman();
            s.setName((String) row.getOrDefault("name", ""));
            s.setPhone(phone);
            s.setPassword(passwordEncoder.encode("123456"));  // 默认密码
            s.setStatus(1);
            s.setTotalCommission(BigDecimal.ZERO);
            s.setBalance(BigDecimal.ZERO);
            salesmanMapper.insert(s);
            success++;
        }
        AdminSystemController.writeLog(AdminSystemController.currentAdminName(), "业务员管理",
                String.format("批量导入业务员 成功%d 跳过%d", success, skip));
        return R.ok(Map.of("success", success, "skip", skip));
    }

    // ─── 佣金规则 ─────────────────────────────────────────────

    /** GET /api/admin/commission-rules */
    @GetMapping("/commission-rules")
    public R<List<CommissionRule>> commissionRules() {
        return R.ok(commissionRuleMapper.selectList(
                new LambdaQueryWrapper<CommissionRule>().orderByAsc(CommissionRule::getPackageType)));
    }

    /** POST /api/admin/commission-rules */
    @PostMapping("/commission-rules")
    public R<Void> saveCommissionRule(@RequestBody CommissionRule rule) {
        if (rule.getId() == null) {
            commissionRuleMapper.insert(rule);
        } else {
            // 显式更新 rate 和 fixedAmount（含 null），避免 MyBatis-Plus 跳过 null 字段
            commissionRuleMapper.update(null, new LambdaUpdateWrapper<CommissionRule>()
                    .eq(CommissionRule::getId, rule.getId())
                    .set(CommissionRule::getName,        rule.getName())
                    .set(CommissionRule::getPackageType, rule.getPackageType())
                    .set(CommissionRule::getRate,        rule.getRate())
                    .set(CommissionRule::getFixedAmount, rule.getFixedAmount())
                    .set(CommissionRule::getDescription, rule.getDescription()));
        }
        AdminSystemController.writeLog(AdminSystemController.currentAdminName(), "业务员管理", "保存佣金规则 pkg=" + rule.getPackageType());
        return R.ok(null);
    }

    /** DELETE /api/admin/commission-rules/{id} */
    @DeleteMapping("/commission-rules/{id}")
    public R<Void> deleteCommissionRule(@PathVariable Integer id) {
        commissionRuleMapper.deleteById(id);
        return R.ok(null);
    }

    // ─── 跟进记录管理 ─────────────────────────────────────────

    /**
     * GET /api/admin/follow/list?page=1&size=15&status=&salesmanName=&merchantName=
     * 查询所有跟进记录（status 不传则返回全部）
     * status: 1=接洽中 2=已合作 3=已失效 4=待审批 5=审批失败
     */
    @GetMapping("/follow/list")
    public R<Map<String, Object>> allFollows(
            @RequestParam(required = false) Integer status,
            @RequestParam(required = false) String salesmanName,
            @RequestParam(required = false) String merchantName,
            @RequestParam(defaultValue = "1")  int page,
            @RequestParam(defaultValue = "15") int size) {
        IPage<Map<String, Object>> result = followMapper.findAllFollowsPage(
                new Page<>(page, size), status,
                StringUtils.hasText(salesmanName) ? salesmanName : null,
                StringUtils.hasText(merchantName) ? merchantName : null);
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("list",  result.getRecords());
        data.put("total", result.getTotal());
        data.put("page",  (int) result.getCurrent());
        data.put("pages", (int) result.getPages());
        return R.ok(data);
    }

    /**
     * GET /api/admin/merchant/{merchantId}/followers
     * 返回该商家所有活跃跟进业务员（非已失效/审批失败），含姓名和电话
     */
    @GetMapping("/merchant/{merchantId}/followers")
    public R<List<Map<String, Object>>> merchantFollowers(@PathVariable Integer merchantId) {
        List<MerchantFollow> follows = followMapper.selectList(
                new LambdaQueryWrapper<MerchantFollow>()
                        .eq(MerchantFollow::getMerchantId, merchantId)
                        .notIn(MerchantFollow::getStatus, 3, 5));
        List<Map<String, Object>> result = follows.stream().map(f -> {
            Salesman s = salesmanMapper.selectById(f.getSalesmanId());
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("name",  s != null ? s.getName()  : "");
            m.put("phone", s != null ? s.getPhone() : "");
            return m;
        }).toList();
        return R.ok(result);
    }

    // ─── 合作申请审批 ─────────────────────────────────────────

    /**
     * GET /api/admin/follow/pending?page=1&size=15
     * 查询业务员提交的待审批合作申请（status=4）
     */
    @GetMapping("/follow/pending")
    public R<Map<String, Object>> pendingFollows(
            @RequestParam(defaultValue = "1")  int page,
            @RequestParam(defaultValue = "15") int size) {
        IPage<Map<String, Object>> result = followMapper.findPendingPage(new Page<>(page, size));
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("list",  result.getRecords());
        data.put("total", result.getTotal());
        data.put("page",  (int) result.getCurrent());
        data.put("pages", (int) result.getPages());
        return R.ok(data);
    }

    /**
     * GET /api/admin/follow/{id}/records
     * 查询跟进历史记录（供审批时展示业务员备注）
     */
    @GetMapping("/follow/{id}/records")
    public R<List<FollowRecord>> followRecords(@PathVariable Integer id) {
        MerchantFollow follow = followMapper.selectById(id);
        if (follow == null) return R.ok(List.of());
        return R.ok(followRecordMapper.findByMerchantId(follow.getMerchantId()));
    }

    /**
     * POST /api/admin/follow/{id}/approve
     * 审批通过：status 4 → 2，记录合作时间，可选结算佣金
     * body (可选): { "ruleId": 1 } 或 { "commissionAmount": 500.0 }
     */
    @PostMapping("/follow/{id}/approve")
    public R<Void> approveFollow(@PathVariable Integer id,
                                  @RequestBody(required = false) Map<String, Object> body) {
        MerchantFollow follow = followMapper.selectById(id);
        if (follow == null || !Integer.valueOf(4).equals(follow.getStatus())) {
            throw new BusinessException(400, "记录不存在或状态不是待审批");
        }
        LocalDateTime now = LocalDateTime.now();
        // 审批通过：将该商家所有活跃跟进（非已失效）同步为已合作
        followMapper.update(null, new LambdaUpdateWrapper<MerchantFollow>()
                .eq(MerchantFollow::getMerchantId, follow.getMerchantId())
                .ne(MerchantFollow::getStatus, 3)
                .set(MerchantFollow::getStatus, 2)
                .set(MerchantFollow::getCooperationTime, now));
        // 将线索商家升级为正式商家（is_lead=0，status=1），使其出现在商家管理中
        // 同时为没有密码的商家设置默认密码 123456（手机号登录）
        Merchant merchant = merchantMapper.selectById(follow.getMerchantId());
        if (merchant != null) {
            LambdaUpdateWrapper<Merchant> mw = new LambdaUpdateWrapper<Merchant>()
                    .eq(Merchant::getId, follow.getMerchantId())
                    .set(Merchant::getIsLead, 0)
                    .set(Merchant::getStatus, 1);
            if (!StringUtils.hasText(merchant.getPassword())) {
                mw.set(Merchant::getPassword, passwordEncoder.encode("123456"));
            }
            merchantMapper.update(null, mw);
        }

        // 计算佣金
        BigDecimal commissionAmount = null;
        if (body != null) {
            if (body.get("ruleId") != null) {
                Integer ruleId = ((Number) body.get("ruleId")).intValue();
                CommissionRule rule = commissionRuleMapper.selectById(ruleId);
                if (rule != null) {
                    BigDecimal cooperation = follow.getCommission() != null ? follow.getCommission() : BigDecimal.ZERO;
                    if (rule.getRate() != null && rule.getRate().compareTo(BigDecimal.ZERO) > 0) {
                        commissionAmount = cooperation.multiply(rule.getRate())
                                .setScale(2, java.math.RoundingMode.HALF_UP);
                    } else if (rule.getFixedAmount() != null && rule.getFixedAmount().compareTo(BigDecimal.ZERO) > 0) {
                        commissionAmount = rule.getFixedAmount();
                    }
                }
            } else if (body.get("commissionAmount") != null) {
                commissionAmount = new BigDecimal(body.get("commissionAmount").toString())
                        .setScale(2, java.math.RoundingMode.HALF_UP);
            }
        }

        // 查找同一商家的所有活跃跟进业务员（联合跟进平均分配佣金）
        List<MerchantFollow> coFollows = followMapper.selectList(
                new LambdaQueryWrapper<MerchantFollow>()
                        .eq(MerchantFollow::getMerchantId, follow.getMerchantId())
                        .notIn(MerchantFollow::getStatus, 3, 5));
        List<Integer> salesmanIds = coFollows.stream()
                .map(MerchantFollow::getSalesmanId)
                .distinct()
                .toList();
        int coCount = salesmanIds.isEmpty() ? 1 : salesmanIds.size();

        // 结算佣金到业务员账户（联合跟进均分），并将每人份额写回 merchant_follow.earned_commission
        StringBuilder content = new StringBuilder("管理员审批通过，合作已确认");
        if (commissionAmount != null && commissionAmount.compareTo(BigDecimal.ZERO) > 0) {
            BigDecimal share = commissionAmount.divide(
                    BigDecimal.valueOf(coCount), 2, java.math.RoundingMode.HALF_UP);
            final String shareStr = share.toPlainString();
            for (Integer sid : salesmanIds) {
                salesmanMapper.update(null, new LambdaUpdateWrapper<Salesman>()
                        .eq(Salesman::getId, sid)
                        .setSql("balance = balance + " + shareStr)
                        .setSql("total_commission = total_commission + " + shareStr));
                // 将实际到账佣金写回对应跟进行
                followMapper.update(null, new LambdaUpdateWrapper<MerchantFollow>()
                        .eq(MerchantFollow::getMerchantId, follow.getMerchantId())
                        .eq(MerchantFollow::getSalesmanId, sid)
                        .set(MerchantFollow::getEarnedCommission, share));
            }
            if (coCount > 1) {
                content.append("，佣金共 ¥").append(commissionAmount.toPlainString())
                       .append(" 由 ").append(coCount).append(" 名业务员均分，每人到账 ¥").append(shareStr);
            } else {
                content.append("，佣金 ¥").append(shareStr).append(" 已到账");
            }
        }

        FollowRecord record = new FollowRecord();
        record.setFollowId(id);
        record.setType("status");
        record.setContent(content.toString());
        followRecordMapper.insert(record);
        return R.ok(null);
    }

    /**
     * POST /api/admin/follow/{id}/reject
     * 审批驳回：status 4 → 5（审批失败），可附拒绝原因
     * body: { reason? }
     */
    @PostMapping("/follow/{id}/reject")
    public R<Void> rejectFollow(@PathVariable Integer id,
                                @RequestBody(required = false) Map<String, Object> body) {
        MerchantFollow follow = followMapper.selectById(id);
        if (follow == null || !Integer.valueOf(4).equals(follow.getStatus())) {
            throw new BusinessException(400, "记录不存在或状态不是待审批");
        }
        followMapper.update(null, new LambdaUpdateWrapper<MerchantFollow>()
                .eq(MerchantFollow::getId, id)
                .set(MerchantFollow::getStatus, 5));
        String reason = body != null ? (String) body.get("reason") : null;
        StringBuilder content = new StringBuilder("管理员驳回合作申请");
        if (StringUtils.hasText(reason)) {
            content.append("，原因：").append(reason.trim());
        }
        FollowRecord record = new FollowRecord();
        record.setFollowId(id);
        record.setType("status");
        record.setContent(content.toString());
        followRecordMapper.insert(record);
        return R.ok(null);
    }
}

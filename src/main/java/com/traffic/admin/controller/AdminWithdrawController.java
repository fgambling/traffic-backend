package com.traffic.admin.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.traffic.common.BusinessException;
import com.traffic.common.R;
import com.traffic.salesman.entity.Salesman;
import com.traffic.salesman.entity.WithdrawApply;
import com.traffic.salesman.mapper.SalesmanMapper;
import com.traffic.salesman.mapper.WithdrawApplyMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 后台提现审核接口
 */
@RestController
@RequestMapping("/api/admin/withdraw")
@RequiredArgsConstructor
public class AdminWithdrawController {

    private final WithdrawApplyMapper withdrawMapper;
    private final SalesmanMapper salesmanMapper;

    /**
     * GET /api/admin/withdraw?status=&salesmanId=&page=1&size=15
     * 列出全部提现申请（可按状态/业务员过滤）
     */
    @GetMapping
    public R<Map<String, Object>> list(
            @RequestParam(required = false) Integer status,
            @RequestParam(required = false) Integer salesmanId,
            @RequestParam(defaultValue = "1")  int page,
            @RequestParam(defaultValue = "15") int size) {

        Page<WithdrawApply> pageObj = new Page<>(page, size);
        LambdaQueryWrapper<WithdrawApply> wrapper = new LambdaQueryWrapper<WithdrawApply>()
                .eq(status != null, WithdrawApply::getStatus, status)
                .eq(salesmanId != null, WithdrawApply::getSalesmanId, salesmanId)
                .orderByDesc(WithdrawApply::getCreatedAt);
        withdrawMapper.selectPage(pageObj, wrapper);

        // 附带业务员姓名
        List<Map<String, Object>> enriched = new ArrayList<>();
        for (WithdrawApply w : pageObj.getRecords()) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("id",         w.getId());
            item.put("salesmanId", w.getSalesmanId());
            item.put("amount",     w.getAmount());
            item.put("way",        w.getWay());
            item.put("account",    w.getAccount());
            item.put("status",     w.getStatus());
            item.put("remark",     w.getRemark());
            item.put("createdAt",  w.getCreatedAt());
            Salesman s = salesmanMapper.selectById(w.getSalesmanId());
            item.put("salesmanName", s != null ? s.getName() : "--");
            item.put("salesmanPhone", s != null ? s.getPhone() : "--");
            enriched.add(item);
        }
        return R.ok(Map.of("list", enriched, "total", pageObj.getTotal()));
    }

    /**
     * PUT /api/admin/withdraw/{id}/approve
     * 审批通过：status 0→1（已打款）
     */
    @PutMapping("/{id}/approve")
    public R<Void> approve(@PathVariable Long id,
                           @RequestBody(required = false) Map<String, String> body) {
        WithdrawApply apply = withdrawMapper.selectById(id);
        if (apply == null) throw new BusinessException(404, "申请不存在");
        if (apply.getStatus() != 0) throw new BusinessException(400, "该申请已处理");

        String remark = body != null ? body.getOrDefault("remark", "") : "";
        withdrawMapper.update(null, new LambdaUpdateWrapper<WithdrawApply>()
                .eq(WithdrawApply::getId, id)
                .set(WithdrawApply::getStatus, 1)
                .set(WithdrawApply::getRemark, remark));

        AdminSystemController.writeLog("admin", "withdraw",
                String.format("通过提现申请 id=%d 金额¥%s", id, apply.getAmount()), "admin");
        return R.ok(null);
    }

    /**
     * PUT /api/admin/withdraw/{id}/reject
     * 驳回：status 0→2，退还余额
     */
    @PutMapping("/{id}/reject")
    public R<Void> reject(@PathVariable Long id,
                          @RequestBody(required = false) Map<String, String> body) {
        WithdrawApply apply = withdrawMapper.selectById(id);
        if (apply == null) throw new BusinessException(404, "申请不存在");
        if (apply.getStatus() != 0) throw new BusinessException(400, "该申请已处理");

        String remark = body != null ? body.getOrDefault("remark", "") : "";
        withdrawMapper.update(null, new LambdaUpdateWrapper<WithdrawApply>()
                .eq(WithdrawApply::getId, id)
                .set(WithdrawApply::getStatus, 2)
                .set(WithdrawApply::getRemark, remark));

        // 退还余额
        BigDecimal refund = apply.getAmount();
        salesmanMapper.update(null, new LambdaUpdateWrapper<Salesman>()
                .eq(Salesman::getId, apply.getSalesmanId())
                .setSql("balance = balance + " + refund));

        AdminSystemController.writeLog("admin", "withdraw",
                String.format("驳回提现申请 id=%d 金额¥%s", id, apply.getAmount()), "admin");
        return R.ok(null);
    }
}

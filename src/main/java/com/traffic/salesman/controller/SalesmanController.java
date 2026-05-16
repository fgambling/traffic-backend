package com.traffic.salesman.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.traffic.common.BusinessException;
import com.traffic.common.ErrorCode;
import com.traffic.common.R;
import com.traffic.merchant.entity.Merchant;
import com.traffic.merchant.mapper.MerchantMapper;
import com.traffic.salesman.dto.AddFollowRequest;
import com.traffic.salesman.dto.FollowVO;
import com.traffic.salesman.dto.UpdateFollowRequest;
import com.traffic.salesman.dto.WithdrawRequest;
import com.traffic.salesman.entity.FollowRecord;
import com.traffic.salesman.entity.MerchantFollow;
import com.traffic.salesman.entity.Salesman;
import com.traffic.salesman.entity.SalesmanMaterial;
import com.traffic.salesman.entity.WithdrawApply;
import com.traffic.salesman.entity.FollowJoinRequest;
import com.traffic.salesman.mapper.FollowJoinRequestMapper;
import com.traffic.salesman.mapper.FollowRecordMapper;
import com.traffic.salesman.mapper.MerchantFollowMapper;
import com.traffic.salesman.mapper.SalesmanMapper;
import com.traffic.salesman.mapper.SalesmanMaterialMapper;
import com.traffic.salesman.mapper.WithdrawApplyMapper;
import com.traffic.merchant.entity.MerchantBusinessInfo;
import com.traffic.merchant.mapper.MerchantBusinessInfoMapper;
import com.traffic.security.JwtPrincipal;
import org.springframework.security.crypto.password.PasswordEncoder;
import lombok.RequiredArgsConstructor;
import jakarta.servlet.http.HttpServletResponse;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.math.BigDecimal;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 业务员端接口
 */
@RestController
@RequestMapping("/api/salesman")
@RequiredArgsConstructor
public class SalesmanController {

    private final SalesmanMapper salesmanMapper;
    private final MerchantFollowMapper followMapper;
    private final WithdrawApplyMapper withdrawMapper;
    private final MerchantMapper merchantMapper;
    private final FollowRecordMapper followRecordMapper;
    private final SalesmanMaterialMapper materialMapper;
    private final FollowJoinRequestMapper joinRequestMapper;
    private final PasswordEncoder passwordEncoder;
    private final MerchantBusinessInfoMapper businessInfoMapper;

    // ─── 鉴权工具 ────────────────────────────────────────────
    private Salesman requireSalesman(JwtPrincipal principal) {
        if (!"salesman".equals(principal.getRole())) throw new BusinessException(ErrorCode.FORBIDDEN);
        Salesman s = salesmanMapper.selectById(principal.getUserId());
        if (s == null) throw new BusinessException(ErrorCode.NOT_FOUND);
        return s;
    }

    // ─── 个人信息（佣金/余额） ─────────────────────────────────
    /**
     * GET /api/salesman/profile
     */
    @GetMapping("/profile")
    public R<Map<String, Object>> profile(@AuthenticationPrincipal JwtPrincipal principal) {
        Salesman s = requireSalesman(principal);
        BigDecimal pendingWithdraw = withdrawMapper.sumPendingWithdraw(s.getId());
        BigDecimal approvedWithdraw = withdrawMapper.sumApprovedWithdraw(s.getId());
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("id", s.getId());
        data.put("name", s.getName());
        data.put("phone", s.getPhone());
        data.put("totalCommission", s.getTotalCommission());
        data.put("balance", s.getBalance());           // 可提现余额
        data.put("frozenAmount", pendingWithdraw);     // 冻结（待审核）金额
        data.put("withdrawnAmount", approvedWithdraw); // 累计已打款
        data.put("status", s.getStatus());
        return R.ok(data);
    }

    // ─── 修改个人信息 ─────────────────────────────────────────
    /**
     * PUT /api/salesman/profile
     * body: { name?, phone?, oldPassword?, newPassword? }
     * 手机号作为登录账号，修改后下次需用新号登录
     */
    @PutMapping("/profile")
    public R<Void> updateProfile(@AuthenticationPrincipal JwtPrincipal principal,
                                 @RequestBody Map<String, Object> body) {
        Salesman s = requireSalesman(principal);
        LambdaUpdateWrapper<Salesman> w = new LambdaUpdateWrapper<Salesman>()
                .eq(Salesman::getId, s.getId());
        String name  = (String) body.get("name");
        String phone = (String) body.get("phone");
        if (StringUtils.hasText(name))  w.set(Salesman::getName,  name.trim());
        if (StringUtils.hasText(phone)) {
            long cnt = salesmanMapper.selectCount(
                    new LambdaQueryWrapper<Salesman>()
                            .eq(Salesman::getPhone, phone.trim())
                            .ne(Salesman::getId, s.getId()));
            if (cnt > 0) throw new BusinessException(409, "该手机号已被使用");
            w.set(Salesman::getPhone, phone.trim());
        }
        // 修改密码
        String oldPassword = (String) body.get("oldPassword");
        String newPassword = (String) body.get("newPassword");
        if (StringUtils.hasText(oldPassword) || StringUtils.hasText(newPassword)) {
            if (!StringUtils.hasText(oldPassword)) throw new BusinessException(400, "请输入原密码");
            if (!StringUtils.hasText(newPassword)) throw new BusinessException(400, "请输入新密码");
            Salesman fresh = salesmanMapper.selectById(s.getId());
            if (!passwordEncoder.matches(oldPassword, fresh.getPassword())) {
                throw new BusinessException(400, "原密码不正确");
            }
            w.set(Salesman::getPassword, passwordEncoder.encode(newPassword));
        }
        salesmanMapper.update(null, w);
        return R.ok(null);
    }

    // ─── 工作台看板 ───────────────────────────────────────────
    /**
     * GET /api/salesman/dashboard?period=month|all
     * period=month（默认）: 本月签约数
     * period=all: 累计签约数
     */
    @GetMapping("/dashboard")
    public R<Map<String, Object>> dashboard(@AuthenticationPrincipal JwtPrincipal principal,
                                            @RequestParam(defaultValue = "month") String period) {
        Salesman s = requireSalesman(principal);
        int sid = s.getId();

        int signCount = "all".equals(period)
                ? followMapper.countByStatus(sid, 2)   // 全部已合作
                : followMapper.countMonthSign(sid);     // 本月签约

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("name", s.getName());
        data.put("totalCommission", s.getTotalCommission());
        data.put("balance", s.getBalance());
        data.put("monthSignCount", signCount);
        data.put("followingCount", followMapper.countByStatus(sid, 1));   // 接洽中
        data.put("totalDoneCount", followMapper.countByStatus(sid, 2));   // 已合作（全部）
        data.put("lostCount", followMapper.countByStatus(sid, 3));        // 已流失
        return R.ok(data);
    }

    // ─── 跟进列表 ─────────────────────────────────────────────
    /**
     * GET /api/salesman/follow/list
     */
    @GetMapping("/follow/list")
    public R<List<FollowVO>> followList(@AuthenticationPrincipal JwtPrincipal principal) {
        Salesman s = requireSalesman(principal);
        return R.ok(followMapper.findBySalesmanId(s.getId()));
    }

    // ─── 营业执照冲突检测 ─────────────────────────────────────
    /**
     * GET /api/salesman/follow/check-license?licenseNo=xxx
     * 检测营业执照是否与已有商家冲突
     * 返回 { type: "ok"|"cooperative"|"contact", salesmanName?, followId? }
     */
    @GetMapping("/follow/check-license")
    public R<Map<String, Object>> checkLicense(@AuthenticationPrincipal JwtPrincipal principal,
                                               @RequestParam String licenseNo) {
        Salesman me = requireSalesman(principal);
        if (!StringUtils.hasText(licenseNo)) return R.ok(Map.of("type", "ok"));

        Merchant existing = merchantMapper.selectOne(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<Merchant>()
                        .apply("UPPER(license_no) = {0}", licenseNo.trim().toUpperCase()).last("LIMIT 1"));
        if (existing == null) return R.ok(Map.of("type", "ok"));

        // 查找该商家的有效跟进（排除已失效）
        List<MerchantFollow> follows = followMapper.selectList(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<MerchantFollow>()
                        .eq(MerchantFollow::getMerchantId, existing.getId())
                        .in(MerchantFollow::getStatus, List.of(1, 2, 4, 5)));
        if (follows.isEmpty()) return R.ok(Map.of("type", "ok"));

        // 检查是否已是自己的跟进（状态不为已失效）
        boolean isMine = follows.stream().anyMatch(f -> f.getSalesmanId().equals(me.getId()));
        if (isMine) return R.ok(Map.of("type", "mine"));

        // 其他业务员：已合作/待审批 → 硬拦截
        Optional<MerchantFollow> cooperative = follows.stream()
                .filter(f -> f.getStatus() == 2 || f.getStatus() == 4).findFirst();
        if (cooperative.isPresent()) {
            String sName = getSalesmanName(cooperative.get().getSalesmanId());
            return R.ok(Map.of("type", "cooperative", "salesmanName", sName));
        }

        // 其他业务员：接洽中(1)或审批失败(5) → 可申请联合跟进
        Optional<MerchantFollow> contactOpt = follows.stream()
                .filter(f -> f.getStatus() == 1 || f.getStatus() == 5).findFirst();
        if (contactOpt.isPresent()) {
            MerchantFollow contact = contactOpt.get();
            String sName = getSalesmanName(contact.getSalesmanId());
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("type", "contact");
            data.put("salesmanName", sName);
            data.put("followId", contact.getId());
            return R.ok(data);
        }

        return R.ok(Map.of("type", "ok"));
    }

    private String getSalesmanName(Integer salesmanId) {
        Salesman s = salesmanMapper.selectById(salesmanId);
        return s != null ? s.getName() : "未知业务员";
    }

    // ─── 联合跟进申请 ─────────────────────────────────────────

    /**
     * POST /api/salesman/follow/join-request
     * body: { followId }
     * 申请联合跟进某条跟进记录
     */
    @PostMapping("/follow/join-request")
    public R<Void> createJoinRequest(@AuthenticationPrincipal JwtPrincipal principal,
                                     @RequestBody Map<String, Object> body) {
        Salesman me = requireSalesman(principal);
        Integer followId = (Integer) body.get("followId");
        if (followId == null) throw new BusinessException(ErrorCode.BAD_REQUEST);

        MerchantFollow follow = followMapper.selectById(followId);
        if (follow == null || follow.getStatus() == 3)
            throw new BusinessException(400, "跟进记录不存在或已失效");
        if (follow.getSalesmanId().equals(me.getId()))
            throw new BusinessException(400, "不能申请跟进自己的记录");

        // 检查是否已有待处理申请
        long pending = joinRequestMapper.selectCount(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<FollowJoinRequest>()
                        .eq(FollowJoinRequest::getFollowId, followId)
                        .eq(FollowJoinRequest::getRequesterId, me.getId())
                        .eq(FollowJoinRequest::getStatus, 0));
        if (pending > 0) throw new BusinessException(409, "您已发起过申请，请等待对方处理");

        FollowJoinRequest req = new FollowJoinRequest();
        req.setFollowId(followId);
        req.setRequesterId(me.getId());
        req.setStatus(0);
        joinRequestMapper.insert(req);
        return R.ok(null);
    }

    /**
     * GET /api/salesman/follow/join-requests/incoming
     * 查询我收到的待处理联合跟进申请
     */
    @GetMapping("/follow/join-requests/incoming")
    public R<List<Map<String, Object>>> incomingJoinRequests(@AuthenticationPrincipal JwtPrincipal principal) {
        Salesman me = requireSalesman(principal);
        return R.ok(joinRequestMapper.findIncomingPending(me.getId()));
    }

    /**
     * POST /api/salesman/follow/join-request/{id}/accept
     * 同意联合跟进：为申请人创建新的跟进记录
     */
    @PostMapping("/follow/join-request/{id}/accept")
    public R<Void> acceptJoinRequest(@AuthenticationPrincipal JwtPrincipal principal,
                                     @PathVariable Integer id) {
        Salesman me = requireSalesman(principal);
        FollowJoinRequest req = joinRequestMapper.selectById(id);
        if (req == null || req.getStatus() != 0) throw new BusinessException(400, "申请不存在或已处理");

        MerchantFollow myFollow = followMapper.selectById(req.getFollowId());
        if (myFollow == null || !myFollow.getSalesmanId().equals(me.getId()))
            throw new BusinessException(ErrorCode.FORBIDDEN);

        // 为申请人创建跟进记录（状态与原跟进同步）
        MerchantFollow newFollow = new MerchantFollow();
        newFollow.setSalesmanId(req.getRequesterId());
        newFollow.setMerchantId(myFollow.getMerchantId());
        newFollow.setStatus(myFollow.getStatus());
        newFollow.setCommission(myFollow.getCommission());
        followMapper.insert(newFollow);

        // 标记申请已同意
        joinRequestMapper.update(null, new LambdaUpdateWrapper<FollowJoinRequest>()
                .eq(FollowJoinRequest::getId, id)
                .set(FollowJoinRequest::getStatus, 1));

        // 写入共享历史记录：记录联合跟进加入事件
        Salesman requester = salesmanMapper.selectById(req.getRequesterId());
        String requesterName = requester != null ? requester.getName() : "未知业务员";
        FollowRecord joinRecord = new FollowRecord();
        joinRecord.setFollowId(myFollow.getId());
        joinRecord.setType("note");
        joinRecord.setContent("业务员「" + requesterName + "」加入联合跟进");
        followRecordMapper.insert(joinRecord);

        return R.ok(null);
    }

    /**
     * POST /api/salesman/follow/join-request/{id}/reject
     * 拒绝联合跟进申请
     */
    @PostMapping("/follow/join-request/{id}/reject")
    public R<Void> rejectJoinRequest(@AuthenticationPrincipal JwtPrincipal principal,
                                     @PathVariable Integer id) {
        Salesman me = requireSalesman(principal);
        FollowJoinRequest req = joinRequestMapper.selectById(id);
        if (req == null || req.getStatus() != 0) throw new BusinessException(400, "申请不存在或已处理");

        MerchantFollow myFollow = followMapper.selectById(req.getFollowId());
        if (myFollow == null || !myFollow.getSalesmanId().equals(me.getId()))
            throw new BusinessException(ErrorCode.FORBIDDEN);

        joinRequestMapper.update(null, new LambdaUpdateWrapper<FollowJoinRequest>()
                .eq(FollowJoinRequest::getId, id)
                .set(FollowJoinRequest::getStatus, 2));
        return R.ok(null);
    }

    // ─── 新增跟进 ─────────────────────────────────────────────
    /**
     * POST /api/salesman/follow/add
     * 重名检测：同一业务员名下存在相同商家名称时返回 409
     */
    @PostMapping("/follow/add")
    public R<Void> followAdd(@AuthenticationPrincipal JwtPrincipal principal,
                             @RequestBody AddFollowRequest req) {
        Salesman s = requireSalesman(principal);
        if (!StringUtils.hasText(req.getName())) {
            throw new BusinessException(ErrorCode.BAD_REQUEST);
        }
        if (!StringUtils.hasText(req.getAddress())) {
            throw new BusinessException(400, "商家地址不能为空");
        }

        // 重名检测：同一业务员跟进列表中是否已有同名商家
        List<FollowVO> existing = followMapper.findBySalesmanId(s.getId());
        boolean duplicate = existing.stream()
                .anyMatch(vo -> req.getName().trim().equals(vo.getMerchantName()));
        if (duplicate) {
            throw new BusinessException(409, "该商家已在您的跟进列表中");
        }

        // 营业执照重复检测（不区分大小写）
        if (StringUtils.hasText(req.getLicenseNo())) {
            Merchant licMerchant = merchantMapper.selectOne(
                    new LambdaQueryWrapper<Merchant>()
                            .apply("UPPER(license_no) = {0}", req.getLicenseNo().trim().toUpperCase())
                            .last("LIMIT 1"));
            if (licMerchant != null) {
                // 判断是否为自己名下且状态不为已失效
                boolean isMyActive = existing.stream()
                        .anyMatch(vo -> licMerchant.getId().equals(vo.getMerchantId())
                                && !Integer.valueOf(3).equals(vo.getStatus()));
                if (isMyActive) {
                    throw new BusinessException(409, "已有使用该营业执照号的商家");
                }
                // 即使不在自己列表也无法重复插入（全局唯一约束）
                throw new BusinessException(409, "已有使用该营业执照号的商家");
            }
        }

        // 创建待激活商家（status=0）
        Merchant merchant = new Merchant();
        merchant.setName(req.getName().trim());
        merchant.setContactPerson(req.getContactPerson());
        merchant.setContactPhone(req.getContactPhone());
        if (StringUtils.hasText(req.getLicenseNo())) merchant.setLicenseNo(req.getLicenseNo().trim().toUpperCase());
        merchant.setAddress(req.getAddress());
        merchant.setStatus(0);  // 待激活
        merchant.setIsLead(1);  // 业务员添加的线索，不在后台商家管理中显示
        merchantMapper.insert(merchant);

        // 创建跟进记录
        MerchantFollow follow = new MerchantFollow();
        follow.setSalesmanId(s.getId());
        follow.setMerchantId(merchant.getId());
        follow.setStatus(req.getStatus() != null ? req.getStatus() : 1);
        if (StringUtils.hasText(req.getRemark())) {
            follow.setFollowRecord(req.getRemark());
        }
        followMapper.insert(follow);

        // 写入创建记录（固定一条，记录开始跟进的时间）
        FollowRecord createRecord = new FollowRecord();
        createRecord.setFollowId(follow.getId());
        createRecord.setType("status");
        createRecord.setContent("开始跟进商家「" + merchant.getName() + "」");
        followRecordMapper.insert(createRecord);

        // 备注写入历史表
        if (StringUtils.hasText(req.getRemark())) {
            FollowRecord record = new FollowRecord();
            record.setFollowId(follow.getId());
            record.setContent(req.getRemark());
            followRecordMapper.insert(record);
        }

        return R.ok(null);
    }

    // ─── 更新跟进状态/凭证 ───────────────────────────────────
    /**
     * PUT /api/salesman/follow/{id}
     * 只更新状态和凭证；状态变更时自动写入历史记录
     */
    @PutMapping("/follow/{id}")
    public R<Void> followUpdate(@AuthenticationPrincipal JwtPrincipal principal,
                                @PathVariable Integer id,
                                @RequestBody UpdateFollowRequest req) {
        Salesman s = requireSalesman(principal);
        MerchantFollow follow = followMapper.selectById(id);
        if (follow == null || !follow.getSalesmanId().equals(s.getId())) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }

        // 已失效、待审批、已合作状态下不允许业务员修改状态
        if (Integer.valueOf(3).equals(follow.getStatus())) {
            throw new BusinessException(400, "已失效商家无法更改状态");
        }
        if (Integer.valueOf(4).equals(follow.getStatus())) {
            throw new BusinessException(400, "合作申请正在审批中，无法更改状态");
        }
        if (Integer.valueOf(2).equals(follow.getStatus())) {
            throw new BusinessException(400, "已合作商家无法修改状态");
        }

        // 业务员将状态设为"已合作"时，实际保存为"待审批(4)"，等管理员审批
        Integer targetStatus = req.getStatus();
        boolean applyingCooperation = Integer.valueOf(2).equals(targetStatus);
        if (applyingCooperation) {
            if (req.getAmount() == null || req.getAmount().compareTo(java.math.BigDecimal.ZERO) <= 0) {
                throw new BusinessException(400, "申请合作时必须填写合作金额");
            }
            targetStatus = 4;
        }

        LambdaUpdateWrapper<MerchantFollow> wrapper = new LambdaUpdateWrapper<MerchantFollow>()
                .eq(MerchantFollow::getId, id)
                .set(targetStatus != null, MerchantFollow::getStatus, targetStatus)
                .set(req.getVoucherUrl() != null, MerchantFollow::getVoucherUrl, req.getVoucherUrl())
                .set(applyingCooperation, MerchantFollow::getCommission, req.getAmount())
                .set(applyingCooperation && StringUtils.hasText(req.getRemark()),
                        MerchantFollow::getFollowRecord, req.getRemark());
        // cooperation_time 在管理员审批通过时才记录

        followMapper.update(null, wrapper);

        // 状态变更时：同步所有联合跟进的状态，并写入共享历史记录
        if (targetStatus != null && !targetStatus.equals(follow.getStatus())) {
            // 同步同一商家其他业务员的状态（跳过已失效和已合作的）
            followMapper.update(null, new LambdaUpdateWrapper<MerchantFollow>()
                    .eq(MerchantFollow::getMerchantId, follow.getMerchantId())
                    .ne(MerchantFollow::getId, id)
                    .notIn(MerchantFollow::getStatus, 2, 3)
                    .set(MerchantFollow::getStatus, targetStatus)
                    .set(applyingCooperation, MerchantFollow::getCommission, req.getAmount()));

            Map<Integer, String> labelMap = Map.of(
                    1, "接洽中", 2, "已合作", 3, "已失效", 4, "待审批", 5, "审批失败");
            String fromLabel = labelMap.getOrDefault(follow.getStatus(), "未知");
            String toLabel   = labelMap.getOrDefault(targetStatus, "未知");
            StringBuilder content = new StringBuilder("跟进状态变更：").append(fromLabel).append(" → ").append(toLabel);
            if (applyingCooperation && req.getAmount() != null) {
                content.append("\n合作金额：¥").append(req.getAmount().toPlainString());
            }
            if (StringUtils.hasText(req.getRemark())) {
                content.append("\n备注：").append(req.getRemark().trim());
            }
            FollowRecord record = new FollowRecord();
            record.setFollowId(id);
            record.setType("status");
            record.setContent(content.toString());
            if (applyingCooperation && StringUtils.hasText(req.getVoucherUrl())) {
                record.setImageUrl(req.getVoucherUrl());
            }
            followRecordMapper.insert(record);
        }

        return R.ok(null);
    }

    // ─── 删除已失效跟进 ───────────────────────────────────────
    /**
     * DELETE /api/salesman/follow/{id}
     * 仅允许删除状态为"已失效(3)"的跟进记录
     */
    @DeleteMapping("/follow/{id}")
    public R<Void> followDelete(@AuthenticationPrincipal JwtPrincipal principal,
                                @PathVariable Integer id) {
        Salesman s = requireSalesman(principal);
        MerchantFollow follow = followMapper.selectById(id);
        if (follow == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND);
        }
        if (!follow.getSalesmanId().equals(s.getId())) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }
        if (!Integer.valueOf(3).equals(follow.getStatus())) {
            throw new BusinessException(400, "只有已失效的跟进记录才能删除");
        }
        // 删除历史记录
        followRecordMapper.delete(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<FollowRecord>()
                        .eq(FollowRecord::getFollowId, id));
        // 删除跟进记录
        followMapper.deleteById(id);
        return R.ok(null);
    }

    // ─── 更新商家基本信息 ─────────────────────────────────────
    /**
     * PUT /api/salesman/merchant/{merchantId}
     * 业务员更新自己跟进商家的基本信息
     */
    @PutMapping("/merchant/{merchantId}")
    public R<Void> updateMerchant(@AuthenticationPrincipal JwtPrincipal principal,
                                  @PathVariable Integer merchantId,
                                  @RequestBody Map<String, Object> body) {
        Salesman s = requireSalesman(principal);
        // 验证业务员确实在跟进此商家
        MerchantFollow merchantFollow = followMapper.selectOne(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<MerchantFollow>()
                        .eq(MerchantFollow::getSalesmanId, s.getId())
                        .eq(MerchantFollow::getMerchantId, merchantId));
        if (merchantFollow == null) throw new BusinessException(ErrorCode.FORBIDDEN);
        if (Integer.valueOf(3).equals(merchantFollow.getStatus())) {
            throw new BusinessException(400, "已失效商家无法更改信息");
        }
        if (Integer.valueOf(2).equals(merchantFollow.getStatus())) {
            throw new BusinessException(400, "已合作商家无法更改信息");
        }

        // 营业执照号重复检测（不区分大小写，排除已失效商家）
        if (body.containsKey("licenseNo") && StringUtils.hasText((String) body.get("licenseNo"))) {
            String newLicense = ((String) body.get("licenseNo")).trim().toUpperCase();
            Merchant conflict = merchantMapper.selectOne(
                    new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<Merchant>()
                            .apply("UPPER(license_no) = {0}", newLicense)
                            .ne(Merchant::getId, merchantId)
                            .last("LIMIT 1"));
            if (conflict != null) {
                // 该商家是否有任意非失效跟进（status != 3）
                long activeFollows = followMapper.selectCount(
                        new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<MerchantFollow>()
                                .eq(MerchantFollow::getMerchantId, conflict.getId())
                                .ne(MerchantFollow::getStatus, 3));
                if (activeFollows > 0) {
                    throw new BusinessException(409, "已有使用该营业执照号的商家，无法修改");
                }
            }
        }

        LambdaUpdateWrapper<Merchant> w = new LambdaUpdateWrapper<Merchant>()
                .eq(Merchant::getId, merchantId);
        if (body.containsKey("name"))          w.set(Merchant::getName,          body.get("name"));
        if (body.containsKey("contactPerson")) w.set(Merchant::getContactPerson, body.get("contactPerson"));
        if (body.containsKey("contactPhone"))  w.set(Merchant::getContactPhone,  body.get("contactPhone"));
        if (body.containsKey("address"))       w.set(Merchant::getAddress,       body.get("address"));
        if (body.containsKey("licenseNo"))     w.set(Merchant::getLicenseNo,     ((String) body.get("licenseNo")).trim().toUpperCase());
        merchantMapper.update(null, w);
        return R.ok(null);
    }

    // ─── 添加跟进记录（文字+图片） ────────────────────────────
    /**
     * POST /api/salesman/follow/{id}/record
     * body: { content, imageUrl? }
     */
    @PostMapping("/follow/{id}/record")
    public R<Void> addFollowRecord(@AuthenticationPrincipal JwtPrincipal principal,
                                   @PathVariable Integer id,
                                   @RequestBody Map<String, Object> body) {
        Salesman s = requireSalesman(principal);
        MerchantFollow follow = followMapper.selectById(id);
        if (follow == null || !follow.getSalesmanId().equals(s.getId())) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }
        String content = (String) body.get("content");
        if (!StringUtils.hasText(content)) throw new BusinessException(400, "记录内容不能为空");

        FollowRecord record = new FollowRecord();
        record.setFollowId(id);
        record.setType("note");
        record.setContent(content);
        String imageUrl = (String) body.get("imageUrl");
        if (StringUtils.hasText(imageUrl)) record.setImageUrl(imageUrl);
        followRecordMapper.insert(record);
        return R.ok(null);
    }

    // ─── 跟进历史记录 ─────────────────────────────────────────
    /**
     * GET /api/salesman/follow/{id}/records
     */
    @GetMapping("/follow/{id}/records")
    public R<List<FollowRecord>> followRecords(@AuthenticationPrincipal JwtPrincipal principal,
                                               @PathVariable Integer id) {
        Salesman s = requireSalesman(principal);
        MerchantFollow follow = followMapper.selectById(id);
        if (follow == null || !follow.getSalesmanId().equals(s.getId())) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }
        // 联合跟进共享记录：查询同商家所有跟进的历史记录
        return R.ok(followRecordMapper.findByMerchantId(follow.getMerchantId()));
    }

    // ─── 同商家跟进的其他业务员 ───────────────────────────────
    /**
     * GET /api/salesman/merchant/{merchantId}/followers
     * 返回同时跟进该商家的其他业务员姓名列表
     */
    @GetMapping("/merchant/{merchantId}/followers")
    public R<List<String>> coFollowers(@AuthenticationPrincipal JwtPrincipal principal,
                                       @PathVariable Integer merchantId) {
        Salesman s = requireSalesman(principal);
        List<String> names = followMapper.findCoSalesmenNames(merchantId, s.getId());
        return R.ok(names);
    }

    // ─── 门店业务信息（业务员代填） ───────────────────────────────

    /**
     * GET /api/salesman/merchant/{merchantId}/business-info
     * 获取商家的门店业务信息（菜单、促销、营业时间等）
     */
    @GetMapping("/merchant/{merchantId}/business-info")
    public R<MerchantBusinessInfo> getBusinessInfo(@AuthenticationPrincipal JwtPrincipal principal,
                                                   @PathVariable Integer merchantId) {
        requireSalesman(principal);
        MerchantBusinessInfo info = businessInfoMapper.findByMerchant(merchantId);
        return R.ok(info != null ? info : new MerchantBusinessInfo());
    }

    /**
     * PUT /api/salesman/merchant/{merchantId}/business-info
     * 业务员为商家填写/更新门店业务信息
     */
    @PutMapping("/merchant/{merchantId}/business-info")
    public R<Void> saveBusinessInfo(@AuthenticationPrincipal JwtPrincipal principal,
                                    @PathVariable Integer merchantId,
                                    @RequestBody MerchantBusinessInfo body) {
        Salesman s = requireSalesman(principal);
        MerchantFollow follow = followMapper.selectOne(new LambdaQueryWrapper<MerchantFollow>()
                .eq(MerchantFollow::getSalesmanId, s.getId())
                .eq(MerchantFollow::getMerchantId, merchantId)
                .last("LIMIT 1"));
        if (follow == null) throw new BusinessException(403, "无权限编辑该商家信息");
        MerchantBusinessInfo existing = businessInfoMapper.findByMerchant(merchantId);
        if (existing == null) {
            body.setId(null);
            body.setMerchantId(merchantId);
            businessInfoMapper.insert(body);
        } else {
            body.setId(existing.getId());
            body.setMerchantId(merchantId);
            businessInfoMapper.updateById(body);
        }
        return R.ok(null);
    }

    // ─── 营销素材 ─────────────────────────────────────────────
    /**
     * GET /api/salesman/material
     */
    @GetMapping("/material")
    public R<List<SalesmanMaterial>> materialList(@AuthenticationPrincipal JwtPrincipal principal) {
        Salesman s = requireSalesman(principal);
        return R.ok(materialMapper.findBySalesmanId(s.getId()));
    }

    /**
     * POST /api/salesman/material
     * body: { title, type, url }
     */
    @PostMapping("/material")
    public R<Void> materialAdd(@AuthenticationPrincipal JwtPrincipal principal,
                               @RequestBody SalesmanMaterial req) {
        Salesman s = requireSalesman(principal);
        SalesmanMaterial m = new SalesmanMaterial();
        m.setSalesmanId(s.getId());
        m.setTitle(req.getTitle());
        m.setType(req.getType());
        m.setUrl(req.getUrl());
        materialMapper.insert(m);
        return R.ok(null);
    }

    /**
     * DELETE /api/salesman/material/{id}
     */
    @DeleteMapping("/material/{id}")
    public R<Void> materialDelete(@AuthenticationPrincipal JwtPrincipal principal,
                                  @PathVariable Integer id) {
        Salesman s = requireSalesman(principal);
        SalesmanMaterial m = materialMapper.selectById(id);
        if (m == null || !m.getSalesmanId().equals(s.getId())) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }
        materialMapper.deleteById(id);
        return R.ok(null);
    }

    // ─── 文件上传 ─────────────────────────────────────────────
    /**
     * POST /api/salesman/upload
     * 接受 multipart/form-data，file 字段，返回可访问的 URL
     */
    @PostMapping("/upload")
    public R<Map<String, String>> upload(@AuthenticationPrincipal JwtPrincipal principal,
                                         @RequestParam("file") MultipartFile file) throws IOException {
        requireSalesman(principal);
        if (file.isEmpty()) throw new BusinessException(ErrorCode.BAD_REQUEST);

        String originalName = file.getOriginalFilename();
        String ext = (originalName != null && originalName.contains("."))
                ? originalName.substring(originalName.lastIndexOf('.'))
                : ".jpg";
        String filename = UUID.randomUUID().toString().replace("-", "") + ext;

        // 保存到项目根目录下的 uploads/
        String uploadDir = Paths.get(System.getProperty("user.dir"), "uploads").toString();
        File dir = new File(uploadDir);
        if (!dir.exists()) dir.mkdirs();

        Files.write(Paths.get(uploadDir, filename), file.getBytes());

        Map<String, String> result = new LinkedHashMap<>();
        result.put("url", "/uploads/" + filename);
        return R.ok(result);
    }

    // ─── 个人业绩统计 ─────────────────────────────────────────

    /**
     * GET /api/salesman/performance/summary
     * 业绩概览：本月签约数、本月新增、待结算佣金、本月提现
     */
    @GetMapping("/performance/summary")
    public R<Map<String, Object>> performanceSummary(@AuthenticationPrincipal JwtPrincipal principal) {
        Salesman s = requireSalesman(principal);
        int sid = s.getId();

        BigDecimal monthWithdraw = withdrawMapper.sumMonthWithdraw(sid);
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("monthSignCount",    followMapper.countMonthSign(sid));
        data.put("monthNewCount",     followMapper.countMonthNew(sid));
        data.put("pendingCommission", s.getBalance());           // 可提现 = 待结算
        data.put("monthWithdrawAmount", monthWithdraw);
        data.put("totalCommission",   s.getTotalCommission());
        data.put("balance",           s.getBalance());
        data.put("followingCount",    followMapper.countByStatus(sid, 1));
        data.put("totalDoneCount",    followMapper.countByStatus(sid, 2));
        data.put("lostCount",         followMapper.countByStatus(sid, 3));
        return R.ok(data);
    }

    /**
     * GET /api/salesman/performance/trend?granularity=month|quarter
     * 返回近6个月（或近4季度）走势，空白期补0
     */
    @GetMapping("/performance/trend")
    public R<List<Map<String, Object>>> performanceTrend(
            @AuthenticationPrincipal JwtPrincipal principal,
            @RequestParam(defaultValue = "month") String granularity) {
        Salesman s = requireSalesman(principal);

        List<Map<String, Object>> raw;
        List<String> labels;

        if ("quarter".equals(granularity)) {
            raw = followMapper.trendByQuarter(s.getId());
            labels = buildQuarterLabels(4);
        } else {
            raw = followMapper.trendByMonth(s.getId());
            labels = buildMonthLabels(6);
        }

        // 将 DB 数据映射为 period → {signCount, commission}
        Map<String, Map<String, Object>> byPeriod = raw.stream()
                .collect(Collectors.toMap(m -> String.valueOf(m.get("period")), m -> m));

        List<Map<String, Object>> result = new ArrayList<>();
        for (String label : labels) {
            Map<String, Object> row = new LinkedHashMap<>();
            Map<String, Object> db = byPeriod.getOrDefault(label, Collections.emptyMap());
            row.put("period",     label);
            row.put("signCount",  db.getOrDefault("signCount", 0));
            row.put("commission", db.getOrDefault("commission", BigDecimal.ZERO));
            result.add(row);
        }
        return R.ok(result);
    }

    /** 生成最近 n 个自然月标签（yyyy-MM），最新在末尾 */
    private List<String> buildMonthLabels(int n) {
        List<String> list = new ArrayList<>();
        java.time.YearMonth cur = java.time.YearMonth.now();
        for (int i = n - 1; i >= 0; i--) {
            list.add(cur.minusMonths(i).toString());   // yyyy-MM
        }
        return list;
    }

    /** 生成最近 n 个季度标签（yyyyQn），最新在末尾 */
    private List<String> buildQuarterLabels(int n) {
        List<String> list = new ArrayList<>();
        java.time.LocalDate now = java.time.LocalDate.now();
        for (int i = n - 1; i >= 0; i--) {
            java.time.LocalDate d = now.minusMonths((long) i * 3);
            list.add(d.getYear() + "Q" + ((d.getMonthValue() - 1) / 3 + 1));
        }
        return list;
    }

    /**
     * GET /api/salesman/follow/signed?page=1&size=10
     * 分页查询已合作商家列表
     */
    @GetMapping("/follow/signed")
    public R<Map<String, Object>> signedList(
            @AuthenticationPrincipal JwtPrincipal principal,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size) {
        Salesman s = requireSalesman(principal);
        Page<FollowVO> pageReq = new Page<>(page, size);
        followMapper.findSignedPage(pageReq, s.getId());

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("list",  pageReq.getRecords());
        data.put("total", pageReq.getTotal());
        data.put("page",  page);
        data.put("size",  size);
        data.put("pages", pageReq.getPages());
        return R.ok(data);
    }

    /**
     * GET /api/salesman/performance/export
     * 导出近3个月业绩 Excel（总览 + 商家明细），保存到 uploads/ 后返回下载 URL
     */
    @GetMapping("/performance/export")
    public R<Map<String, String>> exportPerformance(
            @AuthenticationPrincipal JwtPrincipal principal) throws IOException {
        Salesman s = requireSalesman(principal);
        int sid = s.getId();

        // ── 数据准备 ──────────────────────────────────────────
        List<String> monthLabels = buildMonthLabels(3);  // 近3个自然月
        List<Map<String, Object>> rawTrend = followMapper.trendLast3Months(sid);
        // 按 period 索引方便补0
        Map<String, Map<String, Object>> trendByPeriod = rawTrend.stream()
                .collect(Collectors.toMap(m -> String.valueOf(m.get("period")), m -> m));

        List<FollowVO> detailList = followMapper.findSignedLast3Months(sid);

        // ── 构建 Workbook ─────────────────────────────────────
        try (XSSFWorkbook wb = new XSSFWorkbook()) {

            // ── 公共样式 ──────────────────────────────────────
            CellStyle headerStyle = wb.createCellStyle();
            Font headerFont = wb.createFont();
            headerFont.setBold(true);
            headerStyle.setFont(headerFont);

            CellStyle labelStyle = wb.createCellStyle();
            Font labelFont = wb.createFont();
            labelFont.setBold(true);
            labelStyle.setFont(labelFont);

            // ── Sheet1：近三个月总览 ──────────────────────────
            Sheet sheet1 = wb.createSheet("近三个月总览");
            sheet1.setColumnWidth(0, 5500);
            sheet1.setColumnWidth(1, 5000);
            sheet1.setColumnWidth(2, 5500);
            sheet1.setColumnWidth(3, 5500);

            // 标题行
            Row h1 = sheet1.createRow(0);
            String[] cols1 = {"月份", "签约商家数", "合作金额(元)", "到账佣金(元)"};
            for (int j = 0; j < cols1.length; j++) {
                Cell c = h1.createCell(j);
                c.setCellValue(cols1[j]);
                c.setCellStyle(headerStyle);
            }

            // 月份数据行
            BigDecimal totalCoopAmt = BigDecimal.ZERO;
            BigDecimal totalEarned  = BigDecimal.ZERO;
            int totalSign = 0;
            for (int i = 0; i < monthLabels.size(); i++) {
                String period = monthLabels.get(i);
                Map<String, Object> row = trendByPeriod.getOrDefault(period, Collections.emptyMap());
                int signCount = row.isEmpty() ? 0 : ((Number) row.get("signCount")).intValue();
                BigDecimal coopAmt = row.isEmpty() ? BigDecimal.ZERO
                        : new BigDecimal(row.get("cooperationAmount").toString());
                BigDecimal earned = row.isEmpty() ? BigDecimal.ZERO
                        : new BigDecimal(row.get("earnedCommission").toString());

                totalSign += signCount;
                totalCoopAmt = totalCoopAmt.add(coopAmt);
                totalEarned  = totalEarned.add(earned);

                Row r = sheet1.createRow(i + 1);
                r.createCell(0).setCellValue(period);
                r.createCell(1).setCellValue(signCount);
                r.createCell(2).setCellValue(coopAmt.toPlainString());
                r.createCell(3).setCellValue(earned.toPlainString());
            }

            // 合计行
            Row totalRow = sheet1.createRow(monthLabels.size() + 1);
            Cell tc0 = totalRow.createCell(0); tc0.setCellValue("合计"); tc0.setCellStyle(labelStyle);
            Cell tc1 = totalRow.createCell(1); tc1.setCellValue(totalSign); tc1.setCellStyle(labelStyle);
            Cell tc2 = totalRow.createCell(2); tc2.setCellValue(totalCoopAmt.toPlainString()); tc2.setCellStyle(labelStyle);
            Cell tc3 = totalRow.createCell(3); tc3.setCellValue(totalEarned.toPlainString()); tc3.setCellStyle(labelStyle);

            // 空一行后附加累计账户数据
            int extraRow = monthLabels.size() + 3;
            String[][] accountInfo = {
                {"账户余额(元)",   s.getBalance().toPlainString()},
                {"累计佣金(元)",   s.getTotalCommission().toPlainString()},
                {"跟进中商家数",   String.valueOf(followMapper.countByStatus(sid, 1))},
                {"已合作商家数",   String.valueOf(followMapper.countByStatus(sid, 2))},
            };
            for (String[] kv : accountInfo) {
                Row r = sheet1.createRow(extraRow++);
                Cell k = r.createCell(0); k.setCellValue(kv[0]); k.setCellStyle(labelStyle);
                r.createCell(1).setCellValue(kv[1]);
            }

            // ── Sheet2：商家明细 ──────────────────────────────
            Sheet sheet2 = wb.createSheet("商家明细");
            int[] widths2 = {5500, 4000, 4500, 5500, 5000, 5500, 5500};
            for (int w = 0; w < widths2.length; w++) sheet2.setColumnWidth(w, widths2[w]);

            String[] cols2 = {"商家名称", "联系人", "联系电话", "营业执照号", "合作时间", "合作金额(元)", "到账佣金(元)"};
            Row h2 = sheet2.createRow(0);
            for (int j = 0; j < cols2.length; j++) {
                Cell c = h2.createCell(j);
                c.setCellValue(cols2[j]);
                c.setCellStyle(headerStyle);
            }

            DateTimeFormatter dtf = DateTimeFormatter.ofPattern("yyyy-MM-dd");
            for (int i = 0; i < detailList.size(); i++) {
                FollowVO vo = detailList.get(i);
                Row r = sheet2.createRow(i + 1);
                r.createCell(0).setCellValue(vo.getMerchantName()    != null ? vo.getMerchantName()    : "");
                r.createCell(1).setCellValue(vo.getContactPerson()   != null ? vo.getContactPerson()   : "");
                r.createCell(2).setCellValue(vo.getContactPhone()    != null ? vo.getContactPhone()    : "");
                r.createCell(3).setCellValue(vo.getLicenseNo()       != null ? vo.getLicenseNo()       : "");
                r.createCell(4).setCellValue(vo.getCooperationTime() != null ? vo.getCooperationTime().format(dtf) : "");
                r.createCell(5).setCellValue(vo.getCommission()      != null ? vo.getCommission().toPlainString()      : "0.00");
                r.createCell(6).setCellValue(vo.getEarnedCommission()!= null ? vo.getEarnedCommission().toPlainString(): "0.00");
            }

            // ── 保存到 uploads/ ───────────────────────────────
            String uploadDir = Paths.get(System.getProperty("user.dir"), "uploads").toString();
            new File(uploadDir).mkdirs();
            String filename = "performance_" + s.getId() + "_" + System.currentTimeMillis() + ".xlsx";
            wb.write(Files.newOutputStream(Paths.get(uploadDir, filename)));

            Map<String, String> result = new LinkedHashMap<>();
            result.put("url", "/uploads/" + filename);
            return R.ok(result);
        }
    }

    /** 将趋势 DB 数据与完整时间标签合并，空期补0 */
    private List<Map<String, Object>> buildTrendFull(
            List<Map<String, Object>> raw, List<String> labels) {
        Map<String, Map<String, Object>> byPeriod = raw.stream()
                .collect(Collectors.toMap(m -> String.valueOf(m.get("period")), m -> m));
        List<Map<String, Object>> result = new ArrayList<>();
        for (String label : labels) {
            Map<String, Object> db = byPeriod.getOrDefault(label, Collections.emptyMap());
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("period",     label);
            row.put("signCount",  db.getOrDefault("signCount", 0));
            row.put("commission", db.getOrDefault("commission", BigDecimal.ZERO));
            result.add(row);
        }
        return result;
    }

    // ─── 提现申请 ─────────────────────────────────────────────

    /** 最低提现金额 */
    private static final BigDecimal MIN_WITHDRAW = new BigDecimal("100");

    /**
     * POST /api/salesman/withdraw
     */
    @PostMapping("/withdraw")
    public R<Void> withdraw(@AuthenticationPrincipal JwtPrincipal principal,
                            @RequestBody WithdrawRequest req) {
        Salesman s = requireSalesman(principal);
        if (req.getAmount() == null || req.getAmount().compareTo(BigDecimal.ZERO) <= 0) {
            throw new BusinessException(ErrorCode.BAD_REQUEST);
        }
        if (req.getAmount().compareTo(MIN_WITHDRAW) < 0) {
            throw new BusinessException(400, "提现金额不能低于 " + MIN_WITHDRAW.toPlainString() + " 元");
        }
        if (s.getBalance().compareTo(req.getAmount()) < 0) {
            throw new BusinessException(400, "余额不足，当前可提现 " + s.getBalance().toPlainString() + " 元");
        }

        WithdrawApply apply = new WithdrawApply();
        apply.setSalesmanId(s.getId());
        apply.setAmount(req.getAmount());
        apply.setStatus(0);  // 待审核（冻结）
        withdrawMapper.insert(apply);

        // 冻结余额：从 balance 扣除（驳回时退还，通过时不再扣除）
        salesmanMapper.update(null, new LambdaUpdateWrapper<Salesman>()
                .eq(Salesman::getId, s.getId())
                .setSql("balance = balance - " + req.getAmount()));

        return R.ok(null);
    }

    /**
     * GET /api/salesman/withdraw/list?page=1&size=10
     * 分页查询提现记录
     */
    @GetMapping("/withdraw/list")
    public R<Map<String, Object>> withdrawList(
            @AuthenticationPrincipal JwtPrincipal principal,
            @RequestParam(defaultValue = "1")  int page,
            @RequestParam(defaultValue = "10") int size) {
        Salesman s = requireSalesman(principal);
        Page<WithdrawApply> pageObj = new Page<>(page, size);
        withdrawMapper.findPageBySalesmanId(pageObj, s.getId());
        return R.ok(Map.of("list", pageObj.getRecords(), "total", pageObj.getTotal()));
    }
}

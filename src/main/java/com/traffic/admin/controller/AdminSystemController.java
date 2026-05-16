package com.traffic.admin.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.traffic.admin.entity.AdminUser;
import com.traffic.admin.entity.BugLog;
import com.traffic.admin.entity.OpLog;
import com.traffic.admin.mapper.AdminUserMapper;
import com.traffic.admin.mapper.BugLogMapper;
import com.traffic.admin.mapper.OpLogMapper;
import com.traffic.common.BusinessException;
import com.traffic.common.R;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 后台系统设置接口（管理员账号、操作日志）
 * 开发阶段用内存存储，生产可替换为数据库实现
 */
@RestController
@RequestMapping("/api/admin/system")
@RequiredArgsConstructor
public class AdminSystemController {

    private final BugLogMapper bugLogMapper;
    private final AdminUserMapper adminUserMapper;
    private final OpLogMapper opLogMapper;
    private final PasswordEncoder passwordEncoder;

    private static volatile OpLogMapper staticOpLogMapper;

    @org.springframework.beans.factory.annotation.Autowired
    void initStatic(OpLogMapper opLogMapper) {
        AdminSystemController.staticOpLogMapper = opLogMapper;
    }

    // ── 管理员账号（数据库） ────────────────────────────────────

    @GetMapping("/admins")
    public R<List<Map<String, Object>>> getAdmins() {
        List<AdminUser> users = adminUserMapper.selectList(null);
        List<Map<String, Object>> result = new ArrayList<>();
        for (AdminUser u : users) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("id",        u.getId());
            item.put("username",  u.getUsername());
            item.put("name",      u.getName());
            item.put("createdAt", u.getCreatedAt());
            result.add(item);
        }
        return R.ok(result);
    }

    @PostMapping("/admins")
    public R<Void> addAdmin(@RequestBody Map<String, Object> body) {
        String username = (String) body.get("username");
        String password = (String) body.get("password");
        if (!StringUtils.hasText(username)) throw new BusinessException(400, "账号不能为空");
        if (!StringUtils.hasText(password)) throw new BusinessException(400, "密码不能为空");
        long exists = adminUserMapper.selectCount(
            new LambdaQueryWrapper<AdminUser>().eq(AdminUser::getUsername, username));
        if (exists > 0) throw new BusinessException(409, "账号已存在");
        AdminUser u = new AdminUser()
            .setUsername(username.trim())
            .setName((String) body.getOrDefault("name", username))
            .setPassword(passwordEncoder.encode(password))
            .setCreatedAt(LocalDateTime.now());
        adminUserMapper.insert(u);
        return R.ok(null);
    }

    @PutMapping("/admins/{id}")
    public R<Void> updateAdmin(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        AdminUser u = adminUserMapper.selectById(id);
        if (u == null) throw new BusinessException(404, "管理员不存在");
        LambdaUpdateWrapper<AdminUser> wrapper = new LambdaUpdateWrapper<AdminUser>().eq(AdminUser::getId, id);
        if (StringUtils.hasText((String) body.get("username"))) {
            String newUsername = ((String) body.get("username")).trim();
            long dup = adminUserMapper.selectCount(
                new LambdaQueryWrapper<AdminUser>().eq(AdminUser::getUsername, newUsername).ne(AdminUser::getId, id));
            if (dup > 0) throw new BusinessException(409, "账号已被使用");
            wrapper.set(AdminUser::getUsername, newUsername);
        }
        if (body.containsKey("name")) wrapper.set(AdminUser::getName, body.get("name"));
        if (StringUtils.hasText((String) body.get("password")))
            wrapper.set(AdminUser::getPassword, passwordEncoder.encode((String) body.get("password")));
        adminUserMapper.update(null, wrapper);
        return R.ok(null);
    }

    @DeleteMapping("/admins/{id}")
    public R<Void> deleteAdmin(@PathVariable Long id) {
        if (adminUserMapper.selectCount(null) <= 1)
            throw new BusinessException(400, "至少保留一个管理员账号");
        adminUserMapper.deleteById(id);
        return R.ok(null);
    }

    // ── 操作日志（数据库） ──────────────────────────────────────

    @GetMapping("/logs")
    public R<Map<String, Object>> getLogs(
            @RequestParam(required = false) String operator,
            @RequestParam(required = false) String module,
            @RequestParam(defaultValue = "1")  int page,
            @RequestParam(defaultValue = "20") int size) {

        Page<OpLog> pageObj = new Page<>(page, size);
        LambdaQueryWrapper<OpLog> wrapper = new LambdaQueryWrapper<OpLog>()
                .like(StringUtils.hasText(operator), OpLog::getOperator, operator)
                .eq(StringUtils.hasText(module),     OpLog::getModule,   module)
                .orderByDesc(OpLog::getCreatedAt);
        opLogMapper.selectPage(pageObj, wrapper);
        return R.ok(Map.of("list", pageObj.getRecords(), "total", pageObj.getTotal()));
    }

    private static volatile AdminUserMapper staticAdminUserMapper;

    @org.springframework.beans.factory.annotation.Autowired
    void initAdminMapper(AdminUserMapper m) { AdminSystemController.staticAdminUserMapper = m; }

    /** 从 SecurityContext 取当前登录管理员姓名 */
    public static String currentAdminName() {
        var auth = org.springframework.security.core.context.SecurityContextHolder
                .getContext().getAuthentication();
        if (auth == null || !(auth.getPrincipal() instanceof com.traffic.security.JwtPrincipal p))
            return "管理员";
        if (staticAdminUserMapper == null) return "管理员";
        AdminUser u = staticAdminUserMapper.selectById(p.getMerchantId());
        return u != null && org.springframework.util.StringUtils.hasText(u.getName()) ? u.getName() : "管理员";
    }

    /** 供其他 Controller 调用写日志，operator 为管理员姓名 */
    public static void writeLog(String operator, String module, String action) {
        if (staticOpLogMapper == null) return;
        try {
            staticOpLogMapper.insert(new OpLog()
                    .setOperator(operator)
                    .setModule(module)
                    .setAction(action)
                    .setCreatedAt(LocalDateTime.now()));
        } catch (Exception ignored) {}
    }

    // ── BUG 日志 ────────────────────────────────────────────────

    /**
     * GET /api/admin/system/bugs?level=&module=&resolved=&page=1&size=20
     */
    @GetMapping("/bugs")
    public R<Map<String, Object>> getBugLogs(
            @RequestParam(required = false) String level,
            @RequestParam(required = false) String module,
            @RequestParam(required = false) Integer resolved,
            @RequestParam(defaultValue = "1")  int page,
            @RequestParam(defaultValue = "20") int size) {

        Page<BugLog> pageObj = new Page<>(page, size);
        LambdaQueryWrapper<BugLog> wrapper = new LambdaQueryWrapper<BugLog>()
                .eq(StringUtils.hasText(level),  BugLog::getLevel,    level)
                .eq(StringUtils.hasText(module), BugLog::getModule,   module)
                .eq(resolved != null,            BugLog::getResolved, resolved)
                .orderByDesc(BugLog::getCreatedAt);
        bugLogMapper.selectPage(pageObj, wrapper);

        List<Map<String, Object>> levelStats = bugLogMapper.countByLevel();
        return R.ok(Map.of("list", pageObj.getRecords(), "total", pageObj.getTotal(), "stats", levelStats));
    }

    /**
     * POST /api/admin/system/bugs — 上报 BUG（前端或后端调用）
     */
    @PostMapping("/bugs")
    public R<Void> reportBug(@RequestBody BugLog body) {
        if (!StringUtils.hasText(body.getLevel())) body.setLevel("error");
        body.setResolved(0);
        bugLogMapper.insert(body);
        return R.ok(null);
    }

    /**
     * PUT /api/admin/system/bugs/{id}/resolve — 标记已解决
     */
    @PutMapping("/bugs/{id}/resolve")
    public R<Void> resolveBug(@PathVariable Long id) {
        bugLogMapper.update(null, new LambdaUpdateWrapper<BugLog>()
                .eq(BugLog::getId, id)
                .set(BugLog::getResolved, 1));
        return R.ok(null);
    }

    /**
     * DELETE /api/admin/system/bugs/{id}
     */
    @DeleteMapping("/bugs/{id}")
    public R<Void> deleteBug(@PathVariable Long id) {
        bugLogMapper.deleteById(id);
        return R.ok(null);
    }
}

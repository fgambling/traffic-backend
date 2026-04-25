package com.traffic.admin.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.traffic.admin.entity.BugLog;
import com.traffic.admin.mapper.BugLogMapper;
import com.traffic.common.R;
import lombok.RequiredArgsConstructor;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 后台系统设置接口（管理员账号、操作日志）
 * 开发阶段用内存存储，生产可替换为数据库实现
 */
@RestController
@RequestMapping("/api/admin/system")
@RequiredArgsConstructor
public class AdminSystemController {

    private final BugLogMapper bugLogMapper;

    // ── 管理员列表（内存） ──────────────────────────────────────
    private static final List<Map<String, Object>> ADMINS = new CopyOnWriteArrayList<>(List.of(
        admin(1L, "admin", "管理员", "2026-01-01 00:00:00")
    ));
    private static final AtomicLong ADMIN_ID = new AtomicLong(2);

    private static Map<String, Object> admin(Long id, String username, String name, String createdAt) {
        return new java.util.LinkedHashMap<>(Map.of(
            "id", id, "username", username, "name", name, "createdAt", createdAt
        ));
    }

    @GetMapping("/admins")
    public R<List<Map<String, Object>>> getAdmins() {
        return R.ok(ADMINS);
    }

    @PostMapping("/admins")
    public R<Void> addAdmin(@RequestBody Map<String, Object> body) {
        body.put("id", ADMIN_ID.getAndIncrement());
        body.put("createdAt", LocalDateTime.now().toString().replace("T", " ").substring(0, 19));
        body.remove("password");
        ADMINS.add(body);
        return R.ok(null);
    }

    @PutMapping("/admins/{id}")
    public R<Void> updateAdmin(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        ADMINS.stream()
              .filter(a -> id.equals(((Number) a.get("id")).longValue()))
              .findFirst()
              .ifPresent(a -> {
                  if (body.containsKey("name")) a.put("name", body.get("name"));
              });
        return R.ok(null);
    }

    @DeleteMapping("/admins/{id}")
    public R<Void> deleteAdmin(@PathVariable Long id) {
        ADMINS.removeIf(a -> id.equals(((Number) a.get("id")).longValue()));
        return R.ok(null);
    }

    // ── 操作日志（内存模拟） ────────────────────────────────────
    private static final List<Map<String, Object>> LOGS = new CopyOnWriteArrayList<>(List.of(
        log(1, "admin", "merchant", "启用商家「测试门店」", "127.0.0.1", "2026-04-18 10:00:00"),
        log(2, "admin", "ai",       "新增规则 R006",       "127.0.0.1", "2026-04-18 10:05:00"),
        log(3, "admin", "system",   "新增管理员 operator1", "127.0.0.1", "2026-04-18 10:10:00")
    ));
    private static final AtomicLong LOG_ID = new AtomicLong(4);

    private static Map<String, Object> log(long id, String op, String module, String action, String ip, String time) {
        return Map.of("id", id, "operator", op, "module", module, "action", action, "ip", ip, "createdAt", time);
    }

    @GetMapping("/logs")
    public R<Map<String, Object>> getLogs(
            @RequestParam(required = false) String operator,
            @RequestParam(required = false) String module,
            @RequestParam(defaultValue = "1")  int page,
            @RequestParam(defaultValue = "20") int size) {

        var filtered = LOGS.stream()
            .filter(l -> operator == null || operator.isBlank() || operator.equals(l.get("operator")))
            .filter(l -> module   == null || module.isBlank()   || module.equals(l.get("module")))
            .toList();

        int from  = Math.min((page - 1) * size, filtered.size());
        int to    = Math.min(from + size, filtered.size());
        return R.ok(Map.of("list", filtered.subList(from, to), "total", (long) filtered.size()));
    }

    /** 供其他 Controller 调用写日志 */
    public static void writeLog(String operator, String module, String action, String ip) {
        LOGS.add(0, log(LOG_ID.getAndIncrement(), operator, module, action, ip,
                LocalDateTime.now().toString().replace("T", " ").substring(0, 19)));
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

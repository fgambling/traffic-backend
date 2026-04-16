package com.traffic.customer.service.impl;

import com.traffic.customer.entity.CustomerVisit;
import com.traffic.customer.mapper.CustomerVisitMapper;
import com.traffic.customer.service.CustomerVisitService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDate;
import java.time.ZoneId;

/**
 * 新老客识别服务实现
 *
 * 去重策略：SHA256(personId + merchantId) 作为 person_hash，
 * 保证同一真实顾客在多摄像头下只计一次。
 *
 * 新老客判定：
 *  - 新客：first_visit_date == today（历史上从未来过）
 *  - 老客：visit_count >= 2（来过至少2天）
 *  - 常客：visit_count >= 4（可通过 countFrequent 单独统计）
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CustomerVisitServiceImpl implements CustomerVisitService {

    private final CustomerVisitMapper customerVisitMapper;

    @Override
    public VisitStatus recordVisit(Integer merchantId, String personId) {
        LocalDate today = LocalDate.now(ZoneId.of("Asia/Shanghai"));
        String hash = sha256(personId + merchantId);

        // 单条 SQL 完成 UPSERT（ON DUPLICATE KEY UPDATE）
        customerVisitMapper.upsert(merchantId, hash, today);

        // 查询最新状态以判断新老客
        CustomerVisit visit = customerVisitMapper.findByHash(merchantId, hash);
        if (visit == null) {
            // 理论上不会发生（upsert 刚完成）
            return new VisitStatus(true, false);
        }

        boolean isNew       = visit.getFirstVisitDate().equals(today);
        boolean isReturning = visit.getVisitCount() >= 2;
        return new VisitStatus(isNew, isReturning);
    }

    /** SHA-256(input) → 64位十六进制字符串 */
    private String sha256(String input) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] hash = md.digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(64);
            for (byte b : hash) sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (Exception e) {
            throw new RuntimeException("SHA-256 计算失败", e);
        }
    }
}

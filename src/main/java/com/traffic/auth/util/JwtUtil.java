package com.traffic.auth.util;

import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

/**
 * JWT工具类
 * 负责生成、解析、验证Token
 */
@Slf4j
@Component
public class JwtUtil {

    @Value("${jwt.secret}")
    private String secret;

    @Value("${jwt.expiration}")
    private long expiration;

    /**
     * 生成JWT Token
     *
     * @param userId     用户ID
     * @param role       角色（merchant / salesman）
     * @param merchantId 商家ID（merchant角色时填，salesman时为null）
     */
    public String generateToken(Integer userId, String role, Integer merchantId) {
        Map<String, Object> claims = new HashMap<>();
        claims.put("userId", userId);
        claims.put("role", role);
        if (merchantId != null) {
            claims.put("merchantId", merchantId);
        }
        return buildToken(claims, userId.toString());
    }

    private String buildToken(Map<String, Object> extraClaims, String subject) {
        return Jwts.builder()
                .setClaims(extraClaims)
                .setSubject(subject)
                .setIssuedAt(new Date())
                .setExpiration(new Date(System.currentTimeMillis() + expiration))
                .signWith(getSigningKey(), SignatureAlgorithm.HS256)
                .compact();
    }

    /** 解析Token，返回Claims（失败时返回null） */
    public Claims parseToken(String token) {
        try {
            return Jwts.parserBuilder()
                    .setSigningKey(getSigningKey())
                    .build()
                    .parseClaimsJws(token)
                    .getBody();
        } catch (JwtException e) {
            log.warn("JWT解析失败: {}", e.getMessage());
            return null;
        }
    }

    /** 验证Token是否有效 */
    public boolean isValid(String token) {
        Claims claims = parseToken(token);
        return claims != null && !claims.getExpiration().before(new Date());
    }

    /** 从Token中提取角色 */
    public String extractRole(String token) {
        Claims claims = parseToken(token);
        return claims != null ? claims.get("role", String.class) : null;
    }

    /** 从Token中提取userId */
    public Integer extractUserId(String token) {
        Claims claims = parseToken(token);
        return claims != null ? claims.get("userId", Integer.class) : null;
    }

    /** 从Token中提取merchantId */
    public Integer extractMerchantId(String token) {
        Claims claims = parseToken(token);
        return claims != null ? claims.get("merchantId", Integer.class) : null;
    }

    private SecretKey getSigningKey() {
        byte[] keyBytes = secret.getBytes(StandardCharsets.UTF_8);
        return Keys.hmacShaKeyFor(keyBytes);
    }
}

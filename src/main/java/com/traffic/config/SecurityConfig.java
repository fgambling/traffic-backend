package com.traffic.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.traffic.common.ErrorCode;
import com.traffic.common.R;
import com.traffic.security.JwtAuthenticationFilter;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * Spring Security配置
 * - 无状态JWT，禁用Session和CSRF
 * - 设备上传接口和登录接口不需要认证
 * - 其余接口需要JWT
 */
@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthenticationFilter;
    private final ObjectMapper objectMapper;

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOriginPatterns(List.of("*"));
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS", "PATCH"));
        config.setAllowedHeaders(List.of("*"));
        config.setAllowCredentials(true);
        config.setMaxAge(3600L);
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            // 启用CORS
            .cors(cors -> cors.configurationSource(corsConfigurationSource()))

            // 禁用CSRF（无状态API不需要）
            .csrf(AbstractHttpConfigurer::disable)

            // 无状态Session
            .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))

            // 路由权限
            .authorizeHttpRequests(auth -> auth
                // OPTIONS 预检请求直接放行
                .requestMatchers(org.springframework.http.HttpMethod.OPTIONS, "/**").permitAll()
                // 设备上传（硬件直接调用，无JWT）
                .requestMatchers("/api/device/upload").permitAll()
                // 微信登录
                .requestMatchers("/api/auth/**").permitAll()
                // 后台管理登录
                .requestMatchers("/api/admin/login").permitAll()
                // 其余 /api/admin/** 必须是 admin 角色
                .requestMatchers("/api/admin/**").hasRole("ADMIN")
                // 上传文件的静态访问
                .requestMatchers("/uploads/**").permitAll()
                // 其余全部需要登录
                .anyRequest().authenticated()
            )

            // 未登录时返回JSON而非重定向
            .exceptionHandling(ex -> ex
                .authenticationEntryPoint((req, res, e) -> {
                    res.setStatus(401);
                    res.setContentType(MediaType.APPLICATION_JSON_VALUE);
                    res.setCharacterEncoding(StandardCharsets.UTF_8.name());
                    res.getWriter().write(objectMapper.writeValueAsString(R.fail(ErrorCode.UNAUTHORIZED)));
                })
                .accessDeniedHandler((req, res, e) -> {
                    res.setStatus(403);
                    res.setContentType(MediaType.APPLICATION_JSON_VALUE);
                    res.setCharacterEncoding(StandardCharsets.UTF_8.name());
                    res.getWriter().write(objectMapper.writeValueAsString(R.fail(ErrorCode.FORBIDDEN)));
                })
            )

            // 在UsernamePasswordAuthenticationFilter之前加入JWT过滤器
            .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}

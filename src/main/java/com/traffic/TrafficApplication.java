package com.traffic;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.web.reactive.function.client.WebClient;

/**
 * 商家客流智能分析系统 - 启动入口
 */
@SpringBootApplication
public class TrafficApplication {

    public static void main(String[] args) {
        SpringApplication.run(TrafficApplication.class, args);
    }

    /**
     * WebClient.Builder Bean（用于调用微信API等HTTP请求）
     */
    @Bean
    public WebClient.Builder webClientBuilder() {
        return WebClient.builder();
    }
}

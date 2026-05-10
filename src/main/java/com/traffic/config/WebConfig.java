package com.traffic.config;

import jakarta.annotation.PostConstruct;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Web MVC 配置
 * 将本地 uploads/ 目录映射为静态资源，供前端通过 /uploads/** 访问上传的图片
 */
@Configuration
public class WebConfig implements WebMvcConfigurer {

    private static final Path UPLOAD_DIR =
            Paths.get(System.getProperty("user.dir"), "uploads").toAbsolutePath();

    /** 启动时确保 uploads/ 目录存在，避免首次上传报 IO 错误 */
    @PostConstruct
    public void ensureUploadDir() throws IOException {
        Files.createDirectories(UPLOAD_DIR);
    }

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        registry.addResourceHandler("/uploads/**")
                .addResourceLocations("file:" + UPLOAD_DIR + "/");
    }
}

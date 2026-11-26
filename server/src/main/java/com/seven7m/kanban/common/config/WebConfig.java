package com.seven7m.kanban.common.config;

import com.seven7m.kanban.common.upload.UploadProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.config.annotation.PathMatchConfigurer;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Web 配置：统一追加 API 版本前缀与静态资源映射。
 *
 * <p>认证与鉴权已迁到 Spring Security（见 {@code SecurityConfig} / {@code JwtAuthenticationFilter}），
 * 这里不再注册任何拦截器——旧实现曾用 4 个 HandlerInterceptor 分别做"登录态、项目权限、
 * 管理员、附件归属"四件事，现在统一由 SecurityFilterChain + {@code @PreAuthorize} 承担。
 */
@Configuration
@RequiredArgsConstructor
public class WebConfig implements WebMvcConfigurer {

    private final UploadProperties uploadProperties;

    /** 业务 API 版本前缀，来自配置项 kanban.api.prefix */
    @Value("${kanban.api.prefix:/v1}")
    private String apiPrefix;

    @Override
    public void configurePathMatch(PathMatchConfigurer configurer) {
        // 统一给业务 Controller 追加版本前缀，替代各 Controller 中硬编码的 /v1
        configurer.addPathPrefix(apiPrefix, clazz ->
                clazz.isAnnotationPresent(RestController.class)
                        && clazz.getPackageName().startsWith("com.seven7m.kanban"));
    }

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        // 目录来自 kanban.upload.dir，与落盘位置保持一致（原先硬编码 ./uploads 会与之脱节）
        // 访问鉴权由 SecurityConfig 的 /uploads/** 规则 + UploadsAccessManager 负责
        registry.addResourceHandler("/uploads/**")
                .addResourceLocations(uploadProperties.rootUri());
    }
}

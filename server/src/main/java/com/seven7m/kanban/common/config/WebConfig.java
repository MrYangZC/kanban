package com.seven7m.kanban.common.config;

import com.seven7m.kanban.common.security.AdminAuthInterceptor;
import com.seven7m.kanban.common.security.ProjectPermissionInterceptor;
import com.seven7m.kanban.common.security.UserContextInterceptor;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.PathMatchConfigurer;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Web 配置：统一追加 API 版本前缀、注册操作人上下文拦截器与静态资源映射。
 * 版本前缀来自配置项 kanban.api.prefix（默认 /v1），业务 Controller 不再硬编码。
 */
@Configuration
@RequiredArgsConstructor
public class WebConfig implements WebMvcConfigurer {

    private final UserContextInterceptor userContextInterceptor;
    private final ProjectPermissionInterceptor projectPermissionInterceptor;
    private final AdminAuthInterceptor adminAuthInterceptor;

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
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(userContextInterceptor)
                .addPathPatterns("/**")
                .excludePathPatterns(
                        // 注意：context-path=/api 时，拦截器匹配的是 servletPath，此处不带 /api 前缀
                        "/swagger-ui.html",
                        "/swagger-ui/**",
                        "/v3/api-docs/**",
                        "/doc.html",
                        "/actuator/**",
                        "/error",
                        "/favicon.ico",
                        apiPrefix + "/users/login");
        // 项目级角色鉴权：在 UserContextInterceptor 之后执行（靠后注册，顺序靠后）
        // 上传接口不绑定具体项目，仅需登录态（UserContextInterceptor 仍生效），因此放行；
        // /uploads/** 不再从登录拦截器排除，保证附件文件访问需要登录态
        registry.addInterceptor(projectPermissionInterceptor)
                .addPathPatterns(apiPrefix + "/**")
                .excludePathPatterns(
                        apiPrefix + "/auth/**",
                        apiPrefix + "/users/**",
                        apiPrefix + "/projects",
                        apiPrefix + "/upload/**");
        // 系统管理员后台（RBAC 配置等）：标注 @RequireAdmin 的接口由本拦截器校验管理员身份
        registry.addInterceptor(adminAuthInterceptor)
                .addPathPatterns(apiPrefix + "/admin/**");
    }

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        registry.addResourceHandler("/uploads/**")
                .addResourceLocations("file:./uploads/");
    }
}

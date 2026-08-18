package com.seven7m.kanban.common.config;

import com.seven7m.kanban.common.security.ProjectPermissionInterceptor;
import com.seven7m.kanban.common.security.UserContextInterceptor;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Web 配置：注册操作人上下文拦截器。排除 actuator/静态资源等无需操作人的路径。
 */
@Configuration
@RequiredArgsConstructor
public class WebConfig implements WebMvcConfigurer {

    private final UserContextInterceptor userContextInterceptor;
    private final ProjectPermissionInterceptor projectPermissionInterceptor;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(userContextInterceptor)
                .addPathPatterns("/**")
                .excludePathPatterns(
                        "/api/swagger-ui.html",
                        "/api/swagger-ui/**",
                        "/api/v3/api-docs/**",
                        "/api/doc.html",
                        "/actuator/**",
                        "/error",
                        "/favicon.ico");
        // 项目级角色鉴权：在 UserContextInterceptor 之后执行（靠后注册，顺序靠后）
        registry.addInterceptor(projectPermissionInterceptor)
                .addPathPatterns("/v1/**")
                .excludePathPatterns(
                        "/v1/auth/**",
                        "/v1/users/**",
                        "/v1/workspaces/**",
                        "/v1/projects");
    }
}

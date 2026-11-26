package com.seven7m.kanban.common.security;

import com.seven7m.kanban.common.security.jwt.JwtAuthenticationFilter;
import com.seven7m.kanban.common.security.jwt.JwtService;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

import java.nio.charset.StandardCharsets;

/**
 * 安全配置：JWT 无状态认证 + 方法级鉴权。
 *
 * <p>与旧实现的差别（旧实现是自研拦截器 + HttpSession）：
 * <ul>
 *   <li>不再创建 HttpSession（{@code STATELESS}），所有请求凭 {@code Authorization: Bearer} 认证；</li>
 *   <li>关闭 CSRF——Bearer 头不会被浏览器自动携带，不存在 Cookie 认证下的 CSRF 面；</li>
 *   <li>认证交给 {@link JwtAuthenticationFilter}，鉴权交给 {@code @PreAuthorize}（方法级）；</li>
 *   <li>401/403 统一返回与业务一致的 JSON 结构，前端不必区分两套错误格式。</li>
 * </ul>
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private static final String API_PREFIX = "/v1";

    private final JwtService jwtService;
    private final UploadsAccessManager uploadsAccessManager;

    /** 业务 API 的版本前缀，与 WebConfig 保持同一配置源 */
    @Value("${kanban.api.prefix:/v1}")
    private String apiPrefix;

    /** 复用同一个 BCrypt 实例：UserService 通过注入使用，避免两处各自 new 导致强度不一致 */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        JwtAuthenticationFilter jwtFilter = new JwtAuthenticationFilter(jwtService);
        http
                .csrf(csrf -> csrf.disable())
                .httpBasic(basic -> basic.disable())
                .formLogin(form -> form.disable())
                .logout(logout -> logout.disable())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .exceptionHandling(ex -> ex
                        .authenticationEntryPoint(unauthorizedEntryPoint())
                        .accessDeniedHandler(forbiddenHandler()))
                .authorizeHttpRequests(auth -> auth
                        // 预检请求不鉴权
                        .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                        .requestMatchers(
                                apiPrefix + "/users/login",
                                "/swagger-ui.html", "/swagger-ui/**", "/v3/api-docs/**", "/doc.html",
                                "/actuator/health", "/error", "/favicon.ico").permitAll()
                        // 附件：登录态 + 归属校验（按项目 issue:view 判定）
                        .requestMatchers("/uploads/**").access(uploadsAccessManager)
                        .anyRequest().authenticated())
                .addFilterBefore(jwtFilter, UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }

    /** 未登录：401 + 与业务一致的 JSON */
    private AuthenticationEntryPoint unauthorizedEntryPoint() {
        return (request, response, ex) -> writeJson(response, HttpServletResponse.SC_UNAUTHORIZED, "未登录或登录已过期");
    }

    /** 已登录但无权限：403 */
    private AccessDeniedHandler forbiddenHandler() {
        return (request, response, ex) -> writeJson(response, HttpServletResponse.SC_FORBIDDEN, "无权限执行该操作");
    }

    private void writeJson(HttpServletResponse response, int status, String msg) {
        response.setStatus(status);
        response.setContentType("application/json;charset=UTF-8");
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        try {
            response.getWriter().write("{\"code\":" + status + ",\"msg\":\"" + msg + "\",\"data\":null}");
        } catch (java.io.IOException ignored) {
            // 响应已提交或客户端断开：状态码已设置，无需再处理
        }
    }
}

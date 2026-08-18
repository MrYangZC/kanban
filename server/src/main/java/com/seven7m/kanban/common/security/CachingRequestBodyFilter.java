package com.seven7m.kanban.common.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.util.StreamUtils;

import java.io.IOException;

/**
 * 对 JSON 写请求（POST）预读并缓存请求体，包装为可重复读的 CachedBodyRequest，
 * 使 ProjectPermissionInterceptor 能在 preHandle 阶段读取 body 中的 projectId，
 * 同时不影响后续 @RequestBody 反序列化。
 *
 * 注意：不能使用 Spring 的 ContentCachingRequestWrapper——它只在 body 被消费后才有缓存，
 * 而拦截器 preHandle 早于 @RequestBody，此时缓存为空，会导致鉴权失效。
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class CachingRequestBodyFilter extends org.springframework.web.filter.OncePerRequestFilter {

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        boolean jsonPost = "POST".equalsIgnoreCase(request.getMethod())
                && request.getContentType() != null
                && request.getContentType().toLowerCase().contains("application/json");
        if (jsonPost) {
            byte[] body = StreamUtils.copyToByteArray(request.getInputStream());
            filterChain.doFilter(new CachedBodyRequest(request, body), response);
        } else {
            filterChain.doFilter(request, response);
        }
    }
}

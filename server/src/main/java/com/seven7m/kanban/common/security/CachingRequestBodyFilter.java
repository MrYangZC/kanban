package com.seven7m.kanban.common.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;

/**
 * 对 JSON 写请求（POST）预读并缓存请求体，包装为可重复读的 CachedBodyRequest，
 * 使 ProjectPermissionInterceptor 能在 preHandle 阶段读取 body 中的 projectId，
 * 同时不影响后续 @RequestBody 反序列化。
 *
 * 注意：不能使用 Spring 的 ContentCachingRequestWrapper——它只在 body 被消费后才有缓存，
 * 而拦截器 preHandle 早于 @RequestBody，此时缓存为空，会导致鉴权失效。
 *
 * <p>安全边界：本过滤器按 {@link Ordered#HIGHEST_PRECEDENCE} 注册，执行时机在鉴权拦截器之前，
 * 因此任何匿名请求都能触发一次完整 body 读取。若不设上限，一个超大 JSON POST 就能让 JVM
 * 分配等量字节数组（并发几十个即可 OOM）。这里硬性限制 {@link #MAX_JSON_BODY_BYTES}，
 * 超限直接 413，且只按上限分配缓冲区，不会先读进内存再判断。
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class CachingRequestBodyFilter extends org.springframework.web.filter.OncePerRequestFilter {

    /** 单次 JSON 请求体上限：业务侧最大的请求只会是筛选条件/批量 id，1MB 远超实际需要 */
    private static final int MAX_JSON_BODY_BYTES = 1024 * 1024;

    /** 初始缓冲区：绝大多数请求只有几百字节，按上限预分配 1MB 会造成每个请求一次大对象分配 */
    private static final int INITIAL_BUFFER_BYTES = 8 * 1024;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        boolean jsonPost = "POST".equalsIgnoreCase(request.getMethod())
                && request.getContentType() != null
                && request.getContentType().toLowerCase().contains("application/json");
        if (!jsonPost) {
            filterChain.doFilter(request, response);
            return;
        }

        // 先看声明长度：能提前拒绝的请求不必读一个字节
        long declared = request.getContentLengthLong();
        if (declared > MAX_JSON_BODY_BYTES) {
            reject(response);
            return;
        }

        byte[] body = readBounded(request.getInputStream());
        if (body == null) {
            // 分块传输等未声明（或声明不实）的请求，只有读到上限之外才发现超限
            reject(response);
            return;
        }
        filterChain.doFilter(new CachedBodyRequest(request, body), response);
    }

    /**
     * 最多读取 {@link #MAX_JSON_BODY_BYTES} 字节，缓冲区从 {@link #INITIAL_BUFFER_BYTES} 起按需翻倍。
     * 这样既保证任何请求的堆占用不超过上限（攻击者声明再大的 body 也只读到上限），
     * 又不会让每个几百字节的普通请求都分配一次 1MB 数组。
     * 读满上限后再探测一个字节，若仍有内容则返回 null 表示超限。
     */
    private byte[] readBounded(InputStream in) throws IOException {
        byte[] buffer = new byte[INITIAL_BUFFER_BYTES];
        int total = 0;
        while (true) {
            if (total == buffer.length) {
                if (buffer.length >= MAX_JSON_BODY_BYTES) {
                    return in.read() == -1 ? buffer : null;
                }
                buffer = Arrays.copyOf(buffer, Math.min(buffer.length * 2, MAX_JSON_BODY_BYTES));
            }
            int n = in.read(buffer, total, buffer.length - total);
            if (n == -1) {
                break;
            }
            total += n;
        }
        return total == buffer.length ? buffer : Arrays.copyOf(buffer, total);
    }

    private void reject(HttpServletResponse response) throws IOException {
        response.setStatus(HttpServletResponse.SC_REQUEST_ENTITY_TOO_LARGE);
        response.setContentType("application/json;charset=UTF-8");
        response.getWriter().write("{\"code\":413,\"msg\":\"请求体过大（上限 1MB）\",\"data\":null}");
    }
}

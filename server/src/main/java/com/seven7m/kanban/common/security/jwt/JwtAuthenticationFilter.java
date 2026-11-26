package com.seven7m.kanban.common.security.jwt;

import org.springframework.security.access.prepost.PreAuthorize;
import com.seven7m.kanban.common.security.UserContext;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

/**
 * 从 {@code Authorization: Bearer <token>} 还原登录态。
 *
 * <p>职责边界：只做"认证"（你是谁），不做"鉴权"（你能不能做）。
 * 鉴权由方法级 {@code @PreAuthorize} 完成，因此这里只放两个全局角色：
 * {@code ROLE_ADMIN}（系统管理员）与 {@code ROLE_USER}（已登录用户）。
 * 项目级权限不放进 authorities —— 它取决于请求里的 projectId，由表达式
 * {@code hasProjectPermission(...)} 现查。
 *
 * <p>解析失败（过期/篡改/格式错）不直接返回错误，而是让请求以匿名身份继续，
 * 由 {@code SecurityFilterChain} 的入口点统一给出 401 JSON，
 * 这样公开接口（登录、swagger）不受影响。
 */
@Slf4j
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final String HEADER = "Authorization";
    private static final String PREFIX = "Bearer ";
    /** 附件路径专用的 Cookie 名：只用于 /uploads/** 的静态文件请求 */
    public static final String FILE_COOKIE = "KB_FILE";
    /** 文件 Cookie 的作用路径后缀（实际值为 context-path + 该后缀） */
    public static final String FILE_COOKIE_PATH = "/uploads";

    private final JwtService jwtService;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        String token = extractToken(request);
        if (token != null) {
            try {
                JwtService.Principal principal = jwtService.parse(token);
                List<SimpleGrantedAuthority> authorities = principal.admin()
                        ? List.of(new SimpleGrantedAuthority("ROLE_ADMIN"),
                                  new SimpleGrantedAuthority("ROLE_USER"))
                        : List.of(new SimpleGrantedAuthority("ROLE_USER"));
                UsernamePasswordAuthenticationToken auth =
                        new UsernamePasswordAuthenticationToken(principal.userId(), null, authorities);
                auth.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                SecurityContextHolder.getContext().setAuthentication(auth);
                // 业务层仍通过 UserContext 取当前用户（22 个文件在用），在这里桥接一次
                UserContext.set(principal.userId(), principal.username());
            } catch (JwtException | IllegalArgumentException e) {
                // 不打印 token 本身，避免把凭证写进日志
                log.debug("JWT 校验失败: {}", e.getMessage());
                SecurityContextHolder.clearContext();
            }
        }
        try {
            chain.doFilter(request, response);
        } finally {
            // 线程复用：必须清理，否则下一个请求可能读到上一个请求的身份
            UserContext.clear();
        }
    }

    /**
     * 取 token：优先 Authorization 头（前端接口调用都走这个）。
     *
     * <p>只有 {@code /uploads/**} 允许退回到 Cookie：浏览器渲染 {@code <img src>} 时无法附带
     * 自定义请求头，若没有回退机制，头像与附件会全部 401。该 Cookie 由登录接口按
     * {@code Path=<context-path>/uploads} 下发，作用域被限制在静态文件路径上，
     * 业务接口即使拿到这个 Cookie 也不会被当作登录态，从而不扩大 CSRF 面。
     */
    private String extractToken(HttpServletRequest request) {
        String header = request.getHeader(HEADER);
        if (header != null && header.startsWith(PREFIX)) {
            String token = header.substring(PREFIX.length()).trim();
            if (!token.isEmpty()) {
                return token;
            }
        }
        if (isUploadsRequest(request)) {
            return cookieValue(request, FILE_COOKIE);
        }
        return null;
    }

    private boolean isUploadsRequest(HttpServletRequest request) {
        String uri = request.getRequestURI();
        String ctx = request.getContextPath();
        String path = (ctx != null && !ctx.isEmpty() && uri.startsWith(ctx)) ? uri.substring(ctx.length()) : uri;
        return path.startsWith(FILE_COOKIE_PATH);
    }

    private String cookieValue(HttpServletRequest request, String name) {
        jakarta.servlet.http.Cookie[] cookies = request.getCookies();
        if (cookies == null) {
            return null;
        }
        for (jakarta.servlet.http.Cookie cookie : cookies) {
            if (name.equals(cookie.getName())) {
                String value = cookie.getValue();
                return value == null || value.isEmpty() ? null : value;
            }
        }
        return null;
    }
}

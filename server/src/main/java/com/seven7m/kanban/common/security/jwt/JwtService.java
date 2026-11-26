package com.seven7m.kanban.common.security.jwt;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.UUID;

/**
 * JWT 签发与校验（HS256）。
 *
 * <p>设计取舍（本项目不用 Redis、不做主动失效）：
 * <ul>
 *   <li>token 只携带 userId / 用户名 / 是否系统管理员，<b>不放项目级权限</b>——
 *       项目角色是 per-project 的，塞进 token 会导致"新建项目就要重新签发"，
 *       且角色变更后 token 里仍是旧权限；项目级鉴权改为每次请求查库（带缓存）。</li>
 *   <li>没有黑名单、没有 token 版本号：登出只清客户端，旧 token 在过期前仍有效。
 *       因此 TTL 必须短，靠短 TTL 限制暴露窗口，而不是靠服务端撤销。</li>
 * </ul>
 */
@Slf4j
@Component
public class JwtService {

    private final SecretKey key;
    private final long expireSeconds;
    private final String issuer;

    /** application.yml 里的内置默认密钥；任何非本地环境都不允许实际使用它 */
    private static final String DEV_DEFAULT_SECRET = "kanban-dev-only-secret-change-in-production-32b";

    public JwtService(@Value("${kanban.jwt.secret}") String secret,
                      @Value("${kanban.jwt.expire-minutes:120}") long expireMinutes,
                      @Value("${kanban.jwt.issuer:kanban}") String issuer,
                      @Value("${spring.profiles.active:}") String activeProfiles) {
        // HS256 要求密钥不少于 256 位（32 字节），过短会在启动期直接拒绝，避免弱签名
        byte[] bytes = secret.getBytes(StandardCharsets.UTF_8);
        if (bytes.length < 32) {
            throw new IllegalStateException(
                    "kanban.jwt.secret 至少需要 32 字节（当前 " + bytes.length + "），请用 KANBAN_JWT_SECRET 配置");
        }
        // 部署时漏配 KANBAN_JWT_SECRET 会静默回落到仓库里的默认值——那等于把签名密钥公开，
        // 任何人都能伪造任意用户的 token。除了 local/dev 之外一律启动失败，宁可不启动。
        boolean localLike = java.util.Arrays.stream(activeProfiles.split(","))
                .map(String::trim)
                .anyMatch(p -> p.equals("local") || p.equals("dev"));
        if (DEV_DEFAULT_SECRET.equals(secret) && !localLike) {
            throw new IllegalStateException(
                    "使用了内置的开发默认 JWT 密钥，但当前环境不是 local/dev（profiles=" + activeProfiles
                            + "）。请通过环境变量 KANBAN_JWT_SECRET 配置至少 32 字节的随机密钥。");
        }
        this.key = Keys.hmacShaKeyFor(bytes);
        this.expireSeconds = expireMinutes * 60;
        this.issuer = issuer;
        if (expireMinutes > 24 * 60) {
            log.warn("JWT 有效期 {} 分钟偏长：本方案不支持主动失效，TTL 越长越权窗口越大", expireMinutes);
        }
    }

    /** token 有效期（秒），下发给前端用于展示/提前续期判断 */
    public long expireSeconds() {
        return expireSeconds;
    }

    /** 签发 */
    public Issued issue(UUID userId, String username, boolean admin) {
        Instant now = Instant.now();
        Instant exp = now.plusSeconds(expireSeconds);
        String token = Jwts.builder()
                .setSubject(userId.toString())
                .setIssuer(issuer)
                .setIssuedAt(Date.from(now))
                .setExpiration(Date.from(exp))
                .claim("uname", username)
                .claim("adm", admin)
                .signWith(key, SignatureAlgorithm.HS256)
                .compact();
        return new Issued(token, exp);
    }

    /**
     * 校验并解析。签名错误、格式错误、已过期都会抛 {@link JwtException}。
     * 解析时不查库：这是"无状态"的代价，也是本方案选择短 TTL 的原因。
     */
    public Principal parse(String token) {
        Claims claims = Jwts.parserBuilder()
                .setSigningKey(key)
                .requireIssuer(issuer)
                .build()
                .parseClaimsJws(token)
                .getBody();
        return new Principal(
                UUID.fromString(claims.getSubject()),
                claims.get("uname", String.class),
                Boolean.TRUE.equals(claims.get("adm", Boolean.class)));
    }

    /** 签发结果：token + 过期时刻（前端据此判断是否需要重新登录） */
    public record Issued(String token, Instant expiresAt) {
    }

    /** 解析出的身份信息 */
    public record Principal(UUID userId, String username, boolean admin) {
    }
}

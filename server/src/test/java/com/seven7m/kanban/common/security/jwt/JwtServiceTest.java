package com.seven7m.kanban.common.security.jwt;

import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * JWT 签发与校验的守护测试。
 *
 * <p>这一层是唯一的认证凭据，出错方式很危险：
 * 签名校验失效 = 谁都能伪造任意用户；过期校验失效 = 离职账号永久有效。
 * 因此这里逐条钉死"篡改、过期、错签发方、弱密钥"四种失败路径。
 */
class JwtServiceTest {

    private static final String SECRET = "0123456789abcdef0123456789abcdef";  // 32 字节
    private static final String OTHER_SECRET = "fedcba9876543210fedcba9876543210";

    private JwtService service(long expireMinutes) {
        return new JwtService(SECRET, expireMinutes, "kanban");
    }

    @Test
    @DisplayName("签发后可解析出同一用户，管理员标记保真")
    void issueThenParse() {
        JwtService jwt = service(60);
        UUID userId = UUID.randomUUID();
        JwtService.Issued issued = jwt.issue(userId, "alice", true);

        JwtService.Principal principal = jwt.parse(issued.token());

        assertThat(principal.userId()).isEqualTo(userId);
        assertThat(principal.username()).isEqualTo("alice");
        assertThat(principal.admin()).isTrue();
        assertThat(issued.expiresAt()).isAfter(Instant.now());
    }

    @Test
    @DisplayName("普通用户的管理员标记为 false")
    void nonAdminFlag() {
        JwtService jwt = service(60);
        JwtService.Principal principal = jwt.parse(jwt.issue(UUID.randomUUID(), "bob", false).token());
        assertThat(principal.admin()).isFalse();
    }

    @Test
    @DisplayName("被篡改的 token（载荷改动但签名不变）必须拒绝")
    void tamperedTokenRejected() {
        JwtService jwt = service(60);
        String token = jwt.issue(UUID.randomUUID(), "alice", false).token();
        // 改掉载荷段的一个字符——签名随即对不上
        String[] parts = token.split("\\.");
        char[] payload = parts[1].toCharArray();
        payload[0] = payload[0] == 'e' ? 'f' : 'e';
        String tampered = parts[0] + "." + new String(payload) + "." + parts[2];

        assertThatThrownBy(() -> jwt.parse(tampered)).isInstanceOf(JwtException.class);
    }

    @Test
    @DisplayName("用别的密钥签发的 token 必须拒绝（防伪造）")
    void foreignlySignedTokenRejected() {
        JwtService mine = service(60);
        String forged = Jwts.builder()
                .setSubject(UUID.randomUUID().toString())
                .setIssuer("kanban")
                .setExpiration(Date.from(Instant.now().plusSeconds(600)))
                .signWith(Keys.hmacShaKeyFor(OTHER_SECRET.getBytes(StandardCharsets.UTF_8)),
                        SignatureAlgorithm.HS256)
                .compact();

        assertThatThrownBy(() -> mine.parse(forged)).isInstanceOf(JwtException.class);
    }

    @Test
    @DisplayName("过期 token 必须拒绝，且抛出 ExpiredJwtException")
    void expiredTokenRejected() {
        // 有效期 0 分钟：签发即过期
        JwtService jwt = service(0);
        String token = jwt.issue(UUID.randomUUID(), "alice", false).token();

        assertThatThrownBy(() -> jwt.parse(token)).isInstanceOf(ExpiredJwtException.class);
    }

    @Test
    @DisplayName("签发方不匹配时拒绝（避免别的系统签的 token 混进来）")
    void wrongIssuerRejected() {
        String other = Jwts.builder()
                .setSubject(UUID.randomUUID().toString())
                .setIssuer("someone-else")
                .setExpiration(Date.from(Instant.now().plusSeconds(600)))
                .signWith(Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8)), SignatureAlgorithm.HS256)
                .compact();

        assertThatThrownBy(() -> service(60).parse(other)).isInstanceOf(JwtException.class);
    }

    @Test
    @DisplayName("密钥短于 32 字节时启动即失败，不允许弱密钥上线")
    void shortSecretRejected() {
        assertThatThrownBy(() -> new JwtService("too-short", 60, "kanban"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("32");
    }
}

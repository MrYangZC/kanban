package com.seven7m.kanban.dto.user;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.util.UUID;

@Data
public class LoginVO {

    private UUID id;
    private String username;
    private String displayName;
    /** 头像地址（/api/uploads/xxx），为空时前端回退首字头像；BeanUtils 从 SysUser 同名属性拷贝 */
    private String avatarUrl;

    /**
     * 访问令牌（JWT，HS256）。前端存 localStorage 并在请求头带
     * {@code Authorization: Bearer <token>}。
     *
     * <p>此前这里下发过一个不可校验的 "stub-{userId}"，那确实是误导性死数据；
     * 现在的 token 由服务端签名，篡改与过期都会被拒。
     */
    private String token;

    /** token 过期时刻（epoch 毫秒），前端据此提前提示重新登录，避免请求到一半才发现过期 */
    private Long expiresAt;

    /**
     * 是否系统管理员，供前端路由/菜单判定使用。
     * 必须与后端 PermissionService#isSuperAdmin 口径一致：
     * sys_user.is_admin = true 或 种子管理员（固定 UUID）。
     * 前端此前用 username === 'admin' 自行推断，与后端口径不一致。
     *
     * <p>属性名不带 {@code is} 前缀（手册强制），下发给前端的 JSON 字段名仍是 {@code isAdmin}。
     */
    @JsonProperty("isAdmin")
    private Boolean admin;
}

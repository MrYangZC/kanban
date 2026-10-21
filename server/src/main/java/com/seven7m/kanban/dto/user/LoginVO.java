package com.seven7m.kanban.dto.user;

import lombok.Data;

import java.util.UUID;

@Data
public class LoginVO {

    private UUID id;
    private String username;
    private String displayName;
    /** 头像地址（/api/uploads/xxx），为空时前端回退首字头像；BeanUtils 从 SysUser 同名属性拷贝 */
    private String avatarUrl;
    private String token;

    /**
     * 是否系统管理员，供前端路由/菜单判定使用。
     * 必须与后端 PermissionService#isSuperAdmin 口径一致：
     * sys_user.is_admin = true 或 种子管理员（固定 UUID）。
     * 前端此前用 username === 'admin' 自行推断，与后端口径不一致。
     */
    private Boolean isAdmin;
}

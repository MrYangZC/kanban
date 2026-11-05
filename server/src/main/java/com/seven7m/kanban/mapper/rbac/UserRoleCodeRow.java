package com.seven7m.kanban.mapper.rbac;

import lombok.Data;

import java.util.UUID;

/**
 * 「用户 → 角色编码」的查询投影（项目成员列表批量装配用）。
 *
 * <p>此前返回 {@code List<Map<String, Object>>} 并在业务层强转 {@code (UUID) row.get("user_id")}：
 * 键名写错或类型变化都会在运行时抛 ClassCastException。按《阿里巴巴 Java 开发手册》ORM 规约
 * 改为 POJO 显式映射，映射声明见 {@link UserRoleMapper#selectRoleCodesByProject}。
 */
@Data
public class UserRoleCodeRow {

    private UUID userId;

    /** 角色编码，如 OWNER / PM / DEV（SQL 别名 role_code） */
    private String roleCode;
}

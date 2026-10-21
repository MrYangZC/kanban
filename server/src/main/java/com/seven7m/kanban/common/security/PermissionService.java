package com.seven7m.kanban.common.security;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.toolkit.Db;
import com.seven7m.kanban.entity.rbac.RolePermission;
import com.seven7m.kanban.entity.user.SysUser;
import com.seven7m.kanban.mapper.rbac.RolePermissionMapper;
import com.seven7m.kanban.mapper.user.SysUserMapper;
import com.seven7m.kanban.service.project.ProjectMemberService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * 细粒度权限查询：运行时根据用户在指定项目拥有的角色，查出其具备的权限码集合。
 * 角色 -> 权限码映射存于 role_permission 表（按 role_id 关联），可动态配置，无需改代码。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PermissionService {

    private final ProjectMemberService projectMemberService;
    private final RolePermissionMapper rolePermissionMapper;
    private final SysUserMapper sysUserMapper;

    /**
     * 自注入代理：本类内部方法互相调用时必须经由代理。
     * 同类自调用（self-invocation）会绕过 Spring AOP，
     * 导致 getPermissions / isSuperAdmin 上的 @Cacheable 完全失效
     * （原实现正是因此每次鉴权都打库）。
     */
    @Lazy
    @Autowired
    private PermissionService self;

    /**
     * 返回用户在指定项目内具备的权限码集合。
     * 结果按 (userId, projectId) 缓存，避免每个请求多次查库。
     */
    @Cacheable(cacheNames = "projectPermissions", key = "#userId.toString() + ':' + #projectId.toString()")
    public Set<String> getPermissions(UUID userId, UUID projectId) {
        // 超级管理员直接返回全部权限码，避免其在项目中无角色导致 UI 权限被误判为空
        if (self.isSuperAdmin(userId)) {
            return new HashSet<>(Permission.allCodes());
        }
        Set<UUID> roleIds = projectMemberService.getRoleIds(projectId, userId);
        if (roleIds == null || roleIds.isEmpty()) {
            return Set.of();
        }
        return roleIds.stream()
                .flatMap(roleId -> rolePermissionMapper.selectPermissionsByRole(roleId).stream())
                .collect(Collectors.toSet());
    }

    /** 校验用户在某项目是否具备所需权限码（含缓存） */
    public boolean hasPermission(UUID userId, UUID projectId, String required) {
        // 必须走代理调用，否则 @Cacheable 不生效
        if (self.isSuperAdmin(userId)) {
            return true;
        }
        return self.getPermissions(userId, projectId).contains(required);
    }

    /**
     * 超级管理员判定，与 {@code AdminAuthInterceptor} 口径保持一致：
     * 种子 admin（固定 UUID）或 sys_user.is_admin = true。
     *
     * 结果按 userId 缓存：该判定位于鉴权热路径，若不缓存则每次校验都会查一次用户表。
     * 缓存同样必须经代理调用才生效（见 self 字段说明）。
     */
    @Cacheable(cacheNames = "superAdminFlag", key = "#userId.toString()")
    public boolean isSuperAdmin(UUID userId) {
        if (userId == null) {
            return false;
        }
        if (userId.equals(UserContext.DEFAULT_ADMIN_ID)) {
            return true;
        }
        SysUser user = sysUserMapper.selectById(userId);
        return user != null && Boolean.TRUE.equals(user.getIsAdmin());
    }

    /** 按角色直接查权限码列表（供管理后台使用） */
    @Cacheable(cacheNames = "rolePermissions", key = "#roleId.toString()")
    public List<String> getPermissionsByRole(UUID roleId) {
        return rolePermissionMapper.selectPermissionsByRole(roleId);
    }

    /**
     * 成员角色发生变更后调用，失效该用户在该项目的权限缓存。
     * 与下面的批量失效区分：这里按 (userId, projectId) 精准失效，不影响其他用户。
     *
     * 注意：projectPermissions 使用 ConcurrentMapCache 且无 TTL，若不主动失效，
     * 改成员角色后权限将一直沿用旧值直到重启（已收回的权限仍可用）。
     */
    @CacheEvict(cacheNames = "projectPermissions", key = "#userId.toString() + ':' + #projectId.toString()")
    public void evictUserPermissions(UUID userId, UUID projectId) {
        log.debug("evict projectPermissions: userId={}, projectId={}", userId, projectId);
    }

    /**
     * 删除角色等批量变更时调用：受影响用户无法逐个定位，故全量失效。
     * 同时失效 superAdminFlag，保证 sys_user.is_admin 变更后能立即生效。
     */
    @CacheEvict(cacheNames = {"projectPermissions", "rolePermissions", "superAdminFlag"}, allEntries = true)
    public void evictAllPermissions() {
        log.debug("evict all permission caches");
    }

    /**
     * 设置/覆盖某角色的权限码（先删后插），用于管理后台动态调整。
     * 事务保证：删除后插入失败时整体回滚，避免角色权限全部丢失。
     * 角色权限变更会影响所有持有该角色项目的权限，故全量清空相关缓存。
     */
    @Transactional
    @CacheEvict(cacheNames = {"rolePermissions", "projectPermissions"}, allEntries = true)
    public void setRolePermissions(UUID roleId, List<String> permissionCodes) {
        rolePermissionMapper.delete(Wrappers.<RolePermission>lambdaQuery()
                .eq(RolePermission::getRoleId, roleId));
        if (permissionCodes == null || permissionCodes.isEmpty()) {
            return;
        }
        List<RolePermission> rows = permissionCodes.stream().map(code -> {
            RolePermission rp = new RolePermission();
            rp.setId(UUID.randomUUID());
            rp.setRoleId(roleId);
            rp.setPermissionCode(code);
            rp.setCreatedAt(LocalDateTime.now());
            return rp;
        }).toList();
        Db.saveBatch(rows);
    }
}

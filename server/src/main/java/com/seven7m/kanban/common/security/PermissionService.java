package com.seven7m.kanban.common.security;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.seven7m.kanban.entity.rbac.RolePermission;
import com.seven7m.kanban.mapper.rbac.RolePermissionMapper;
import com.seven7m.kanban.service.project.ProjectMemberService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

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

    /**
     * 返回用户在指定项目内具备的权限码集合。
     * 结果按 (userId, projectId) 缓存，避免每个请求多次查库。
     */
    @Cacheable(cacheNames = "projectPermissions", key = "#userId.toString() + ':' + #projectId.toString()")
    public Set<String> getPermissions(UUID userId, UUID projectId) {
        // 超级管理员（种子 admin）直接返回全部权限码，避免其在项目中无角色导致 UI 权限被误判为空
        if (isSuperAdmin(userId)) {
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
        if (isSuperAdmin(userId)) {
            return true;
        }
        return getPermissions(userId, projectId).contains(required);
    }

    /** 超级管理员（种子 admin）跳过所有权限校验 */
    public boolean isSuperAdmin(UUID userId) {
        return userId != null && userId.equals(UserContext.DEFAULT_ADMIN_ID);
    }

    /** 按角色直接查权限码列表（供管理后台使用） */
    @Cacheable(cacheNames = "rolePermissions", key = "#roleId.toString()")
    public List<String> getPermissionsByRole(UUID roleId) {
        return rolePermissionMapper.selectPermissionsByRole(roleId);
    }

    /**
     * 设置/覆盖某角色的权限码（先删后插），用于管理后台动态调整。
     * 角色权限变更会影响所有持有该角色项目的权限，故全量清空相关缓存。
     */
    @CacheEvict(cacheNames = {"rolePermissions", "projectPermissions"}, allEntries = true)
    public void setRolePermissions(UUID roleId, List<String> permissionCodes) {
        rolePermissionMapper.delete(Wrappers.<RolePermission>lambdaQuery()
                .eq(RolePermission::getRoleId, roleId));
        if (permissionCodes != null) {
            for (String code : permissionCodes) {
                RolePermission rp = new RolePermission();
                rp.setId(UUID.randomUUID());
                rp.setRoleId(roleId);
                rp.setPermissionCode(code);
                rp.setCreatedAt(LocalDateTime.now());
                rolePermissionMapper.insert(rp);
            }
        }
    }
}

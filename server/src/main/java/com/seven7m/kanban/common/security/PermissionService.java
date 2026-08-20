package com.seven7m.kanban.common.security;

import com.seven7m.kanban.entity.rbac.RolePermission;
import com.seven7m.kanban.mapper.rbac.RolePermissionMapper;
import com.seven7m.kanban.service.project.ProjectMemberService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * 操作权限查询：运行时根据用户在指定项目拥有的角色，查出其具备的操作权限集合。
 * 角色 -> 操作权限映射存于 role_permission 表，可动态配置，无需改代码。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PermissionService {

    private final ProjectMemberService projectMemberService;
    private final RolePermissionMapper rolePermissionMapper;

    /**
     * 返回用户在指定项目内具备的操作权限集合。
     * 结果按 (userId, projectId) 缓存，避免每个请求多次查库。
     */
    @Cacheable(cacheNames = "projectPermissions", key = "#userId.toString() + ':' + #projectId.toString()")
    public Set<Permission> getPermissions(UUID userId, UUID projectId) {
        Set<String> roleCodes = projectMemberService.getRoles(userId, projectId);
        if (roleCodes == null || roleCodes.isEmpty()) {
            return Set.of();
        }
        return roleCodes.stream()
                .flatMap(roleCode -> rolePermissionMapper.selectPermissionsByRole(roleCode).stream())
                .map(Permission::valueOf)
                .collect(Collectors.toSet());
    }

    /** 校验用户在某项目是否具备所需权限（含缓存） */
    public boolean hasPermission(UUID userId, UUID projectId, Permission required) {
        return getPermissions(userId, projectId).contains(required);
    }

    /** 按角色直接查权限码（供管理后台使用） */
    @Cacheable(cacheNames = "rolePermissions", key = "#roleCode")
    public List<String> getPermissionsByRole(String roleCode) {
        return rolePermissionMapper.selectPermissionsByRole(roleCode);
    }

    /** 设置/覆盖某角色的权限（先删后插），用于管理后台动态调整 */
    public void setRolePermissions(String roleCode, List<Permission> permissions) {
        rolePermissionMapper.delete(com.baomidou.mybatisplus.core.toolkit.Wrappers
                .<RolePermission>lambdaQuery().eq(RolePermission::getRoleCode, roleCode));
        if (permissions != null) {
            for (Permission p : permissions) {
                RolePermission rp = new RolePermission();
                rp.setRoleCode(roleCode);
                rp.setPermissionCode(p.name());
                rp.setCreatedAt(java.time.LocalDateTime.now());
                rolePermissionMapper.insert(rp);
            }
        }
    }
}

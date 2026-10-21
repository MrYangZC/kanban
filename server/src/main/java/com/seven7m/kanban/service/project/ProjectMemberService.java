package com.seven7m.kanban.service.project;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.seven7m.kanban.common.exception.ServiceException;
import com.seven7m.kanban.common.security.PermissionService;
import com.seven7m.kanban.common.security.UserContext;
import com.seven7m.kanban.entity.project.ProjectRole;
import com.seven7m.kanban.dto.project.AddMemberRequest;
import com.seven7m.kanban.dto.project.ProjectMemberVO;
import com.seven7m.kanban.entity.project.ProjectMember;
import com.seven7m.kanban.mapper.project.ProjectMemberMapper;
import com.seven7m.kanban.entity.rbac.Role;
import com.seven7m.kanban.entity.rbac.UserRole;
import com.seven7m.kanban.mapper.rbac.RoleMapper;
import com.seven7m.kanban.mapper.rbac.UserRoleMapper;
import com.seven7m.kanban.entity.user.SysUser;
import com.seven7m.kanban.mapper.user.SysUserMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ProjectMemberService {

    private final ProjectMemberMapper projectMemberMapper;
    @Autowired
    @Lazy
    private ProjectService projectService;
    /**
     * 用于角色变更后失效 Spring 的 projectPermissions 缓存。
     * 该类被 PermissionService 依赖，此处必须 @Lazy 注入以打破循环依赖。
     */
    @Autowired
    @Lazy
    private PermissionService permissionService;
    private final SysUserMapper userMapper;
    private final RoleMapper roleMapper;
    private final UserRoleMapper userRoleMapper;

    /** 角色缓存：key=projectId:userId，value=角色集合或 NONE（非成员），TTL 5 分钟 */
    private final java.util.Map<String, CacheEntry> roleCache = new java.util.concurrent.ConcurrentHashMap<>();

    private static final long ROLE_CACHE_TTL_MS = 5 * 60 * 1000;

    /** 容量上限：超限整体清空，防止"用户数 × 项目数"很大时单例 Bean 内存无界增长 */
    private static final int ROLE_CACHE_MAX_SIZE = 50_000;

    private void ensureRoleCacheCapacity() {
        if (roleCache.size() >= ROLE_CACHE_MAX_SIZE) {
            roleCache.clear();
        }
    }

    private static class CacheEntry {
        final Set<String> roles;
        final long expireAt;
        CacheEntry(Set<String> roles) {
            this.roles = roles;
            this.expireAt = System.currentTimeMillis() + ROLE_CACHE_TTL_MS;
        }
        boolean alive() {
            return System.currentTimeMillis() < expireAt;
        }
    }

    /** 项目成员列表（含用户展示信息与角色集合），按加入时间排序 */
    public List<ProjectMemberVO> list(UUID projectId) {
        ProjectMember project = projectMemberMapper.selectOne(Wrappers.<ProjectMember>lambdaQuery()
                .eq(ProjectMember::getProjectId, projectId).last("LIMIT 1"));
        if (project == null) {
            return List.of();
        }
        List<ProjectMember> memberList = projectMemberMapper.selectList(Wrappers.<ProjectMember>lambdaQuery()
                        .eq(ProjectMember::getProjectId, projectId)
                        .orderByAsc(ProjectMember::getCreatedAt));
        Set<UUID> userIds = memberList.stream().map(ProjectMember::getUserId).collect(Collectors.toSet());
        Map<UUID, SysUser> userMap = userIds.isEmpty() ? Map.of() :
                userMapper.selectBatchIds(userIds).stream()
                        .collect(Collectors.toMap(SysUser::getId, Function.identity()));
        Map<UUID, Set<String>> roleMap = buildRoleMap(projectId);
        return memberList.stream().map(m -> toVO(m, userMap, roleMap)).collect(Collectors.toList());
    }

    private Map<UUID, Set<String>> buildRoleMap(UUID projectId) {
        Map<UUID, Set<String>> map = new HashMap<>();
        List<Map<String, Object>> rows = userRoleMapper.selectRoleCodesByProject(projectId);
        for (Map<String, Object> row : rows) {
            UUID userId = (UUID) row.get("user_id");
            String code = (String) row.get("code");
            map.computeIfAbsent(userId, k -> new LinkedHashSet<>()).add(code);
        }
        return map;
    }

    @Transactional
    public ProjectMemberVO add(UUID projectId, AddMemberRequest req) {
        requireAdmin(projectId);
        validateRoles(req.getRoles());
        if (userMapper.selectById(req.getUserId()) == null) {
            throw new ServiceException(404, "用户不存在");
        }
        if (projectService.isMember(projectId, req.getUserId())) {
            throw new ServiceException(400, "该用户已是项目成员");
        }
        ProjectMember m = new ProjectMember();
        m.setId(UUID.randomUUID());
        m.setProjectId(projectId);
        m.setUserId(req.getUserId());
        m.setCreatedAt(LocalDateTime.now());
        projectMemberMapper.insert(m);
        grantRoles(projectId, req.getUserId(), req.getRoles());
        evictRoleCache(projectId, req.getUserId());
        permissionService.evictUserPermissions(req.getUserId(), projectId);
        return toVO(m);
    }

    @Transactional
    public void remove(UUID projectId, UUID userId) {
        requireAdmin(projectId);
        if (!projectService.isMember(projectId, userId)) {
            throw new ServiceException(404, "该用户不是项目成员");
        }
        // 不允许移除最后一个 OWNER，否则项目失去管理者
        if (getRoles(projectId, userId).contains(ProjectRole.OWNER) && countOwner(projectId) <= 1) {
            throw new ServiceException(400, "不能移除最后一个项目所有者");
        }
        projectMemberMapper.delete(Wrappers.<ProjectMember>lambdaQuery()
                .eq(ProjectMember::getProjectId, projectId)
                .eq(ProjectMember::getUserId, userId));
        userRoleMapper.delete(Wrappers.<UserRole>lambdaQuery()
                .eq(UserRole::getProjectId, projectId)
                .eq(UserRole::getUserId, userId));
        evictRoleCache(projectId, userId);
        permissionService.evictUserPermissions(userId, projectId);
    }

    @Transactional
    public ProjectMemberVO updateRoles(UUID projectId, UUID userId, List<String> roles) {
        requireAdmin(projectId);
        validateRoles(roles);
        ProjectMember m = projectMemberMapper.selectOne(Wrappers.<ProjectMember>lambdaQuery()
                .eq(ProjectMember::getProjectId, projectId)
                .eq(ProjectMember::getUserId, userId));
        if (m == null) {
            throw new ServiceException(404, "该用户不是项目成员");
        }
        // 不允许把最后一个 OWNER 的角色全部移除
        Set<String> oldRoles = getRoles(projectId, userId);
        if (oldRoles.contains(ProjectRole.OWNER) && !roles.contains(ProjectRole.OWNER) && countOwner(projectId) <= 1) {
            throw new ServiceException(400, "项目至少需保留一个所有者");
        }
        userRoleMapper.delete(Wrappers.<UserRole>lambdaQuery()
                .eq(UserRole::getProjectId, projectId)
                .eq(UserRole::getUserId, userId));
        grantRoles(projectId, userId, roles);
        evictRoleCache(projectId, userId);
        permissionService.evictUserPermissions(userId, projectId);
        return toVO(m);
    }

    /**
     * 查询用户在指定项目中的角色集合，带短 TTL 缓存避免鉴权拦截器每次查库。
     * 非成员返回空集合。缓存 key 为 projectId:userId，TTL 5 分钟自动失效。
     */
    public Set<String> getRoles(UUID projectId, UUID userId) {
        if (projectId == null || userId == null) {
            return Set.of();
        }
        String cacheKey = projectId + ":" + userId;
        CacheEntry entry = roleCache.get(cacheKey);
        if (entry != null && entry.alive()) {
            return entry.roles;
        }
        if (!projectService.isMember(projectId, userId)) {
            ensureRoleCacheCapacity();
            roleCache.put(cacheKey, new CacheEntry(Set.of()));
            return Set.of();
        }
        Set<String> roles = new LinkedHashSet<>(userRoleMapper.selectRoleCodesByProjectAndUser(projectId, userId));
        ensureRoleCacheCapacity();
        roleCache.put(cacheKey, new CacheEntry(roles));
        return roles;
    }

    /**
     * 查询用户在指定项目中的角色 id 集合（供 role_permission 按 role_id 联查权限）。
     */
    public Set<UUID> getRoleIds(UUID projectId, UUID userId) {
        if (projectId == null || userId == null) {
            return Set.of();
        }
        if (!projectService.isMember(projectId, userId)) {
            return Set.of();
        }
        return new LinkedHashSet<>(userRoleMapper.selectRoleIdsByProjectAndUser(projectId, userId));
    }

    /**
     * 角色变更后使缓存失效，保证权限及时生效。
     * userId 为 null 时失效该项目下的全部用户（原实现只删 "pid:null" 这个不存在的键，等同空操作）。
     */
    public void evictRoleCache(UUID projectId, UUID userId) {
        if (projectId == null) {
            // 项目维度未知，只能全量失效（极少使用）
            roleCache.clear();
            return;
        }
        if (userId == null) {
            String prefix = projectId + ":";
            roleCache.keySet().removeIf(k -> k.startsWith(prefix));
            return;
        }
        roleCache.remove(projectId + ":" + userId);
    }

    /** 写入用户-角色关联（RBAC：一人可多角色），供项目创建等内部逻辑调用 */
    public void grantRoles(UUID projectId, UUID userId, List<String> roles) {
        for (String code : roles) {
            Role role = roleMapper.selectOne(Wrappers.<Role>lambdaQuery()
                    .eq(Role::getCode, code)
                    .last("LIMIT 1"));
            if (role == null) {
                throw new ServiceException(400, "角色不存在：" + code);
            }
            UserRole ur = new UserRole();
            ur.setId(UUID.randomUUID());
            ur.setUserId(userId);
            ur.setRoleId(role.getId());
            ur.setProjectId(projectId);
            ur.setCreatedAt(LocalDateTime.now());
            userRoleMapper.insert(ur);
        }
    }

    private void validateRoles(List<String> roles) {
        for (String r : roles) {
            if (!ProjectRole.isValid(r)) {
                throw new ServiceException(400, "不支持的角色：" + r);
            }
        }
    }

    private long countOwner(UUID projectId) {
        return userRoleMapper.selectCount(Wrappers.<UserRole>lambdaQuery()
                .eq(UserRole::getProjectId, projectId)
                .inSql(UserRole::getRoleId, "SELECT id FROM role WHERE code = '" + ProjectRole.OWNER + "'"));
    }

    private void requireAdmin(UUID projectId) {
        if (!projectService.isOwner(projectId, UserContext.getUserId())) {
            throw new ServiceException(403, "仅项目所有者可管理成员");
        }
    }

    private ProjectMemberVO toVO(ProjectMember m, Map<UUID, SysUser> userMap, Map<UUID, Set<String>> roleMap) {
        ProjectMemberVO vo = new ProjectMemberVO();
        vo.setId(m.getId());
        vo.setProjectId(m.getProjectId());
        vo.setUserId(m.getUserId());
        vo.setRoles(roleMap.getOrDefault(m.getUserId(), Set.of()).stream().toList());
        vo.setCreatedAt(m.getCreatedAt());
        SysUser u = userMap.get(m.getUserId());
        if (u != null) {
            vo.setUsername(u.getUsername());
            vo.setDisplayName(u.getDisplayName());
            vo.setAvatarUrl(u.getAvatarUrl());
        }
        return vo;
    }

    private ProjectMemberVO toVO(ProjectMember m) {
        ProjectMemberVO vo = new ProjectMemberVO();
        vo.setId(m.getId());
        vo.setProjectId(m.getProjectId());
        vo.setUserId(m.getUserId());
        vo.setRoles(getRoles(m.getProjectId(), m.getUserId()).stream().toList());
        vo.setCreatedAt(m.getCreatedAt());
        SysUser u = userMapper.selectById(m.getUserId());
        if (u != null) {
            vo.setUsername(u.getUsername());
            vo.setDisplayName(u.getDisplayName());
            vo.setAvatarUrl(u.getAvatarUrl());
        }
        return vo;
    }
}

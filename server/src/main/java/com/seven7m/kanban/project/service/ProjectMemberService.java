package com.seven7m.kanban.project.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.seven7m.kanban.common.exception.ServiceException;
import com.seven7m.kanban.common.security.UserContext;
import com.seven7m.kanban.project.ProjectRole;
import com.seven7m.kanban.project.dto.AddMemberRequest;
import com.seven7m.kanban.project.dto.ProjectMemberVO;
import com.seven7m.kanban.project.entity.Project;
import com.seven7m.kanban.project.entity.ProjectMember;
import com.seven7m.kanban.project.mapper.ProjectMemberMapper;
import com.seven7m.kanban.user.entity.SysUser;
import com.seven7m.kanban.user.mapper.SysUserMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ProjectMemberService {

    private final ProjectMemberMapper projectMemberMapper;
    @Autowired
    @Lazy
    private ProjectService projectService;
    private final SysUserMapper userMapper;

    /** 角色缓存：key=projectId:userId，value=角色或 NONE（非成员），TTL 5 分钟 */
    private final java.util.Map<String, CacheEntry> roleCache = new java.util.concurrent.ConcurrentHashMap<>();

    private static final long ROLE_CACHE_TTL_MS = 5 * 60 * 1000;

    private static class CacheEntry {
        final String role;
        final long expireAt;
        CacheEntry(String role) {
            this.role = role;
            this.expireAt = System.currentTimeMillis() + ROLE_CACHE_TTL_MS;
        }
        boolean alive() {
            return System.currentTimeMillis() < expireAt;
        }
    }

    /** 项目成员列表（含用户展示信息），按加入时间排序 */
    public List<ProjectMemberVO> list(UUID projectId) {
        ProjectMember project = projectMemberMapper.selectOne(Wrappers.<ProjectMember>lambdaQuery()
                .eq(ProjectMember::getProjectId, projectId).last("LIMIT 1"));
        if (project == null) {
            // 项目可能被删除，直接返回空列表
            return List.of();
        }
        return projectMemberMapper.selectList(Wrappers.<ProjectMember>lambdaQuery()
                        .eq(ProjectMember::getProjectId, projectId)
                        .orderByAsc(ProjectMember::getCreatedAt))
                .stream().map(this::toVO).collect(Collectors.toList());
    }

    @Transactional
    public ProjectMemberVO add(UUID projectId, AddMemberRequest req) {
        requireOwner(projectId);
        if (!ProjectRole.isValid(req.getRole())) {
            throw new ServiceException(400, "不支持的角色：" + req.getRole());
        }
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
        m.setRole(req.getRole());
        m.setCreatedAt(LocalDateTime.now());
        projectMemberMapper.insert(m);
        evictRoleCache(projectId, req.getUserId());
        return toVO(m);
    }

    @Transactional
    public void remove(UUID projectId, UUID userId) {
        requireOwner(projectId);
        if (!projectService.isMember(projectId, userId)) {
            throw new ServiceException(404, "该用户不是项目成员");
        }
        // 不允许移除最后一个 OWNER，否则项目失去管理者
        long ownerCount = projectMemberMapper.selectCount(Wrappers.<ProjectMember>lambdaQuery()
                .eq(ProjectMember::getProjectId, projectId)
                .eq(ProjectMember::getRole, ProjectRole.OWNER));
        if (ownerCount <= 1 && projectService.isOwner(projectId, userId)) {
            throw new ServiceException(400, "不能移除最后一个项目所有者");
        }
        projectMemberMapper.delete(Wrappers.<ProjectMember>lambdaQuery()
                .eq(ProjectMember::getProjectId, projectId)
                .eq(ProjectMember::getUserId, userId));
        evictRoleCache(projectId, userId);
    }

    @Transactional
    public ProjectMemberVO updateRole(UUID projectId, UUID userId, String role) {
        requireOwner(projectId);
        if (!ProjectRole.isValid(role)) {
            throw new ServiceException(400, "不支持的角色：" + role);
        }
        ProjectMember m = projectMemberMapper.selectOne(Wrappers.<ProjectMember>lambdaQuery()
                .eq(ProjectMember::getProjectId, projectId)
                .eq(ProjectMember::getUserId, userId));
        if (m == null) {
            throw new ServiceException(404, "该用户不是项目成员");
        }
        // 不允许把最后一个 OWNER 降级为 MEMBER
        long ownerCount = projectMemberMapper.selectCount(Wrappers.<ProjectMember>lambdaQuery()
                .eq(ProjectMember::getProjectId, projectId)
                .eq(ProjectMember::getRole, ProjectRole.OWNER));
        if (ownerCount <= 1 && ProjectRole.isOwner(m.getRole()) && !ProjectRole.isOwner(role)) {
            throw new ServiceException(400, "项目至少需保留一个所有者");
        }
        m.setRole(role);
        projectMemberMapper.updateById(m);
        evictRoleCache(projectId, userId);
        return toVO(m);
    }

    /**
     * 查询用户在指定项目中的角色，带短 TTL 缓存避免鉴权拦截器每次查库。
     * 非成员返回 null。缓存 key 为 projectId:userId，TTL 5 分钟自动失效。
     */
    public String getRole(UUID projectId, UUID userId) {
        if (projectId == null || userId == null) {
            return null;
        }
        String cacheKey = projectId + ":" + userId;
        CacheEntry entry = roleCache.get(cacheKey);
        if (entry != null && entry.alive()) {
            return "NONE".equals(entry.role) ? null : entry.role;
        }
        ProjectMember m = projectMemberMapper.selectOne(Wrappers.<ProjectMember>lambdaQuery()
                .eq(ProjectMember::getProjectId, projectId)
                .eq(ProjectMember::getUserId, userId)
                .last("LIMIT 1"));
        String role = m == null ? null : m.getRole();
        roleCache.put(cacheKey, new CacheEntry(role == null ? "NONE" : role));
        return role;
    }

    /** 角色变更后使缓存失效，保证权限及时生效 */
    public void evictRoleCache(UUID projectId, UUID userId) {
        roleCache.remove(projectId + ":" + userId);
        if (projectId == null) {
            // 全量失效（极少使用）
            roleCache.clear();
        }
    }

    private void requireOwner(UUID projectId) {
        if (!projectService.isOwner(projectId, UserContext.getUserId())) {
            throw new ServiceException(403, "仅项目所有者可管理成员");
        }
    }

    private ProjectMemberVO toVO(ProjectMember m) {
        ProjectMemberVO vo = new ProjectMemberVO();
        vo.setId(m.getId());
        vo.setProjectId(m.getProjectId());
        vo.setUserId(m.getUserId());
        vo.setRole(m.getRole());
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

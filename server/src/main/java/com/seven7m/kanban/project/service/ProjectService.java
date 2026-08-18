package com.seven7m.kanban.project.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.seven7m.kanban.common.exception.ServiceException;
import com.seven7m.kanban.common.security.UserContext;
import com.seven7m.kanban.project.ProjectInitializer;
import com.seven7m.kanban.project.ProjectRole;
import com.seven7m.kanban.project.dto.CreateProjectRequest;
import com.seven7m.kanban.project.dto.ProjectStats;
import com.seven7m.kanban.project.dto.ProjectVO;
import com.seven7m.kanban.project.dto.UpdateProjectRequest;
import com.seven7m.kanban.project.entity.Project;
import com.seven7m.kanban.project.entity.ProjectMember;
import com.seven7m.kanban.project.mapper.ProjectMapper;
import com.seven7m.kanban.project.mapper.ProjectMemberMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ProjectService {

    private final ProjectMapper projectMapper;
    private final ProjectMemberMapper projectMemberMapper;
    private final ProjectMemberService projectMemberService;
    private final ProjectInitializer projectInitializer;

    @Transactional
    public ProjectVO create(CreateProjectRequest req) {
        long dup = projectMapper.selectCount(Wrappers.<Project>lambdaQuery()
                .eq(Project::getWorkspaceId, req.getWorkspaceId())
                .eq(Project::getProjectKey, req.getProjectKey()));
        if (dup > 0) {
            throw new ServiceException(400, "项目 Key 已存在：" + req.getProjectKey());
        }

        UUID currentUser = UserContext.getUserId();
        Project project = new Project();
        BeanUtils.copyProperties(req, project);
        project.setId(UUID.randomUUID());
        project.setStatus("ACTIVE");
        project.setCreatedBy(currentUser);
        project.setCreatedAt(LocalDateTime.now());
        project.setUpdatedAt(LocalDateTime.now());
        projectMapper.insert(project);

        // 创建人自动成为项目 OWNER，数据权限据此隔离
        ProjectMember owner = new ProjectMember();
        owner.setId(UUID.randomUUID());
        owner.setProjectId(project.getId());
        owner.setUserId(currentUser);
        owner.setRole(ProjectRole.OWNER);
        owner.setCreatedAt(LocalDateTime.now());
        projectMemberMapper.insert(owner);

        // 事务内初始化默认数据（类型/状态/工作流/流转/看板）
        projectInitializer.initDefaults(project.getId(), currentUser);

        return toVO(project);
    }

    @Transactional
    public ProjectVO update(UUID id, UpdateProjectRequest req) {
        // 1. 校验项目存在且未删除
        Project p = projectMapper.selectById(id);
        if (p == null || p.getDeleted() != null && p.getDeleted() == 1) {
            throw new ServiceException(404, "项目不存在");
        }
        // 2. 权限校验：至少 MEMBER 可编辑（OWNER 自动满足）
        if (!isMember(id, UserContext.getUserId())) {
            throw new ServiceException(403, "无项目访问权限");
        }

        // 3. 仅覆盖非空字段（不改动 projectKey / workspaceId / createdBy 等不可变字段）
        if (req.getName() != null) {
            p.setName(req.getName());
        }
        if (req.getDescription() != null) {
            p.setDescription(req.getDescription());
        }
        if (req.getAvatarUrl() != null) {
            p.setAvatarUrl(req.getAvatarUrl());
        }
        if (req.getStatus() != null) {
            p.setStatus(req.getStatus());
        }
        if (req.getLeadUserId() != null) {
            p.setLeadUserId(req.getLeadUserId());
        }
        p.setUpdatedAt(LocalDateTime.now());
        projectMapper.updateById(p);
        return toVO(p);
    }

    public List<ProjectVO> listByWorkspace(UUID workspaceId) {
        UUID currentUser = UserContext.getUserId();
        List<Project> projects = projectMapper.selectList(Wrappers.<Project>lambdaQuery()
                        .eq(Project::getWorkspaceId, workspaceId)
                        .eq(Project::getDeleted, 0)
                        // 数据权限：仅返回当前用户为成员的项目
                        .inSql(Project::getId,
                                "SELECT project_id FROM project_member WHERE user_id = '" + currentUser + "'")
                        .orderByDesc(Project::getCreatedAt));
        if (projects.isEmpty()) {
            return java.util.Collections.emptyList();
        }
        // 单次聚合统计，避免 N+1
        List<ProjectStats> stats = projectMapper.selectStatsByProjectIds(
                projects.stream().map(Project::getId).collect(Collectors.toList()));
        java.util.Map<UUID, ProjectStats> statsMap = stats.stream()
                .collect(Collectors.toMap(ProjectStats::getProjectId, s -> s));
        return projects.stream().map(p -> {
            ProjectVO vo = toVO(p);
            vo.setStats(statsMap.getOrDefault(p.getId(), new ProjectStats()));
            return vo;
        }).collect(Collectors.toList());
    }

    public ProjectVO get(UUID id) {
        Project p = projectMapper.selectById(id);
        if (p == null || p.getDeleted() != null && p.getDeleted() == 1) {
            throw new ServiceException(404, "项目不存在");
        }
        // 数据权限：非项目成员拒绝访问
        if (!isMember(id, UserContext.getUserId())) {
            throw new ServiceException(403, "无项目访问权限");
        }
        ProjectVO vo = toVO(p);
        List<ProjectStats> stats = projectMapper.selectStatsByProjectIds(java.util.List.of(id));
        vo.setStats(stats.isEmpty() ? new ProjectStats() : stats.get(0));
        return vo;
    }

    /** 判断项目 Key 是否已存在（跨 workspace 唯一约束） */
    public boolean existsByKey(String projectKey) {
        return projectMapper.selectCount(Wrappers.<Project>lambdaQuery()
                .eq(Project::getProjectKey, projectKey)) > 0;
    }

    /** 判断某用户是否为项目成员（含任意角色） */
    public boolean isMember(UUID projectId, UUID userId) {
        return projectMemberMapper.selectCount(Wrappers.<ProjectMember>lambdaQuery()
                .eq(ProjectMember::getProjectId, projectId)
                .eq(ProjectMember::getUserId, userId)) > 0;
    }

    /** 判断某用户是否为项目 OWNER */
    public boolean isOwner(UUID projectId, UUID userId) {
        return projectMemberMapper.selectCount(Wrappers.<ProjectMember>lambdaQuery()
                .eq(ProjectMember::getProjectId, projectId)
                .eq(ProjectMember::getUserId, userId)
                .eq(ProjectMember::getRole, ProjectRole.OWNER)) > 0;
    }

    private ProjectVO toVO(Project p) {
        ProjectVO vo = new ProjectVO();
        BeanUtils.copyProperties(p, vo);
        return vo;
    }

    /** 根据角色判断当前用户是否可写（OWNER 或 MEMBER）。 */
    public boolean canWrite(UUID projectId, UUID userId) {
        String role = projectMemberService.getRole(projectId, userId);
        return ProjectRole.OWNER.equals(role) || ProjectRole.MEMBER.equals(role);
    }
}

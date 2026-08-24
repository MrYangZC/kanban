package com.seven7m.kanban.service.project;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.seven7m.kanban.common.domain.PageResult;
import com.seven7m.kanban.common.exception.ServiceException;
import com.seven7m.kanban.common.security.UserContext;
import com.seven7m.kanban.entity.project.ProjectCategoryKey;
import com.seven7m.kanban.entity.project.ProjectInitializer;
import com.seven7m.kanban.entity.project.ProjectRole;
import com.seven7m.kanban.entity.project.ProjectStatus;
import com.seven7m.kanban.dto.project.CreateProjectRequest;
import com.seven7m.kanban.dto.project.ProjectCategoryCountsVO;
import com.seven7m.kanban.dto.project.ProjectFilterRequest;
import com.seven7m.kanban.dto.project.ProjectStats;
import com.seven7m.kanban.dto.project.ProjectVO;
import com.seven7m.kanban.dto.project.UpdateProjectRequest;
import com.seven7m.kanban.entity.project.Project;
import com.seven7m.kanban.entity.project.ProjectFavorite;
import com.seven7m.kanban.entity.project.ProjectMember;
import com.seven7m.kanban.mapper.project.ProjectFavoriteMapper;
import com.seven7m.kanban.mapper.project.ProjectMapper;
import com.seven7m.kanban.mapper.project.ProjectMemberMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ProjectService {

    private final ProjectMapper projectMapper;
    private final ProjectMemberMapper projectMemberMapper;
    private final ProjectFavoriteMapper projectFavoriteMapper;
    private final ProjectMemberService projectMemberService;
    private final ProjectInitializer projectInitializer;

    @Transactional
    public ProjectVO create(CreateProjectRequest req) {
        long dup = projectMapper.selectCount(Wrappers.<Project>lambdaQuery()
                .eq(Project::getProjectKey, req.getProjectKey()));
        if (dup > 0) {
            throw new ServiceException(400, "项目 Key 已存在：" + req.getProjectKey());
        }

        UUID currentUser = UserContext.getUserId();
        Project project = new Project();
        BeanUtils.copyProperties(req, project);
        project.setId(UUID.randomUUID());
        project.setStatus(ProjectStatus.ACTIVE.name());
        project.setCreatedBy(currentUser);
        project.setCreatedAt(LocalDateTime.now());
        project.setUpdatedAt(LocalDateTime.now());
        projectMapper.insert(project);

        ProjectMember owner = new ProjectMember();
        owner.setId(UUID.randomUUID());
        owner.setProjectId(project.getId());
        owner.setUserId(currentUser);
        owner.setCreatedAt(LocalDateTime.now());
        projectMemberMapper.insert(owner);
        projectMemberService.grantRoles(project.getId(), currentUser, List.of(ProjectRole.OWNER));

        projectInitializer.initDefaults(project.getId(), currentUser);

        return toVO(project);
    }

    @Transactional
    public ProjectVO update(UUID id, UpdateProjectRequest req) {
        Project p = projectMapper.selectById(id);
        if (p == null || p.getDeleted() != null && p.getDeleted() == 1) {
            throw new ServiceException(404, "项目不存在");
        }
        UUID currentUser = UserContext.getUserId();
        if (!canWrite(id, currentUser)) {
            throw new ServiceException(403, "无项目编辑权限");
        }

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

    /**
     * 查询当前用户可见的全部项目（向后兼容入口，等价于 listAll(ALL)）。
     */
    public List<ProjectVO> listAll() {
        return listAll(ProjectCategoryKey.ALL);
    }

    /**
     * 按分类查询当前用户的项目列表。
     * - ALL/PARTICIPATED：项目成员且 status=ACTIVE
     * - MANAGED：角色含 OWNER/ADMIN/PM 且 status=ACTIVE
     * - FAVORITED：project_favorite 含当前用户且 status=ACTIVE
     * - ARCHIVED：项目成员且 status=ARCHIVED
     * 查询后填充 favorited 与 canEdit 字段。
     */
    public List<ProjectVO> listAll(ProjectCategoryKey category) {
        if (category == null) {
            category = ProjectCategoryKey.ALL;
        }
        UUID currentUser = UserContext.getUserId();
        String statusFilter = ProjectCategoryKey.ARCHIVED == category
                ? ProjectStatus.ARCHIVED.name()
                : ProjectStatus.ACTIVE.name();

        var query = Wrappers.<Project>lambdaQuery()
                .eq(Project::getDeleted, 0)
                .eq(Project::getStatus, statusFilter)
                .orderByDesc(Project::getCreatedAt);

        switch (category) {
            case MANAGED -> query.apply("id IN (SELECT ur.project_id FROM user_role ur " +
                    "JOIN role r ON r.id = ur.role_id " +
                    "WHERE ur.user_id = {0} " +
                    "AND r.code IN ({1},{2},{3}))",
                    currentUser, ProjectRole.OWNER, ProjectRole.ADMIN, ProjectRole.PM);
            case FAVORITED -> query.apply("id IN (SELECT project_id FROM project_favorite WHERE user_id = {0})", currentUser);
            default -> query.apply("id IN (SELECT project_id FROM project_member WHERE user_id = {0})", currentUser);
        }

        List<Project> projects = projectMapper.selectList(query);
        if (projects.isEmpty()) {
            return Collections.emptyList();
        }

        List<UUID> projectIds = projects.stream().map(Project::getId).collect(Collectors.toList());
        List<ProjectStats> stats = projectMapper.selectStatsByProjectIds(projectIds);
        Map<UUID, ProjectStats> statsMap = stats.stream()
                .collect(Collectors.toMap(ProjectStats::getProjectId, s -> s));

        Set<UUID> favoritedIds = listFavoritedProjectIds(currentUser, projectIds);

        return projects.stream().map(p -> {
            ProjectVO vo = toVO(p);
            vo.setStats(statsMap.getOrDefault(p.getId(), new ProjectStats()));
            vo.setFavorited(favoritedIds.contains(p.getId()));
            vo.setCanEdit(canManage(p.getId(), currentUser));
            vo.setCanManage(canManage(p.getId(), currentUser));
            return vo;
        }).collect(Collectors.toList());
    }

    /**
     * 分页查询当前用户的项目列表，支持分类筛选、关键词模糊匹配与排序。
     * 分页模式：先 selectCount（不带 ORDER BY），再加排序与 LIMIT/OFFSET，最后 selectList。
     */
    public PageResult<ProjectVO> listByFilter(ProjectFilterRequest req) {
        ProjectCategoryKey category = ProjectCategoryKey.of(req.getCategory());
        UUID currentUser = UserContext.getUserId();
        String statusFilter = ProjectCategoryKey.ARCHIVED == category
                ? ProjectStatus.ARCHIVED.name()
                : ProjectStatus.ACTIVE.name();

        var query = Wrappers.<Project>lambdaQuery()
                .eq(Project::getDeleted, 0)
                .eq(Project::getStatus, statusFilter);

        switch (category) {
            case MANAGED -> query.apply("id IN (SELECT ur.project_id FROM user_role ur " +
                    "JOIN role r ON r.id = ur.role_id " +
                    "WHERE ur.user_id = {0} " +
                    "AND r.code IN ({1},{2},{3}))",
                    currentUser, ProjectRole.OWNER, ProjectRole.ADMIN, ProjectRole.PM);
            case FAVORITED -> query.apply("id IN (SELECT project_id FROM project_favorite WHERE user_id = {0})", currentUser);
            default -> query.apply("id IN (SELECT project_id FROM project_member WHERE user_id = {0})", currentUser);
        }

        if (req.getKeyword() != null && !req.getKeyword().isBlank()) {
            String kw = req.getKeyword().trim();
            query.and(w -> w.like(Project::getName, kw).or().like(Project::getProjectKey, kw));
        }

        int page = req.getPage() == null || req.getPage() < 1 ? 1 : req.getPage();
        int size = req.getSize() == null || req.getSize() < 1 ? 10 : Math.min(req.getSize(), 100);

        long total = projectMapper.selectCount(query);

        applyProjectSort(query, req.getSortField(), req.getSortOrder());
        query.last("LIMIT " + size + " OFFSET " + ((long) (page - 1) * size));

        List<Project> projects = projectMapper.selectList(query);
        if (projects.isEmpty()) {
            return new PageResult<>(total, page, size, Collections.emptyList());
        }

        List<UUID> projectIds = projects.stream().map(Project::getId).collect(Collectors.toList());
        List<ProjectStats> stats = projectMapper.selectStatsByProjectIds(projectIds);
        Map<UUID, ProjectStats> statsMap = stats.stream()
                .collect(Collectors.toMap(ProjectStats::getProjectId, s -> s));
        Set<UUID> favoritedIds = listFavoritedProjectIds(currentUser, projectIds);

        List<ProjectVO> voList = projects.stream().map(p -> {
            ProjectVO vo = toVO(p);
            vo.setStats(statsMap.getOrDefault(p.getId(), new ProjectStats()));
            vo.setFavorited(favoritedIds.contains(p.getId()));
            vo.setCanEdit(canManage(p.getId(), currentUser));
            vo.setCanManage(canManage(p.getId(), currentUser));
            return vo;
        }).collect(Collectors.toList());

        return new PageResult<>(total, page, size, voList);
    }

    /** 排序字段白名单，防止 SQL 注入 */
    private void applyProjectSort(LambdaQueryWrapper<Project> q, String sortField, String sortOrder) {
        if (sortField == null || sortField.isBlank()) {
            q.orderByDesc(Project::getCreatedAt);
            return;
        }
        boolean asc = !"desc".equalsIgnoreCase(sortOrder);
        switch (sortField) {
            case "createdAt" -> { if (asc) q.orderByAsc(Project::getCreatedAt); else q.orderByDesc(Project::getCreatedAt); }
            case "updatedAt" -> { if (asc) q.orderByAsc(Project::getUpdatedAt); else q.orderByDesc(Project::getUpdatedAt); }
            case "name" -> { if (asc) q.orderByAsc(Project::getName); else q.orderByDesc(Project::getName); }
            case "projectKey" -> { if (asc) q.orderByAsc(Project::getProjectKey); else q.orderByDesc(Project::getProjectKey); }
            default -> q.orderByDesc(Project::getCreatedAt);
        }
    }

    /**
     * 查询当前用户五个分类的项目计数，满足包含关系：managed ≤ participated ≤ all。
     */
    public ProjectCategoryCountsVO getCategoryCounts() {
        UUID currentUser = UserContext.getUserId();
        ProjectCategoryCountsVO vo = new ProjectCategoryCountsVO();

        vo.setAll(projectMapper.selectCount(Wrappers.<Project>lambdaQuery()
                .eq(Project::getDeleted, 0)
                .eq(Project::getStatus, ProjectStatus.ACTIVE.name())
                .apply("id IN (SELECT project_id FROM project_member WHERE user_id = {0})", currentUser)));

        vo.setManaged(projectMapper.selectCount(Wrappers.<Project>lambdaQuery()
                .eq(Project::getDeleted, 0)
                .eq(Project::getStatus, ProjectStatus.ACTIVE.name())
                .apply("id IN (SELECT ur.project_id FROM user_role ur " +
                        "JOIN role r ON r.id = ur.role_id " +
                        "WHERE ur.user_id = {0} " +
                        "AND r.code IN ({1},{2},{3}))",
                        currentUser, ProjectRole.OWNER, ProjectRole.ADMIN, ProjectRole.PM)));

        vo.setParticipated(vo.getAll());

        vo.setFavorited(projectMapper.selectCount(Wrappers.<Project>lambdaQuery()
                .eq(Project::getDeleted, 0)
                .eq(Project::getStatus, ProjectStatus.ACTIVE.name())
                .apply("id IN (SELECT project_id FROM project_favorite WHERE user_id = {0})", currentUser)));

        vo.setArchived(projectMapper.selectCount(Wrappers.<Project>lambdaQuery()
                .eq(Project::getDeleted, 0)
                .eq(Project::getStatus, ProjectStatus.ARCHIVED.name())
                .apply("id IN (SELECT project_id FROM project_member WHERE user_id = {0})", currentUser)));

        return vo;
    }

    public ProjectVO get(UUID id) {
        Project p = projectMapper.selectById(id);
        if (p == null || p.getDeleted() != null && p.getDeleted() == 1) {
            throw new ServiceException(404, "项目不存在");
        }
        if (!isMember(id, UserContext.getUserId())) {
            throw new ServiceException(403, "无项目访问权限");
        }
        ProjectVO vo = toVO(p);
        List<ProjectStats> stats = projectMapper.selectStatsByProjectIds(List.of(id));
        vo.setStats(stats.isEmpty() ? new ProjectStats() : stats.get(0));
        UUID currentUser = UserContext.getUserId();
        vo.setFavorited(projectFavoriteMapper.selectCount(Wrappers.<ProjectFavorite>lambdaQuery()
                .eq(ProjectFavorite::getProjectId, id)
                .eq(ProjectFavorite::getUserId, currentUser)) > 0);
        vo.setCanEdit(canManage(id, currentUser));
        vo.setCanManage(canManage(id, currentUser));
        return vo;
    }

    /**
     * 归档项目：仅 OWNER/ADMIN 可操作，项目须为 ACTIVE 状态。
     */
    @Transactional
    public void archive(UUID projectId, UUID userId) {
        Project p = requireProject(projectId);
        if (!isAdmin(projectId, userId)) {
            throw new ServiceException(403, "仅项目管理员可归档");
        }
        if (ProjectStatus.ARCHIVED == ProjectStatus.of(p.getStatus())) {
            throw new ServiceException(400, "项目已归档");
        }
        p.setStatus(ProjectStatus.ARCHIVED.name());
        p.setUpdatedAt(LocalDateTime.now());
        projectMapper.updateById(p);
        projectMemberService.evictRoleCache(projectId, null);
    }

    /**
     * 取消归档：仅 OWNER/ADMIN 可操作，项目须为 ARCHIVED 状态。
     */
    @Transactional
    public void unarchive(UUID projectId, UUID userId) {
        Project p = requireProject(projectId);
        if (!isAdmin(projectId, userId)) {
            throw new ServiceException(403, "仅项目管理员可操作");
        }
        if (ProjectStatus.ACTIVE == ProjectStatus.of(p.getStatus())) {
            throw new ServiceException(400, "项目未归档");
        }
        p.setStatus(ProjectStatus.ACTIVE.name());
        p.setUpdatedAt(LocalDateTime.now());
        projectMapper.updateById(p);
        projectMemberService.evictRoleCache(projectId, null);
    }

    public boolean existsByKey(String projectKey) {
        return projectMapper.selectCount(Wrappers.<Project>lambdaQuery()
                .eq(Project::getProjectKey, projectKey)) > 0;
    }

    public boolean isMember(UUID projectId, UUID userId) {
        if (userId != null && userId.equals(com.seven7m.kanban.common.security.UserContext.DEFAULT_ADMIN_ID)) {
            return true;
        }
        return projectMemberMapper.selectCount(Wrappers.<ProjectMember>lambdaQuery()
                .eq(ProjectMember::getProjectId, projectId)
                .eq(ProjectMember::getUserId, userId)) > 0;
    }

    public boolean isOwner(UUID projectId, UUID userId) {
        if (userId != null && userId.equals(com.seven7m.kanban.common.security.UserContext.DEFAULT_ADMIN_ID)) {
            return true;
        }
        return projectMemberService.getRoles(projectId, userId).contains(ProjectRole.OWNER);
    }

    /** 判断当前用户是否为项目管理员（OWNER 或 ADMIN） */
    public boolean isAdmin(UUID projectId, UUID userId) {
        if (userId != null && userId.equals(com.seven7m.kanban.common.security.UserContext.DEFAULT_ADMIN_ID)) {
            return true;
        }
        Set<String> roles = projectMemberService.getRoles(projectId, userId);
        return roles.contains(ProjectRole.OWNER) || roles.contains(ProjectRole.ADMIN);
    }

    private ProjectVO toVO(Project p) {
        ProjectVO vo = new ProjectVO();
        BeanUtils.copyProperties(p, vo);
        return vo;
    }

    public boolean canWrite(UUID projectId, UUID userId) {
        if (userId != null && userId.equals(com.seven7m.kanban.common.security.UserContext.DEFAULT_ADMIN_ID)) {
            return true;
        }
        return projectMemberService.getRoles(projectId, userId).stream()
                .anyMatch(ProjectRole::isMember);
    }

    /** 判断当前用户是否为项目管理员（OWNER/ADMIN/PM，可管理成员与项目设置） */
    public boolean canManage(UUID projectId, UUID userId) {
        if (userId != null && userId.equals(com.seven7m.kanban.common.security.UserContext.DEFAULT_ADMIN_ID)) {
            return true;
        }
        Set<String> roles = projectMemberService.getRoles(projectId, userId);
        return roles.contains(ProjectRole.OWNER) || roles.contains(ProjectRole.ADMIN) || roles.contains(ProjectRole.PM);
    }

    /** 查询当前用户在指定项目集合中已收藏的项目 ID 集合 */
    private Set<UUID> listFavoritedProjectIds(UUID userId, List<UUID> projectIds) {
        if (projectIds.isEmpty()) {
            return Collections.emptySet();
        }
        return projectFavoriteMapper.selectList(Wrappers.<ProjectFavorite>lambdaQuery()
                        .eq(ProjectFavorite::getUserId, userId)
                        .in(ProjectFavorite::getProjectId, projectIds))
                .stream().map(ProjectFavorite::getProjectId).collect(Collectors.toSet());
    }

    private Project requireProject(UUID projectId) {
        Project p = projectMapper.selectById(projectId);
        if (p == null || p.getDeleted() != null && p.getDeleted() == 1) {
            throw new ServiceException(404, "项目不存在");
        }
        return p;
    }
}

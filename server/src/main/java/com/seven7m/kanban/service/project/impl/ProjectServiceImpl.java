package com.seven7m.kanban.service.project.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.core.toolkit.support.SFunction;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.seven7m.kanban.common.constant.PageLimits;
import com.seven7m.kanban.common.domain.PageResult;
import com.seven7m.kanban.common.exception.ErrorCode;
import com.seven7m.kanban.common.exception.ServiceException;
import com.seven7m.kanban.common.security.Permission;
import com.seven7m.kanban.common.security.PermissionService;
import com.seven7m.kanban.common.security.UserContext;
import com.seven7m.kanban.dto.project.CreateProjectRequest;
import com.seven7m.kanban.dto.project.ProjectCategoryCountsVO;
import com.seven7m.kanban.dto.project.ProjectFilterRequest;
import com.seven7m.kanban.dto.project.ProjectStats;
import com.seven7m.kanban.dto.project.ProjectVO;
import com.seven7m.kanban.dto.project.UpdateProjectRequest;
import com.seven7m.kanban.entity.announcement.Announcement;
import com.seven7m.kanban.entity.board.Board;
import com.seven7m.kanban.entity.handoff.ProjectHandoff;
import com.seven7m.kanban.entity.milestone.Milestone;
import com.seven7m.kanban.entity.project.Project;
import com.seven7m.kanban.entity.project.ProjectCategoryKey;
import com.seven7m.kanban.entity.project.ProjectFavorite;
import com.seven7m.kanban.entity.project.ProjectMember;
import com.seven7m.kanban.entity.project.ProjectRole;
import com.seven7m.kanban.entity.project.ProjectStatus;
import com.seven7m.kanban.mapper.announcement.AnnouncementMapper;
import com.seven7m.kanban.mapper.board.BoardMapper;
import com.seven7m.kanban.mapper.handoff.ProjectHandoffMapper;
import com.seven7m.kanban.mapper.milestone.MilestoneMapper;
import com.seven7m.kanban.mapper.project.ProjectFavoriteMapper;
import com.seven7m.kanban.mapper.project.ProjectMapper;
import com.seven7m.kanban.mapper.project.ProjectVisibilityQuery;
import com.seven7m.kanban.mapper.project.ProjectMemberMapper;
import com.seven7m.kanban.service.project.ProjectInitializer;
import com.seven7m.kanban.service.project.ProjectMemberService;
import com.seven7m.kanban.service.project.ProjectQueryScope;
import com.seven7m.kanban.service.project.ProjectService;
import com.seven7m.kanban.service.workitem.WorkItemRouter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class ProjectServiceImpl extends ServiceImpl<ProjectMapper, Project> implements ProjectService {

    /**
     * 项目列表缺省每页条数。
     * 与 {@code ProjectFilterRequest#size} 的注释（"为空按 10 处理"）保持一致；
     * {@code PageLimits.ADMIN_DEFAULT_SIZE} 是 20，管理类列表用，不要拿它来改这里的行为。
     */
    private static final int DEFAULT_PAGE_SIZE = 10;


    private final ProjectMemberMapper projectMemberMapper;
    private final ProjectFavoriteMapper projectFavoriteMapper;
    private final ProjectMemberService projectMemberService;
    private final ProjectInitializer projectInitializer;
    private final WorkItemRouter workItemRouter;
    private final BoardMapper boardMapper;
    private final AnnouncementMapper announcementMapper;
    private final MilestoneMapper milestoneMapper;
    private final ProjectHandoffMapper projectHandoffMapper;

    /**
     * 超级管理员判定统一入口。
     * 此前本类自己实现了一套「只认种子 UUID」的判定，而 PermissionService / AdminAuthInterceptor
     * 还认 sys_user.is_admin=true，导致被标记为管理员的账号「权限码全亮、打不开项目」（403）。
     * 这里改为完全委托，保证列表可见性、成员豁免、权限码三处口径一致。
     * 循环依赖：PermissionService -> ProjectMemberService -> ProjectService，故用 @Lazy 打破。
     */
    @Lazy
    @Autowired
    private PermissionService permissionService;

    @Override
    @Transactional
    public ProjectVO create(CreateProjectRequest req) {
        long dup = count(Wrappers.<Project>lambdaQuery()
                .eq(Project::getProjectKey, req.getProjectKey()));
        if (dup > 0) {
            throw new ServiceException(ErrorCode.PROJECT_KEY_EXISTS, req.getProjectKey());
        }

        UUID currentUser = UserContext.getUserId();
        Project project = new Project();
        BeanUtils.copyProperties(req, project);
        project.setId(UUID.randomUUID());
        project.setStatus(ProjectStatus.ACTIVE.name());
        project.setCreatedBy(currentUser);
        project.setCreatedAt(LocalDateTime.now());
        project.setUpdatedAt(LocalDateTime.now());
        save(project);

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

    @Override
    @Transactional
    public ProjectVO update(UUID id, UpdateProjectRequest req) {
        Project p = getById(id);
        if (p == null) {
            throw new ServiceException(ErrorCode.PROJECT_NOT_FOUND);
        }
        UUID currentUser = UserContext.getUserId();
        if (!canWrite(id, currentUser)) {
            throw new ServiceException(ErrorCode.PROJECT_EDIT_DENIED);
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
        updateById(p);
        return toVO(p);
    }

    @Override
    public List<ProjectVO> listAll() {
        return listAll(ProjectCategoryKey.ALL);
    }

    /**
     * 按分类查询当前用户的项目列表（非分页）。
     * 可见性规则见 {@link ProjectQueryScope#visibleQuery}，与分页查询共用同一套条件；
     * 它返回空 Optional 表示"该分类必然没有项目"，此处直接短路返回空列表。
     */
    @Override
    public List<ProjectVO> listAll(ProjectCategoryKey category) {
        UUID currentUser = UserContext.getUserId();
        ProjectVisibilityQuery query = ProjectQueryScope.visibility(
                category, currentUser, isSuperAdmin(currentUser), null, null, null);
        return assembleBatch(baseMapper.selectVisible(query), currentUser);
    }

    /**
     * 分页查询当前用户的项目列表，支持分类筛选、关键词模糊匹配与排序。
     * 分页模式：先 selectCount（不带 ORDER BY），再加排序与 LIMIT/OFFSET，最后 selectList。
     * 空集合语义同 {@link #listAll(ProjectCategoryKey)}：直接返回空分页（总数 0），不查库。
     */
    @Override
    public PageResult<ProjectVO> listByFilter(ProjectFilterRequest req) {
        ProjectCategoryKey category = ProjectCategoryKey.of(req.getCategory());
        UUID currentUser = UserContext.getUserId();

        int pageNum = req.getPage() == null || req.getPage() < PageLimits.MIN_PAGE
                ? PageLimits.MIN_PAGE
                : req.getPage();
        int size = req.getSize() == null || req.getSize() < 1
                ? DEFAULT_PAGE_SIZE
                : Math.min(req.getSize(), PageLimits.ADMIN_MAX_SIZE);

        // 可见性条件（成员/角色/收藏/关键词）与排序都在 Mapper 的 EXISTS 查询里，服务层只负责翻译参数
        ProjectVisibilityQuery query = ProjectQueryScope.visibility(category, currentUser,
                isSuperAdmin(currentUser), req.getKeyword(), req.getSortField(), req.getSortOrder());

        long total = baseMapper.countVisible(query);
        // 分页由 MyBatis-Plus 的拦截器根据 IPage 参数追加 LIMIT/OFFSET；total 用同源的 countVisible 单独取，
        // 避免 count SQL 也带上 ORDER BY
        List<Project> projects = total == 0
                ? List.of()
                : baseMapper.selectVisiblePage(new Page<>(pageNum, size, false), query);
        return new PageResult<>(total, pageNum, size, assembleBatch(projects, currentUser));
    }

    /**
     * 批量装配项目 VO：一次查统计与收藏，再逐项按权限码填充能力标记。
     * 列表（分页/非分页）共用；单项目 {@link #get(UUID)} 走自己的轻量路径。
     */
    private List<ProjectVO> assembleBatch(List<Project> projects, UUID currentUser) {
        if (projects == null || projects.isEmpty()) {
            return Collections.emptyList();
        }
        List<UUID> projectIds = projects.stream().map(Project::getId).toList();
        Map<UUID, ProjectStats> statsMap = baseMapper.selectStatsByProjectIds(projectIds).stream()
                .collect(Collectors.toMap(ProjectStats::getProjectId, s -> s));
        Set<UUID> favoritedIds = listFavoritedProjectIds(currentUser, projectIds);

        return projects.stream().map(p -> {
            ProjectVO vo = toVO(p);
            vo.setStats(statsMap.getOrDefault(p.getId(), new ProjectStats()));
            vo.setFavorited(favoritedIds.contains(p.getId()));
            applyCapabilities(vo, p.getId(), currentUser);
            return vo;
        }).collect(Collectors.toList());
    }


    /**
     * 查询当前用户五个分类的项目计数，满足包含关系：managed ≤ participated ≤ all。
     *
     * <p>成员/管理/收藏三组项目 id 由 {@link ProjectMapper} 的子查询方法给出，
     * 这里只做 {@code eq(status) + in(ids) + count}；id 集合为空说明该分类必然没有数据，
     * 直接记 0（PostgreSQL 的 {@code IN ()} 是语法错误，不能拼空集合）。
     */
    @Override
    public ProjectCategoryCountsVO getCategoryCounts() {
        UUID currentUser = UserContext.getUserId();
        ProjectCategoryCountsVO vo = new ProjectCategoryCountsVO();

        // 与列表口径完全同源：每个分类都走同一条可见性 SQL 的计数，不再各自拼子查询
        boolean superAdmin = isSuperAdmin(currentUser);
        long activeAll = countVisible(ProjectCategoryKey.ALL, currentUser, superAdmin);
        vo.setAll(activeAll);
        vo.setParticipated(activeAll);
        vo.setManaged(countVisible(ProjectCategoryKey.MANAGED, currentUser, superAdmin));
        vo.setArchived(countVisible(ProjectCategoryKey.ARCHIVED, currentUser, superAdmin));
        // 收藏是用户私人数据、不是权限，因此对超管同样按自己的收藏过滤（与改前一致）
        vo.setFavorited(countVisible(ProjectCategoryKey.FAVORITED, currentUser, superAdmin));
        return vo;
    }

    /** 单个分类的项目计数：参数翻译复用 {@link ProjectQueryScope}，SQL 在 Mapper 里 */
    private long countVisible(ProjectCategoryKey category, UUID userId, boolean superAdmin) {
        ProjectVisibilityQuery query = ProjectQueryScope.visibility(category, userId, superAdmin, null, null, null);
        return baseMapper.countVisible(query);
    }

    @Override
    public ProjectVO get(UUID id) {
        Project p = getById(id);
        if (p == null) {
            throw new ServiceException(ErrorCode.PROJECT_NOT_FOUND);
        }
        if (!isMember(id, UserContext.getUserId())) {
            throw new ServiceException(ErrorCode.PROJECT_ACCESS_DENIED);
        }
        ProjectVO vo = toVO(p);
        List<ProjectStats> stats = baseMapper.selectStatsByProjectIds(List.of(id));
        vo.setStats(stats.isEmpty() ? new ProjectStats() : stats.get(0));
        UUID currentUser = UserContext.getUserId();
        // 收藏是多实体之外的第三张表，走 ProjectFavoriteMapper：父类的 count(...) 只接受 Project 条件
        vo.setFavorited(projectFavoriteMapper.selectCount(Wrappers.<ProjectFavorite>lambdaQuery()
                .eq(ProjectFavorite::getProjectId, id)
                .eq(ProjectFavorite::getUserId, currentUser)) > 0);
        applyCapabilities(vo, id, currentUser);
        return vo;
    }

    /**
     * 按权限码填充"当前用户能对项目做什么"，作为前端按钮显隐的唯一来源。
     *
     * <p>此前这里用角色硬编码（canManage 同时充当 canEdit），导致两处问题：
     * ①PM 的"删除项目"按钮可点，但后端要求 project:delete（仅 OWNER/ADMIN），点下去必然 403；
     * ②同一项目每行要算两次角色，一页 10 条就是 20 次查询。
     * 现在一次取权限码集合（带缓存），四个能力全部由它派生，与接口注解完全同源。
     */
    private void applyCapabilities(ProjectVO vo, UUID projectId, UUID userId) {
        Set<String> permissions = permissionService.getPermissions(userId, projectId);
        vo.setCanEdit(permissions.contains(Permission.PROJECT_SETTING));
        vo.setCanManage(permissions.contains(Permission.PROJECT_MEMBER));
        vo.setCanArchive(permissions.contains(Permission.PROJECT_ARCHIVE));
        vo.setCanDelete(permissions.contains(Permission.PROJECT_DELETE));
    }

    /**
     * 归档项目：仅 OWNER/ADMIN 可操作，项目须为 ACTIVE 状态。
     */
    @Override
    @Transactional
    public void archive(UUID projectId, UUID userId) {
        Project p = requireProject(projectId);
        if (!isAdmin(projectId, userId)) {
            throw new ServiceException(ErrorCode.PROJECT_ARCHIVE_ADMIN_ONLY);
        }
        if (ProjectStatus.ARCHIVED == ProjectStatus.of(p.getStatus())) {
            throw new ServiceException(ErrorCode.PROJECT_ALREADY_ARCHIVED);
        }
        p.setStatus(ProjectStatus.ARCHIVED.name());
        p.setUpdatedAt(LocalDateTime.now());
        updateById(p);
        projectMemberService.evictRoleCache(projectId, null);
        log.info("project.archive projectId={} operator={}", projectId, userId);
    }

    /**
     * 取消归档：仅 OWNER/ADMIN 可操作，项目须为 ARCHIVED 状态。
     */
    @Override
    @Transactional
    public void unarchive(UUID projectId, UUID userId) {
        Project p = requireProject(projectId);
        if (!isAdmin(projectId, userId)) {
            throw new ServiceException(ErrorCode.PROJECT_ADMIN_ONLY);
        }
        if (ProjectStatus.ACTIVE == ProjectStatus.of(p.getStatus())) {
            throw new ServiceException(ErrorCode.PROJECT_NOT_ARCHIVED);
        }
        p.setStatus(ProjectStatus.ACTIVE.name());
        p.setUpdatedAt(LocalDateTime.now());
        updateById(p);
        projectMemberService.evictRoleCache(projectId, null);
        log.info("project.unarchive projectId={} operator={}", projectId, userId);
    }

    @Override
    public boolean existsByKey(String projectKey) {
        return count(Wrappers.<Project>lambdaQuery()
                .eq(Project::getProjectKey, projectKey)) > 0;
    }

    /**
     * 删除项目：仅 OWNER/ADMIN 可操作。
     * 删除顺序（遵守外键 RESTRICT 约束）：
     *   1. announcement（无外键）
     *   2. milestone（无外键）
     *   3. 工作项（workItemRouter.deleteByProject 按 task/requirement/bug 三表级联清理，
     *      各自清理 attachment/issue_activity/issue_cc/issue_comment/issue_label/issue_participant/issue_relation）
     *   4. board（board_column 对 board 为 ON DELETE CASCADE，自动清理）
     *   5. project（CASCADE 自动清理 project_member/project_favorite/project_label/risk/sprint/user_role）
     */
    @Override
    @Transactional
    public void delete(UUID projectId, UUID userId) {
        Project p = requireProject(projectId);
        if (!isAdmin(projectId, userId)) {
            throw new ServiceException(ErrorCode.PROJECT_DELETE_ADMIN_ONLY);
        }
        // 有工作项时拒绝删除：此前会静默级联清空全部工作项，属误删高风险
        long itemCount = workItemRouter.countByProject(projectId);
        if (itemCount > 0) {
            throw new ServiceException(ErrorCode.PROJECT_HAS_WORK_ITEMS, itemCount);
        }

        announcementMapper.delete(Wrappers.<Announcement>lambdaQuery()
                .eq(Announcement::getProjectId, projectId));
        milestoneMapper.delete(Wrappers.<Milestone>lambdaQuery()
                .eq(Milestone::getProjectId, projectId));
        workItemRouter.deleteByProject(projectId);
        boardMapper.delete(Wrappers.<Board>lambdaQuery()
                .eq(Board::getProjectId, projectId));
        // 交接配置是「项目 × 类别 × 状态」的附属数据且无外键，项目删除后必须手动清理，
        // 否则会留下指向已删项目的孤儿配置（通知是用户级历史，刻意保留）
        projectHandoffMapper.delete(Wrappers.<ProjectHandoff>lambdaQuery()
                .eq(ProjectHandoff::getProjectId, projectId));
        removeById(projectId);

        projectMemberService.evictRoleCache(projectId, null);
        // 删除是不可逆操作，必须留下"谁删了哪个项目"的审计线索
        log.info("project.delete projectId={} projectKey={} operator={}", projectId, p.getProjectKey(), userId);
    }

    @Override
    public boolean hasMemberRecord(UUID projectId, UUID userId) {
        return projectMemberMapper.selectCount(Wrappers.<ProjectMember>lambdaQuery()
                .eq(ProjectMember::getProjectId, projectId)
                .eq(ProjectMember::getUserId, userId)) > 0;
    }

    /**
     * 是否有该项目访问权：超管对全部项目隐式具备成员身份，其余人须在成员名册中。
     * 注意「重复添加成员/移除成员」这类需要真实成员行的判定请用 hasMemberRecord，
     * 否则超管会被误判成“已是成员”而永远加不进项目。
     */
    @Override
    public boolean isMember(UUID projectId, UUID userId) {
        if (isSuperAdmin(userId)) {
            return true;
        }
        return hasMemberRecord(projectId, userId);
    }

    /**
     * 内置超管：对全部项目拥有隐式成员身份与管理权。
     * 判定统一委托给 {@link PermissionService#isSuperAdmin}（种子 UUID 或 sys_user.is_admin），
     * 不要再在这里写第二套规则——两套口径必然漂移。
     */
    private boolean isSuperAdmin(UUID userId) {
        return userId != null && permissionService.isSuperAdmin(userId);
    }

    @Override
    public boolean isOwner(UUID projectId, UUID userId) {
        if (isSuperAdmin(userId)) {
            return true;
        }
        return projectMemberService.getRoles(projectId, userId).contains(ProjectRole.OWNER);
    }

    /** 判断当前用户是否为项目管理员（OWNER 或 ADMIN） */
    @Override
    public boolean isAdmin(UUID projectId, UUID userId) {
        if (isSuperAdmin(userId)) {
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

    @Override
    public boolean canWrite(UUID projectId, UUID userId) {
        if (isSuperAdmin(userId)) {
            return true;
        }
        return projectMemberService.getRoles(projectId, userId).stream()
                .anyMatch(ProjectRole::isMember);
    }

    /** 判断当前用户是否为项目管理员（OWNER/ADMIN/PM，可管理成员与项目设置） */
    @Override
    public boolean canManage(UUID projectId, UUID userId) {
        if (isSuperAdmin(userId)) {
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
        Project p = getById(projectId);
        if (p == null) {
            throw new ServiceException(ErrorCode.PROJECT_NOT_FOUND);
        }
        return p;
    }
}

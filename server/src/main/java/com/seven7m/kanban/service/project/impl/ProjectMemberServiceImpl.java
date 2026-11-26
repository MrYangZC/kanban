package com.seven7m.kanban.service.project.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.seven7m.kanban.common.cache.RequestScopedCacheStore;
import com.seven7m.kanban.common.exception.ErrorCode;
import com.seven7m.kanban.common.util.LikeEscaper;
import com.seven7m.kanban.common.exception.ServiceException;
import com.seven7m.kanban.common.security.MemberRolePort;
import com.seven7m.kanban.common.security.PermissionService;
import com.seven7m.kanban.common.security.UserContext;
import com.seven7m.kanban.dto.project.AddMemberRequest;
import com.seven7m.kanban.dto.project.MemberCandidateVO;
import com.seven7m.kanban.dto.project.ProjectMemberVO;
import com.seven7m.kanban.entity.project.ProjectMember;
import com.seven7m.kanban.entity.project.ProjectRole;
import com.seven7m.kanban.entity.rbac.Role;
import com.seven7m.kanban.entity.rbac.UserRole;
import com.seven7m.kanban.entity.user.SysUser;
import com.seven7m.kanban.mapper.project.ProjectMemberMapper;
import com.seven7m.kanban.mapper.rbac.RoleMapper;
import com.seven7m.kanban.mapper.rbac.UserRoleCodeRow;
import com.seven7m.kanban.mapper.rbac.UserRoleMapper;
import com.seven7m.kanban.mapper.user.SysUserMapper;
import com.seven7m.kanban.service.project.ProjectMemberService;
import com.seven7m.kanban.service.project.ProjectService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 项目成员服务实现。
 *
 * <p>{@link MemberRolePort} 的实现身份保留在本类（而不是搬到接口上）：
 * 公共安全层只该看到"取角色 id"这一个能力，接口暴露的是完整的成员维护契约。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ProjectMemberServiceImpl extends ServiceImpl<ProjectMemberMapper, ProjectMember>
        implements ProjectMemberService, MemberRolePort {

    /** 候选用户搜索的每页条数下限 */
    private static final int MIN_CANDIDATE_LIMIT = 1;

    /** 候选用户搜索的每页条数上限（下拉框不需要更多） */
    private static final int MAX_CANDIDATE_LIMIT = 50;


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

    /** 请求级缓存的 cacheName：同一请求内反复判角色只打一次库，跨请求重新读，避免多实例陈旧 */
    private static final String ROLE_CACHE_NAME = "memberRoles";

    /** 项目成员列表（含用户展示信息与角色集合），按加入时间排序 */
    @Override
    public List<ProjectMemberVO> list(UUID projectId) {
        // 先判有无成员行：空项目直接返回空列表，省掉后续角色/用户两次批量查询。
        // 原实现用 selectOne(.. LIMIT 1) 只为判存在，取回的对象从未使用，这里换成计数更直白
        long memberCount = count(Wrappers.<ProjectMember>lambdaQuery()
                .eq(ProjectMember::getProjectId, projectId));
        if (memberCount == 0) {
            return List.of();
        }
        List<ProjectMember> memberList = list(Wrappers.<ProjectMember>lambdaQuery()
                .eq(ProjectMember::getProjectId, projectId)
                .orderByAsc(ProjectMember::getCreatedAt));
        Set<UUID> userIds = memberList.stream().map(ProjectMember::getUserId).collect(Collectors.toSet());
        Map<UUID, SysUser> userMap = userIds.isEmpty() ? Map.of() :
                userMapper.selectBatchIds(userIds).stream()
                        .collect(Collectors.toMap(SysUser::getId, Function.identity()));
        // 一次批量查全部角色再按人分组（避免每个成员一次查询）；Map 只作局部索引，不出现在方法签名上
        Map<UUID, Set<String>> roleMap = new HashMap<>();
        for (UserRoleCodeRow row : userRoleMapper.selectRoleCodesByProject(projectId)) {
            roleMap.computeIfAbsent(row.getUserId(), k -> new LinkedHashSet<>()).add(row.getRoleCode());
        }
        return memberList.stream().map(m -> toVO(m, userMap, roleMap)).collect(Collectors.toList());
    }

    /**
     * 「添加成员」的可选用户：尚未加入该项目的用户，按关键字（账号/显示名）模糊匹配。
     * 前端因此不必再拉取全量用户（GET /users 仅系统管理员可读，项目管理员拿不到），
     * 也不会再把整张用户表塞进一个下拉里。
     */
    @Override
    public List<MemberCandidateVO> candidates(UUID projectId, String keyword, int limit) {
        Set<UUID> memberIds = list(Wrappers.<ProjectMember>lambdaQuery()
                .eq(ProjectMember::getProjectId, projectId))
                .stream().map(ProjectMember::getUserId).collect(Collectors.toSet());
        String kw = keyword == null ? "" : keyword.trim();
        int max = Math.min(Math.max(limit, MIN_CANDIDATE_LIMIT), MAX_CANDIDATE_LIMIT);
        var q = Wrappers.<SysUser>lambdaQuery()
                .select(SysUser::getId, SysUser::getUsername, SysUser::getDisplayName, SysUser::getAvatarUrl)
                .orderByAsc(SysUser::getUsername);
        if (!memberIds.isEmpty()) {
            q.notIn(SysUser::getId, memberIds);
        }
        if (!kw.isEmpty()) {
            // 与项目列表同一口径：转义 LIKE 通配符，否则搜 "_" / "%" 会把整张用户表都命中
            String pattern = LikeEscaper.contains(kw);
            q.and(w -> w.like(SysUser::getUsername, pattern).or().like(SysUser::getDisplayName, pattern));
        }
        return userMapper.selectPage(new Page<>(1, max, false), q).getRecords().stream().map(u -> {
            MemberCandidateVO vo = new MemberCandidateVO();
            vo.setId(u.getId());
            vo.setUsername(u.getUsername());
            vo.setDisplayName(u.getDisplayName());
            vo.setAvatarUrl(u.getAvatarUrl());
            return vo;
        }).collect(Collectors.toList());
    }

    @Override
    @Transactional
    public ProjectMemberVO add(UUID projectId, AddMemberRequest req) {
        requireAdmin(projectId);
        // roles 为空时兜底为 MEMBER：DTO 按规约不再设默认值，而内部调用方（如 Excel 导入）
        // 可能只填 userId，这里必须兜住，否则 validateRoles 会对 null 抛 NPE
        List<String> roles = req.getRoles() == null || req.getRoles().isEmpty()
                ? List.of(ProjectRole.MEMBER)
                : req.getRoles();
        validateRoles(roles);
        if (userMapper.selectById(req.getUserId()) == null) {
            throw new ServiceException(ErrorCode.USER_NOT_FOUND);
        }
        // 必须用真实成员行判定：isMember 对超管恒为 true，会导致超管永远加不进项目
        if (projectService.hasMemberRecord(projectId, req.getUserId())) {
            throw new ServiceException(ErrorCode.MEMBER_ALREADY_EXISTS);
        }
        ProjectMember m = new ProjectMember();
        m.setId(UUID.randomUUID());
        m.setProjectId(projectId);
        m.setUserId(req.getUserId());
        m.setCreatedAt(LocalDateTime.now());
        save(m);
        grantRoles(projectId, req.getUserId(), roles);
        evictRoleCache(projectId, req.getUserId());
        permissionService.evictUserPermissions(req.getUserId(), projectId);
        log.info("project.member.add projectId={} member={} roles={} operator={}",
                projectId, req.getUserId(), roles, UserContext.getUserId());
        return toVO(m);
    }

    @Override
    @Transactional
    public void remove(UUID projectId, UUID userId) {
        requireAdmin(projectId);
        // 同上：移除校验也要看真实成员行，否则移除未加入的用户会静默成功
        if (!projectService.hasMemberRecord(projectId, userId)) {
            throw new ServiceException(ErrorCode.MEMBER_NOT_FOUND);
        }
        // 不允许移除最后一个 OWNER，否则项目失去管理者
        if (getRoles(projectId, userId).contains(ProjectRole.OWNER) && countOwner(projectId) <= 1) {
            throw new ServiceException(ErrorCode.MEMBER_LAST_OWNER);
        }
        remove(Wrappers.<ProjectMember>lambdaQuery()
                .eq(ProjectMember::getProjectId, projectId)
                .eq(ProjectMember::getUserId, userId));
        userRoleMapper.delete(Wrappers.<UserRole>lambdaQuery()
                .eq(UserRole::getProjectId, projectId)
                .eq(UserRole::getUserId, userId));
        evictRoleCache(projectId, userId);
        permissionService.evictUserPermissions(userId, projectId);
        log.info("project.member.remove projectId={} member={} operator={}",
                projectId, userId, UserContext.getUserId());
    }

    @Override
    @Transactional
    public ProjectMemberVO updateRoles(UUID projectId, UUID userId, List<String> roles) {
        requireAdmin(projectId);
        validateRoles(roles);
        ProjectMember m = getOne(Wrappers.<ProjectMember>lambdaQuery()
                .eq(ProjectMember::getProjectId, projectId)
                .eq(ProjectMember::getUserId, userId));
        if (m == null) {
            throw new ServiceException(ErrorCode.MEMBER_NOT_FOUND);
        }
        // 不允许把最后一个 OWNER 的角色全部移除
        Set<String> oldRoles = getRoles(projectId, userId);
        if (oldRoles.contains(ProjectRole.OWNER) && !roles.contains(ProjectRole.OWNER) && countOwner(projectId) <= 1) {
            throw new ServiceException(ErrorCode.PROJECT_AT_LEAST_ONE_OWNER);
        }
        userRoleMapper.delete(Wrappers.<UserRole>lambdaQuery()
                .eq(UserRole::getProjectId, projectId)
                .eq(UserRole::getUserId, userId));
        grantRoles(projectId, userId, roles);
        evictRoleCache(projectId, userId);
        permissionService.evictUserPermissions(userId, projectId);
        log.info("project.member.roles projectId={} member={} from={} to={} operator={}",
                projectId, userId, oldRoles, roles, UserContext.getUserId());
        return toVO(m);
    }

    /**
     * 查询用户在指定项目中的角色集合。非成员返回空集合。
     *
     * <p>缓存只活在**当前请求**内（key = projectId:userId）：鉴权拦截器 + 列表装配会在同一次请求里
     * 反复判角色，请求级缓存足以省掉这些重复查询；跨请求一律重新读库，
     * 避免多实例部署时"A 节点改了角色、B 节点还按旧角色放行"。
     */
    @Override
    public Set<String> getRoles(UUID projectId, UUID userId) {
        if (projectId == null || userId == null) {
            return Set.of();
        }
        String cacheKey = projectId + ":" + userId;
        Object cached = RequestScopedCacheStore.get(ROLE_CACHE_NAME, cacheKey);
        if (cached instanceof Set<?>) {
            @SuppressWarnings("unchecked")
            Set<String> hit = (Set<String>) cached;
            return hit;
        }
        Set<String> roles = projectService.isMember(projectId, userId)
                ? new LinkedHashSet<>(userRoleMapper.selectRoleCodesByProjectAndUser(projectId, userId))
                : Set.of();
        RequestScopedCacheStore.put(ROLE_CACHE_NAME, cacheKey, roles);
        return roles;
    }

    /**
     * 查询用户在指定项目中的角色 id 集合（供 role_permission 按 role_id 联查权限）。
     */
    @Override
    public Set<UUID> getRoleIds(UUID projectId, UUID userId) {
        if (projectId == null || userId == null) {
            return Set.of();
        }
        if (!projectService.isMember(projectId, userId)) {
            return Set.of();
        }
        return new LinkedHashSet<>(userRoleMapper.selectRoleIdsByProjectAndUser(projectId, userId));
    }

    /** {@link MemberRolePort} 实现：给公共安全层用，避免它直接依赖业务 Service */
    @Override
    public Set<UUID> roleIdsOf(UUID projectId, UUID userId) {
        return getRoleIds(projectId, userId);
    }

    /**
     * 角色变更后使缓存失效，保证同一请求内后续的判定立刻看到新角色。
     *
     * <p>缓存本身随请求结束而消失，所以这里不需要"跨实例广播"；userId 为 null 时整体清空
     * （粒度做不到按项目前缀清，且代价只是本请求多查几次库）。
     */
    @Override
    public void evictRoleCache(UUID projectId, UUID userId) {
        if (projectId == null) {
            // 项目维度未知，只能把当前请求内的角色缓存整体清空（极少使用）
            RequestScopedCacheStore.clear(ROLE_CACHE_NAME);
            return;
        }
        if (userId == null) {
            // 缓存只活在当前请求里，粒度做不到按项目前缀清；直接整体清空即可（代价仅是本请求再查几次库）
            RequestScopedCacheStore.clear(ROLE_CACHE_NAME);
            return;
        }
        RequestScopedCacheStore.evict(ROLE_CACHE_NAME, projectId + ":" + userId);
    }

    /** 写入用户-角色关联（RBAC：一人可多角色），供项目创建等内部逻辑调用 */
    @Override
    public void grantRoles(UUID projectId, UUID userId, List<String> roles) {
        for (String code : roles) {
            // 不用 selectOne + last("LIMIT 1")：在条件构造器上拼 SQL 片段属于越层写法；
            // role.code 有唯一约束，取列表再取第一条等价
            Role role = roleMapper.selectList(Wrappers.<Role>lambdaQuery()
                            .eq(Role::getCode, code))
                    .stream().findFirst().orElse(null);
            if (role == null) {
                throw new ServiceException(ErrorCode.MEMBER_ROLE_NOT_FOUND, code);
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
                throw new ServiceException(ErrorCode.MEMBER_ROLE_UNSUPPORTED, r);
            }
        }
    }

    /** 项目下持有 OWNER 角色的人数（SQL 已下沉到 Mapper） */
    private long countOwner(UUID projectId) {
        return baseMapper.selectRoleHolderCount(projectId, ProjectRole.OWNER);
    }

    /**
     * 成员维护权限：OWNER/ADMIN/PM。
     * 与接口上的 {@code @PreAuthorize("@projectAuth.has(#projectId, 'project:member')")} 口径保持一致
     * （role_permission 里 OWNER/ADMIN/PM 都持有 project:member），
     * 之前这里用的是 isOwner，导致前端提示可管理的 ADMIN/PM 一操作就 403。
     */
    private void requireAdmin(UUID projectId) {
        if (!projectService.canManage(projectId, UserContext.getUserId())) {
            throw new ServiceException(ErrorCode.MEMBER_MANAGE_DENIED);
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

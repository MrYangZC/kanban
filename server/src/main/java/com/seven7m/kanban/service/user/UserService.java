package com.seven7m.kanban.service.user;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.seven7m.kanban.common.domain.PageResult;
import com.seven7m.kanban.common.exception.ServiceException;
import com.seven7m.kanban.common.security.PermissionService;
import com.seven7m.kanban.common.security.UserContext;
import com.seven7m.kanban.common.security.UserSessionRegistry;
import com.seven7m.kanban.common.util.LikeEscaper;
import com.seven7m.kanban.dto.project.MemberCandidateVO;
import com.seven7m.kanban.dto.user.ChangePasswordRequest;
import com.seven7m.kanban.dto.user.CreateUserRequest;
import com.seven7m.kanban.dto.user.LoginRequest;
import com.seven7m.kanban.dto.user.LoginVO;
import com.seven7m.kanban.dto.user.ProfileVO;
import com.seven7m.kanban.dto.user.UpdateProfileRequest;
import com.seven7m.kanban.dto.user.UpdateUserRequest;
import com.seven7m.kanban.dto.user.UserVO;
import com.seven7m.kanban.entity.issue.IssueActivity;
import com.seven7m.kanban.entity.issue.comment.IssueComment;
import com.seven7m.kanban.entity.project.ProjectMember;
import com.seven7m.kanban.entity.rbac.UserRole;
import com.seven7m.kanban.entity.user.SysUser;
import com.seven7m.kanban.mapper.issue.IssueActivityMapper;
import com.seven7m.kanban.mapper.issue.comment.IssueCommentMapper;
import com.seven7m.kanban.mapper.project.ProjectMemberMapper;
import com.seven7m.kanban.mapper.rbac.UserRoleMapper;
import com.seven7m.kanban.mapper.user.SysUserMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.BeanUtils;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * 登录与用户管理：用户名存在校验 + BCrypt 密码校验 + 设置线程级 UserContext。
 * 后续接入 JWT/SaToken 后由拦截器填充 UserContext，本实现替换为真实鉴权。
 *
 * <p>口令策略（手册安全规约：禁止在代码中出现默认弱口令）：
 * 种子管理员口令只以 bcrypt 哈希形式存在于数据库迁移脚本中，代码与注释不再出现明文；
 * 管理员新建用户时若不填口令，由服务端生成随机口令，不再使用固定值。
 */
@Service
@RequiredArgsConstructor
public class UserService {

    private final SysUserMapper userMapper;
    private final ProjectMemberMapper projectMemberMapper;
    private final UserRoleMapper userRoleMapper;
    private final IssueCommentMapper commentMapper;
    private final IssueActivityMapper activityMapper;
    /** 账号被禁用/删除/改密后立即踢掉旧会话，避免"禁用不生效" */
    private final UserSessionRegistry sessionRegistry;
    /** is_admin 变更后需要失效权限缓存（superAdminFlag / projectPermissions） */
    private final PermissionService permissionService;
    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    /** 用户名不存在时也跑一次等成本哈希比对，让响应时间与「密码错误」一致，避免用户名枚举 */
    private static final String DUMMY_HASH = "$2a$10$" + "0".repeat(53);
    /** 同一账号窗口期内允许的连续失败次数与窗口长度 */
    private static final int MAX_LOGIN_FAILURES = 5;
    private static final long FAILURE_WINDOW_MS = 10 * 60 * 1000L;
    private static final int MAX_TRACKED_USERNAMES = 10_000;

    /**
     * 登录失败计数（进程内）。多实例部署时各实例独立计数，
     * 只作为暴力破解的减速带，不追求全局精确。
     */
    private final Map<String, Deque<Long>> loginFailures = new ConcurrentHashMap<>();

    public LoginVO login(LoginRequest req) {
        String username = req.getUsername() == null ? "" : req.getUsername().trim();
        String password = req.getPassword() == null ? "" : req.getPassword();

        ensureNotLocked(username);

        SysUser user = userMapper.selectOne(Wrappers.<SysUser>lambdaQuery()
                .eq(SysUser::getUsername, username));
        if (user == null) {
            passwordEncoder.matches(password, DUMMY_HASH);
            recordLoginFailure(username);
            throw new ServiceException(401, "用户名或密码错误");
        }
        // BCrypt 校验密码哈希；空哈希直接拒绝，避免任意登录
        if (!Boolean.TRUE.equals(user.getEnabled())
                || user.getPasswordHash() == null
                || !passwordEncoder.matches(password, user.getPasswordHash())) {
            recordLoginFailure(username);
            throw new ServiceException(401, "用户名或密码错误");
        }
        loginFailures.remove(username);

        LoginVO vo = new LoginVO();
        BeanUtils.copyProperties(user, vo);
        // 与 PermissionService#isSuperAdmin 口径保持一致：is_admin 字段或种子管理员 UUID。
        // 不能只依赖 BeanUtils 拷贝的 is_admin，否则种子管理员（该字段可能为空）会被判为非管理员。
        vo.setAdmin(Boolean.TRUE.equals(user.getAdmin())
                || user.getId().equals(UserContext.DEFAULT_ADMIN_ID));
        return vo;
    }

    /** 窗口期内失败次数达到上限时直接拒绝，避免无限次撞库 */
    private void ensureNotLocked(String username) {
        Deque<Long> fails = loginFailures.get(username);
        if (fails == null) {
            return;
        }
        synchronized (fails) {
            pruneFailures(fails);
            if (fails.size() >= MAX_LOGIN_FAILURES) {
                throw new ServiceException(429, "登录失败次数过多，请稍后再试");
            }
        }
    }

    private void recordLoginFailure(String username) {
        // 用户名可任意构造，限制 Map 规模避免被刷爆内存
        if (loginFailures.size() >= MAX_TRACKED_USERNAMES) {
            loginFailures.clear();
        }
        Deque<Long> fails = loginFailures.computeIfAbsent(username, k -> new ArrayDeque<>());
        synchronized (fails) {
            pruneFailures(fails);
            fails.addLast(System.currentTimeMillis());
        }
    }

    private static void pruneFailures(Deque<Long> fails) {
        long deadline = System.currentTimeMillis() - FAILURE_WINDOW_MS;
        while (!fails.isEmpty() && fails.peekFirst() < deadline) {
            fails.pollFirst();
        }
    }

    public SysUser getById(UUID id) {
        return userMapper.selectById(id);
    }

    /**
     * 人员下拉用的精简列表（id/账号/显示名/头像）。
     * 与用户管理列表 {@link #pageForAdmin} 刻意区分：后者含 email/enabled、只对系统管理员开放；
     * 本方法供项目内"选负责人/验证人/按人筛选"使用，任何登录用户可用，且不下发 email。
     */
    public List<MemberCandidateVO> listOptions() {
        return userMapper.selectList(Wrappers.<SysUser>lambdaQuery()
                        .select(SysUser::getId, SysUser::getUsername, SysUser::getDisplayName, SysUser::getAvatarUrl)
                        .orderByAsc(SysUser::getDisplayName))
                .stream().map(u -> {
                    MemberCandidateVO vo = new MemberCandidateVO();
                    vo.setId(u.getId());
                    vo.setUsername(u.getUsername());
                    vo.setDisplayName(u.getDisplayName());
                    vo.setAvatarUrl(u.getAvatarUrl());
                    return vo;
                }).collect(Collectors.toList());
    }

    /** 用户管理视图：分页 + 关键字（username / displayName / email），与工作项列表一致的 PageResult 结构 */
    public PageResult<UserVO> pageForAdmin(int page, int size, String keyword) {
        int p = page < 1 ? 1 : page;
        int s = size < 1 ? 20 : Math.min(size, 100);
        LambdaQueryWrapper<SysUser> q = Wrappers.lambdaQuery();
        if (keyword != null && !keyword.isBlank()) {
            // 转义 LIKE 通配符，避免关键字 _ / % 命中全部用户
            String pattern = LikeEscaper.contains(keyword);
            q.and(w -> w.apply(
                    "(username LIKE {0} ESCAPE '\\' OR display_name LIKE {1} ESCAPE '\\'"
                            + " OR email LIKE {2} ESCAPE '\\')",
                    pattern, pattern, pattern));
        }
        long total = userMapper.selectCount(q);
        q.orderByAsc(SysUser::getDisplayName);
        List<UserVO> list = userMapper.selectPage(new Page<>(p, s, false), q).getRecords()
                .stream().map(this::toVO).collect(Collectors.toList());
        return new PageResult<>(total, p, s, list);
    }

    @Transactional
    public UserVO create(CreateUserRequest req) {
        if (userMapper.selectOne(Wrappers.<SysUser>lambdaQuery().eq(SysUser::getUsername, req.getUsername())) != null) {
            throw new ServiceException(400, "用户名已存在");
        }
        if (req.getPassword() == null || req.getPassword().isBlank()) {
            throw new ServiceException(400, "初始密码不能为空");
        }
        SysUser u = new SysUser();
        u.setId(UUID.randomUUID());
        u.setUsername(req.getUsername());
        u.setDisplayName(req.getDisplayName());
        u.setEmail(req.getEmail());
        u.setAvatarUrl(req.getAvatarUrl());
        u.setPasswordHash(passwordEncoder.encode(req.getPassword()));
        u.setEnabled(true);
        // 系统管理员标识以前只能改库：DTO 里没有字段、Service 也不设置，
        // 文档承诺的"是否管理员"实际不可用，而权限判定又依赖这个字段
        u.setAdmin(Boolean.TRUE.equals(req.getAdmin()));

        userMapper.insert(u);
        if (Boolean.TRUE.equals(u.getAdmin())) {
            // 新增管理员会影响 isSuperAdmin 缓存（未命中会查库，命中旧值会误判）
            permissionService.evictAllPermissions();
        }
        return toVO(u);
    }

    @Transactional
    public UserVO update(UUID id, UpdateUserRequest req) {
        SysUser u = userMapper.selectById(id);
        if (u == null) {
            throw new ServiceException(404, "用户不存在");
        }
        if (req.getDisplayName() != null) u.setDisplayName(req.getDisplayName());
        if (req.getEmail() != null) u.setEmail(req.getEmail());
        if (req.getAvatarUrl() != null) u.setAvatarUrl(req.getAvatarUrl());
        if (req.getEnabled() != null) u.setEnabled(req.getEnabled());
        boolean adminChanged = false;
        if (req.getAdmin() != null && !req.getAdmin().equals(Boolean.TRUE.equals(u.getAdmin()))) {
            // 内置种子管理员是保底的管理入口，不允许被降级（否则可能把系统锁死在一个没有管理员的库上）
            if (id.equals(UserContext.DEFAULT_ADMIN_ID) && !req.getAdmin()) {
                throw new ServiceException(400, "内置管理员账号不允许取消管理员身份");
            }
            u.setAdmin(req.getAdmin());
            adminChanged = true;
        }
        boolean passwordChanged = req.getPassword() != null && !req.getPassword().isBlank();
        if (passwordChanged) {
            u.setPasswordHash(passwordEncoder.encode(req.getPassword()));
        }
        userMapper.updateById(u);

        if (adminChanged) {
            // 缓存里可能还留着旧的 superAdminFlag / 权限码集合
            permissionService.evictAllPermissions();
        }
        // 禁用账号或重置密码后，旧会话必须立刻失效：否则"已禁用"的账号还能继续操作到会话超时
        if (passwordChanged || Boolean.FALSE.equals(req.getEnabled())) {
            sessionRegistry.invalidateAll(id);
        }
        return toVO(u);
    }

    @Transactional
    public void delete(UUID id) {
        if (userMapper.selectById(id) == null) {
            throw new ServiceException(404, "用户不存在");
        }
        if (id.equals(UserContext.DEFAULT_ADMIN_ID)) {
            throw new ServiceException(400, "内置管理员账号不允许删除");
        }
        if (id.equals(UserContext.getUserId())) {
            throw new ServiceException(400, "不能删除当前登录账号");
        }
        // 硬引用检查：以下表对 sys_user 为 NO ACTION 外键，直接物理删除会触发数据库约束错误
        if (projectMemberMapper.selectCount(Wrappers.<ProjectMember>lambdaQuery()
                .eq(ProjectMember::getUserId, id)) > 0) {
            throw new ServiceException(400, "该用户仍是项目成员，请先将其移出所有项目");
        }
        if (userRoleMapper.selectCount(Wrappers.<UserRole>lambdaQuery()
                .eq(UserRole::getUserId, id)) > 0) {
            throw new ServiceException(400, "该用户仍持有角色授权，请先移除其项目角色");
        }
        if (commentMapper.selectCount(Wrappers.<IssueComment>lambdaQuery()
                .eq(IssueComment::getUserId, id)) > 0
                || activityMapper.selectCount(Wrappers.<IssueActivity>lambdaQuery()
                .eq(IssueActivity::getActorId, id)) > 0) {
            throw new ServiceException(400, "该用户存在评论或动态记录，不允许删除，可改为禁用账号");
        }
        // 删号前先踢掉会话：否则被删用户仍能凭旧 Session 继续调用接口（直到超时）
        sessionRegistry.invalidateAll(id);
        userMapper.deleteById(id);
    }

    private UserVO toVO(SysUser u) {
        UserVO vo = new UserVO();
        vo.setId(u.getId());
        vo.setUsername(u.getUsername());
        vo.setDisplayName(u.getDisplayName());
        vo.setEmail(u.getEmail());
        vo.setAvatarUrl(u.getAvatarUrl());
        vo.setEnabled(u.getEnabled());
        vo.setAdmin(u.getAdmin());
        return vo;
    }

    private ProfileVO toProfileVO(SysUser u) {
        ProfileVO vo = new ProfileVO();
        vo.setId(u.getId());
        vo.setUsername(u.getUsername());
        vo.setDisplayName(u.getDisplayName());
        vo.setEmail(u.getEmail());
        vo.setAvatarUrl(u.getAvatarUrl());
        vo.setEnabled(u.getEnabled());
        vo.setCreatedAt(u.getCreatedAt());
        return vo;
    }

    /** 查询当前登录用户个人资料 */
    public ProfileVO getProfile(UUID id) {
        SysUser u = userMapper.selectById(id);
        if (u == null) {
            throw new ServiceException(404, "用户不存在");
        }
        return toProfileVO(u);
    }

    /** 当前用户修改个人资料（不允许改 username/enabled） */
    @Transactional
    public ProfileVO updateProfile(UUID id, UpdateProfileRequest req) {
        SysUser u = userMapper.selectById(id);
        if (u == null) {
            throw new ServiceException(404, "用户不存在");
        }
        if (req.getDisplayName() != null && !req.getDisplayName().isBlank()) {
            u.setDisplayName(req.getDisplayName());
        }
        if (req.getEmail() != null) {
            u.setEmail(req.getEmail());
        }
        if (req.getAvatarUrl() != null) {
            u.setAvatarUrl(req.getAvatarUrl());
        }
        userMapper.updateById(u);
        return toProfileVO(u);
    }

    /** 当前用户修改密码：需校验旧密码；成功后踢掉该用户其它会话（含被盗会话） */
    @Transactional
    public void changePassword(UUID id, ChangePasswordRequest req, String currentSessionId) {
        SysUser u = userMapper.selectById(id);
        if (u == null) {
            throw new ServiceException(404, "用户不存在");
        }
        if (u.getPasswordHash() == null
                || !passwordEncoder.matches(req.getOldPassword(), u.getPasswordHash())) {
            throw new ServiceException(400, "原密码不正确");
        }
        if (req.getNewPassword().length() < 6) {
            throw new ServiceException(400, "新密码长度不能少于 6 位");
        }
        u.setPasswordHash(passwordEncoder.encode(req.getNewPassword()));
        userMapper.updateById(u);
        // 保留当前会话（本人刚改完密码不该被自己踢下线），其余会话一律失效
        sessionRegistry.invalidateOthers(id, currentSessionId);
    }
}

package com.seven7m.kanban.service.user;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.seven7m.kanban.common.domain.PageResult;
import com.seven7m.kanban.common.exception.ServiceException;
import com.seven7m.kanban.common.security.UserContext;
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

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * 第一版登录桩：用户名存在校验 + BCrypt 密码校验 + 设置线程级 UserContext。
 * 后续接入 JWT/SaToken 后由拦截器填充 UserContext，本实现替换为真实鉴权。
 * 默认管理员：admin / admin123（bcrypt 校验）。
 */
@Service
@RequiredArgsConstructor
public class UserService {

    private final SysUserMapper userMapper;
    private final ProjectMemberMapper projectMemberMapper;
    private final UserRoleMapper userRoleMapper;
    private final IssueCommentMapper commentMapper;
    private final IssueActivityMapper activityMapper;
    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    public LoginVO login(LoginRequest req) {
        SysUser user = userMapper.selectOne(Wrappers.<SysUser>lambdaQuery()
                .eq(SysUser::getUsername, req.getUsername()));
        if (user == null || !Boolean.TRUE.equals(user.getEnabled())) {
            throw new ServiceException(401, "用户名或密码错误");
        }
        // BCrypt 校验密码哈希；空哈希直接拒绝，避免任意登录
        if (user.getPasswordHash() == null
                || !passwordEncoder.matches(req.getPassword(), user.getPasswordHash())) {
            throw new ServiceException(401, "用户名或密码错误");
        }

        LoginVO vo = new LoginVO();
        BeanUtils.copyProperties(user, vo);
        vo.setToken("stub-" + user.getId());
        // 与 PermissionService#isSuperAdmin 口径保持一致：is_admin 字段或种子管理员 UUID。
        // 不能只依赖 BeanUtils 拷贝的 is_admin，否则种子管理员（该字段可能为空）会被判为非管理员。
        vo.setIsAdmin(Boolean.TRUE.equals(user.getIsAdmin())
                || user.getId().equals(UserContext.DEFAULT_ADMIN_ID));
        return vo;
    }

    public SysUser getById(UUID id) {
        return userMapper.selectById(id);
    }

    /** 全量用户简表，供缺陷负责人/验证人下拉选人 */
    public List<UserVO> list() {
        return userMapper.selectList(Wrappers.<SysUser>lambdaQuery().orderByAsc(SysUser::getDisplayName))
                .stream().map(this::toVO)
                .collect(Collectors.toList());
    }

    /** 用户管理视图：分页 + 关键字（username / displayName / email），与工作项列表一致的 PageResult 结构 */
    public PageResult<UserVO> pageForAdmin(int page, int size, String keyword) {
        int p = page < 1 ? 1 : page;
        int s = size < 1 ? 20 : Math.min(size, 100);
        LambdaQueryWrapper<SysUser> q = Wrappers.lambdaQuery();
        if (keyword != null && !keyword.isBlank()) {
            // 转义 LIKE 通配符，避免关键字 _ / % 命中全部用户
            String pattern = "%" + keyword.trim().replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_") + "%";
            q.and(w -> w.apply("(username LIKE {0} ESCAPE '\\' OR display_name LIKE {1} ESCAPE '\\' OR email LIKE {2} ESCAPE '\\')",
                    pattern, pattern, pattern));
        }
        long total = userMapper.selectCount(q);
        q.orderByAsc(SysUser::getDisplayName)
                .last("LIMIT " + s + " OFFSET " + ((long) (p - 1) * s));
        List<UserVO> list = userMapper.selectList(q).stream().map(this::toVO).collect(Collectors.toList());
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

        userMapper.insert(u);
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
        if (req.getPassword() != null && !req.getPassword().isBlank()) {
            u.setPasswordHash(passwordEncoder.encode(req.getPassword()));
        }
        userMapper.updateById(u);
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

    /** 当前用户修改密码：需校验旧密码 */
    @Transactional
    public void changePassword(UUID id, ChangePasswordRequest req) {
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
    }
}

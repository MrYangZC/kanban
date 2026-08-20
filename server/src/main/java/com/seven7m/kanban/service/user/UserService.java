package com.seven7m.kanban.service.user;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
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
import com.seven7m.kanban.entity.user.SysUser;
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
        UserContext.set(user.getId(), user.getUsername());

        LoginVO vo = new LoginVO();
        BeanUtils.copyProperties(user, vo);
        vo.setToken("stub-" + user.getId());
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

    /** 用户管理视图：含 username/email/enabled，列表默认过滤已删除 */
    public List<UserVO> listForAdmin() {
        return userMapper.selectList(Wrappers.<SysUser>lambdaQuery()
                        .eq(SysUser::getDeleted, 0)
                        .orderByAsc(SysUser::getDisplayName))
                .stream().map(this::toVO)
                .collect(Collectors.toList());
    }

    @Transactional
    public UserVO create(CreateUserRequest req) {
        if (userMapper.selectOne(Wrappers.<SysUser>lambdaQuery().eq(SysUser::getUsername, req.getUsername())) != null) {
            throw new ServiceException(400, "用户名已存在");
        }
        SysUser u = new SysUser();
        u.setId(UUID.randomUUID());
        u.setUsername(req.getUsername());
        u.setDisplayName(req.getDisplayName());
        u.setEmail(req.getEmail());
        u.setAvatarUrl(req.getAvatarUrl());
        u.setPasswordHash(passwordEncoder.encode(req.getPassword() == null || req.getPassword().isBlank()
                ? "admin123" : req.getPassword()));
        u.setEnabled(true);
        u.setDeleted(0);
        userMapper.insert(u);
        return toVO(u);
    }

    @Transactional
    public UserVO update(UUID id, UpdateUserRequest req) {
        SysUser u = userMapper.selectById(id);
        if (u == null || Boolean.TRUE.equals(u.getDeleted())) {
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
        SysUser u = userMapper.selectById(id);
        if (u == null) return;
        // 逻辑删除（@TableLogic）
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
        if (u == null || Boolean.TRUE.equals(u.getDeleted())) {
            throw new ServiceException(404, "用户不存在");
        }
        return toProfileVO(u);
    }

    /** 当前用户修改个人资料（不允许改 username/enabled） */
    @Transactional
    public ProfileVO updateProfile(UUID id, UpdateProfileRequest req) {
        SysUser u = userMapper.selectById(id);
        if (u == null || Boolean.TRUE.equals(u.getDeleted())) {
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
        if (u == null || Boolean.TRUE.equals(u.getDeleted())) {
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

package com.seven7m.kanban.service.user;

import com.baomidou.mybatisplus.extension.service.IService;
import com.seven7m.kanban.common.domain.PageResult;
import com.seven7m.kanban.dto.project.MemberCandidateVO;
import com.seven7m.kanban.dto.user.ChangePasswordRequest;
import com.seven7m.kanban.dto.user.CreateUserRequest;
import com.seven7m.kanban.dto.user.LoginRequest;
import com.seven7m.kanban.dto.user.LoginVO;
import com.seven7m.kanban.dto.user.ProfileVO;
import com.seven7m.kanban.dto.user.UpdateProfileRequest;
import com.seven7m.kanban.dto.user.UpdateUserRequest;
import com.seven7m.kanban.dto.user.UserVO;
import com.seven7m.kanban.entity.user.SysUser;

import java.util.List;
import java.util.UUID;

/**
 * 登录与用户管理接口。
 *
 * <p>继承 {@link IService} 是为了复用 MyBatis-Plus 的通用能力（按 id 取、保存、分页、计数），
 * 不必在实现里重复 {@code mapper.xxx} 的样板；同时保留领域方法（登录、建档、改密、踢会话）。
 *
 * <p>实现见 {@code com.seven7m.kanban.service.user.impl.UserServiceImpl}。
 */
public interface UserService extends IService<SysUser> {

    /** 登录：校验口令 + 失败计数限流，成功返回登录态所需信息 */
    LoginVO login(LoginRequest req);

    /** 按 id 取用户实体；不存在返回 null（内部装配用，对外请用 {@link #getProfile}） */
    SysUser findById(UUID id);

    /** 人员下拉用的精简列表（id/账号/显示名/头像），任何登录用户可用且不下发 email */
    List<MemberCandidateVO> listOptions();

    /** 用户管理视图：分页 + 关键字（username / displayName / email） */
    PageResult<UserVO> pageForAdmin(int page, int size, String keyword);

    /** 新建用户（管理员操作，口令走强度策略） */
    UserVO create(CreateUserRequest req);

    /** 修改用户（管理员操作，可重置口令、启停用、改管理员身份） */
    UserVO update(UUID id, UpdateUserRequest req);

    /** 删除用户；存在项目成员/角色/评论等硬引用时拒绝并提示改为禁用 */
    void delete(UUID id);

    /** 当前登录用户的个人资料 */
    ProfileVO getProfile(UUID id);

    /** 当前用户修改个人资料（不允许改 username / enabled） */
    ProfileVO updateProfile(UUID id, UpdateProfileRequest req);

    /** 当前用户修改密码：需校验旧密码 */
    void changePassword(UUID id, ChangePasswordRequest req);
}

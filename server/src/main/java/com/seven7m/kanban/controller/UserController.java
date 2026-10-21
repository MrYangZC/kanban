package com.seven7m.kanban.controller;

import com.seven7m.kanban.common.domain.PageResult;
import com.seven7m.kanban.common.domain.R;
import com.seven7m.kanban.common.security.RequireAdmin;
import com.seven7m.kanban.common.security.UserContext;
import com.seven7m.kanban.common.security.UserContextInterceptor;
import com.seven7m.kanban.dto.user.ChangePasswordRequest;
import com.seven7m.kanban.dto.user.CreateUserRequest;
import com.seven7m.kanban.dto.user.LoginRequest;
import com.seven7m.kanban.dto.user.LoginVO;
import com.seven7m.kanban.dto.user.ProfileVO;
import com.seven7m.kanban.dto.user.UpdateProfileRequest;
import com.seven7m.kanban.dto.user.UpdateUserRequest;
import com.seven7m.kanban.dto.user.UserVO;
import com.seven7m.kanban.service.user.UserService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    @PostMapping("/login")
    public R<LoginVO> login(@Valid @RequestBody LoginRequest req, HttpServletRequest request) {
        LoginVO vo = userService.login(req);
        // 登录态维持：把用户写进 HttpSession，拦截器据此还原真实操作人
        request.getSession(true)
                .setAttribute(UserContextInterceptor.SESSION_UID, vo.getId());
        request.getSession(true)
                .setAttribute(UserContextInterceptor.SESSION_UNAME, vo.getUsername());
        return R.ok(vo);
    }

    /**
     * 登出：销毁服务端 HttpSession。
     * 此前前端仅在本地清理凭证，服务端会话仍存活（会话劫持/复用风险）。
     */
    @PostMapping("/logout")
    public R<Void> logout(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        if (session != null) {
            session.invalidate();
        }
        return R.ok();
    }

    /** 用户管理列表（含 username/email/enabled）：分页 + 关键字检索 */
    @RequireAdmin
    @GetMapping
    public R<PageResult<UserVO>> list(@RequestParam(defaultValue = "1") int page,
                                      @RequestParam(defaultValue = "20") int size,
                                      @RequestParam(required = false) String keyword) {
        return R.ok(userService.pageForAdmin(page, size, keyword));
    }

    /** 全量用户简表：仅供「添加项目成员」等人员下拉使用 */
    @RequireAdmin
    @GetMapping("/options")
    public R<List<UserVO>> options() {
        return R.ok(userService.list());
    }

    @RequireAdmin
    @PostMapping
    public R<UserVO> create(@Valid @RequestBody CreateUserRequest req) {
        return R.ok(userService.create(req));
    }

    @RequireAdmin
    @PutMapping("/{id}")
    public R<UserVO> update(@PathVariable UUID id, @Valid @RequestBody UpdateUserRequest req) {
        return R.ok(userService.update(id, req));
    }

    @RequireAdmin
    @DeleteMapping("/{id}")
    public R<Void> delete(@PathVariable UUID id) {
        userService.delete(id);
        return R.ok();
    }

    /** 获取当前登录用户个人资料 */
    @GetMapping("/me")
    public R<ProfileVO> me() {
        return R.ok(userService.getProfile(UserContext.getUserId()));
    }

    /** 当前用户修改个人资料 */
    @PutMapping("/me")
    public R<ProfileVO> updateMe(@Valid @RequestBody UpdateProfileRequest req) {
        return R.ok(userService.updateProfile(UserContext.getUserId(), req));
    }

    /** 当前用户修改密码 */
    @PutMapping("/me/password")
    public R<Void> changePassword(@Valid @RequestBody ChangePasswordRequest req) {
        userService.changePassword(UserContext.getUserId(), req);
        return R.ok();
    }
}

package com.seven7m.kanban.user.controller;

import com.seven7m.kanban.common.domain.R;
import com.seven7m.kanban.common.security.UserContext;
import com.seven7m.kanban.common.security.UserContextInterceptor;
import com.seven7m.kanban.user.dto.ChangePasswordRequest;
import com.seven7m.kanban.user.dto.CreateUserRequest;
import com.seven7m.kanban.user.dto.LoginRequest;
import com.seven7m.kanban.user.dto.LoginVO;
import com.seven7m.kanban.user.dto.ProfileVO;
import com.seven7m.kanban.user.dto.UpdateProfileRequest;
import com.seven7m.kanban.user.dto.UpdateUserRequest;
import com.seven7m.kanban.user.dto.UserVO;
import com.seven7m.kanban.user.service.UserService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/v1/users")
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

    /** 用户管理列表（含 username/email/enabled） */
    @GetMapping
    public R<List<UserVO>> list() {
        return R.ok(userService.listForAdmin());
    }

    @PostMapping
    public R<UserVO> create(@Valid @RequestBody CreateUserRequest req) {
        return R.ok(userService.create(req));
    }

    @PutMapping("/{id}")
    public R<UserVO> update(@PathVariable UUID id, @RequestBody UpdateUserRequest req) {
        return R.ok(userService.update(id, req));
    }

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
    public R<ProfileVO> updateMe(@RequestBody UpdateProfileRequest req) {
        return R.ok(userService.updateProfile(UserContext.getUserId(), req));
    }

    /** 当前用户修改密码 */
    @PutMapping("/me/password")
    public R<Void> changePassword(@Valid @RequestBody ChangePasswordRequest req) {
        userService.changePassword(UserContext.getUserId(), req);
        return R.ok();
    }
}

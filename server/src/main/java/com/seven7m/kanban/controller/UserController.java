package com.seven7m.kanban.controller;

import org.springframework.security.access.prepost.PreAuthorize;
import com.seven7m.kanban.common.domain.PageResult;
import com.seven7m.kanban.common.domain.R;
import com.seven7m.kanban.common.security.UserContext;
import com.seven7m.kanban.common.security.jwt.JwtAuthenticationFilter;
import com.seven7m.kanban.common.security.jwt.JwtService;
import com.seven7m.kanban.dto.project.MemberCandidateVO;
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
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/**
 * 用户与账号：登录、登出、人员下拉、用户管理（分页/增删改）、个人中心、我的权限码。
 *
 * <p>鉴权分层要注意：登录与 /users/me 只要求登录态；用户管理与 RBAC 由
 * AdminAuthInterceptor 的 @PreAuthorize("hasRole('ADMIN')")（sys_user.is_admin）把关，**不是**项目级权限码；
 * 只有 /projects/{id}/my-permissions 走项目成员身份。
 */
@RestController
@RequestMapping("/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;
    /** 会话登记表：登录时登记，登出/被踢时移除，供"禁用即下线"使用 */
    private final JwtService jwtService;
    private final com.seven7m.kanban.common.upload.UploadProperties uploadProperties;

    @PostMapping("/login")
    public R<LoginVO> login(@Valid @RequestBody LoginRequest req, HttpServletRequest request,
                            HttpServletResponse response) {
        LoginVO vo = userService.login(req);
        // 签发 JWT：无状态，不再创建 HttpSession（也就不存在会话固定攻击面）
        JwtService.Issued issued = jwtService.issue(vo.getId(), vo.getUsername(), Boolean.TRUE.equals(vo.getAdmin()));
        vo.setToken(issued.token());
        vo.setExpiresAt(issued.expiresAt().toEpochMilli());
        // 附件静态文件无法通过 <img src> 带 Authorization 头，单独下发一个路径受限的 Cookie
        writeFileCookie(request, response, issued);
        return R.ok(vo);
    }

    /**
     * 附件读取用的 Cookie：HttpOnly + 仅作用于 {@code /uploads} 路径 + SameSite=Lax。
     * 它的存在只为让 {@code <img src>} 能通过鉴权，不参与业务接口的身份判定。
     */
    private void writeFileCookie(HttpServletRequest request, HttpServletResponse response, JwtService.Issued issued) {
        jakarta.servlet.http.Cookie cookie = new jakarta.servlet.http.Cookie(
                JwtAuthenticationFilter.FILE_COOKIE, issued.token());
        cookie.setHttpOnly(true);
        cookie.setPath(uploadProperties.publicPrefix() + JwtAuthenticationFilter.FILE_COOKIE_PATH);
        cookie.setMaxAge((int) jwtService.expireSeconds());
        cookie.setSecure(request.isSecure());
        cookie.setAttribute("SameSite", "Lax");
        response.addCookie(cookie);
    }

    /**
     * 登出：JWT 无状态，服务端没有可销毁的会话，前端丢弃 token 即可。
     * 保留该接口是为了前端调用口径统一（且便于将来加黑名单时只改一处）。
     */
    @PostMapping("/logout")
    public R<Void> logout(HttpServletRequest request, HttpServletResponse response) {
        // 顺手清掉文件 Cookie（token 本身无法撤销，见下方说明）
        jakarta.servlet.http.Cookie cookie = new jakarta.servlet.http.Cookie(
                JwtAuthenticationFilter.FILE_COOKIE, "");
        cookie.setHttpOnly(true);
        cookie.setPath(uploadProperties.publicPrefix() + JwtAuthenticationFilter.FILE_COOKIE_PATH);
        cookie.setMaxAge(0);
        response.addCookie(cookie);
        // JWT 无状态且不做主动失效：服务端没有可销毁的会话，登出等价于前端丢弃 token。
        // 旧 token 会在 kanban.jwt.expire-minutes 之后自然失效，期间若仍持有它依然可用。
        return R.ok();
    }

    /** 用户管理列表（含 username/email/enabled/isAdmin）：分页 + 关键字检索 */
    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping
    public R<PageResult<UserVO>> list(@RequestParam(defaultValue = "1") int page,
                                      @RequestParam(defaultValue = "20") int size,
                                      @RequestParam(required = false) String keyword) {
        return R.ok(userService.pageForAdmin(page, size, keyword));
    }

    /**
     * 人员下拉（负责人/验证人/按人筛选），返回 id/账号/显示名/头像，不含 email。
     *
     * <p>此接口**不能**加 @PreAuthorize("hasRole('ADMIN')")：项目成员在详情抽屉里改负责人时会用到它，
     * 加管理员限制会让普通成员每次打开详情都吃到 403 且选不到人（本次修复的 P1 问题）。
     * 用户管理列表继续由 {@link #list} 承担，那个才需要管理员。
     */
    @GetMapping("/options")
    public R<List<MemberCandidateVO>> options() {
        return R.ok(userService.listOptions());
    }

    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping
    public R<UserVO> create(@Valid @RequestBody CreateUserRequest req) {
        return R.ok(userService.create(req));
    }

    @PreAuthorize("hasRole('ADMIN')")
    @PutMapping("/{id}")
    public R<UserVO> update(@PathVariable UUID id, @Valid @RequestBody UpdateUserRequest req) {
        return R.ok(userService.update(id, req));
    }

    @PreAuthorize("hasRole('ADMIN')")
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

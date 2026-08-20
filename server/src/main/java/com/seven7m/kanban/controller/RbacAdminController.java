package com.seven7m.kanban.controller;

import com.seven7m.kanban.common.domain.R;
import com.seven7m.kanban.common.security.RequireAdmin;
import com.seven7m.kanban.entity.rbac.Menu;
import com.seven7m.kanban.entity.rbac.Role;
import com.seven7m.kanban.service.rbac.RbacAdminService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/**
 * RBAC 全局管理后台接口（系统管理员）。受 @RequireAdmin 保护。
 */
@RestController
@RequestMapping("/admin")
@RequiredArgsConstructor
public class RbacAdminController {

    private final RbacAdminService rbacAdminService;

    // ---------------- 角色 ----------------
    @RequireAdmin
    @GetMapping("/roles")
    public R<List<Role>> listRoles() {
        return R.ok(rbacAdminService.listRoles());
    }

    @RequireAdmin
    @PostMapping("/roles")
    public R<Role> createRole(@RequestBody Role role) {
        return R.ok(rbacAdminService.createRole(role));
    }

    @RequireAdmin
    @PutMapping("/roles/{id}")
    public R<Role> updateRole(@PathVariable UUID id, @RequestBody Role role) {
        return R.ok(rbacAdminService.updateRole(id, role));
    }

    @RequireAdmin
    @DeleteMapping("/roles/{id}")
    public R<Void> deleteRole(@PathVariable UUID id) {
        rbacAdminService.deleteRole(id);
        return R.ok();
    }

    // ---------------- 菜单 / 权限点 ----------------
    @RequireAdmin
    @GetMapping("/menus")
    public R<List<Menu>> listMenus() {
        return R.ok(rbacAdminService.listMenus());
    }

    @RequireAdmin
    @PostMapping("/menus")
    public R<Menu> createMenu(@RequestBody Menu menu) {
        return R.ok(rbacAdminService.createMenu(menu));
    }

    @RequireAdmin
    @PutMapping("/menus/{id}")
    public R<Menu> updateMenu(@PathVariable UUID id, @RequestBody Menu menu) {
        return R.ok(rbacAdminService.updateMenu(id, menu));
    }

    @RequireAdmin
    @DeleteMapping("/menus/{id}")
    public R<Void> deleteMenu(@PathVariable UUID id) {
        rbacAdminService.deleteMenu(id);
        return R.ok();
    }

    // ---------------- 角色-菜单 ----------------
    @RequireAdmin
    @GetMapping("/roles/{roleId}/menus")
    public R<List<UUID>> listRoleMenus(@PathVariable UUID roleId) {
        return R.ok(rbacAdminService.listRoleMenus(roleId));
    }

    @RequireAdmin
    @PostMapping("/roles/{roleId}/menus")
    public R<Void> assignRoleMenus(@PathVariable UUID roleId, @RequestBody List<UUID> menuIds) {
        rbacAdminService.assignRoleMenus(roleId, menuIds);
        return R.ok();
    }

    // ---------------- 用户-角色 ----------------
    @RequireAdmin
    @GetMapping("/users/{userId}/roles")
    public R<List<UUID>> listUserRoles(@PathVariable UUID userId, @RequestParam UUID projectId) {
        return R.ok(rbacAdminService.listUserRoles(userId, projectId));
    }

    @RequireAdmin
    @PostMapping("/users/{userId}/roles")
    public R<Void> assignUserRoles(@PathVariable UUID userId,
                                   @RequestParam UUID projectId,
                                   @RequestBody List<UUID> roleIds) {
        rbacAdminService.assignUserRoles(userId, projectId, roleIds);
        return R.ok();
    }
}

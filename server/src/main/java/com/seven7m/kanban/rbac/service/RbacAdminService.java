package com.seven7m.kanban.rbac.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.seven7m.kanban.common.exception.ServiceException;
import com.seven7m.kanban.common.security.UserContext;
import com.seven7m.kanban.rbac.entity.Menu;
import com.seven7m.kanban.rbac.entity.Role;
import com.seven7m.kanban.rbac.entity.RoleMenu;
import com.seven7m.kanban.rbac.entity.UserRole;
import com.seven7m.kanban.rbac.mapper.MenuMapper;
import com.seven7m.kanban.rbac.mapper.RoleMapper;
import com.seven7m.kanban.rbac.mapper.RoleMenuMapper;
import com.seven7m.kanban.rbac.mapper.UserRoleMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * RBAC 全局管理后台服务：角色、菜单/权限点、角色-菜单、用户-角色 的配置维护。
 * 接口受 {@code @RequireAdmin} + AdminAuthInterceptor 保护。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RbacAdminService {

    private final RoleMapper roleMapper;
    private final MenuMapper menuMapper;
    private final RoleMenuMapper roleMenuMapper;
    private final UserRoleMapper userRoleMapper;

    // ---------------- 角色 ----------------
    public List<Role> listRoles() {
        return roleMapper.selectList(Wrappers.<Role>lambdaQuery().orderByAsc(Role::getSortOrder));
    }

    public Role createRole(Role role) {
        role.setCreatedBy(UserContext.getUserId());
        role.setUpdatedBy(UserContext.getUserId());
        roleMapper.insert(role);
        return role;
    }

    public Role updateRole(UUID id, Role role) {
        Role exist = requireRole(id);
        exist.setCode(role.getCode());
        exist.setName(role.getName());
        exist.setDescription(role.getDescription());
        exist.setSortOrder(role.getSortOrder());
        exist.setUpdatedBy(UserContext.getUserId());
        roleMapper.updateById(exist);
        return exist;
    }

    @Transactional
    public void deleteRole(UUID id) {
        requireRole(id);
        // 级联清理角色-菜单关联 与 用户-角色关联，避免残留孤儿数据
        roleMenuMapper.delete(Wrappers.<RoleMenu>lambdaQuery().eq(RoleMenu::getRoleId, id));
        userRoleMapper.delete(Wrappers.<UserRole>lambdaQuery().eq(UserRole::getRoleId, id));
        roleMapper.deleteById(id);
    }

    // ---------------- 菜单 / 权限点 ----------------
    public List<Menu> listMenus() {
        return menuMapper.selectList(Wrappers.<Menu>lambdaQuery().orderByAsc(Menu::getSortOrder));
    }

    public Menu createMenu(Menu menu) {
        menu.setCreatedBy(UserContext.getUserId());
        menu.setUpdatedBy(UserContext.getUserId());
        menuMapper.insert(menu);
        return menu;
    }

    public Menu updateMenu(UUID id, Menu menu) {
        Menu exist = requireMenu(id);
        exist.setCode(menu.getCode());
        exist.setName(menu.getName());
        exist.setParentId(menu.getParentId());
        exist.setType(menu.getType());
        exist.setSortOrder(menu.getSortOrder());
        exist.setUpdatedBy(UserContext.getUserId());
        menuMapper.updateById(exist);
        return exist;
    }

    @Transactional
    public void deleteMenu(UUID id) {
        requireMenu(id);
        roleMenuMapper.delete(Wrappers.<RoleMenu>lambdaQuery().eq(RoleMenu::getMenuId, id));
        menuMapper.deleteById(id);
    }

    // ---------------- 角色-菜单 ----------------
    public List<UUID> listRoleMenus(UUID roleId) {
        return roleMenuMapper.selectList(Wrappers.<RoleMenu>lambdaQuery().eq(RoleMenu::getRoleId, roleId))
                .stream().map(RoleMenu::getMenuId).toList();
    }

    /** 全量设置角色菜单（先删后插） */
    @Transactional
    public void assignRoleMenus(UUID roleId, List<UUID> menuIds) {
        requireRole(roleId);
        roleMenuMapper.delete(Wrappers.<RoleMenu>lambdaQuery().eq(RoleMenu::getRoleId, roleId));
        if (menuIds != null) {
            for (UUID menuId : menuIds) {
                RoleMenu rm = new RoleMenu();
                rm.setRoleId(roleId);
                rm.setMenuId(menuId);
                rm.setCreatedAt(LocalDateTime.now());
                roleMenuMapper.insert(rm);
            }
        }
    }

    // ---------------- 用户-角色 ----------------
    public List<UUID> listUserRoles(UUID userId, UUID projectId) {
        return userRoleMapper.selectList(Wrappers.<UserRole>lambdaQuery()
                        .eq(UserRole::getUserId, userId)
                        .eq(UserRole::getProjectId, projectId))
                .stream().map(UserRole::getRoleId).toList();
    }

    /** 全量设置用户在某项目的角色（先删后插） */
    @Transactional
    public void assignUserRoles(UUID userId, UUID projectId, List<UUID> roleIds) {
        userRoleMapper.delete(Wrappers.<UserRole>lambdaQuery()
                .eq(UserRole::getUserId, userId)
                .eq(UserRole::getProjectId, projectId));
        if (roleIds != null) {
            for (UUID roleId : roleIds) {
                UserRole ur = new UserRole();
                ur.setUserId(userId);
                ur.setRoleId(roleId);
                ur.setProjectId(projectId);
                ur.setCreatedAt(LocalDateTime.now());
                userRoleMapper.insert(ur);
            }
        }
    }

    private Role requireRole(UUID id) {
        Role role = roleMapper.selectById(id);
        if (role == null) {
            throw new ServiceException(404, "角色不存在");
        }
        return role;
    }

    private Menu requireMenu(UUID id) {
        Menu menu = menuMapper.selectById(id);
        if (menu == null) {
            throw new ServiceException(404, "菜单/权限点不存在");
        }
        return menu;
    }
}

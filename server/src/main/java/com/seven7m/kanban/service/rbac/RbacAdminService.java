package com.seven7m.kanban.service.rbac;

import org.springframework.security.access.prepost.PreAuthorize;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.toolkit.Db;
import com.seven7m.kanban.common.exception.ServiceException;
import com.seven7m.kanban.common.security.UserContext;
import com.seven7m.kanban.dto.rbac.MenuRequest;
import com.seven7m.kanban.dto.rbac.RoleRequest;
import com.seven7m.kanban.entity.rbac.Menu;
import com.seven7m.kanban.entity.rbac.PermissionDef;
import com.seven7m.kanban.entity.rbac.Role;
import com.seven7m.kanban.entity.rbac.RoleMenu;
import com.seven7m.kanban.entity.rbac.UserRole;
import com.seven7m.kanban.entity.project.ProjectRole;
import com.seven7m.kanban.mapper.rbac.MenuMapper;
import com.seven7m.kanban.mapper.rbac.PermissionMapper;
import com.seven7m.kanban.mapper.rbac.RoleMapper;
import com.seven7m.kanban.mapper.rbac.RoleMenuMapper;
import com.seven7m.kanban.mapper.rbac.UserRoleMapper;
import com.seven7m.kanban.common.security.PermissionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * RBAC 全局管理后台服务：角色、菜单（导航）、角色-菜单、用户-角色、角色-权限码 的配置维护。
 * 接口受 {@code @PreAuthorize("hasRole('ADMIN')")} + AdminAuthInterceptor 保护。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RbacAdminService {

    private final RoleMapper roleMapper;
    private final MenuMapper menuMapper;
    private final RoleMenuMapper roleMenuMapper;
    private final UserRoleMapper userRoleMapper;
    private final PermissionMapper permissionMapper;
    private final PermissionService permissionService;

    /**
     * 内置基础角色，禁止删除、也禁止改编码。
     * 删除或改码会让依赖 role.code 的逻辑失效（如按 code='OWNER' 统计所有者），
     * 导致项目失去管理者、新建项目时授权失败。
     */
    private static final Set<String> PROTECTED_ROLE_CODES = Set.of(ProjectRole.OWNER, ProjectRole.ADMIN);

    // ---------------- 角色 ----------------
    public List<Role> listRoles() {
        return roleMapper.selectList(Wrappers.<Role>lambdaQuery().orderByAsc(Role::getSortOrder));
    }

    public Role createRole(RoleRequest req) {
        Role role = new Role();
        role.setId(UUID.randomUUID());
        applyRoleRequest(role, req);
        role.setCreatedBy(UserContext.getUserId());
        role.setUpdatedBy(UserContext.getUserId());
        roleMapper.insert(role);
        return role;
    }

    public Role updateRole(UUID id, RoleRequest req) {
        Role exist = requireRole(id);
        if (isProtected(exist.getCode())
                && (req.getCode() == null || !req.getCode().equals(exist.getCode()))) {
            throw new ServiceException(400, "内置角色的编码不可修改：" + exist.getCode());
        }
        applyRoleRequest(exist, req);
        exist.setUpdatedBy(UserContext.getUserId());
        roleMapper.updateById(exist);
        return exist;
    }

    /** 只落库客户端允许提交的字段，审计字段由服务端维护 */
    private void applyRoleRequest(Role role, RoleRequest req) {
        role.setCode(req.getCode());
        role.setName(req.getName());
        role.setDescription(req.getDescription());
        role.setSortOrder(req.getSortOrder());
    }

    @Transactional
    public void deleteRole(UUID id) {
        Role role = requireRole(id);
        if (isProtected(role.getCode())) {
            throw new ServiceException(400, "内置角色不可删除：" + role.getCode());
        }
        // 已被引用的角色不静默解绑：先提示受影响范围，避免用户权限被无声剥夺
        long userCount = userRoleMapper.selectCount(Wrappers.<UserRole>lambdaQuery().eq(UserRole::getRoleId, id));
        if (userCount > 0) {
            throw new ServiceException(400,
                    "该角色已分配给 " + userCount + " 个用户，请先解除这些用户的角色绑定后再删除");
        }
        // 级联清理角色-菜单关联 与 用户-角色关联，避免残留孤儿数据
        roleMenuMapper.delete(Wrappers.<RoleMenu>lambdaQuery().eq(RoleMenu::getRoleId, id));
        userRoleMapper.delete(Wrappers.<UserRole>lambdaQuery().eq(UserRole::getRoleId, id));
        roleMapper.deleteById(id);
        // 删除角色会波及所有持有该角色的用户，无法逐个定位，全量失效权限缓存
        permissionService.evictAllPermissions();
    }

    // ---------------- 权限定义 + 角色-权限码 ----------------
    public List<PermissionDef> listAllPermissions() {
        return permissionMapper.selectList(Wrappers.<PermissionDef>lambdaQuery()
                .orderByAsc(PermissionDef::getModule)
                .orderByAsc(PermissionDef::getSortOrder));
    }

    public List<String> listRolePermissions(UUID roleId) {
        return permissionService.getPermissionsByRole(roleId);
    }

    @Transactional
    public void assignRolePermissions(UUID roleId, List<String> permissionCodes) {
        permissionService.setRolePermissions(roleId, permissionCodes);
    }

    // ---------------- 菜单（导航） ----------------
    public List<Menu> listMenus() {
        return menuMapper.selectList(Wrappers.<Menu>lambdaQuery().orderByAsc(Menu::getSortOrder));
    }

    public Menu createMenu(MenuRequest req) {
        Menu menu = new Menu();
        menu.setId(UUID.randomUUID());
        applyMenuRequest(menu, req);
        menu.setCreatedBy(UserContext.getUserId());
        menu.setUpdatedBy(UserContext.getUserId());
        menuMapper.insert(menu);
        return menu;
    }

    public Menu updateMenu(UUID id, MenuRequest req) {
        Menu exist = requireMenu(id);
        applyMenuRequest(exist, req);
        exist.setUpdatedBy(UserContext.getUserId());
        menuMapper.updateById(exist);
        return exist;
    }

    /** 只落库客户端允许提交的字段，审计字段由服务端维护 */
    private void applyMenuRequest(Menu menu, MenuRequest req) {
        menu.setCode(req.getCode());
        menu.setName(req.getName());
        menu.setParentId(req.getParentId());
        menu.setType(req.getType());
        menu.setSortOrder(req.getSortOrder());
    }

    @Transactional
    public void deleteMenu(UUID id) {
        requireMenu(id);
        // menu.parent_id 没有外键约束，必须手动收集子孙一并删除，否则会留下孤儿菜单节点
        List<UUID> ids = new ArrayList<>();
        collectMenuDescendants(id, ids);
        roleMenuMapper.delete(Wrappers.<RoleMenu>lambdaQuery().in(RoleMenu::getMenuId, ids));
        menuMapper.delete(Wrappers.<Menu>lambdaQuery().in(Menu::getId, ids));
    }

    /** 递归收集菜单及其全部子孙 id */
    private void collectMenuDescendants(UUID parentId, List<UUID> out) {
        out.add(parentId);
        List<Menu> children = menuMapper.selectList(
                Wrappers.<Menu>lambdaQuery().eq(Menu::getParentId, parentId));
        for (Menu child : children) {
            collectMenuDescendants(child.getId(), out);
        }
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
        if (menuIds == null || menuIds.isEmpty()) {
            return;
        }
        // 批量插入，替代循环单条 insert
        List<RoleMenu> rows = menuIds.stream().map(menuId -> {
            RoleMenu rm = new RoleMenu();
            // 主键必须显式赋值：IdType.ASSIGN_UUID 在批量插入时生成的是 32 位字符串，
            // 反射写入 UUID 字段会抛 IllegalArgumentException（argument type mismatch）
            rm.setId(UUID.randomUUID());
            rm.setRoleId(roleId);
            rm.setMenuId(menuId);
            rm.setCreatedAt(LocalDateTime.now());
            return rm;
        }).toList();
        Db.saveBatch(rows);
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
        if (roleIds != null && !roleIds.isEmpty()) {
            // 批量插入，替代循环单条 insert
            List<UserRole> rows = roleIds.stream().map(roleId -> {
                UserRole ur = new UserRole();
                // 同 assignRoleMenus：批量插入必须显式给 UUID 主键
                ur.setId(UUID.randomUUID());
                ur.setUserId(userId);
                ur.setRoleId(roleId);
                ur.setProjectId(projectId);
                ur.setCreatedAt(LocalDateTime.now());
                return ur;
            }).toList();
            Db.saveBatch(rows);
        }
        permissionService.evictUserPermissions(userId, projectId);
    }

    /** 是否受保护的内置角色 */
    private boolean isProtected(String roleCode) {
        return roleCode != null && PROTECTED_ROLE_CODES.contains(roleCode);
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
            throw new ServiceException(404, "菜单不存在");
        }
        return menu;
    }
}

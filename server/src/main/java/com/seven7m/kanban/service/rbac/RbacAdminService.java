package com.seven7m.kanban.service.rbac;

import com.seven7m.kanban.dto.rbac.MenuRequest;
import com.seven7m.kanban.dto.rbac.RoleRequest;
import com.seven7m.kanban.entity.rbac.Menu;
import com.seven7m.kanban.entity.rbac.PermissionDef;
import com.seven7m.kanban.entity.rbac.Role;

import java.util.List;
import java.util.UUID;

/**
 * RBAC 全局管理后台服务：角色、菜单（导航）、角色-菜单、用户-角色、角色-权限码 的配置维护。
 * 接口受 {@code @PreAuthorize("hasRole('ADMIN')")} + AdminAuthInterceptor 保护。
 *
 * <p>不套 {@code IService} / {@code ServiceImpl}：这里操作的是 role / menu / role_menu /
 * user_role / permission 五张表，单实体 CRUD 惯例无法复用，硬套只会让"哪个 Mapper 是主表"变得含糊。
 *
 * <p>实现见 {@code com.seven7m.kanban.service.rbac.impl.RbacAdminServiceImpl}。
 */
public interface RbacAdminService {

    // ---------------- 角色 ----------------

    /** 全部角色，按 sort_order 升序 */
    List<Role> listRoles();

    /** 新建角色（审计字段由服务端填充） */
    Role createRole(RoleRequest req);

    /** 修改角色；内置角色的编码不允许改（改码会让依赖 role.code 的逻辑失效） */
    Role updateRole(UUID id, RoleRequest req);

    /** 删除角色；已被用户引用时拒绝，避免静默剥夺权限；成功后全量失效权限缓存 */
    void deleteRole(UUID id);

    // ---------------- 权限定义 + 角色-权限码 ----------------

    /** 全部权限定义，按 module + sort_order 排序 */
    List<PermissionDef> listAllPermissions();

    /** 某角色已授予的权限码 */
    List<String> listRolePermissions(UUID roleId);

    /** 全量设置角色权限码 */
    void assignRolePermissions(UUID roleId, List<String> permissionCodes);

    // ---------------- 菜单（导航） ----------------

    /** 全部菜单，按 sort_order 升序 */
    List<Menu> listMenus();

    /** 新建菜单 */
    Menu createMenu(MenuRequest req);

    /** 修改菜单 */
    Menu updateMenu(UUID id, MenuRequest req);

    /** 删除菜单及其全部子孙（menu.parent_id 无外键，必须手动递归） */
    void deleteMenu(UUID id);

    // ---------------- 角色-菜单 ----------------

    /** 某角色已绑定的菜单 id 集合 */
    List<UUID> listRoleMenus(UUID roleId);

    /** 全量设置角色菜单（先删后插） */
    void assignRoleMenus(UUID roleId, List<UUID> menuIds);

    // ---------------- 用户-角色 ----------------

    /** 用户在某项目下的角色 id 集合 */
    List<UUID> listUserRoles(UUID userId, UUID projectId);

    /** 全量设置用户在某项目的角色（先删后插） */
    void assignUserRoles(UUID userId, UUID projectId, List<UUID> roleIds);
}

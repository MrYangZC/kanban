package com.seven7m.kanban.mapper.rbac;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.seven7m.kanban.entity.rbac.UserRole;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;
import java.util.UUID;

@Mapper
public interface UserRoleMapper extends BaseMapper<UserRole> {

    /**
     * 查询用户在指定项目下的所有角色编码。
     */
    @Select("""
            SELECT r.code
            FROM user_role ur
            JOIN role r ON r.id = ur.role_id
            WHERE ur.project_id = #{projectId}
              AND ur.user_id = #{userId}
            ORDER BY r.sort_order
            """)
    List<String> selectRoleCodesByProjectAndUser(@Param("projectId") UUID projectId,
                                                 @Param("userId") UUID userId);

    /**
     * 当前用户是否在任一项目拥有 OWNER / ADMIN 角色（即是否系统管理员）。
     */
    @Select("""
            SELECT COUNT(1)
            FROM user_role ur
            JOIN role r ON r.id = ur.role_id
            WHERE ur.user_id = #{userId}
              AND r.code IN ('OWNER', 'ADMIN')
            """)
    int countAdminProjects(@Param("userId") UUID userId);
}

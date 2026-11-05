package com.seven7m.kanban.mapper.rbac;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.seven7m.kanban.entity.rbac.UserRole;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Result;
import org.apache.ibatis.annotations.Results;
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
     * 查询用户在指定项目下的所有角色 id（供 role_permission 按 role_id 联查权限）。
     */
    @Select("""
            SELECT ur.role_id
            FROM user_role ur
            WHERE ur.project_id = #{projectId}
              AND ur.user_id = #{userId}
            """)
    List<UUID> selectRoleIdsByProjectAndUser(@Param("projectId") UUID projectId,
                                             @Param("userId") UUID userId);

    /**
     * 批量查询项目下所有用户的角色编码。
     *
     * <p>结果用 {@link UserRoleCodeRow} 承载并显式映射（手册 ORM 规约：禁止 Map 作查询结果）。
     */
    @Select("""
            SELECT ur.user_id AS user_id, r.code AS role_code
            FROM user_role ur
            JOIN role r ON r.id = ur.role_id
            WHERE ur.project_id = #{projectId}
            ORDER BY r.sort_order
            """)
    @Results(id = "userRoleCodeRow", value = {
            @Result(column = "user_id", property = "userId"),
            @Result(column = "role_code", property = "roleCode")
    })
    List<UserRoleCodeRow> selectRoleCodesByProject(@Param("projectId") UUID projectId);

}

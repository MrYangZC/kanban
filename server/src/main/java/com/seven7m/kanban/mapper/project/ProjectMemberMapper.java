package com.seven7m.kanban.mapper.project;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.seven7m.kanban.entity.project.ProjectMember;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.UUID;

@Mapper
public interface ProjectMemberMapper extends BaseMapper<ProjectMember> {

    /**
     * 统计项目下持有指定角色码的人数（用于"最后一个所有者不能被移除"这类校验）。
     *
     * <p>此前该统计写在服务层：{@code inSql("SELECT id FROM role WHERE code = '" + OWNER + "'")}。
     * 虽然拼进去的是常量、不构成注入，但它把 SQL 文本留在了业务代码里——后来者照抄时
     * 换成任何变量都会变成注入模板，因此一并下沉到 Mapper，角色码走参数绑定。
     *
     * <p>放在项目成员 Mapper 而不是 role Mapper：它回答的是"这个项目的成员里有几个是某角色"，
     * 主语仍是成员名册，{@code user_role} / {@code role} 只是取角色码的必要联表。
     */
    @Select("""
            SELECT COUNT(*)
            FROM user_role ur
            JOIN role r ON r.id = ur.role_id
            WHERE ur.project_id = #{projectId}
              AND r.code = #{roleCode}
            """)
    long selectRoleHolderCount(@Param("projectId") UUID projectId, @Param("roleCode") String roleCode);
}

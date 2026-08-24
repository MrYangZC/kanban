package com.seven7m.kanban.mapper.rbac;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.seven7m.kanban.entity.rbac.RolePermission;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;
import java.util.UUID;

@Mapper
public interface RolePermissionMapper extends BaseMapper<RolePermission> {

    @Select("SELECT permission_code FROM role_permission WHERE role_id = #{roleId}")
    List<String> selectPermissionsByRole(@Param("roleId") UUID roleId);
}

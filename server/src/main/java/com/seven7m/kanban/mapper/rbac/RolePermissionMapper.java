package com.seven7m.kanban.mapper.rbac;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.seven7m.kanban.entity.rbac.RolePermission;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface RolePermissionMapper extends BaseMapper<RolePermission> {

    @Select("SELECT permission_code FROM role_permission WHERE role_code = #{roleCode}")
    List<String> selectPermissionsByRole(String roleCode);
}

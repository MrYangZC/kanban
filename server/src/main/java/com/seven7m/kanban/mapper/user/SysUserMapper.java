package com.seven7m.kanban.mapper.user;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.seven7m.kanban.entity.user.SysUser;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface SysUserMapper extends BaseMapper<SysUser> {

    @Select("SELECT * FROM sys_user WHERE display_name = #{displayName} AND deleted = 0 LIMIT 1")
    SysUser selectByDisplayName(@Param("displayName") String displayName);
}

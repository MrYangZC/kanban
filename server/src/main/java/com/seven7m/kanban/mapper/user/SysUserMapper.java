package com.seven7m.kanban.mapper.user;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.seven7m.kanban.entity.user.SysUser;
import org.apache.ibatis.annotations.Mapper;

/**
 * 用户表 Mapper。
 *
 * <p>只保留 MyBatis-Plus 的通用能力：按展示名查询等场景统一走 {@code selectOne(Wrappers…)}，
 * 由 MP 生成显式列清单，避免手写 {@code SELECT *}（手册 ORM 强制条目）。
 */
@Mapper
public interface SysUserMapper extends BaseMapper<SysUser> {
}

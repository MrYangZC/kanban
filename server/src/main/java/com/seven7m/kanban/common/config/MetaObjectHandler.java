package com.seven7m.kanban.common.config;

import org.apache.ibatis.reflection.MetaObject;

/**
 * 历史占位：实际审计填充逻辑已迁移至 {@link AuditMetaObjectHandler}（文件名与类名一致的规范实现）。
 * 本文件保留仅为兼容旧路径引用，不再注册为 Spring Bean，避免重复 MetaObjectHandler 冲突。
 */
class MetaObjectHandlerPlaceholder implements com.baomidou.mybatisplus.core.handlers.MetaObjectHandler {
    @Override
    public void insertFill(MetaObject metaObject) {
    }

    @Override
    public void updateFill(MetaObject metaObject) {
    }
}

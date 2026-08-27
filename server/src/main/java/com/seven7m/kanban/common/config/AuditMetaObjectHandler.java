package com.seven7m.kanban.common.config;

import com.baomidou.mybatisplus.core.handlers.MetaObjectHandler;
import com.seven7m.kanban.common.security.UserContext;
import org.apache.ibatis.reflection.MetaObject;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * 审计字段自动填充：created_at / created_by / updated_at / updated_by。
 */
@Component
public class AuditMetaObjectHandler implements MetaObjectHandler {

    @Override
    public void insertFill(MetaObject metaObject) {
        LocalDateTime now = LocalDateTime.now();
        UUID operator = UserContext.getUserId();
        strictInsertFill(metaObject, "createdAt", LocalDateTime.class, now);
        strictInsertFill(metaObject, "updatedAt", LocalDateTime.class, now);
        strictInsertFill(metaObject, "createdBy", UUID.class, operator);
        strictInsertFill(metaObject, "updatedBy", UUID.class, operator);
    }

    @Override
    public void updateFill(MetaObject metaObject) {
        strictUpdateFill(metaObject, "updatedAt", LocalDateTime.class, LocalDateTime.now());
        strictUpdateFill(metaObject, "updatedBy", UUID.class, UserContext.getUserId());
    }
}

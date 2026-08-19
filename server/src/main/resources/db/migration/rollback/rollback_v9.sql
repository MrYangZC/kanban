-- =============================================================================
-- V9 回滚脚本（参考用，Flyway 不会执行此目录，需要手动执行）
-- 逆序还原 V9__rbac_rework.sql 的所有变更
-- 警告：会丢失 user_role / role_menu / menu 中的数据，慎用。
-- =============================================================================

-- 1. 重建 workspace / workspace_member
CREATE TABLE workspace (
    id            UUID PRIMARY KEY,
    name          VARCHAR(100) NOT NULL,
    workspace_key VARCHAR(32)  NOT NULL,
    description   VARCHAR(500),
    avatar_url    VARCHAR(500),
    created_by    UUID,
    updated_by    UUID,
    created_at    TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at    TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted       INTEGER NOT NULL DEFAULT 0,
    CONSTRAINT uk_workspace_key UNIQUE (workspace_key)
);

CREATE TABLE workspace_member (
    id           UUID PRIMARY KEY,
    workspace_id UUID NOT NULL,
    user_id      UUID NOT NULL,
    role         VARCHAR(32) NOT NULL DEFAULT 'MEMBER',
    joined_at    TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_workspace_member UNIQUE (workspace_id, user_id)
);

-- 2. project 恢复 workspace_id 与复合唯一键
ALTER TABLE project DROP CONSTRAINT IF EXISTS uk_project_key;
ALTER TABLE project ADD COLUMN workspace_id UUID;
ALTER TABLE project ADD CONSTRAINT uk_project_key UNIQUE (workspace_id, project_key);

-- 3. project_member 恢复 role 列（从 user_role 回迁）
ALTER TABLE project_member ADD COLUMN role VARCHAR(32) NOT NULL DEFAULT 'MEMBER';
UPDATE project_member pm
SET role = r.code
FROM user_role ur
JOIN role r ON r.id = ur.role_id
WHERE ur.project_id = pm.project_id AND ur.user_id = pm.user_id;

-- 4. 删 RBAC 四表
DROP TABLE IF EXISTS role_menu;
DROP TABLE IF EXISTS user_role;
DROP TABLE IF EXISTS menu;
DROP TABLE IF EXISTS role;

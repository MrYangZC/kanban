-- ============================================================
-- V18 幂等兜底：确保操作级 RBAC 的 permission / role_permission 表
-- 一定存在且有正确的种子数据。
-- 解决 V17 首次因 role.code 大小写不一致导致迁移 FAILED、
-- 在 flyway_schema_history 留下失败记录、应用重启被挡住的问题。
-- 本迁移可重复安全执行（全部 IF EXISTS / ON CONFLICT DO NOTHING）。
-- ============================================================

-- 1) 清掉可能残留的 FAILED 记录（V17 在 PG 单事务里已回滚，不会丢数据）
DELETE FROM flyway_schema_history WHERE version = '17' AND success = false;

-- 2) 确保表存在
CREATE TABLE IF NOT EXISTS permission (
    code        VARCHAR(32)  NOT NULL PRIMARY KEY,
    name        VARCHAR(64)  NOT NULL,
    description VARCHAR(255),
    created_at  TIMESTAMP    NOT NULL DEFAULT now()
);

CREATE TABLE IF NOT EXISTS role_permission (
    role_code       VARCHAR(32) NOT NULL REFERENCES role(code) ON DELETE CASCADE,
    permission_code VARCHAR(32) NOT NULL REFERENCES permission(code) ON DELETE CASCADE,
    created_at      TIMESTAMP   NOT NULL DEFAULT now(),
    PRIMARY KEY (role_code, permission_code)
);

-- 3) 确保字典数据
INSERT INTO permission (code, name, description) VALUES
    ('READ',  '查看', '查看项目内资源（issue/评论/附件/活动流/状态等）'),
    ('WRITE', '协作', '在项目内创建/编辑/评论/附件等协作操作'),
    ('MANAGE','管理', '项目成员、标签、状态等配置类管理操作')
ON CONFLICT (code) DO NOTHING;

-- 4) 确保角色权限种子（role.code 为大写，与 V9 一致）
INSERT INTO role_permission (role_code, permission_code) VALUES
    ('OWNER',  'READ'),
    ('OWNER',  'WRITE'),
    ('OWNER',  'MANAGE'),
    ('ADMIN',  'READ'),
    ('ADMIN',  'WRITE'),
    ('ADMIN',  'MANAGE'),
    ('PM',     'READ'),
    ('PM',     'WRITE'),
    ('PM',     'MANAGE'),
    ('DEV',    'READ'),
    ('DEV',    'WRITE'),
    ('QA',     'READ'),
    ('QA',     'WRITE'),
    ('MEMBER', 'READ'),
    ('MEMBER', 'WRITE'),
    ('VIEWER', 'READ')
ON CONFLICT (role_code, permission_code) DO NOTHING;

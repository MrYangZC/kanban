-- ============================================================
-- 操作级 RBAC：角色 -> 操作权限 映射表 + 种子数据
-- 接口只声明所需操作权限(READ/WRITE/MANAGE)，角色能否执行由本表决定。
-- 加新角色 / 调整权限语义只改数据，无需改代码。
-- ============================================================

-- 操作权限字典（只增不删）
CREATE TABLE IF NOT EXISTS permission (
    code        VARCHAR(32)  NOT NULL PRIMARY KEY,
    name        VARCHAR(64)  NOT NULL,
    description VARCHAR(255),
    created_at  TIMESTAMP    NOT NULL DEFAULT now()
);

INSERT INTO permission (code, name, description) VALUES
    ('READ',  '查看',   '查看项目内资源（issue/评论/附件/活动流/状态等）'),
    ('WRITE', '协作',   '在项目内创建/编辑/评论/附件等协作操作'),
    ('MANAGE','管理',   '项目成员、标签、状态等配置类管理操作')
ON CONFLICT (code) DO NOTHING;

-- 角色 -> 操作权限 映射（多对多）
CREATE TABLE IF NOT EXISTS role_permission (
    role_code      VARCHAR(32) NOT NULL REFERENCES role(code) ON DELETE CASCADE,
    permission_code VARCHAR(32) NOT NULL REFERENCES permission(code) ON DELETE CASCADE,
    created_at     TIMESTAMP   NOT NULL DEFAULT now(),
    PRIMARY KEY (role_code, permission_code)
);

-- 种子数据：依据 RBAC 既有语义初始化角色权限
--   查看(READ)       : 全部角色（含 viewer）
--   协作(WRITE)      : owner/admin/pm/dev/qa/member（不含 viewer）
--   管理(MANAGE)     : owner/admin/pm
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

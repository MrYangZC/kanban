-- =============================================================================
-- V9: RBAC 重构
-- 1. 新增角色体系：role / user_role / menu / role_menu
--    - user_role 直接挂 project（去掉 workspace 层级，全局一套角色字典）
--    - 存量 project_member.role 数据迁移至 user_role
-- 2. 移除 workspace 概念：删 workspace / workspace_member 表
--    - project 去掉 workspace_id，project_key 改为全局唯一
-- 3. project_member 退化为纯成员关系表（去掉 role 列）
-- 说明：PostgreSQL DDL 为事务性，迁移失败会自动回滚；
--       回滚脚本见 db/migration/rollback/rollback_v9.sql（Flyway 不执行）。
-- =============================================================================

-- =============================================================================
-- 一、建 RBAC 四表
-- =============================================================================

-- 1. role：全局角色字典（项目内按 user_role 关联）
CREATE TABLE role (
    id          UUID PRIMARY KEY,
    code        VARCHAR(32)  NOT NULL,
    name        VARCHAR(50)  NOT NULL,
    description VARCHAR(255),
    sort_order  INTEGER      NOT NULL DEFAULT 0,
    created_by  UUID,
    updated_by  UUID,
    created_at  TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at  TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted     INTEGER      NOT NULL DEFAULT 0,
    CONSTRAINT uk_role_code UNIQUE (code)
);

-- 2. user_role：用户-角色中间表（多对多，一个用户在一个项目可有多个角色）
CREATE TABLE user_role (
    id         UUID PRIMARY KEY,
    user_id    UUID NOT NULL,
    role_id    UUID NOT NULL,
    project_id UUID NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_user_role UNIQUE (user_id, role_id, project_id)
);

-- 3. menu：菜单 / 权限点
CREATE TABLE menu (
    id         UUID PRIMARY KEY,
    code       VARCHAR(64) NOT NULL,
    name       VARCHAR(50) NOT NULL,
    parent_id  UUID,
    type       VARCHAR(16) NOT NULL DEFAULT 'MENU',
    sort_order INTEGER     NOT NULL DEFAULT 0,
    created_at TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted    INTEGER     NOT NULL DEFAULT 0,
    CONSTRAINT uk_menu_code UNIQUE (code)
);

-- 4. role_menu：角色-菜单关联（多对多）
CREATE TABLE role_menu (
    id         UUID PRIMARY KEY,
    role_id    UUID NOT NULL,
    menu_id    UUID NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_role_menu UNIQUE (role_id, menu_id)
);

-- =============================================================================
-- 二、外键与索引
-- =============================================================================
ALTER TABLE user_role
    ADD CONSTRAINT fk_ur_user    FOREIGN KEY (user_id)    REFERENCES sys_user (id);
ALTER TABLE user_role
    ADD CONSTRAINT fk_ur_role    FOREIGN KEY (role_id)    REFERENCES role (id);
ALTER TABLE user_role
    ADD CONSTRAINT fk_ur_project FOREIGN KEY (project_id) REFERENCES project (id);

ALTER TABLE role_menu
    ADD CONSTRAINT fk_rm_role FOREIGN KEY (role_id) REFERENCES role (id);
ALTER TABLE role_menu
    ADD CONSTRAINT fk_rm_menu FOREIGN KEY (menu_id) REFERENCES menu (id);

CREATE INDEX idx_user_role_project_user ON user_role (project_id, user_id);
CREATE INDEX idx_role_menu_role ON role_menu (role_id);

-- =============================================================================
-- 三、种子数据：角色字典 + 权限点 + 角色权限映射
-- =============================================================================

-- 角色字典（存量 OWNER/MEMBER/VIEWER 迁移需要，另加业务职能角色便于后续使用）
INSERT INTO role (id, code, name, description, sort_order, created_by, updated_by, created_at, updated_at, deleted)
VALUES
    ('00000000-0000-0000-0000-000000000011', 'OWNER',  '项目所有者', '项目创建人，拥有全部权限', 1, '00000000-0000-0000-0000-000000000001', '00000000-0000-0000-0000-000000000001', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0),
    ('00000000-0000-0000-0000-000000000012', 'ADMIN',  '管理员',     '项目管理员，可管理成员与项目设置', 2, '00000000-0000-0000-0000-000000000001', '00000000-0000-0000-0000-000000000001', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0),
    ('00000000-0000-0000-0000-000000000013', 'PM',     '项目经理',   '负责计划与迭代，可管理任务', 3, '00000000-0000-0000-0000-000000000001', '00000000-0000-0000-0000-000000000001', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0),
    ('00000000-0000-0000-0000-000000000014', 'DEV',    '开发',       '开发人员，可处理指派任务', 4, '00000000-0000-0000-0000-000000000001', '00000000-0000-0000-0000-000000000001', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0),
    ('00000000-0000-0000-0000-000000000015', 'QA',     '测试',       '测试人员，可验证与关闭缺陷', 5, '00000000-0000-0000-0000-000000000001', '00000000-0000-0000-0000-000000000001', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0),
    ('00000000-0000-0000-0000-000000000016', 'MEMBER', '项目成员',   '可参与协作：创建/编辑/分配/拖拽', 6, '00000000-0000-0000-0000-000000000001', '00000000-0000-0000-0000-000000000001', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0),
    ('00000000-0000-0000-0000-000000000017', 'VIEWER', '访客',       '只读访问', 7, '00000000-0000-0000-0000-000000000001', '00000000-0000-0000-0000-000000000001', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0)
ON CONFLICT (code) DO NOTHING;

-- 权限点（菜单/操作级，前端菜单与后端接口共用）
INSERT INTO menu (id, code, name, parent_id, type, sort_order, created_at, updated_at, deleted)
VALUES
    ('00000000-0000-0000-0000-000000000021', 'PROJECT_VIEW',    '查看项目',    NULL, 'MENU', 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0),
    ('00000000-0000-0000-0000-000000000022', 'ISSUE_VIEW',      '查看任务',    NULL, 'MENU', 2, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0),
    ('00000000-0000-0000-0000-000000000023', 'ISSUE_CREATE',    '创建任务',    NULL, 'BUTTON', 3, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0),
    ('00000000-0000-0000-0000-000000000024', 'ISSUE_EDIT',      '编辑任务',    NULL, 'BUTTON', 4, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0),
    ('00000000-0000-0000-0000-000000000025', 'ISSUE_DELETE',    '删除任务',    NULL, 'BUTTON', 5, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0),
    ('00000000-0000-0000-0000-000000000026', 'MEMBER_MANAGE',   '成员管理',    NULL, 'BUTTON', 6, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0),
    ('00000000-0000-0000-0000-000000000027', 'PROJECT_SETTING', '项目设置',    NULL, 'BUTTON', 7, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0),
    ('00000000-0000-0000-0000-000000000028', 'SPRINT_MANAGE',   '迭代管理',    NULL, 'BUTTON', 8, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0),
    ('00000000-0000-0000-0000-000000000029', 'WORKFLOW_MANAGE', '工作流管理',  NULL, 'BUTTON', 9, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0)
ON CONFLICT (code) DO NOTHING;

-- 角色-权限映射
INSERT INTO role_menu (id, role_id, menu_id, created_at)
SELECT gen_random_uuid(), r.id, m.id, CURRENT_TIMESTAMP
FROM role r
JOIN menu m ON 1 = 1
WHERE r.code = 'OWNER'
ON CONFLICT DO NOTHING;

INSERT INTO role_menu (id, role_id, menu_id, created_at)
SELECT gen_random_uuid(), r.id, m.id, CURRENT_TIMESTAMP
FROM role r
JOIN menu m ON m.code IN ('PROJECT_VIEW', 'ISSUE_VIEW', 'ISSUE_CREATE', 'ISSUE_EDIT', 'ISSUE_DELETE')
WHERE r.code IN ('ADMIN', 'PM', 'DEV', 'QA', 'MEMBER')
ON CONFLICT DO NOTHING;

INSERT INTO role_menu (id, role_id, menu_id, created_at)
SELECT gen_random_uuid(), r.id, m.id, CURRENT_TIMESTAMP
FROM role r
JOIN menu m ON m.code IN ('PROJECT_VIEW', 'ISSUE_VIEW')
WHERE r.code = 'VIEWER'
ON CONFLICT DO NOTHING;

-- =============================================================================
-- 四、存量数据迁移：project_member.role -> user_role
-- =============================================================================
INSERT INTO user_role (id, user_id, role_id, project_id, created_at)
SELECT gen_random_uuid(), pm.user_id, r.id, pm.project_id, pm.created_at
FROM project_member pm
JOIN role r ON r.code = pm.role
ON CONFLICT DO NOTHING;

-- =============================================================================
-- 五、移除 workspace 概念
-- =============================================================================

-- 1. project 去掉 workspace 关联（DROP COLUMN 会连带删除 uk_project_key 约束）
ALTER TABLE project
    DROP CONSTRAINT fk_project_workspace;
DROP INDEX IF EXISTS idx_project_ws;
ALTER TABLE project
    DROP COLUMN workspace_id;

-- 2. project_key 改为全局唯一（先清理可能的历史重复 key，保证约束可建）
DELETE FROM project p
USING project p2
WHERE p.id <> p2.id
  AND p.project_key = p2.project_key
  AND p.created_at > p2.created_at;
ALTER TABLE project
    ADD CONSTRAINT uk_project_key UNIQUE (project_key);

-- 3. project_member 去掉 role 列（数据已迁移至 user_role）
ALTER TABLE project_member
    DROP COLUMN role;

-- 4. 删 workspace 相关表（先子后父）
DROP TABLE IF EXISTS workspace_member;
DROP TABLE IF EXISTS workspace;

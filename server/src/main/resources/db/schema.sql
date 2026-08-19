-- =============================================================================
-- Kanban 数据库统一脚本（全量 schema，PostgreSQL）
-- -----------------------------------------------------------------------------
-- 本文件是 db/migration 下 V7~V10 依次执行后的最终数据库形态，合并为一个完整
-- 脚本，供：
--   1. 从零初始化（新环境建库，docker-compose 起库后直接执行）
--   2. 整体评审当前 schema（无需逐个查看迁移脚本推算最终结果）
--   3. 手工重置重建本地开发库
--
-- 合并来源对照：
--   - V7__baseline.sql    ：基础表 + issue_number_seq + 外键 + 索引 + admin 种子
--                            （已按 V9 结果剔除 workspace / workspace_member）
--   - V8__add_issue_detail_fields.sql
--                        ：issue 增加 iteration / participants / cc_users /
--                            plan_start / no_fix_reason 五个字段
--   - V9__rbac_rework.sql ：新增 role / user_role / menu / role_menu 四表；
--                            project 去掉 workspace_id（project_key 全局唯一）；
--                            project_member 去掉 role 列（迁移至 user_role）；
--                            删除 workspace / workspace_member 两表
--   - V10__fix_constraints_and_attachment_url.sql
--                        ：workflow 每项目最多一条默认工作流（部分唯一索引）；
--                            attachment 增加 url 字段
--
-- 注意：
--   - db/migration/ 下的 Flyway 脚本保持不动，Flyway 继续在已上线库上做增量
--     演进；本文件只是同一 schema 的"最终态快照"，Flyway 不会执行它。
--   - V8/V10 中的存量数据清理（WONT_FIX→CLOSED 禁转、重复默认工作流去重）
--     仅针对历史存量数据，空库从零初始化时无需包含。
-- =============================================================================

-- 0. 重置清理（用于重建本地库；空库可直接忽略本段） ---------------------------
DROP TABLE IF EXISTS
    issue_activity, attachment, issue_comment, issue_relation, issue_label,
    label, board_column, board, sprint, workflow_transition, workflow_status,
    workflow, issue_status, issue_type, role_menu, user_role, menu, role,
    project_member, project, sys_user, issue
    CASCADE;
DROP SEQUENCE IF EXISTS issue_number_seq;

-- 1. sys_user -----------------------------------------------------------------
CREATE TABLE sys_user (
    id            UUID PRIMARY KEY,
    username      VARCHAR(100) NOT NULL,
    display_name  VARCHAR(100) NOT NULL,
    email         VARCHAR(200),
    avatar_url    VARCHAR(500),
    password_hash VARCHAR(255),
    enabled       BOOLEAN NOT NULL DEFAULT TRUE,
    created_by    UUID,
    updated_by    UUID,
    created_at    TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at    TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted       INTEGER NOT NULL DEFAULT 0,
    CONSTRAINT uk_sys_user_username UNIQUE (username)
);

-- 2. project（RBAC 后无 workspace_id，project_key 全局唯一） ------------------
CREATE TABLE project (
    id           UUID PRIMARY KEY,
    project_key  VARCHAR(32)  NOT NULL,
    name         VARCHAR(200) NOT NULL,
    description  TEXT,
    avatar_url   VARCHAR(500),
    status       VARCHAR(32) NOT NULL DEFAULT 'ACTIVE',
    lead_user_id UUID,
    created_by   UUID,
    updated_by   UUID,
    created_at   TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at   TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted      INTEGER NOT NULL DEFAULT 0,
    CONSTRAINT uk_project_key UNIQUE (project_key)
);

-- 3. project_member（纯成员关系表，角色迁移至 user_role） ---------------------
CREATE TABLE project_member (
    id         UUID PRIMARY KEY,
    project_id UUID NOT NULL,
    user_id    UUID NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_project_member UNIQUE (project_id, user_id)
);

-- 4. issue_type ---------------------------------------------------------------
CREATE TABLE issue_type (
    id         UUID PRIMARY KEY,
    project_id UUID NOT NULL,
    name       VARCHAR(50) NOT NULL,
    code       VARCHAR(32) NOT NULL,
    icon       VARCHAR(100),
    color      VARCHAR(32),
    sort_order INTEGER NOT NULL DEFAULT 0,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_issue_type UNIQUE (project_id, code)
);

-- 5. issue_status（项目级状态池，含 code/category/color） ---------------------
CREATE TABLE issue_status (
    id         UUID PRIMARY KEY,
    project_id UUID NOT NULL,
    name       VARCHAR(50) NOT NULL,
    code       VARCHAR(32) NOT NULL,
    category   VARCHAR(32) NOT NULL,
    color      VARCHAR(32),
    sort_order INTEGER NOT NULL DEFAULT 0,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_issue_status UNIQUE (project_id, code)
);

-- 6. workflow -----------------------------------------------------------------
CREATE TABLE workflow (
    id          UUID PRIMARY KEY,
    project_id  UUID NOT NULL,
    name        VARCHAR(100) NOT NULL,
    description VARCHAR(500),
    is_default  BOOLEAN NOT NULL DEFAULT FALSE,
    created_at  TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at  TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- 7. workflow_status（Workflow <-> Status 映射） ------------------------------
CREATE TABLE workflow_status (
    id          UUID PRIMARY KEY,
    workflow_id UUID NOT NULL,
    status_id   UUID NOT NULL,
    sort_order  INTEGER NOT NULL DEFAULT 0,
    CONSTRAINT uk_workflow_status UNIQUE (workflow_id, status_id)
);

-- 8. workflow_transition（流转规则） ------------------------------------------
CREATE TABLE workflow_transition (
    id              UUID PRIMARY KEY,
    workflow_id     UUID NOT NULL,
    from_status_id  UUID NOT NULL,
    to_status_id    UUID NOT NULL,
    name            VARCHAR(100),
    created_at      TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_workflow_transition UNIQUE (workflow_id, from_status_id, to_status_id)
);

-- 9. issue（含 V8 缺陷详情扩展字段） ------------------------------------------
CREATE TABLE issue (
    id             UUID PRIMARY KEY,
    project_id     UUID NOT NULL,
    issue_number   BIGINT NOT NULL,
    parent_id      UUID,
    issue_type_id  UUID NOT NULL,
    status_id      UUID NOT NULL,
    title          VARCHAR(500) NOT NULL,
    description    TEXT,
    priority       VARCHAR(20) NOT NULL DEFAULT 'P2',
    reporter_id    UUID,
    assignee_id    UUID,
    verifier_id    UUID,
    sprint_id      UUID,
    estimate       NUMERIC(10, 2),
    due_date       TIMESTAMP,
    sort_key       VARCHAR(64) NOT NULL,
    severity       VARCHAR(20) NOT NULL DEFAULT 'S3',
    module         VARCHAR(100),
    fix_version    VARCHAR(60),
    source         VARCHAR(60),
    archived       INTEGER NOT NULL DEFAULT 0,
    -- V8 新增：缺陷详情扩展
    iteration      VARCHAR(100),
    participants   JSONB NOT NULL DEFAULT '[]',
    cc_users       JSONB NOT NULL DEFAULT '[]',
    plan_start     TIMESTAMP,
    no_fix_reason  VARCHAR(1000),
    created_by     UUID,
    updated_by     UUID,
    created_at     TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at     TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted        INTEGER NOT NULL DEFAULT 0,
    CONSTRAINT uk_issue_number UNIQUE (project_id, issue_number)
);

-- 10. board -------------------------------------------------------------------
CREATE TABLE board (
    id         UUID PRIMARY KEY,
    project_id UUID NOT NULL,
    name       VARCHAR(100) NOT NULL,
    board_type VARCHAR(32) NOT NULL DEFAULT 'KANBAN',
    created_by UUID,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- 11. board_column ------------------------------------------------------------
CREATE TABLE board_column (
    id         UUID PRIMARY KEY,
    board_id   UUID NOT NULL,
    status_id  UUID NOT NULL,
    name       VARCHAR(100) NOT NULL,
    sort_order INTEGER NOT NULL DEFAULT 0,
    wip_limit  INTEGER,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_board_column UNIQUE (board_id, status_id)
);

-- 12. sprint ------------------------------------------------------------------
CREATE TABLE sprint (
    id         UUID PRIMARY KEY,
    project_id UUID NOT NULL,
    name       VARCHAR(100) NOT NULL,
    goal       VARCHAR(500),
    status     VARCHAR(32) NOT NULL DEFAULT 'PLANNED',
    start_date DATE,
    end_date   DATE,
    created_by UUID,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- 13. label / issue_label -----------------------------------------------------
CREATE TABLE label (
    id         UUID PRIMARY KEY,
    project_id UUID NOT NULL,
    name       VARCHAR(50) NOT NULL,
    color      VARCHAR(32),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_label UNIQUE (project_id, name)
);

CREATE TABLE issue_label (
    issue_id  UUID NOT NULL,
    label_id  UUID NOT NULL,
    PRIMARY KEY (issue_id, label_id)
);

-- 14. issue_relation ----------------------------------------------------------
CREATE TABLE issue_relation (
    id               UUID PRIMARY KEY,
    issue_id         UUID NOT NULL,
    target_issue_id  UUID NOT NULL,
    relation_type    VARCHAR(32) NOT NULL,
    created_at       TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_issue_relation UNIQUE (issue_id, target_issue_id, relation_type)
);

-- 15. issue_comment -----------------------------------------------------------
CREATE TABLE issue_comment (
    id         UUID PRIMARY KEY,
    issue_id   UUID NOT NULL,
    user_id    UUID NOT NULL,
    content    TEXT NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted    INTEGER NOT NULL DEFAULT 0
);

-- 16. attachment（含 V10 新增 url 字段，承载可访问地址） ----------------------
CREATE TABLE attachment (
    id            UUID PRIMARY KEY,
    issue_id      UUID NOT NULL,
    file_name     VARCHAR(500) NOT NULL,
    storage_key   VARCHAR(1000) NOT NULL,
    content_type  VARCHAR(200),
    file_size     BIGINT,
    uploaded_by   UUID,
    created_at    TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    url           VARCHAR(1000)
);

-- 17. issue_activity（审计，不软删除） ----------------------------------------
CREATE TABLE issue_activity (
    id         UUID PRIMARY KEY,
    issue_id   UUID NOT NULL,
    actor_id   UUID,
    action     VARCHAR(50) NOT NULL,
    old_value  JSONB,
    new_value  JSONB,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- 18. role（RBAC 全局角色字典） -----------------------------------------------
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

-- 19. user_role（用户-角色多对多，一个用户在一个项目可有多个角色） -------------
CREATE TABLE user_role (
    id         UUID PRIMARY KEY,
    user_id    UUID NOT NULL,
    role_id    UUID NOT NULL,
    project_id UUID NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_user_role UNIQUE (user_id, role_id, project_id)
);

-- 20. menu（菜单 / 权限点） ---------------------------------------------------
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

-- 21. role_menu（角色-权限关联） ----------------------------------------------
CREATE TABLE role_menu (
    id         UUID PRIMARY KEY,
    role_id    UUID NOT NULL,
    menu_id    UUID NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_role_menu UNIQUE (role_id, menu_id)
);

-- 22. 全局自增序列（issue_number 生成，替代应用层 selectCount+1） -------------
CREATE SEQUENCE issue_number_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 50;

-- =============================================================================
-- 外键约束
-- =============================================================================
ALTER TABLE project
    ADD CONSTRAINT fk_project_lead FOREIGN KEY (lead_user_id) REFERENCES sys_user (id);

ALTER TABLE project_member
    ADD CONSTRAINT fk_pm_project FOREIGN KEY (project_id) REFERENCES project (id);
ALTER TABLE project_member
    ADD CONSTRAINT fk_pm_user FOREIGN KEY (user_id) REFERENCES sys_user (id);

ALTER TABLE issue_type
    ADD CONSTRAINT fk_issue_type_project FOREIGN KEY (project_id) REFERENCES project (id);

ALTER TABLE issue_status
    ADD CONSTRAINT fk_issue_status_project FOREIGN KEY (project_id) REFERENCES project (id);

ALTER TABLE workflow
    ADD CONSTRAINT fk_workflow_project FOREIGN KEY (project_id) REFERENCES project (id);

ALTER TABLE workflow_status
    ADD CONSTRAINT fk_ws_workflow FOREIGN KEY (workflow_id) REFERENCES workflow (id);
ALTER TABLE workflow_status
    ADD CONSTRAINT fk_ws_status FOREIGN KEY (status_id) REFERENCES issue_status (id);

ALTER TABLE workflow_transition
    ADD CONSTRAINT fk_wt_workflow FOREIGN KEY (workflow_id) REFERENCES workflow (id);
ALTER TABLE workflow_transition
    ADD CONSTRAINT fk_wt_from FOREIGN KEY (from_status_id) REFERENCES issue_status (id);
ALTER TABLE workflow_transition
    ADD CONSTRAINT fk_wt_to FOREIGN KEY (to_status_id) REFERENCES issue_status (id);

ALTER TABLE issue
    ADD CONSTRAINT fk_issue_project FOREIGN KEY (project_id) REFERENCES project (id);
ALTER TABLE issue
    ADD CONSTRAINT fk_issue_type FOREIGN KEY (issue_type_id) REFERENCES issue_type (id);
ALTER TABLE issue
    ADD CONSTRAINT fk_issue_status FOREIGN KEY (status_id) REFERENCES issue_status (id);
ALTER TABLE issue
    ADD CONSTRAINT fk_issue_parent FOREIGN KEY (parent_id) REFERENCES issue (id);
ALTER TABLE issue
    ADD CONSTRAINT fk_issue_reporter FOREIGN KEY (reporter_id) REFERENCES sys_user (id);
ALTER TABLE issue
    ADD CONSTRAINT fk_issue_assignee FOREIGN KEY (assignee_id) REFERENCES sys_user (id);
ALTER TABLE issue
    ADD CONSTRAINT fk_issue_verifier FOREIGN KEY (verifier_id) REFERENCES sys_user (id);
ALTER TABLE issue
    ADD CONSTRAINT fk_issue_sprint FOREIGN KEY (sprint_id) REFERENCES sprint (id);

ALTER TABLE board
    ADD CONSTRAINT fk_board_project FOREIGN KEY (project_id) REFERENCES project (id);

ALTER TABLE board_column
    ADD CONSTRAINT fk_board_column_board FOREIGN KEY (board_id) REFERENCES board (id);
ALTER TABLE board_column
    ADD CONSTRAINT fk_board_column_status FOREIGN KEY (status_id) REFERENCES issue_status (id);

ALTER TABLE sprint
    ADD CONSTRAINT fk_sprint_project FOREIGN KEY (project_id) REFERENCES project (id);

ALTER TABLE label
    ADD CONSTRAINT fk_label_project FOREIGN KEY (project_id) REFERENCES project (id);

ALTER TABLE issue_label
    ADD CONSTRAINT fk_issue_label_issue FOREIGN KEY (issue_id) REFERENCES issue (id);
ALTER TABLE issue_label
    ADD CONSTRAINT fk_issue_label_label FOREIGN KEY (label_id) REFERENCES label (id);

ALTER TABLE issue_relation
    ADD CONSTRAINT fk_issue_relation_issue FOREIGN KEY (issue_id) REFERENCES issue (id);
ALTER TABLE issue_relation
    ADD CONSTRAINT fk_issue_relation_target FOREIGN KEY (target_issue_id) REFERENCES issue (id);

ALTER TABLE issue_comment
    ADD CONSTRAINT fk_issue_comment_issue FOREIGN KEY (issue_id) REFERENCES issue (id);
ALTER TABLE issue_comment
    ADD CONSTRAINT fk_issue_comment_user FOREIGN KEY (user_id) REFERENCES sys_user (id);

ALTER TABLE attachment
    ADD CONSTRAINT fk_attachment_issue FOREIGN KEY (issue_id) REFERENCES issue (id);

ALTER TABLE issue_activity
    ADD CONSTRAINT fk_issue_activity_issue FOREIGN KEY (issue_id) REFERENCES issue (id);
ALTER TABLE issue_activity
    ADD CONSTRAINT fk_issue_activity_actor FOREIGN KEY (actor_id) REFERENCES sys_user (id);

-- RBAC 外键
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

-- =============================================================================
-- 索引
-- =============================================================================
CREATE INDEX idx_issue_project ON issue (project_id);
CREATE INDEX idx_issue_status ON issue (status_id);
CREATE INDEX idx_issue_assignee ON issue (assignee_id);
CREATE INDEX idx_issue_reporter ON issue (reporter_id);
CREATE INDEX idx_issue_verifier ON issue (verifier_id);
CREATE INDEX idx_issue_archived ON issue (archived);
CREATE INDEX idx_issue_sprint ON issue (sprint_id);
CREATE INDEX idx_issue_parent ON issue (parent_id);
CREATE INDEX idx_issue_created_at ON issue (created_at);
CREATE INDEX idx_issue_project_status ON issue (project_id, status_id);
CREATE INDEX idx_issue_project_sprint ON issue (project_id, sprint_id);
CREATE INDEX idx_workflow_status_wf ON workflow_status (workflow_id);
CREATE INDEX idx_workflow_transition_wf ON workflow_transition (workflow_id);
CREATE INDEX idx_board_project ON board (project_id);
CREATE INDEX idx_board_column_board ON board_column (board_id);

-- RBAC 索引
CREATE INDEX idx_user_role_project_user ON user_role (project_id, user_id);
CREATE INDEX idx_role_menu_role ON role_menu (role_id);

-- V10：每个项目最多一条默认工作流（部分唯一索引，幂等）
CREATE UNIQUE INDEX uk_workflow_default_per_project
    ON workflow (project_id)
    WHERE is_default = TRUE;

-- =============================================================================
-- 种子数据
-- =============================================================================

-- 1. 初始管理员（用户名 admin / 密码 admin123，仅本地开发起步）
INSERT INTO sys_user (id, username, display_name, email, password_hash, enabled, created_by, updated_by, created_at, updated_at, deleted)
VALUES (
    '00000000-0000-0000-0000-000000000001',
    'admin',
    '系统管理员',
    'admin@kanban.dev',
    -- bcrypt: admin123
    '$2b$10$9b/E/PsZdWqY2cq.Cv5u1u9kXnlBjayTSzI8qZOYtlDkqhSDT3Oou',
    TRUE,
    '00000000-0000-0000-0000-000000000001',
    '00000000-0000-0000-0000-000000000001',
    CURRENT_TIMESTAMP,
    CURRENT_TIMESTAMP,
    0
)
ON CONFLICT (username) DO NOTHING;

-- 2. 角色字典（OWNER/MEMBER/VIEWER 为存量迁移需要，另加业务职能角色）
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

-- 3. 权限点（菜单/操作级，前端菜单与后端接口共用）
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

-- 4. 角色-权限映射
-- OWNER 拥有全部权限点
INSERT INTO role_menu (id, role_id, menu_id, created_at)
SELECT gen_random_uuid(), r.id, m.id, CURRENT_TIMESTAMP
FROM role r
JOIN menu m ON 1 = 1
WHERE r.code = 'OWNER'
ON CONFLICT DO NOTHING;

-- ADMIN/PM/DEV/QA/MEMBER 拥有业务读写权限
INSERT INTO role_menu (id, role_id, menu_id, created_at)
SELECT gen_random_uuid(), r.id, m.id, CURRENT_TIMESTAMP
FROM role r
JOIN menu m ON m.code IN ('PROJECT_VIEW', 'ISSUE_VIEW', 'ISSUE_CREATE', 'ISSUE_EDIT', 'ISSUE_DELETE')
WHERE r.code IN ('ADMIN', 'PM', 'DEV', 'QA', 'MEMBER')
ON CONFLICT DO NOTHING;

-- VIEWER 只读
INSERT INTO role_menu (id, role_id, menu_id, created_at)
SELECT gen_random_uuid(), r.id, m.id, CURRENT_TIMESTAMP
FROM role r
JOIN menu m ON m.code IN ('PROJECT_VIEW', 'ISSUE_VIEW')
WHERE r.code = 'VIEWER'
ON CONFLICT DO NOTHING;

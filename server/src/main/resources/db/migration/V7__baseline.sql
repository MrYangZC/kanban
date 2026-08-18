-- =============================================================================
-- Kanban 统一基线脚本（合并原 V1~V6 为单一脚本）
-- 说明：
--   1. 原文档第9节名为 workflow_status 的"状态实体表"在此正式命名为 issue_status
--      （项目级状态池，含 code/category/color）。
--   2. 原文档第11节 workflow_status 为 Workflow<->Status 映射表，保留原名。
--   3. Issue.status_id 直接引用 issue_status；Workflow 通过 workflow_status 映射。
--   4. 卡片排序用 issue.sort_key（fractional indexing）；列排序用 sort_order。
--   5. 缺陷扩展字段（severity/module/archived/verifier_id/fix_version/source）
--      与 6 列缺陷工作流种子已并入：字段在 issue 建表时直接定义；6 列工作流与
--      缺陷专属 IssueType 由 Java ProjectInitializer 在创建项目时生成，不在此脚本。
--   6. 本脚本用于重置重建：仅 DROP 本看板自己的表/序列/迁移历史（不动 public
--      模式本身，以免与同实例其它库/客户端争用 public 的 AccessExclusive 锁），
--      再重建全表 + 种子 + 序列。
--      版本号取 7 以高于历史已执行版本（V1~V6），使 Flyway 在清空迁移历史后重跑。
-- =============================================================================

-- 0. 清空历史（仅本看板对象 + flyway 迁移记录） ------------------------------
DROP TABLE IF EXISTS
    issue_activity, attachment, issue_comment, issue_relation, issue_label,
    label, board_column, board, sprint, workflow_transition, workflow_status,
    workflow, issue_status, issue_type, project_member, project,
    workspace_member, sys_user, workspace, issue
    CASCADE;
DROP SEQUENCE IF EXISTS issue_number_seq;
DELETE FROM flyway_schema_history;

-- 1. workspace ----------------------------------------------------------------
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

-- 2. sys_user -----------------------------------------------------------------
CREATE TABLE sys_user (
    id           UUID PRIMARY KEY,
    username     VARCHAR(100) NOT NULL,
    display_name VARCHAR(100) NOT NULL,
    email        VARCHAR(200),
    avatar_url   VARCHAR(500),
    password_hash VARCHAR(255),
    enabled      BOOLEAN NOT NULL DEFAULT TRUE,
    created_by   UUID,
    updated_by   UUID,
    created_at   TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at   TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted      INTEGER NOT NULL DEFAULT 0,
    CONSTRAINT uk_sys_user_username UNIQUE (username)
);

-- 3. workspace_member ---------------------------------------------------------
CREATE TABLE workspace_member (
    id           UUID PRIMARY KEY,
    workspace_id UUID NOT NULL,
    user_id      UUID NOT NULL,
    role         VARCHAR(32) NOT NULL DEFAULT 'MEMBER',
    joined_at    TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_workspace_member UNIQUE (workspace_id, user_id)
);

-- 4. project ------------------------------------------------------------------
CREATE TABLE project (
    id           UUID PRIMARY KEY,
    workspace_id UUID NOT NULL,
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
    CONSTRAINT uk_project_key UNIQUE (workspace_id, project_key)
);

-- 5. project_member -----------------------------------------------------------
CREATE TABLE project_member (
    id         UUID PRIMARY KEY,
    project_id UUID NOT NULL,
    user_id    UUID NOT NULL,
    role       VARCHAR(32) NOT NULL DEFAULT 'MEMBER',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_project_member UNIQUE (project_id, user_id)
);

-- 6. issue_type ---------------------------------------------------------------
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

-- 7. issue_status (状态实体表，原文档第9节 workflow_status) -------------------
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

-- 8. workflow -----------------------------------------------------------------
CREATE TABLE workflow (
    id          UUID PRIMARY KEY,
    project_id  UUID NOT NULL,
    name        VARCHAR(100) NOT NULL,
    description VARCHAR(500),
    is_default  BOOLEAN NOT NULL DEFAULT FALSE,
    created_at  TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at  TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- 9. workflow_status (Workflow<->Status 映射表，原文档第11节) -----------------
CREATE TABLE workflow_status (
    id          UUID PRIMARY KEY,
    workflow_id UUID NOT NULL,
    status_id   UUID NOT NULL,
    sort_order  INTEGER NOT NULL DEFAULT 0,
    CONSTRAINT uk_workflow_status UNIQUE (workflow_id, status_id)
);

-- 10. workflow_transition (流转规则) ------------------------------------------
CREATE TABLE workflow_transition (
    id              UUID PRIMARY KEY,
    workflow_id     UUID NOT NULL,
    from_status_id  UUID NOT NULL,
    to_status_id    UUID NOT NULL,
    name            VARCHAR(100),
    created_at      TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_workflow_transition UNIQUE (workflow_id, from_status_id, to_status_id)
);

-- 11. issue -------------------------------------------------------------------
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
    created_by     UUID,
    updated_by     UUID,
    created_at     TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at     TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted        INTEGER NOT NULL DEFAULT 0,
    CONSTRAINT uk_issue_number UNIQUE (project_id, issue_number)
);

-- 12. board -------------------------------------------------------------------
CREATE TABLE board (
    id         UUID PRIMARY KEY,
    project_id UUID NOT NULL,
    name       VARCHAR(100) NOT NULL,
    board_type VARCHAR(32) NOT NULL DEFAULT 'KANBAN',
    created_by UUID,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- 13. board_column ------------------------------------------------------------
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

-- 14. sprint ------------------------------------------------------------------
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

-- 15. label -------------------------------------------------------------------
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

-- 16. issue_relation ----------------------------------------------------------
CREATE TABLE issue_relation (
    id               UUID PRIMARY KEY,
    issue_id         UUID NOT NULL,
    target_issue_id  UUID NOT NULL,
    relation_type    VARCHAR(32) NOT NULL,
    created_at       TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_issue_relation UNIQUE (issue_id, target_issue_id, relation_type)
);

-- 17. issue_comment -----------------------------------------------------------
CREATE TABLE issue_comment (
    id         UUID PRIMARY KEY,
    issue_id   UUID NOT NULL,
    user_id    UUID NOT NULL,
    content    TEXT NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted    INTEGER NOT NULL DEFAULT 0
);

-- 18. attachment --------------------------------------------------------------
CREATE TABLE attachment (
    id            UUID PRIMARY KEY,
    issue_id      UUID NOT NULL,
    file_name     VARCHAR(500) NOT NULL,
    storage_key   VARCHAR(1000) NOT NULL,
    content_type  VARCHAR(200),
    file_size     BIGINT,
    uploaded_by   UUID,
    created_at    TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- 19. issue_activity (审计，不软删除) -----------------------------------------
CREATE TABLE issue_activity (
    id         UUID PRIMARY KEY,
    issue_id   UUID NOT NULL,
    actor_id   UUID,
    action     VARCHAR(50) NOT NULL,
    old_value  JSONB,
    new_value  JSONB,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- 20. 全局自增序列（issue_number 生成，替代应用层 selectCount+1） ------------
CREATE SEQUENCE issue_number_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 50;

-- =============================================================================
-- 外键约束
-- =============================================================================
ALTER TABLE workspace_member
    ADD CONSTRAINT fk_wm_workspace FOREIGN KEY (workspace_id) REFERENCES workspace (id);
ALTER TABLE workspace_member
    ADD CONSTRAINT fk_wm_user FOREIGN KEY (user_id) REFERENCES sys_user (id);

ALTER TABLE project
    ADD CONSTRAINT fk_project_workspace FOREIGN KEY (workspace_id) REFERENCES workspace (id);
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

-- =============================================================================
-- 索引
-- =============================================================================
CREATE INDEX idx_workspace_member_ws ON workspace_member (workspace_id);
CREATE INDEX idx_project_ws ON project (workspace_id);
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

-- =============================================================================
-- 种子数据：初始管理员（用户名 admin / 密码 admin123，仅本地开发起步）
-- =============================================================================
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

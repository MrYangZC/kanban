-- =====================================================================
-- V1.0.0 baseline - 看板系统 v1.0.0 定版初始化脚本
--
-- 内容：完整表结构（序列 / 表 / 主键 / 唯一 / 检查 / 索引 / 外键）+ 最小种子数据。
-- 由线上 v37 的实际结构一次性固化而来；历史上的增量脚本（V1.0.1、V24~V37）
-- 已全部合并进本文件并从仓库删除，v1.0.0 即为结构定版起点。
--
-- 种子数据（系统运行必需的字典与账号，不含任何业务数据）：
--   sys_user           内置管理员 admin / admin123
--   issue_type         工作项类型字典
--   issue_status       工作项状态字典
--   workflow / workflow_status / workflow_transition   按类别划分的工作流
--   permission / role / role_menu / role_permission    RBAC 定义
--   menu               菜单定义
-- 项目 / 看板 / 工作项 / 成员 / 评论等业务数据一律不含，新库即为干净的第一版。
--
-- 约定：本文件即 v1.0.0 定版基线，不再修改；后续结构变更请新增
--       V1.0.1__xxx.sql / V1.1.0__xxx.sql 等增量迁移脚本。
-- =====================================================================

-- ----------------------------
-- 1. 序列
-- ----------------------------

CREATE SEQUENCE "public"."issue_number_seq"
INCREMENT BY 1
MINVALUE 1
MAXVALUE 9223372036854775807
START WITH 1
CACHE 50 NO CYCLE;

-- ----------------------------
-- 2. 表结构
-- ----------------------------

CREATE TABLE "public"."announcement" (
    "id" uuid NOT NULL DEFAULT gen_random_uuid(),
    "project_id" uuid NOT NULL,
    "title" character varying(200) NOT NULL,
    "content" text,
    "pinned" boolean NOT NULL DEFAULT false,
    "created_by" uuid,
    "updated_by" uuid,
    "created_at" timestamp(6) without time zone NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "updated_at" timestamp(6) without time zone NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE "public"."attachment" (
    "id" uuid NOT NULL DEFAULT gen_random_uuid(),
    "issue_id" uuid NOT NULL,
    "file_name" character varying(500) NOT NULL,
    "storage_key" character varying(1000) NOT NULL,
    "content_type" character varying(200),
    "file_size" bigint,
    "uploaded_by" uuid,
    "created_at" timestamp(6) without time zone NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "url" character varying(1000),
    "category" character varying(16) NOT NULL DEFAULT 'TASK'::character varying
);

CREATE TABLE "public"."board" (
    "id" uuid NOT NULL DEFAULT gen_random_uuid(),
    "project_id" uuid NOT NULL,
    "name" character varying(100) NOT NULL,
    "board_type" character varying(32) NOT NULL DEFAULT 'KANBAN'::character varying,
    "created_by" uuid,
    "created_at" timestamp(6) without time zone NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "updated_at" timestamp(6) without time zone NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE "public"."board_column" (
    "id" uuid NOT NULL DEFAULT gen_random_uuid(),
    "board_id" uuid NOT NULL,
    "status_id" uuid NOT NULL,
    "sort_order" integer NOT NULL DEFAULT 0,
    "wip_limit" integer,
    "created_at" timestamp(6) without time zone NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "updated_at" timestamp(6) without time zone NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE "public"."bug" (
    "id" uuid NOT NULL DEFAULT gen_random_uuid(),
    "project_id" uuid NOT NULL,
    "issue_number" character varying(64) NOT NULL,
    "parent_id" uuid,
    "issue_type_id" uuid NOT NULL,
    "status_id" uuid NOT NULL,
    "title" character varying(500) NOT NULL,
    "description" text,
    "priority" character varying(20) NOT NULL DEFAULT 'P2'::character varying,
    "reporter_id" uuid,
    "assignee_id" uuid,
    "verifier_id" uuid,
    "sprint_id" uuid,
    "estimate" numeric(10,2),
    "due_date" date,
    "sort_key" character varying(255) NOT NULL,
    "archived" boolean NOT NULL DEFAULT false,
    "severity" character varying(20) NOT NULL DEFAULT 'S3'::character varying,
    "module" character varying(100),
    "fix_version" character varying(60),
    "source" character varying(60),
    "iteration" character varying(100),
    "plan_start" date,
    "no_fix_reason" character varying(1000),
    "created_by" uuid,
    "updated_by" uuid,
    "created_at" timestamp(6) without time zone NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "updated_at" timestamp(6) without time zone NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE "public"."issue_activity" (
    "id" uuid NOT NULL DEFAULT gen_random_uuid(),
    "issue_id" uuid NOT NULL,
    "actor_id" uuid,
    "action" character varying(50) NOT NULL,
    "old_value" jsonb,
    "new_value" jsonb,
    "created_at" timestamp(6) without time zone NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "category" character varying(16) NOT NULL DEFAULT 'TASK'::character varying
);

CREATE TABLE "public"."issue_cc" (
    "id" uuid NOT NULL DEFAULT gen_random_uuid(),
    "issue_id" uuid NOT NULL,
    "user_id" uuid NOT NULL,
    "created_at" timestamp(6) without time zone NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "category" character varying(16) NOT NULL DEFAULT 'TASK'::character varying
);

CREATE TABLE "public"."issue_comment" (
    "id" uuid NOT NULL DEFAULT gen_random_uuid(),
    "issue_id" uuid NOT NULL,
    "user_id" uuid NOT NULL,
    "content" text NOT NULL,
    "created_at" timestamp(6) without time zone NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "updated_at" timestamp(6) without time zone NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "parent_id" uuid,
    "category" character varying(16) NOT NULL DEFAULT 'TASK'::character varying
);

CREATE TABLE "public"."issue_label" (
    "issue_id" uuid NOT NULL,
    "label_id" uuid NOT NULL,
    "category" character varying(16) NOT NULL DEFAULT 'TASK'::character varying
);

CREATE TABLE "public"."issue_participant" (
    "id" uuid NOT NULL DEFAULT gen_random_uuid(),
    "issue_id" uuid NOT NULL,
    "user_id" uuid NOT NULL,
    "created_at" timestamp(6) without time zone NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "category" character varying(16) NOT NULL DEFAULT 'TASK'::character varying
);

CREATE TABLE "public"."issue_relation" (
    "id" uuid NOT NULL DEFAULT gen_random_uuid(),
    "issue_id" uuid NOT NULL,
    "target_issue_id" uuid NOT NULL,
    "relation_type" character varying(32) NOT NULL,
    "created_at" timestamp(6) without time zone NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "category" character varying(16) NOT NULL DEFAULT 'TASK'::character varying
);

CREATE TABLE "public"."issue_status" (
    "id" uuid NOT NULL DEFAULT gen_random_uuid(),
    "name" character varying(50) NOT NULL,
    "code" character varying(32) NOT NULL,
    "category" character varying(32) NOT NULL,
    "color" character varying(32),
    "sort_order" integer NOT NULL DEFAULT 0,
    "created_at" timestamp(6) without time zone NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "updated_at" timestamp(6) without time zone NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE "public"."issue_type" (
    "id" uuid NOT NULL DEFAULT gen_random_uuid(),
    "name" character varying(50) NOT NULL,
    "code" character varying(32) NOT NULL,
    "icon" character varying(100),
    "color" character varying(32),
    "sort_order" integer NOT NULL DEFAULT 0,
    "created_at" timestamp(6) without time zone NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE "public"."label" (
    "id" uuid NOT NULL DEFAULT gen_random_uuid(),
    "name" character varying(50) NOT NULL,
    "color" character varying(32),
    "created_at" timestamp(6) without time zone NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE "public"."menu" (
    "id" uuid NOT NULL DEFAULT gen_random_uuid(),
    "code" character varying(64) NOT NULL,
    "name" character varying(50) NOT NULL,
    "parent_id" uuid,
    "type" character varying(16) NOT NULL DEFAULT 'MENU'::character varying,
    "sort_order" integer NOT NULL DEFAULT 0,
    "created_at" timestamp(6) without time zone NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "updated_at" timestamp(6) without time zone NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "created_by" uuid,
    "updated_by" uuid
);

CREATE TABLE "public"."milestone" (
    "id" uuid NOT NULL DEFAULT gen_random_uuid(),
    "project_id" uuid NOT NULL,
    "name" character varying(200) NOT NULL,
    "description" text,
    "due_date" date,
    "status" character varying(20) NOT NULL DEFAULT 'OPEN'::character varying,
    "progress" integer NOT NULL DEFAULT 0,
    "created_by" uuid,
    "updated_by" uuid,
    "created_at" timestamp(6) without time zone NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "updated_at" timestamp(6) without time zone NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE "public"."permission" (
    "code" character varying(64) NOT NULL,
    "name" character varying(64) NOT NULL,
    "description" character varying(255),
    "created_at" timestamp(6) without time zone NOT NULL DEFAULT now(),
    "module" character varying(32) NOT NULL DEFAULT 'general'::character varying,
    "sort_order" integer NOT NULL DEFAULT 0
);

CREATE TABLE "public"."project" (
    "id" uuid NOT NULL DEFAULT gen_random_uuid(),
    "project_key" character varying(32) NOT NULL,
    "name" character varying(200) NOT NULL,
    "description" text,
    "avatar_url" character varying(500),
    "status" character varying(32) NOT NULL DEFAULT 'ACTIVE'::character varying,
    "lead_user_id" uuid,
    "created_by" uuid,
    "updated_by" uuid,
    "created_at" timestamp(6) without time zone NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "updated_at" timestamp(6) without time zone NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "workflow_id" uuid
);

CREATE TABLE "public"."project_favorite" (
    "id" uuid NOT NULL DEFAULT gen_random_uuid(),
    "project_id" uuid NOT NULL,
    "user_id" uuid NOT NULL,
    "created_at" timestamp(6) without time zone NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE "public"."project_label" (
    "id" uuid NOT NULL DEFAULT gen_random_uuid(),
    "project_id" uuid NOT NULL,
    "label_id" uuid NOT NULL,
    "created_at" timestamp(6) without time zone NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE "public"."project_member" (
    "id" uuid NOT NULL DEFAULT gen_random_uuid(),
    "project_id" uuid NOT NULL,
    "user_id" uuid NOT NULL,
    "created_at" timestamp(6) without time zone NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE "public"."requirement" (
    "id" uuid NOT NULL DEFAULT gen_random_uuid(),
    "project_id" uuid NOT NULL,
    "issue_number" character varying(64) NOT NULL,
    "parent_id" uuid,
    "issue_type_id" uuid NOT NULL,
    "status_id" uuid NOT NULL,
    "title" character varying(500) NOT NULL,
    "description" text,
    "acceptance_criteria" text,
    "module" character varying(100),
    "priority" character varying(20) NOT NULL DEFAULT 'P2'::character varying,
    "reporter_id" uuid,
    "assignee_id" uuid,
    "verifier_id" uuid,
    "sprint_id" uuid,
    "estimate" numeric(10,2),
    "due_date" date,
    "sort_key" character varying(255) NOT NULL,
    "archived" boolean NOT NULL DEFAULT false,
    "created_by" uuid,
    "updated_by" uuid,
    "created_at" timestamp(6) without time zone NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "updated_at" timestamp(6) without time zone NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE "public"."risk" (
    "id" uuid NOT NULL DEFAULT gen_random_uuid(),
    "project_id" uuid NOT NULL,
    "title" character varying(200) NOT NULL,
    "description" text,
    "level" character varying(20) NOT NULL DEFAULT 'MEDIUM'::character varying,
    "status" character varying(20) NOT NULL DEFAULT 'OPEN'::character varying,
    "owner_id" uuid,
    "due_date" date,
    "created_at" timestamp(6) without time zone NOT NULL DEFAULT now(),
    "updated_at" timestamp(6) without time zone NOT NULL DEFAULT now(),
    "created_by" uuid,
    "updated_by" uuid
);

CREATE TABLE "public"."role" (
    "id" uuid NOT NULL DEFAULT gen_random_uuid(),
    "code" character varying(32) NOT NULL,
    "name" character varying(50) NOT NULL,
    "description" character varying(255),
    "sort_order" integer NOT NULL DEFAULT 0,
    "created_by" uuid,
    "updated_by" uuid,
    "created_at" timestamp(6) without time zone NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "updated_at" timestamp(6) without time zone NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE "public"."role_menu" (
    "id" uuid NOT NULL DEFAULT gen_random_uuid(),
    "role_id" uuid NOT NULL,
    "menu_id" uuid NOT NULL,
    "created_at" timestamp(6) without time zone NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE "public"."role_permission" (
    "id" uuid NOT NULL DEFAULT gen_random_uuid(),
    "role_id" uuid NOT NULL,
    "permission_code" character varying(64) NOT NULL,
    "created_at" timestamp(6) without time zone NOT NULL DEFAULT now()
);

CREATE TABLE "public"."sprint" (
    "id" uuid NOT NULL DEFAULT gen_random_uuid(),
    "project_id" uuid NOT NULL,
    "name" character varying(100) NOT NULL,
    "goal" character varying(500),
    "status" character varying(32) NOT NULL DEFAULT 'PLANNED'::character varying,
    "start_date" date,
    "end_date" date,
    "created_by" uuid,
    "created_at" timestamp(6) without time zone NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "updated_at" timestamp(6) without time zone NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "updated_by" uuid
);

CREATE TABLE "public"."sys_user" (
    "id" uuid NOT NULL DEFAULT gen_random_uuid(),
    "username" character varying(100) NOT NULL,
    "display_name" character varying(100) NOT NULL,
    "email" character varying(200),
    "avatar_url" character varying(500),
    "password_hash" character varying(255),
    "enabled" boolean NOT NULL DEFAULT true,
    "created_by" uuid,
    "updated_by" uuid,
    "created_at" timestamp(6) without time zone NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "updated_at" timestamp(6) without time zone NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "is_admin" boolean NOT NULL DEFAULT false
);

CREATE TABLE "public"."task" (
    "id" uuid NOT NULL DEFAULT gen_random_uuid(),
    "project_id" uuid NOT NULL,
    "issue_number" character varying(64) NOT NULL,
    "parent_id" uuid,
    "issue_type_id" uuid NOT NULL,
    "status_id" uuid NOT NULL,
    "title" character varying(500) NOT NULL,
    "description" text,
    "priority" character varying(20) NOT NULL DEFAULT 'P2'::character varying,
    "reporter_id" uuid,
    "assignee_id" uuid,
    "verifier_id" uuid,
    "sprint_id" uuid,
    "estimate" numeric(10,2),
    "due_date" date,
    "sort_key" character varying(255) NOT NULL,
    "archived" boolean NOT NULL DEFAULT false,
    "iteration" character varying(100),
    "created_by" uuid,
    "updated_by" uuid,
    "created_at" timestamp(6) without time zone NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "updated_at" timestamp(6) without time zone NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE "public"."upload_file" (
    "id" uuid NOT NULL,
    "filename" character varying(160) NOT NULL,
    "uploader_id" uuid NOT NULL,
    "project_id" uuid,
    "created_at" timestamp without time zone NOT NULL DEFAULT now()
);

CREATE TABLE "public"."user_role" (
    "id" uuid NOT NULL DEFAULT gen_random_uuid(),
    "user_id" uuid NOT NULL,
    "role_id" uuid NOT NULL,
    "project_id" uuid NOT NULL,
    "created_at" timestamp(6) without time zone NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE "public"."workflow" (
    "id" uuid NOT NULL DEFAULT gen_random_uuid(),
    "name" character varying(100) NOT NULL,
    "description" character varying(500),
    "is_default" boolean NOT NULL DEFAULT false,
    "created_at" timestamp(6) without time zone NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "updated_at" timestamp(6) without time zone NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "applies_to" character varying(32) NOT NULL DEFAULT 'BUG'::character varying
);

CREATE TABLE "public"."workflow_status" (
    "id" uuid NOT NULL DEFAULT gen_random_uuid(),
    "workflow_id" uuid NOT NULL,
    "status_id" uuid NOT NULL,
    "sort_order" integer NOT NULL DEFAULT 0
);

CREATE TABLE "public"."workflow_transition" (
    "id" uuid NOT NULL DEFAULT gen_random_uuid(),
    "workflow_id" uuid NOT NULL,
    "from_status_id" uuid NOT NULL,
    "to_status_id" uuid NOT NULL,
    "name" character varying(100),
    "created_at" timestamp(6) without time zone NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- ----------------------------
-- 3. 主键 / 唯一 / 检查约束
-- ----------------------------

ALTER TABLE "public"."announcement" ADD CONSTRAINT "announcement_pkey" PRIMARY KEY (id);

ALTER TABLE "public"."attachment" ADD CONSTRAINT "chk_attachment_file_size" CHECK (file_size >= 0);

ALTER TABLE "public"."attachment" ADD CONSTRAINT "attachment_pkey" PRIMARY KEY (id);

ALTER TABLE "public"."board" ADD CONSTRAINT "board_pkey" PRIMARY KEY (id);

ALTER TABLE "public"."board_column" ADD CONSTRAINT "chk_board_column_wip_limit" CHECK (wip_limit > 0 OR wip_limit IS NULL);

ALTER TABLE "public"."board_column" ADD CONSTRAINT "board_column_pkey" PRIMARY KEY (id);

ALTER TABLE "public"."board_column" ADD CONSTRAINT "uk_board_column" UNIQUE (board_id, status_id);

ALTER TABLE "public"."bug" ADD CONSTRAINT "pk_bug" PRIMARY KEY (id);

ALTER TABLE "public"."bug" ADD CONSTRAINT "uk_bug_number" UNIQUE (project_id, issue_number);

ALTER TABLE "public"."issue_activity" ADD CONSTRAINT "issue_activity_pkey" PRIMARY KEY (id);

ALTER TABLE "public"."issue_cc" ADD CONSTRAINT "issue_cc_pkey" PRIMARY KEY (id);

ALTER TABLE "public"."issue_cc" ADD CONSTRAINT "uk_issue_cc" UNIQUE (issue_id, user_id);

ALTER TABLE "public"."issue_comment" ADD CONSTRAINT "issue_comment_pkey" PRIMARY KEY (id);

ALTER TABLE "public"."issue_label" ADD CONSTRAINT "issue_label_pkey" PRIMARY KEY (issue_id, label_id);

ALTER TABLE "public"."issue_participant" ADD CONSTRAINT "issue_participant_pkey" PRIMARY KEY (id);

ALTER TABLE "public"."issue_participant" ADD CONSTRAINT "uk_issue_participant" UNIQUE (issue_id, user_id);

ALTER TABLE "public"."issue_relation" ADD CONSTRAINT "issue_relation_pkey" PRIMARY KEY (id);

ALTER TABLE "public"."issue_relation" ADD CONSTRAINT "uk_issue_relation" UNIQUE (issue_id, target_issue_id, relation_type);

ALTER TABLE "public"."issue_status" ADD CONSTRAINT "issue_status_pkey" PRIMARY KEY (id);

ALTER TABLE "public"."issue_status" ADD CONSTRAINT "uk_issue_status_code" UNIQUE (code);

ALTER TABLE "public"."issue_type" ADD CONSTRAINT "issue_type_pkey" PRIMARY KEY (id);

ALTER TABLE "public"."issue_type" ADD CONSTRAINT "uk_issue_type_code" UNIQUE (code);

ALTER TABLE "public"."label" ADD CONSTRAINT "label_pkey" PRIMARY KEY (id);

ALTER TABLE "public"."label" ADD CONSTRAINT "uk_label_name" UNIQUE (name);

ALTER TABLE "public"."menu" ADD CONSTRAINT "menu_pkey" PRIMARY KEY (id);

ALTER TABLE "public"."menu" ADD CONSTRAINT "uk_menu_code" UNIQUE (code);

ALTER TABLE "public"."milestone" ADD CONSTRAINT "chk_milestone_progress" CHECK (progress >= 0 AND progress <= 100);

ALTER TABLE "public"."milestone" ADD CONSTRAINT "milestone_pkey" PRIMARY KEY (id);

ALTER TABLE "public"."permission" ADD CONSTRAINT "permission_pkey" PRIMARY KEY (code);

ALTER TABLE "public"."project" ADD CONSTRAINT "project_pkey" PRIMARY KEY (id);

ALTER TABLE "public"."project" ADD CONSTRAINT "uk_project_key" UNIQUE (project_key);

ALTER TABLE "public"."project_favorite" ADD CONSTRAINT "project_favorite_pkey" PRIMARY KEY (id);

ALTER TABLE "public"."project_favorite" ADD CONSTRAINT "uk_project_favorite" UNIQUE (project_id, user_id);

ALTER TABLE "public"."project_label" ADD CONSTRAINT "project_label_pkey" PRIMARY KEY (id);

ALTER TABLE "public"."project_label" ADD CONSTRAINT "uk_project_label" UNIQUE (project_id, label_id);

ALTER TABLE "public"."project_member" ADD CONSTRAINT "project_member_pkey" PRIMARY KEY (id);

ALTER TABLE "public"."project_member" ADD CONSTRAINT "uk_project_member" UNIQUE (project_id, user_id);

ALTER TABLE "public"."requirement" ADD CONSTRAINT "pk_requirement" PRIMARY KEY (id);

ALTER TABLE "public"."requirement" ADD CONSTRAINT "uk_requirement_number" UNIQUE (project_id, issue_number);

ALTER TABLE "public"."risk" ADD CONSTRAINT "risk_pkey" PRIMARY KEY (id);

ALTER TABLE "public"."role" ADD CONSTRAINT "role_pkey" PRIMARY KEY (id);

ALTER TABLE "public"."role" ADD CONSTRAINT "uk_role_code" UNIQUE (code);

ALTER TABLE "public"."role_menu" ADD CONSTRAINT "role_menu_pkey" PRIMARY KEY (id);

ALTER TABLE "public"."role_menu" ADD CONSTRAINT "uk_role_menu" UNIQUE (role_id, menu_id);

ALTER TABLE "public"."role_permission" ADD CONSTRAINT "role_permission_pkey" PRIMARY KEY (id);

ALTER TABLE "public"."role_permission" ADD CONSTRAINT "uk_role_permission" UNIQUE (role_id, permission_code);

ALTER TABLE "public"."sprint" ADD CONSTRAINT "sprint_pkey" PRIMARY KEY (id);

ALTER TABLE "public"."sys_user" ADD CONSTRAINT "sys_user_pkey" PRIMARY KEY (id);

ALTER TABLE "public"."sys_user" ADD CONSTRAINT "uk_sys_user_username" UNIQUE (username);

ALTER TABLE "public"."task" ADD CONSTRAINT "pk_task" PRIMARY KEY (id);

ALTER TABLE "public"."task" ADD CONSTRAINT "uk_task_number" UNIQUE (project_id, issue_number);

ALTER TABLE "public"."upload_file" ADD CONSTRAINT "upload_file_pkey" PRIMARY KEY (id);

ALTER TABLE "public"."user_role" ADD CONSTRAINT "user_role_pkey" PRIMARY KEY (id);

ALTER TABLE "public"."user_role" ADD CONSTRAINT "uk_user_role" UNIQUE (user_id, role_id, project_id);

ALTER TABLE "public"."workflow" ADD CONSTRAINT "ck_workflow_applies_to" CHECK (applies_to::text = ANY (ARRAY['TASK'::character varying, 'REQUIREMENT'::character varying, 'BUG'::character varying]::text[]));

ALTER TABLE "public"."workflow" ADD CONSTRAINT "workflow_pkey" PRIMARY KEY (id);

ALTER TABLE "public"."workflow" ADD CONSTRAINT "uk_workflow_name" UNIQUE (name);

ALTER TABLE "public"."workflow_status" ADD CONSTRAINT "workflow_status_pkey" PRIMARY KEY (id);

ALTER TABLE "public"."workflow_status" ADD CONSTRAINT "uk_workflow_status" UNIQUE (workflow_id, status_id);

ALTER TABLE "public"."workflow_transition" ADD CONSTRAINT "workflow_transition_pkey" PRIMARY KEY (id);

ALTER TABLE "public"."workflow_transition" ADD CONSTRAINT "uk_workflow_transition" UNIQUE (workflow_id, from_status_id, to_status_id);

-- ----------------------------
-- 4. 普通索引
-- ----------------------------

CREATE INDEX idx_announcement_project ON public.announcement USING btree (project_id);

CREATE INDEX idx_attachment_issue ON public.attachment USING btree (issue_id);

CREATE INDEX idx_board_project ON public.board USING btree (project_id);

CREATE INDEX idx_board_column_board ON public.board_column USING btree (board_id);

CREATE INDEX idx_bug_assignee ON public.bug USING btree (assignee_id);

CREATE INDEX idx_bug_project ON public.bug USING btree (project_id);

CREATE INDEX idx_bug_severity ON public.bug USING btree (severity);

CREATE INDEX idx_bug_status ON public.bug USING btree (status_id);

CREATE INDEX idx_bug_type ON public.bug USING btree (issue_type_id);

CREATE INDEX idx_issue_activity_issue ON public.issue_activity USING btree (issue_id);

CREATE INDEX idx_issue_comment_issue ON public.issue_comment USING btree (issue_id);

CREATE INDEX idx_issue_comment_parent ON public.issue_comment USING btree (parent_id);

CREATE INDEX idx_issue_label_label ON public.issue_label USING btree (label_id);

CREATE INDEX idx_issue_relation_target ON public.issue_relation USING btree (target_issue_id);

CREATE INDEX idx_milestone_project ON public.milestone USING btree (project_id);

CREATE INDEX idx_project_favorite_project ON public.project_favorite USING btree (project_id);

CREATE INDEX idx_project_favorite_user ON public.project_favorite USING btree (user_id);

CREATE INDEX idx_project_label_label ON public.project_label USING btree (label_id);

CREATE INDEX idx_project_member_user ON public.project_member USING btree (user_id);

CREATE INDEX idx_requirement_assignee ON public.requirement USING btree (assignee_id);

CREATE INDEX idx_requirement_project ON public.requirement USING btree (project_id);

CREATE INDEX idx_requirement_status ON public.requirement USING btree (status_id);

CREATE INDEX idx_requirement_type ON public.requirement USING btree (issue_type_id);

CREATE INDEX idx_risk_level ON public.risk USING btree (level);

CREATE INDEX idx_risk_project ON public.risk USING btree (project_id);

CREATE INDEX idx_risk_status ON public.risk USING btree (status);

CREATE INDEX idx_role_menu_role ON public.role_menu USING btree (role_id);

CREATE INDEX idx_role_permission_code ON public.role_permission USING btree (permission_code);

CREATE INDEX idx_sprint_project ON public.sprint USING btree (project_id);

CREATE INDEX idx_sprint_status ON public.sprint USING btree (status);

CREATE INDEX idx_task_assignee ON public.task USING btree (assignee_id);

CREATE INDEX idx_task_project ON public.task USING btree (project_id);

CREATE INDEX idx_task_status ON public.task USING btree (status_id);

CREATE INDEX idx_task_type ON public.task USING btree (issue_type_id);

CREATE INDEX idx_upload_file_project ON public.upload_file USING btree (project_id);

CREATE UNIQUE INDEX uk_upload_file_filename ON public.upload_file USING btree (filename);

CREATE INDEX idx_user_role_project_user ON public.user_role USING btree (project_id, user_id);

CREATE UNIQUE INDEX uk_workflow_default_per_category ON public.workflow USING btree (applies_to) WHERE (is_default = true);

CREATE INDEX idx_workflow_transition_wf ON public.workflow_transition USING btree (workflow_id);

-- ----------------------------
-- 5. 序列归属
-- ----------------------------

-- ----------------------------
-- 6. 外键约束
-- ----------------------------

ALTER TABLE "public"."board" ADD CONSTRAINT "fk_board_project" FOREIGN KEY (project_id) REFERENCES project(id) ON DELETE RESTRICT;

ALTER TABLE "public"."board_column" ADD CONSTRAINT "fk_board_column_board" FOREIGN KEY (board_id) REFERENCES board(id) ON DELETE CASCADE;

ALTER TABLE "public"."board_column" ADD CONSTRAINT "fk_board_column_status" FOREIGN KEY (status_id) REFERENCES issue_status(id) ON DELETE RESTRICT;

ALTER TABLE "public"."bug" ADD CONSTRAINT "fk_bug_assignee" FOREIGN KEY (assignee_id) REFERENCES sys_user(id) ON DELETE SET NULL;

ALTER TABLE "public"."bug" ADD CONSTRAINT "fk_bug_parent" FOREIGN KEY (parent_id) REFERENCES bug(id) ON DELETE CASCADE;

ALTER TABLE "public"."bug" ADD CONSTRAINT "fk_bug_project" FOREIGN KEY (project_id) REFERENCES project(id) ON DELETE RESTRICT;

ALTER TABLE "public"."bug" ADD CONSTRAINT "fk_bug_reporter" FOREIGN KEY (reporter_id) REFERENCES sys_user(id) ON DELETE SET NULL;

ALTER TABLE "public"."bug" ADD CONSTRAINT "fk_bug_sprint" FOREIGN KEY (sprint_id) REFERENCES sprint(id) ON DELETE SET NULL;

ALTER TABLE "public"."bug" ADD CONSTRAINT "fk_bug_status" FOREIGN KEY (status_id) REFERENCES issue_status(id) ON DELETE RESTRICT;

ALTER TABLE "public"."bug" ADD CONSTRAINT "fk_bug_type" FOREIGN KEY (issue_type_id) REFERENCES issue_type(id) ON DELETE RESTRICT;

ALTER TABLE "public"."bug" ADD CONSTRAINT "fk_bug_verifier" FOREIGN KEY (verifier_id) REFERENCES sys_user(id) ON DELETE SET NULL;

ALTER TABLE "public"."issue_activity" ADD CONSTRAINT "fk_issue_activity_actor" FOREIGN KEY (actor_id) REFERENCES sys_user(id) ON DELETE SET NULL;

ALTER TABLE "public"."issue_cc" ADD CONSTRAINT "fk_issue_cc_user" FOREIGN KEY (user_id) REFERENCES sys_user(id) ON DELETE SET NULL;

ALTER TABLE "public"."issue_comment" ADD CONSTRAINT "fk_issue_comment_parent" FOREIGN KEY (parent_id) REFERENCES issue_comment(id) ON DELETE RESTRICT;

ALTER TABLE "public"."issue_comment" ADD CONSTRAINT "fk_issue_comment_user" FOREIGN KEY (user_id) REFERENCES sys_user(id) ON DELETE SET NULL;

ALTER TABLE "public"."issue_label" ADD CONSTRAINT "fk_issue_label_label" FOREIGN KEY (label_id) REFERENCES label(id) ON DELETE CASCADE;

ALTER TABLE "public"."issue_participant" ADD CONSTRAINT "fk_issue_participant_user" FOREIGN KEY (user_id) REFERENCES sys_user(id) ON DELETE SET NULL;

ALTER TABLE "public"."project" ADD CONSTRAINT "fk_project_lead" FOREIGN KEY (lead_user_id) REFERENCES sys_user(id) ON DELETE SET NULL;

ALTER TABLE "public"."project" ADD CONSTRAINT "fk_project_workflow" FOREIGN KEY (workflow_id) REFERENCES workflow(id) ON DELETE RESTRICT;

ALTER TABLE "public"."project_favorite" ADD CONSTRAINT "fk_project_favorite_project" FOREIGN KEY (project_id) REFERENCES project(id) ON DELETE CASCADE;

ALTER TABLE "public"."project_label" ADD CONSTRAINT "fk_project_label_label" FOREIGN KEY (label_id) REFERENCES label(id) ON DELETE CASCADE;

ALTER TABLE "public"."project_label" ADD CONSTRAINT "fk_project_label_project" FOREIGN KEY (project_id) REFERENCES project(id) ON DELETE CASCADE;

ALTER TABLE "public"."project_member" ADD CONSTRAINT "fk_pm_project" FOREIGN KEY (project_id) REFERENCES project(id) ON DELETE CASCADE;

ALTER TABLE "public"."project_member" ADD CONSTRAINT "fk_pm_user" FOREIGN KEY (user_id) REFERENCES sys_user(id) ON DELETE CASCADE;

ALTER TABLE "public"."requirement" ADD CONSTRAINT "fk_requirement_assignee" FOREIGN KEY (assignee_id) REFERENCES sys_user(id) ON DELETE SET NULL;

ALTER TABLE "public"."requirement" ADD CONSTRAINT "fk_requirement_parent" FOREIGN KEY (parent_id) REFERENCES requirement(id) ON DELETE CASCADE;

ALTER TABLE "public"."requirement" ADD CONSTRAINT "fk_requirement_project" FOREIGN KEY (project_id) REFERENCES project(id) ON DELETE RESTRICT;

ALTER TABLE "public"."requirement" ADD CONSTRAINT "fk_requirement_reporter" FOREIGN KEY (reporter_id) REFERENCES sys_user(id) ON DELETE SET NULL;

ALTER TABLE "public"."requirement" ADD CONSTRAINT "fk_requirement_sprint" FOREIGN KEY (sprint_id) REFERENCES sprint(id) ON DELETE SET NULL;

ALTER TABLE "public"."requirement" ADD CONSTRAINT "fk_requirement_status" FOREIGN KEY (status_id) REFERENCES issue_status(id) ON DELETE RESTRICT;

ALTER TABLE "public"."requirement" ADD CONSTRAINT "fk_requirement_type" FOREIGN KEY (issue_type_id) REFERENCES issue_type(id) ON DELETE RESTRICT;

ALTER TABLE "public"."requirement" ADD CONSTRAINT "fk_requirement_verifier" FOREIGN KEY (verifier_id) REFERENCES sys_user(id) ON DELETE SET NULL;

ALTER TABLE "public"."risk" ADD CONSTRAINT "risk_project_id_fkey" FOREIGN KEY (project_id) REFERENCES project(id) ON DELETE CASCADE;

ALTER TABLE "public"."role_menu" ADD CONSTRAINT "fk_rm_menu" FOREIGN KEY (menu_id) REFERENCES menu(id) ON DELETE CASCADE;

ALTER TABLE "public"."role_menu" ADD CONSTRAINT "fk_rm_role" FOREIGN KEY (role_id) REFERENCES role(id) ON DELETE CASCADE;

ALTER TABLE "public"."role_permission" ADD CONSTRAINT "role_permission_permission_code_fkey" FOREIGN KEY (permission_code) REFERENCES permission(code) ON DELETE CASCADE;

ALTER TABLE "public"."role_permission" ADD CONSTRAINT "role_permission_role_id_fkey" FOREIGN KEY (role_id) REFERENCES role(id) ON DELETE CASCADE;

ALTER TABLE "public"."sprint" ADD CONSTRAINT "fk_sprint_project" FOREIGN KEY (project_id) REFERENCES project(id) ON DELETE CASCADE;

ALTER TABLE "public"."task" ADD CONSTRAINT "fk_task_assignee" FOREIGN KEY (assignee_id) REFERENCES sys_user(id) ON DELETE SET NULL;

ALTER TABLE "public"."task" ADD CONSTRAINT "fk_task_parent" FOREIGN KEY (parent_id) REFERENCES task(id) ON DELETE CASCADE;

ALTER TABLE "public"."task" ADD CONSTRAINT "fk_task_project" FOREIGN KEY (project_id) REFERENCES project(id) ON DELETE RESTRICT;

ALTER TABLE "public"."task" ADD CONSTRAINT "fk_task_reporter" FOREIGN KEY (reporter_id) REFERENCES sys_user(id) ON DELETE SET NULL;

ALTER TABLE "public"."task" ADD CONSTRAINT "fk_task_sprint" FOREIGN KEY (sprint_id) REFERENCES sprint(id) ON DELETE SET NULL;

ALTER TABLE "public"."task" ADD CONSTRAINT "fk_task_status" FOREIGN KEY (status_id) REFERENCES issue_status(id) ON DELETE RESTRICT;

ALTER TABLE "public"."task" ADD CONSTRAINT "fk_task_type" FOREIGN KEY (issue_type_id) REFERENCES issue_type(id) ON DELETE RESTRICT;

ALTER TABLE "public"."task" ADD CONSTRAINT "fk_task_verifier" FOREIGN KEY (verifier_id) REFERENCES sys_user(id) ON DELETE SET NULL;

ALTER TABLE "public"."user_role" ADD CONSTRAINT "fk_ur_project" FOREIGN KEY (project_id) REFERENCES project(id) ON DELETE CASCADE;

ALTER TABLE "public"."user_role" ADD CONSTRAINT "fk_ur_role" FOREIGN KEY (role_id) REFERENCES role(id) ON DELETE CASCADE;

ALTER TABLE "public"."user_role" ADD CONSTRAINT "fk_ur_user" FOREIGN KEY (user_id) REFERENCES sys_user(id) ON DELETE CASCADE;

ALTER TABLE "public"."workflow_status" ADD CONSTRAINT "fk_ws_status" FOREIGN KEY (status_id) REFERENCES issue_status(id) ON DELETE RESTRICT;

ALTER TABLE "public"."workflow_status" ADD CONSTRAINT "fk_ws_workflow" FOREIGN KEY (workflow_id) REFERENCES workflow(id) ON DELETE CASCADE;

ALTER TABLE "public"."workflow_transition" ADD CONSTRAINT "fk_wt_from" FOREIGN KEY (from_status_id) REFERENCES issue_status(id) ON DELETE RESTRICT;

ALTER TABLE "public"."workflow_transition" ADD CONSTRAINT "fk_wt_to" FOREIGN KEY (to_status_id) REFERENCES issue_status(id) ON DELETE RESTRICT;

ALTER TABLE "public"."workflow_transition" ADD CONSTRAINT "fk_wt_workflow" FOREIGN KEY (workflow_id) REFERENCES workflow(id) ON DELETE CASCADE;

-- ----------------------------
-- 7. 字典种子数据
-- ----------------------------

-- Records of sys_user
INSERT INTO "public"."sys_user" ("id", "username", "display_name", "email", "avatar_url", "password_hash", "enabled", "created_by", "updated_by", "created_at", "updated_at", "is_admin") VALUES ('00000000-0000-0000-0000-000000000001', 'admin', 'Admin', 'admin@kanban.dev', '/api/uploads/91029fd6-713d-4aea-bbb5-853ecf1c98d6.jpg', '$2a$10$v8tslO9ZJFuUpo67lLuc.OZgsDBUcfqUDKZ0dvuYp.LFPmymuFzKe', true, '00000000-0000-0000-0000-000000000001', '00000000-0000-0000-0000-000000000001', '2026-08-17 17:40:18.539255', '2026-10-22 09:27:12.344912', true);

-- Records of issue_type
INSERT INTO "public"."issue_type" ("id", "name", "code", "icon", "color", "sort_order", "created_at") VALUES ('e071e2f5-1baa-4215-9ff5-27e4744ee353', '缺陷', 'BUG', '🐞', '#FF4D4F', 1, '2026-08-17 23:03:16.67908');
INSERT INTO "public"."issue_type" ("id", "name", "code", "icon", "color", "sort_order", "created_at") VALUES ('dc8df0c9-801f-4e7d-9321-88b53abc9859', '兼容性问题', 'COMPAT_DEFECT', '🔗', '#13C2C2', 8, '2026-08-17 23:03:16.67908');
INSERT INTO "public"."issue_type" ("id", "name", "code", "icon", "color", "sort_order", "created_at") VALUES ('aa726ce5-8f4f-4120-ac24-caede9cee51c', '特性', 'FEATURE', '✨', '#722ED1', 3, '2026-08-17 23:03:16.67908');
INSERT INTO "public"."issue_type" ("id", "name", "code", "icon", "color", "sort_order", "created_at") VALUES ('71ab4446-7ebe-403b-be51-1f5937c2e8a9', '功能缺陷', 'FUNC_DEFECT', '⚙️', '#FF4D4F', 5, '2026-08-17 23:03:16.67908');
INSERT INTO "public"."issue_type" ("id", "name", "code", "icon", "color", "sort_order", "created_at") VALUES ('5e597204-8731-4cd7-a6d9-c80c5228b989', '改进', 'IMPROVEMENT', '🔧', '#FAAD14', 4, '2026-08-17 23:03:16.67908');
INSERT INTO "public"."issue_type" ("id", "name", "code", "icon", "color", "sort_order", "created_at") VALUES ('f269891e-89fb-4002-b6bb-5064325593f0', '性能缺陷', 'PERF_DEFECT', '🚀', '#FA8C16', 6, '2026-08-17 23:03:16.67908');
INSERT INTO "public"."issue_type" ("id", "name", "code", "icon", "color", "sort_order", "created_at") VALUES ('c732b43e-cf6d-4ed6-97f4-104c3c2d07e3', '安全漏洞', 'SEC_DEFECT', '🛡️', '#722ED1', 7, '2026-08-17 23:03:16.67908');
INSERT INTO "public"."issue_type" ("id", "name", "code", "icon", "color", "sort_order", "created_at") VALUES ('3aa83f61-ec88-413c-9e49-727d70431bcc', '故事', 'STORY', '📖', '#52C41A', 2, '2026-08-17 23:03:16.67908');
INSERT INTO "public"."issue_type" ("id", "name", "code", "icon", "color", "sort_order", "created_at") VALUES ('aa7a2f5a-5d01-4feb-a1c2-29f40e6037d4', '任务', 'TASK', '📋', '#1677FF', 0, '2026-08-17 23:03:16.678079');
INSERT INTO "public"."issue_type" ("id", "name", "code", "icon", "color", "sort_order", "created_at") VALUES ('012e1d09-ba8d-428d-937f-f87162802cb1', '界面缺陷', 'UI_DEFECT', '🎨', '#EB2F96', 9, '2026-08-17 23:03:16.67908');

-- Records of issue_status
INSERT INTO "public"."issue_status" ("id", "name", "code", "category", "color", "sort_order", "created_at", "updated_at") VALUES ('f6f6d65b-5f93-4c1f-b7aa-bd456569814e', '已关闭', 'CLOSED', 'DONE', '#8C8C8C', 5, '2026-08-17 23:03:16.71263', '2026-08-17 23:03:16.71263');
INSERT INTO "public"."issue_status" ("id", "name", "code", "category", "color", "sort_order", "created_at", "updated_at") VALUES ('1bddf952-4834-440a-b9e5-a7c10ef027b0', '已修复', 'FIXED', 'DONE', '#52C41A', 3, '2026-08-17 23:03:16.71263', '2026-08-17 23:03:16.71263');
INSERT INTO "public"."issue_status" ("id", "name", "code", "category", "color", "sort_order", "created_at", "updated_at") VALUES ('23d0078c-154a-43d2-a73c-5d04ff64bc49', '处理中', 'IN_PROGRESS', 'IN_PROGRESS', '#1677FF', 2, '2026-08-17 23:03:16.71263', '2026-08-17 23:03:16.71263');
INSERT INTO "public"."issue_status" ("id", "name", "code", "category", "color", "sort_order", "created_at", "updated_at") VALUES ('92baa2d9-9b63-4701-be3b-330f0fedb5d9', '再次打开', 'REOPENED', 'IN_PROGRESS', '#FA8C16', 1, '2026-08-17 23:03:16.71263', '2026-08-17 23:03:16.71263');
INSERT INTO "public"."issue_status" ("id", "name", "code", "category", "color", "sort_order", "created_at", "updated_at") VALUES ('5074bc2b-62fb-488b-ba7a-04db61ceed06', '待确认', 'TO_CONFIRM', 'TODO', '#8F959E', 0, '2026-08-17 23:03:16.71263', '2026-08-17 23:03:16.71263');
INSERT INTO "public"."issue_status" ("id", "name", "code", "category", "color", "sort_order", "created_at", "updated_at") VALUES ('9f6a65a2-a124-401d-a845-a48e8cf0eb1b', '暂不修复', 'WONT_FIX', 'DONE', '#BFBFBF', 4, '2026-08-17 23:03:16.71263', '2026-08-17 23:03:16.71263');
INSERT INTO "public"."issue_status" ("id", "name", "code", "category", "color", "sort_order", "created_at", "updated_at") VALUES ('a1000000-0000-4000-8000-000000000001', '待办', 'TASK_TODO', 'TODO', '#8F959E', 10, '2026-10-21 10:42:39.474441', '2026-10-21 10:42:39.474441');
INSERT INTO "public"."issue_status" ("id", "name", "code", "category", "color", "sort_order", "created_at", "updated_at") VALUES ('a1000000-0000-4000-8000-000000000002', '进行中', 'TASK_IN_PROGRESS', 'IN_PROGRESS', '#1677FF', 11, '2026-10-21 10:42:39.474441', '2026-10-21 10:42:39.474441');
INSERT INTO "public"."issue_status" ("id", "name", "code", "category", "color", "sort_order", "created_at", "updated_at") VALUES ('a1000000-0000-4000-8000-000000000003', '已完成', 'TASK_DONE', 'DONE', '#52C41A', 12, '2026-10-21 10:42:39.474441', '2026-10-21 10:42:39.474441');
INSERT INTO "public"."issue_status" ("id", "name", "code", "category", "color", "sort_order", "created_at", "updated_at") VALUES ('a1000000-0000-4000-8000-000000000004', '已取消', 'TASK_CANCELLED', 'DONE', '#BFBFBF', 13, '2026-10-21 10:42:39.474441', '2026-10-21 10:42:39.474441');
INSERT INTO "public"."issue_status" ("id", "name", "code", "category", "color", "sort_order", "created_at", "updated_at") VALUES ('b1000000-0000-4000-8000-000000000003', '开发中', 'REQ_DEVELOPING', 'IN_PROGRESS', '#40A9FF', 22, '2026-10-21 10:42:39.474441', '2026-10-21 10:42:39.474441');
INSERT INTO "public"."issue_status" ("id", "name", "code", "category", "color", "sort_order", "created_at", "updated_at") VALUES ('b1000000-0000-4000-8000-000000000001', '待处理', 'REQ_TODO', 'TODO', '#8F959E', 20, '2026-10-21 10:42:39.474441', '2026-10-21 10:42:39.474441');
INSERT INTO "public"."issue_status" ("id", "name", "code", "category", "color", "sort_order", "created_at", "updated_at") VALUES ('b1000000-0000-4000-8000-000000000002', '设计中', 'REQ_DESIGNING', 'IN_PROGRESS', '#1677FF', 21, '2026-10-21 10:42:39.474441', '2026-10-21 10:42:39.474441');
INSERT INTO "public"."issue_status" ("id", "name", "code", "category", "color", "sort_order", "created_at", "updated_at") VALUES ('b1000000-0000-4000-8000-000000000006', '测试中', 'REQ_TESTING', 'IN_PROGRESS', '#722ED1', 23, '2026-10-21 13:17:21.024167', '2026-10-21 13:17:21.024167');
INSERT INTO "public"."issue_status" ("id", "name", "code", "category", "color", "sort_order", "created_at", "updated_at") VALUES ('b1000000-0000-4000-8000-000000000004', '已完成', 'REQ_DONE', 'DONE', '#52C41A', 24, '2026-10-21 10:42:39.474441', '2026-10-21 10:42:39.474441');
INSERT INTO "public"."issue_status" ("id", "name", "code", "category", "color", "sort_order", "created_at", "updated_at") VALUES ('b1000000-0000-4000-8000-000000000005', '已取消', 'REQ_CANCELLED', 'DONE', '#BFBFBF', 25, '2026-10-21 10:42:39.474441', '2026-10-21 10:42:39.474441');

-- Records of workflow
INSERT INTO "public"."workflow" ("id", "name", "description", "is_default", "created_at", "updated_at", "applies_to") VALUES ('8308bb4d-16d3-4c1d-a6d7-27f9dffe686e', '缺陷工作流', '待确认 → 再次打开 → 处理中 → 已修复 → 暂不修复 → 已关闭', true, '2026-08-17 23:03:16.730911', '2026-08-17 23:03:16.730911', 'BUG');
INSERT INTO "public"."workflow" ("id", "name", "description", "is_default", "created_at", "updated_at", "applies_to") VALUES ('c1000000-0000-4000-8000-000000000002', '需求工作流', '待处理/设计中/开发中/测试中/已完成 可互转（已完成可打回设计、开发、测试）；任一活跃阶段可取消；已取消可重新打开；已完成不能回待处理、不能取消', true, '2026-10-21 10:42:39.474441', '2026-10-21 10:42:39.474441', 'REQUIREMENT');
INSERT INTO "public"."workflow" ("id", "name", "description", "is_default", "created_at", "updated_at", "applies_to") VALUES ('c1000000-0000-4000-8000-000000000001', '任务工作流', '待办/进行中互转，进行中可完成或取消；已完成、已取消都可退回待办（重做/重新打开），已取消也可直接重新开始', true, '2026-10-21 10:42:39.474441', '2026-10-21 10:42:39.474441', 'TASK');

-- Records of workflow_status
INSERT INTO "public"."workflow_status" ("id", "workflow_id", "status_id", "sort_order") VALUES ('1efdf816-9cfa-4aa0-aecf-858ed4cf4162', '8308bb4d-16d3-4c1d-a6d7-27f9dffe686e', '1bddf952-4834-440a-b9e5-a7c10ef027b0', 3);
INSERT INTO "public"."workflow_status" ("id", "workflow_id", "status_id", "sort_order") VALUES ('034ab9af-2861-4886-b8ea-4d898bf012cb', '8308bb4d-16d3-4c1d-a6d7-27f9dffe686e', '23d0078c-154a-43d2-a73c-5d04ff64bc49', 2);
INSERT INTO "public"."workflow_status" ("id", "workflow_id", "status_id", "sort_order") VALUES ('1e9ab63e-b55c-42c0-9de8-e3fc752d3f7d', '8308bb4d-16d3-4c1d-a6d7-27f9dffe686e', '5074bc2b-62fb-488b-ba7a-04db61ceed06', 0);
INSERT INTO "public"."workflow_status" ("id", "workflow_id", "status_id", "sort_order") VALUES ('06869e73-46e6-4a52-a293-65668bb2bdf0', '8308bb4d-16d3-4c1d-a6d7-27f9dffe686e', '92baa2d9-9b63-4701-be3b-330f0fedb5d9', 1);
INSERT INTO "public"."workflow_status" ("id", "workflow_id", "status_id", "sort_order") VALUES ('205b20bc-0363-414e-87ab-ac11680c6ceb', '8308bb4d-16d3-4c1d-a6d7-27f9dffe686e', '9f6a65a2-a124-401d-a845-a48e8cf0eb1b', 4);
INSERT INTO "public"."workflow_status" ("id", "workflow_id", "status_id", "sort_order") VALUES ('06fbb601-bda3-42ba-8c5e-69c3afba8cc6', '8308bb4d-16d3-4c1d-a6d7-27f9dffe686e', 'f6f6d65b-5f93-4c1f-b7aa-bd456569814e', 5);
INSERT INTO "public"."workflow_status" ("id", "workflow_id", "status_id", "sort_order") VALUES ('d1000000-0000-4000-8000-000000000001', 'c1000000-0000-4000-8000-000000000001', 'a1000000-0000-4000-8000-000000000001', 0);
INSERT INTO "public"."workflow_status" ("id", "workflow_id", "status_id", "sort_order") VALUES ('d1000000-0000-4000-8000-000000000002', 'c1000000-0000-4000-8000-000000000001', 'a1000000-0000-4000-8000-000000000002', 1);
INSERT INTO "public"."workflow_status" ("id", "workflow_id", "status_id", "sort_order") VALUES ('d1000000-0000-4000-8000-000000000003', 'c1000000-0000-4000-8000-000000000001', 'a1000000-0000-4000-8000-000000000003', 2);
INSERT INTO "public"."workflow_status" ("id", "workflow_id", "status_id", "sort_order") VALUES ('d1000000-0000-4000-8000-000000000004', 'c1000000-0000-4000-8000-000000000001', 'a1000000-0000-4000-8000-000000000004', 3);
INSERT INTO "public"."workflow_status" ("id", "workflow_id", "status_id", "sort_order") VALUES ('d2000000-0000-4000-8000-000000000001', 'c1000000-0000-4000-8000-000000000002', 'b1000000-0000-4000-8000-000000000001', 0);
INSERT INTO "public"."workflow_status" ("id", "workflow_id", "status_id", "sort_order") VALUES ('d2000000-0000-4000-8000-000000000002', 'c1000000-0000-4000-8000-000000000002', 'b1000000-0000-4000-8000-000000000002', 1);
INSERT INTO "public"."workflow_status" ("id", "workflow_id", "status_id", "sort_order") VALUES ('d2000000-0000-4000-8000-000000000003', 'c1000000-0000-4000-8000-000000000002', 'b1000000-0000-4000-8000-000000000003', 2);
INSERT INTO "public"."workflow_status" ("id", "workflow_id", "status_id", "sort_order") VALUES ('d2000000-0000-4000-8000-000000000004', 'c1000000-0000-4000-8000-000000000002', 'b1000000-0000-4000-8000-000000000004', 4);
INSERT INTO "public"."workflow_status" ("id", "workflow_id", "status_id", "sort_order") VALUES ('d2000000-0000-4000-8000-000000000005', 'c1000000-0000-4000-8000-000000000002', 'b1000000-0000-4000-8000-000000000005', 5);
INSERT INTO "public"."workflow_status" ("id", "workflow_id", "status_id", "sort_order") VALUES ('d2000000-0000-4000-8000-000000000006', 'c1000000-0000-4000-8000-000000000002', 'b1000000-0000-4000-8000-000000000006', 3);

-- Records of workflow_transition
INSERT INTO "public"."workflow_transition" ("id", "workflow_id", "from_status_id", "to_status_id", "name", "created_at") VALUES ('dae03f20-ff5b-435c-9d58-5b30c82b246d', '8308bb4d-16d3-4c1d-a6d7-27f9dffe686e', '1bddf952-4834-440a-b9e5-a7c10ef027b0', '23d0078c-154a-43d2-a73c-5d04ff64bc49', '修复不通过', '2026-08-17 23:03:16.761916');
INSERT INTO "public"."workflow_transition" ("id", "workflow_id", "from_status_id", "to_status_id", "name", "created_at") VALUES ('1018ced0-9f73-480e-8120-990b49e127c4', '8308bb4d-16d3-4c1d-a6d7-27f9dffe686e', '1bddf952-4834-440a-b9e5-a7c10ef027b0', 'f6f6d65b-5f93-4c1f-b7aa-bd456569814e', '验证通过', '2026-08-17 23:03:16.765926');
INSERT INTO "public"."workflow_transition" ("id", "workflow_id", "from_status_id", "to_status_id", "name", "created_at") VALUES ('a2ef3830-6565-44f7-94dc-54606fa2f746', '8308bb4d-16d3-4c1d-a6d7-27f9dffe686e', '23d0078c-154a-43d2-a73c-5d04ff64bc49', '1bddf952-4834-440a-b9e5-a7c10ef027b0', '提交修复', '2026-08-17 23:03:16.756918');
INSERT INTO "public"."workflow_transition" ("id", "workflow_id", "from_status_id", "to_status_id", "name", "created_at") VALUES ('10a19438-8e02-47bf-9a27-5f85504ca840', '8308bb4d-16d3-4c1d-a6d7-27f9dffe686e', '23d0078c-154a-43d2-a73c-5d04ff64bc49', '5074bc2b-62fb-488b-ba7a-04db61ceed06', '退回待确认', '2026-08-17 23:03:16.752931');
INSERT INTO "public"."workflow_transition" ("id", "workflow_id", "from_status_id", "to_status_id", "name", "created_at") VALUES ('c6c56c2f-b4fd-4520-8551-0788d2316aa0', '8308bb4d-16d3-4c1d-a6d7-27f9dffe686e', '23d0078c-154a-43d2-a73c-5d04ff64bc49', '9f6a65a2-a124-401d-a845-a48e8cf0eb1b', '暂不修复', '2026-08-17 23:03:16.769917');
INSERT INTO "public"."workflow_transition" ("id", "workflow_id", "from_status_id", "to_status_id", "name", "created_at") VALUES ('9a7fe363-55d1-46d1-a25a-0e5129671444', '8308bb4d-16d3-4c1d-a6d7-27f9dffe686e', '5074bc2b-62fb-488b-ba7a-04db61ceed06', '23d0078c-154a-43d2-a73c-5d04ff64bc49', '开始处理', '2026-08-17 23:03:16.748909');
INSERT INTO "public"."workflow_transition" ("id", "workflow_id", "from_status_id", "to_status_id", "name", "created_at") VALUES ('bf3b5927-0122-46a0-b41b-930399b9f196', '8308bb4d-16d3-4c1d-a6d7-27f9dffe686e', '5074bc2b-62fb-488b-ba7a-04db61ceed06', '92baa2d9-9b63-4701-be3b-330f0fedb5d9', '再次打开', '2026-08-17 23:03:16.77991');
INSERT INTO "public"."workflow_transition" ("id", "workflow_id", "from_status_id", "to_status_id", "name", "created_at") VALUES ('dd611e8f-0a82-4907-ae68-2a1120d66e35', '8308bb4d-16d3-4c1d-a6d7-27f9dffe686e', '92baa2d9-9b63-4701-be3b-330f0fedb5d9', '23d0078c-154a-43d2-a73c-5d04ff64bc49', '重新处理', '2026-08-17 23:03:16.774915');
INSERT INTO "public"."workflow_transition" ("id", "workflow_id", "from_status_id", "to_status_id", "name", "created_at") VALUES ('54700017-0487-45e8-b631-e4aa8815e753', '8308bb4d-16d3-4c1d-a6d7-27f9dffe686e', '9f6a65a2-a124-401d-a845-a48e8cf0eb1b', '23d0078c-154a-43d2-a73c-5d04ff64bc49', '重新处理', '2026-08-17 23:03:16.772915');
INSERT INTO "public"."workflow_transition" ("id", "workflow_id", "from_status_id", "to_status_id", "name", "created_at") VALUES ('2dcf8f24-4754-4bcb-b8e5-1443196e8b65', '8308bb4d-16d3-4c1d-a6d7-27f9dffe686e', 'f6f6d65b-5f93-4c1f-b7aa-bd456569814e', '92baa2d9-9b63-4701-be3b-330f0fedb5d9', '重新打开', '2026-08-17 23:03:16.781912');
INSERT INTO "public"."workflow_transition" ("id", "workflow_id", "from_status_id", "to_status_id", "name", "created_at") VALUES ('e1000000-0000-4000-8000-000000000001', 'c1000000-0000-4000-8000-000000000001', 'a1000000-0000-4000-8000-000000000001', 'a1000000-0000-4000-8000-000000000002', '开始处理', '2026-10-21 10:42:39.474441');
INSERT INTO "public"."workflow_transition" ("id", "workflow_id", "from_status_id", "to_status_id", "name", "created_at") VALUES ('e1000000-0000-4000-8000-000000000002', 'c1000000-0000-4000-8000-000000000001', 'a1000000-0000-4000-8000-000000000002', 'a1000000-0000-4000-8000-000000000003', '完成', '2026-10-21 10:42:39.474441');
INSERT INTO "public"."workflow_transition" ("id", "workflow_id", "from_status_id", "to_status_id", "name", "created_at") VALUES ('e1000000-0000-4000-8000-000000000003', 'c1000000-0000-4000-8000-000000000001', 'a1000000-0000-4000-8000-000000000002', 'a1000000-0000-4000-8000-000000000001', '退回待办', '2026-10-21 10:42:39.474441');
INSERT INTO "public"."workflow_transition" ("id", "workflow_id", "from_status_id", "to_status_id", "name", "created_at") VALUES ('e1000000-0000-4000-8000-000000000004', 'c1000000-0000-4000-8000-000000000001', 'a1000000-0000-4000-8000-000000000001', 'a1000000-0000-4000-8000-000000000004', '取消任务', '2026-10-21 10:42:39.474441');
INSERT INTO "public"."workflow_transition" ("id", "workflow_id", "from_status_id", "to_status_id", "name", "created_at") VALUES ('e1000000-0000-4000-8000-000000000005', 'c1000000-0000-4000-8000-000000000001', 'a1000000-0000-4000-8000-000000000002', 'a1000000-0000-4000-8000-000000000004', '取消任务', '2026-10-21 10:42:39.474441');
INSERT INTO "public"."workflow_transition" ("id", "workflow_id", "from_status_id", "to_status_id", "name", "created_at") VALUES ('e1000000-0000-4000-8000-000000000006', 'c1000000-0000-4000-8000-000000000001', 'a1000000-0000-4000-8000-000000000003', 'a1000000-0000-4000-8000-000000000002', '重新打开', '2026-10-21 10:42:39.474441');
INSERT INTO "public"."workflow_transition" ("id", "workflow_id", "from_status_id", "to_status_id", "name", "created_at") VALUES ('e2000000-0000-4000-8000-000000000001', 'c1000000-0000-4000-8000-000000000002', 'b1000000-0000-4000-8000-000000000001', 'b1000000-0000-4000-8000-000000000002', '开始设计', '2026-10-21 13:26:21.456243');
INSERT INTO "public"."workflow_transition" ("id", "workflow_id", "from_status_id", "to_status_id", "name", "created_at") VALUES ('e2000000-0000-4000-8000-000000000002', 'c1000000-0000-4000-8000-000000000002', 'b1000000-0000-4000-8000-000000000001', 'b1000000-0000-4000-8000-000000000003', '开始开发', '2026-10-21 13:26:21.456243');
INSERT INTO "public"."workflow_transition" ("id", "workflow_id", "from_status_id", "to_status_id", "name", "created_at") VALUES ('e2000000-0000-4000-8000-000000000003', 'c1000000-0000-4000-8000-000000000002', 'b1000000-0000-4000-8000-000000000001', 'b1000000-0000-4000-8000-000000000006', '开始测试', '2026-10-21 13:26:21.456243');
INSERT INTO "public"."workflow_transition" ("id", "workflow_id", "from_status_id", "to_status_id", "name", "created_at") VALUES ('e2000000-0000-4000-8000-000000000004', 'c1000000-0000-4000-8000-000000000002', 'b1000000-0000-4000-8000-000000000002', 'b1000000-0000-4000-8000-000000000001', '退回待处理', '2026-10-21 13:26:21.456243');
INSERT INTO "public"."workflow_transition" ("id", "workflow_id", "from_status_id", "to_status_id", "name", "created_at") VALUES ('e2000000-0000-4000-8000-000000000005', 'c1000000-0000-4000-8000-000000000002', 'b1000000-0000-4000-8000-000000000002', 'b1000000-0000-4000-8000-000000000003', '开始开发', '2026-10-21 13:26:21.456243');
INSERT INTO "public"."workflow_transition" ("id", "workflow_id", "from_status_id", "to_status_id", "name", "created_at") VALUES ('e2000000-0000-4000-8000-000000000006', 'c1000000-0000-4000-8000-000000000002', 'b1000000-0000-4000-8000-000000000002', 'b1000000-0000-4000-8000-000000000006', '开始测试', '2026-10-21 13:26:21.456243');
INSERT INTO "public"."workflow_transition" ("id", "workflow_id", "from_status_id", "to_status_id", "name", "created_at") VALUES ('e2000000-0000-4000-8000-000000000007', 'c1000000-0000-4000-8000-000000000002', 'b1000000-0000-4000-8000-000000000003', 'b1000000-0000-4000-8000-000000000001', '退回待处理', '2026-10-21 13:26:21.456243');
INSERT INTO "public"."workflow_transition" ("id", "workflow_id", "from_status_id", "to_status_id", "name", "created_at") VALUES ('e2000000-0000-4000-8000-000000000008', 'c1000000-0000-4000-8000-000000000002', 'b1000000-0000-4000-8000-000000000003', 'b1000000-0000-4000-8000-000000000002', '退回设计', '2026-10-21 13:26:21.456243');
INSERT INTO "public"."workflow_transition" ("id", "workflow_id", "from_status_id", "to_status_id", "name", "created_at") VALUES ('e2000000-0000-4000-8000-000000000009', 'c1000000-0000-4000-8000-000000000002', 'b1000000-0000-4000-8000-000000000003', 'b1000000-0000-4000-8000-000000000006', '提交测试', '2026-10-21 13:26:21.456243');
INSERT INTO "public"."workflow_transition" ("id", "workflow_id", "from_status_id", "to_status_id", "name", "created_at") VALUES ('e2000000-0000-4000-8000-00000000000a', 'c1000000-0000-4000-8000-000000000002', 'b1000000-0000-4000-8000-000000000006', 'b1000000-0000-4000-8000-000000000001', '退回待处理', '2026-10-21 13:26:21.456243');
INSERT INTO "public"."workflow_transition" ("id", "workflow_id", "from_status_id", "to_status_id", "name", "created_at") VALUES ('e2000000-0000-4000-8000-00000000000b', 'c1000000-0000-4000-8000-000000000002', 'b1000000-0000-4000-8000-000000000006', 'b1000000-0000-4000-8000-000000000002', '退回设计', '2026-10-21 13:26:21.456243');
INSERT INTO "public"."workflow_transition" ("id", "workflow_id", "from_status_id", "to_status_id", "name", "created_at") VALUES ('e2000000-0000-4000-8000-00000000000c', 'c1000000-0000-4000-8000-000000000002', 'b1000000-0000-4000-8000-000000000006', 'b1000000-0000-4000-8000-000000000003', '测试不通过', '2026-10-21 13:26:21.456243');
INSERT INTO "public"."workflow_transition" ("id", "workflow_id", "from_status_id", "to_status_id", "name", "created_at") VALUES ('e2000000-0000-4000-8000-00000000000d', 'c1000000-0000-4000-8000-000000000002', 'b1000000-0000-4000-8000-000000000001', 'b1000000-0000-4000-8000-000000000005', '取消需求', '2026-10-21 13:26:21.456243');
INSERT INTO "public"."workflow_transition" ("id", "workflow_id", "from_status_id", "to_status_id", "name", "created_at") VALUES ('e2000000-0000-4000-8000-00000000000e', 'c1000000-0000-4000-8000-000000000002', 'b1000000-0000-4000-8000-000000000002', 'b1000000-0000-4000-8000-000000000005', '取消需求', '2026-10-21 13:26:21.456243');
INSERT INTO "public"."workflow_transition" ("id", "workflow_id", "from_status_id", "to_status_id", "name", "created_at") VALUES ('e2000000-0000-4000-8000-00000000000f', 'c1000000-0000-4000-8000-000000000002', 'b1000000-0000-4000-8000-000000000003', 'b1000000-0000-4000-8000-000000000005', '取消需求', '2026-10-21 13:26:21.456243');
INSERT INTO "public"."workflow_transition" ("id", "workflow_id", "from_status_id", "to_status_id", "name", "created_at") VALUES ('e2000000-0000-4000-8000-000000000010', 'c1000000-0000-4000-8000-000000000002', 'b1000000-0000-4000-8000-000000000006', 'b1000000-0000-4000-8000-000000000005', '取消需求', '2026-10-21 13:26:21.456243');
INSERT INTO "public"."workflow_transition" ("id", "workflow_id", "from_status_id", "to_status_id", "name", "created_at") VALUES ('e2000000-0000-4000-8000-000000000011', 'c1000000-0000-4000-8000-000000000002', 'b1000000-0000-4000-8000-000000000005', 'b1000000-0000-4000-8000-000000000001', '重新打开', '2026-10-21 13:26:21.456243');
INSERT INTO "public"."workflow_transition" ("id", "workflow_id", "from_status_id", "to_status_id", "name", "created_at") VALUES ('e2000000-0000-4000-8000-000000000012', 'c1000000-0000-4000-8000-000000000002', 'b1000000-0000-4000-8000-000000000006', 'b1000000-0000-4000-8000-000000000004', '测试通过', '2026-10-21 13:26:21.456243');
INSERT INTO "public"."workflow_transition" ("id", "workflow_id", "from_status_id", "to_status_id", "name", "created_at") VALUES ('e2000000-0000-4000-8000-000000000013', 'c1000000-0000-4000-8000-000000000002', 'b1000000-0000-4000-8000-000000000004', 'b1000000-0000-4000-8000-000000000002', '重新设计', '2026-10-21 13:30:37.9775');
INSERT INTO "public"."workflow_transition" ("id", "workflow_id", "from_status_id", "to_status_id", "name", "created_at") VALUES ('e2000000-0000-4000-8000-000000000014', 'c1000000-0000-4000-8000-000000000002', 'b1000000-0000-4000-8000-000000000004', 'b1000000-0000-4000-8000-000000000003', '重新开发', '2026-10-21 13:30:37.9775');
INSERT INTO "public"."workflow_transition" ("id", "workflow_id", "from_status_id", "to_status_id", "name", "created_at") VALUES ('e2000000-0000-4000-8000-000000000015', 'c1000000-0000-4000-8000-000000000002', 'b1000000-0000-4000-8000-000000000004', 'b1000000-0000-4000-8000-000000000006', '重新测试', '2026-10-21 13:30:37.9775');
INSERT INTO "public"."workflow_transition" ("id", "workflow_id", "from_status_id", "to_status_id", "name", "created_at") VALUES ('e1000000-0000-4000-8000-000000000007', 'c1000000-0000-4000-8000-000000000001', 'a1000000-0000-4000-8000-000000000003', 'a1000000-0000-4000-8000-000000000001', '退回待办', '2026-10-21 14:10:48.669934');
INSERT INTO "public"."workflow_transition" ("id", "workflow_id", "from_status_id", "to_status_id", "name", "created_at") VALUES ('e1000000-0000-4000-8000-000000000008', 'c1000000-0000-4000-8000-000000000001', 'a1000000-0000-4000-8000-000000000004', 'a1000000-0000-4000-8000-000000000001', '重新打开', '2026-10-21 14:10:48.669934');
INSERT INTO "public"."workflow_transition" ("id", "workflow_id", "from_status_id", "to_status_id", "name", "created_at") VALUES ('e1000000-0000-4000-8000-000000000009', 'c1000000-0000-4000-8000-000000000001', 'a1000000-0000-4000-8000-000000000004', 'a1000000-0000-4000-8000-000000000002', '重新开始', '2026-10-21 14:10:48.669934');

-- Records of permission
INSERT INTO "public"."permission" ("code", "name", "description", "created_at", "module", "sort_order") VALUES ('issue:view', '查看工作项', '查看需求/缺陷/任务/风险等工作项', '2026-08-21 11:17:17.81769', 'workitem', 1);
INSERT INTO "public"."permission" ("code", "name", "description", "created_at", "module", "sort_order") VALUES ('issue:create', '创建工作项', '新建需求/缺陷/任务等', '2026-08-21 11:17:17.81769', 'workitem', 2);
INSERT INTO "public"."permission" ("code", "name", "description", "created_at", "module", "sort_order") VALUES ('issue:edit', '编辑工作项', '编辑工作项内容/字段', '2026-08-21 11:17:17.81769', 'workitem', 3);
INSERT INTO "public"."permission" ("code", "name", "description", "created_at", "module", "sort_order") VALUES ('issue:delete', '删除工作项', '删除工作项', '2026-08-21 11:17:17.81769', 'workitem', 4);
INSERT INTO "public"."permission" ("code", "name", "description", "created_at", "module", "sort_order") VALUES ('issue:comment', '评论', '发表/回复评论', '2026-08-21 11:17:17.81769', 'workitem', 5);
INSERT INTO "public"."permission" ("code", "name", "description", "created_at", "module", "sort_order") VALUES ('issue:status', '状态修改', '修改工作项状态/拖拽看板', '2026-08-21 11:17:17.81769', 'workitem', 6);
INSERT INTO "public"."permission" ("code", "name", "description", "created_at", "module", "sort_order") VALUES ('issue:assign', '分配', '分配/修改负责人', '2026-08-21 11:17:17.81769', 'workitem', 7);
INSERT INTO "public"."permission" ("code", "name", "description", "created_at", "module", "sort_order") VALUES ('issue:export', '导出', '批量导出工作项', '2026-08-21 11:17:17.81769', 'workitem', 8);
INSERT INTO "public"."permission" ("code", "name", "description", "created_at", "module", "sort_order") VALUES ('sprint:view', '查看迭代', '查看迭代列表/详情', '2026-08-21 11:17:17.81769', 'sprint', 1);
INSERT INTO "public"."permission" ("code", "name", "description", "created_at", "module", "sort_order") VALUES ('sprint:manage', '管理迭代', '创建/编辑/删除迭代', '2026-08-21 11:17:17.81769', 'sprint', 2);
INSERT INTO "public"."permission" ("code", "name", "description", "created_at", "module", "sort_order") VALUES ('milestone:view', '查看里程碑', '查看里程碑/路线图', '2026-08-21 11:17:17.81769', 'milestone', 1);
INSERT INTO "public"."permission" ("code", "name", "description", "created_at", "module", "sort_order") VALUES ('milestone:manage', '管理里程碑', '创建/编辑/删除里程碑', '2026-08-21 11:17:17.81769', 'milestone', 2);
INSERT INTO "public"."permission" ("code", "name", "description", "created_at", "module", "sort_order") VALUES ('board:view', '查看看板', '查看看板/列表/表格视图', '2026-08-21 11:17:17.81769', 'board', 1);
INSERT INTO "public"."permission" ("code", "name", "description", "created_at", "module", "sort_order") VALUES ('board:manage', '管理看板', '配置看板列/布局', '2026-08-21 11:17:17.81769', 'board', 2);
INSERT INTO "public"."permission" ("code", "name", "description", "created_at", "module", "sort_order") VALUES ('risk:view', '查看风险', '查看风险列表', '2026-08-21 11:17:17.81769', 'risk', 1);
INSERT INTO "public"."permission" ("code", "name", "description", "created_at", "module", "sort_order") VALUES ('risk:manage', '管理风险', '创建/编辑/删除风险', '2026-08-21 11:17:17.81769', 'risk', 2);
INSERT INTO "public"."permission" ("code", "name", "description", "created_at", "module", "sort_order") VALUES ('project:setting', '项目设置', '修改项目基本信息', '2026-08-21 11:17:17.81769', 'project', 1);
INSERT INTO "public"."permission" ("code", "name", "description", "created_at", "module", "sort_order") VALUES ('project:member', '成员管理', '添加/移除项目成员', '2026-08-21 11:17:17.81769', 'project', 2);
INSERT INTO "public"."permission" ("code", "name", "description", "created_at", "module", "sort_order") VALUES ('project:role', '角色权限设置', '配置角色权限矩阵', '2026-08-21 11:17:17.81769', 'project', 3);
INSERT INTO "public"."permission" ("code", "name", "description", "created_at", "module", "sort_order") VALUES ('project:label', '标签设置', '管理项目标签', '2026-08-21 11:17:17.81769', 'project', 4);
INSERT INTO "public"."permission" ("code", "name", "description", "created_at", "module", "sort_order") VALUES ('project:workflow', '工作流管理', '配置工作流/状态/类型', '2026-08-21 11:17:17.81769', 'project', 5);
INSERT INTO "public"."permission" ("code", "name", "description", "created_at", "module", "sort_order") VALUES ('announcement:manage', '公告管理', '发布/编辑公告', '2026-08-21 11:17:17.81769', 'announcement', 1);
INSERT INTO "public"."permission" ("code", "name", "description", "created_at", "module", "sort_order") VALUES ('issue:archive', '归档工作项', '归档/取消归档工作项', '2026-08-24 13:25:21.63478', 'workitem', 9);
INSERT INTO "public"."permission" ("code", "name", "description", "created_at", "module", "sort_order") VALUES ('risk:create', '创建风险', '新建风险', '2026-08-24 13:25:21.63478', 'risk', 3);
INSERT INTO "public"."permission" ("code", "name", "description", "created_at", "module", "sort_order") VALUES ('risk:edit', '编辑风险', '编辑风险', '2026-08-24 13:25:21.63478', 'risk', 4);
INSERT INTO "public"."permission" ("code", "name", "description", "created_at", "module", "sort_order") VALUES ('risk:delete', '删除风险', '删除风险', '2026-08-24 13:25:21.63478', 'risk', 5);
INSERT INTO "public"."permission" ("code", "name", "description", "created_at", "module", "sort_order") VALUES ('view:manage', '管理视图', '保存/删除自定义视图', '2026-08-24 13:25:21.63478', 'board', 3);
INSERT INTO "public"."permission" ("code", "name", "description", "created_at", "module", "sort_order") VALUES ('project:archive', '归档项目', '归档/取消归档项目', '2026-08-24 13:25:21.63478', 'project', 6);
INSERT INTO "public"."permission" ("code", "name", "description", "created_at", "module", "sort_order") VALUES ('project:delete', '删除项目', '删除项目', '2026-08-24 13:25:21.63478', 'project', 7);
INSERT INTO "public"."permission" ("code", "name", "description", "created_at", "module", "sort_order") VALUES ('project:notify', '通知设置', '配置项目通知', '2026-08-24 13:25:21.63478', 'project', 8);

-- Records of role
INSERT INTO "public"."role" ("id", "code", "name", "description", "sort_order", "created_by", "updated_by", "created_at", "updated_at") VALUES ('00000000-0000-0000-0000-000000000011', 'OWNER', '项目所有者', '项目创建人，拥有全部权限', 1, '00000000-0000-0000-0000-000000000001', '00000000-0000-0000-0000-000000000001', '2026-08-19 09:38:02.958565', '2026-08-19 09:38:02.958565');
INSERT INTO "public"."role" ("id", "code", "name", "description", "sort_order", "created_by", "updated_by", "created_at", "updated_at") VALUES ('00000000-0000-0000-0000-000000000012', 'ADMIN', '管理员', '项目管理员，可管理成员与项目设置', 2, '00000000-0000-0000-0000-000000000001', '00000000-0000-0000-0000-000000000001', '2026-08-19 09:38:02.958565', '2026-08-19 09:38:02.958565');
INSERT INTO "public"."role" ("id", "code", "name", "description", "sort_order", "created_by", "updated_by", "created_at", "updated_at") VALUES ('00000000-0000-0000-0000-000000000013', 'PM', '项目经理', '负责计划与迭代，可管理任务', 3, '00000000-0000-0000-0000-000000000001', '00000000-0000-0000-0000-000000000001', '2026-08-19 09:38:02.958565', '2026-08-19 09:38:02.958565');
INSERT INTO "public"."role" ("id", "code", "name", "description", "sort_order", "created_by", "updated_by", "created_at", "updated_at") VALUES ('00000000-0000-0000-0000-000000000014', 'DEV', '开发', '开发人员，可处理指派任务', 4, '00000000-0000-0000-0000-000000000001', '00000000-0000-0000-0000-000000000001', '2026-08-19 09:38:02.958565', '2026-08-19 09:38:02.958565');
INSERT INTO "public"."role" ("id", "code", "name", "description", "sort_order", "created_by", "updated_by", "created_at", "updated_at") VALUES ('00000000-0000-0000-0000-000000000015', 'QA', '测试', '测试人员，可验证与关闭缺陷', 5, '00000000-0000-0000-0000-000000000001', '00000000-0000-0000-0000-000000000001', '2026-08-19 09:38:02.958565', '2026-08-19 09:38:02.958565');
INSERT INTO "public"."role" ("id", "code", "name", "description", "sort_order", "created_by", "updated_by", "created_at", "updated_at") VALUES ('00000000-0000-0000-0000-000000000016', 'MEMBER', '项目成员', '可参与协作：创建/编辑/分配/拖拽', 6, '00000000-0000-0000-0000-000000000001', '00000000-0000-0000-0000-000000000001', '2026-08-19 09:38:02.958565', '2026-08-19 09:38:02.958565');
INSERT INTO "public"."role" ("id", "code", "name", "description", "sort_order", "created_by", "updated_by", "created_at", "updated_at") VALUES ('00000000-0000-0000-0000-000000000017', 'VIEWER', '访客', '只读访问', 7, '00000000-0000-0000-0000-000000000001', '00000000-0000-0000-0000-000000000001', '2026-08-19 09:38:02.958565', '2026-08-19 09:38:02.958565');

-- Records of menu
INSERT INTO "public"."menu" ("id", "code", "name", "parent_id", "type", "sort_order", "created_at", "updated_at", "created_by", "updated_by") VALUES ('00000000-0000-0000-0000-000000000040', 'RBAC_MANAGE', '权限管理', NULL, 'MENU', 50, '2026-08-20 13:57:01.181057', '2026-08-20 13:57:01.181057', NULL, NULL);
INSERT INTO "public"."menu" ("id", "code", "name", "parent_id", "type", "sort_order", "created_at", "updated_at", "created_by", "updated_by") VALUES ('00000000-0000-0000-0000-000000000041', 'RBAC_ROLE', '角色管理', '00000000-0000-0000-0000-000000000040', 'MENU', 1, '2026-08-20 13:57:01.181057', '2026-08-20 13:57:01.181057', NULL, NULL);
INSERT INTO "public"."menu" ("id", "code", "name", "parent_id", "type", "sort_order", "created_at", "updated_at", "created_by", "updated_by") VALUES ('00000000-0000-0000-0000-000000000042', 'RBAC_MENU', '菜单与权限', '00000000-0000-0000-0000-000000000040', 'MENU', 2, '2026-08-20 13:57:01.181057', '2026-08-20 13:57:01.181057', NULL, NULL);
INSERT INTO "public"."menu" ("id", "code", "name", "parent_id", "type", "sort_order", "created_at", "updated_at", "created_by", "updated_by") VALUES ('00000000-0000-0000-0000-000000000043', 'RBAC_USER_ROLE', '用户角色', '00000000-0000-0000-0000-000000000040', 'MENU', 3, '2026-08-20 13:57:01.181057', '2026-08-20 13:57:01.181057', NULL, NULL);
INSERT INTO "public"."menu" ("id", "code", "name", "parent_id", "type", "sort_order", "created_at", "updated_at", "created_by", "updated_by") VALUES ('00000000-0000-0000-0000-0a0000000001', 'ISSUE_MODULE', '工作项', NULL, 'MENU', 1, '2026-08-24 08:43:06.927708', '2026-08-24 08:43:06.927708', NULL, NULL);
INSERT INTO "public"."menu" ("id", "code", "name", "parent_id", "type", "sort_order", "created_at", "updated_at", "created_by", "updated_by") VALUES ('00000000-0000-0000-0000-0a0000000002', 'RISK_MODULE', '风险管理', NULL, 'MENU', 2, '2026-08-24 08:43:06.927708', '2026-08-24 08:43:06.927708', NULL, NULL);
INSERT INTO "public"."menu" ("id", "code", "name", "parent_id", "type", "sort_order", "created_at", "updated_at", "created_by", "updated_by") VALUES ('00000000-0000-0000-0000-0a0000000003', 'ITERATION_MODULE', '迭代', NULL, 'MENU', 3, '2026-08-24 08:43:06.927708', '2026-08-24 08:43:06.927708', NULL, NULL);
INSERT INTO "public"."menu" ("id", "code", "name", "parent_id", "type", "sort_order", "created_at", "updated_at", "created_by", "updated_by") VALUES ('00000000-0000-0000-0000-0a0000000004', 'PROJECT_MODULE', '项目管理', NULL, 'MENU', 4, '2026-08-24 08:43:06.927708', '2026-08-24 08:43:06.927708', NULL, NULL);
INSERT INTO "public"."menu" ("id", "code", "name", "parent_id", "type", "sort_order", "created_at", "updated_at", "created_by", "updated_by") VALUES ('00000000-0000-0000-0000-000000000022', 'ISSUE_VIEW', '查看任务', '00000000-0000-0000-0000-0a0000000001', 'MENU', 2, '2026-08-19 09:38:02.958565', '2026-08-19 09:38:02.958565', NULL, NULL);
INSERT INTO "public"."menu" ("id", "code", "name", "parent_id", "type", "sort_order", "created_at", "updated_at", "created_by", "updated_by") VALUES ('00000000-0000-0000-0000-000000000021', 'PROJECT_VIEW', '查看项目', '00000000-0000-0000-0000-0a0000000004', 'MENU', 1, '2026-08-19 09:38:02.958565', '2026-08-19 09:38:02.958565', NULL, NULL);

-- Records of role_menu
INSERT INTO "public"."role_menu" ("id", "role_id", "menu_id", "created_at") VALUES ('f8e15b29-4472-4789-bc36-57134b9aa213', '00000000-0000-0000-0000-000000000011', '00000000-0000-0000-0000-000000000041', '2026-08-20 13:57:01.181057');
INSERT INTO "public"."role_menu" ("id", "role_id", "menu_id", "created_at") VALUES ('f2129bf5-9b53-4076-97a9-fd74f3cca9a5', '00000000-0000-0000-0000-000000000012', '00000000-0000-0000-0000-000000000041', '2026-08-20 13:57:01.181057');
INSERT INTO "public"."role_menu" ("id", "role_id", "menu_id", "created_at") VALUES ('1b9f72a5-8dc1-461b-86ef-cc02f486ed74', '00000000-0000-0000-0000-000000000011', '00000000-0000-0000-0000-000000000042', '2026-08-20 13:57:01.181057');
INSERT INTO "public"."role_menu" ("id", "role_id", "menu_id", "created_at") VALUES ('73bc997a-4974-4507-b6eb-1dd3d9100347', '00000000-0000-0000-0000-000000000012', '00000000-0000-0000-0000-000000000042', '2026-08-20 13:57:01.181057');
INSERT INTO "public"."role_menu" ("id", "role_id", "menu_id", "created_at") VALUES ('da16c4ca-b34f-4c68-b0dc-b04b8befe396', '00000000-0000-0000-0000-000000000011', '00000000-0000-0000-0000-000000000043', '2026-08-20 13:57:01.181057');
INSERT INTO "public"."role_menu" ("id", "role_id", "menu_id", "created_at") VALUES ('291a8054-36eb-408b-9277-4a46c7d4d61a', '00000000-0000-0000-0000-000000000012', '00000000-0000-0000-0000-000000000043', '2026-08-20 13:57:01.181057');
INSERT INTO "public"."role_menu" ("id", "role_id", "menu_id", "created_at") VALUES ('9d92a25b-8262-41ea-a9a3-99f2ff440ef2', '00000000-0000-0000-0000-000000000011', '00000000-0000-0000-0000-0a0000000001', '2026-08-24 08:43:06.927708');
INSERT INTO "public"."role_menu" ("id", "role_id", "menu_id", "created_at") VALUES ('2a3aa58a-1c8b-43b1-b310-2f4ce1881e95', '00000000-0000-0000-0000-000000000011', '00000000-0000-0000-0000-0a0000000002', '2026-08-24 08:43:06.927708');
INSERT INTO "public"."role_menu" ("id", "role_id", "menu_id", "created_at") VALUES ('ca5d8c86-dcd9-46ce-a743-7576bb002c4f', '00000000-0000-0000-0000-000000000011', '00000000-0000-0000-0000-0a0000000003', '2026-08-24 08:43:06.927708');
INSERT INTO "public"."role_menu" ("id", "role_id", "menu_id", "created_at") VALUES ('44737cee-3b26-4bca-8fed-fc862c0156d0', '00000000-0000-0000-0000-000000000011', '00000000-0000-0000-0000-0a0000000004', '2026-08-24 08:43:06.927708');
INSERT INTO "public"."role_menu" ("id", "role_id", "menu_id", "created_at") VALUES ('3576c1c8-57f3-4bcd-b2d4-e74e6f4b1101', '00000000-0000-0000-0000-000000000011', '00000000-0000-0000-0000-000000000022', '2026-08-24 08:43:06.927708');
INSERT INTO "public"."role_menu" ("id", "role_id", "menu_id", "created_at") VALUES ('b48b2fec-aeb2-4484-9210-1d5baedccfb0', '00000000-0000-0000-0000-000000000011', '00000000-0000-0000-0000-000000000021', '2026-08-24 08:43:06.927708');
INSERT INTO "public"."role_menu" ("id", "role_id", "menu_id", "created_at") VALUES ('be914c6d-0e75-47c5-8178-c41766c332fa', '00000000-0000-0000-0000-000000000012', '00000000-0000-0000-0000-0a0000000001', '2026-08-24 08:43:06.927708');
INSERT INTO "public"."role_menu" ("id", "role_id", "menu_id", "created_at") VALUES ('49cac09a-5471-466e-af9f-5d75b4a6f214', '00000000-0000-0000-0000-000000000012', '00000000-0000-0000-0000-0a0000000002', '2026-08-24 08:43:06.927708');
INSERT INTO "public"."role_menu" ("id", "role_id", "menu_id", "created_at") VALUES ('2a0c1b22-2881-48e7-9f66-526a616cadbb', '00000000-0000-0000-0000-000000000012', '00000000-0000-0000-0000-0a0000000003', '2026-08-24 08:43:06.927708');
INSERT INTO "public"."role_menu" ("id", "role_id", "menu_id", "created_at") VALUES ('fbf87827-f348-4aa8-999c-53ec8d5878d4', '00000000-0000-0000-0000-000000000012', '00000000-0000-0000-0000-0a0000000004', '2026-08-24 08:43:06.927708');
INSERT INTO "public"."role_menu" ("id", "role_id", "menu_id", "created_at") VALUES ('8cdb4eb7-4178-4b32-806a-f5a1481e5b24', '00000000-0000-0000-0000-000000000012', '00000000-0000-0000-0000-000000000022', '2026-08-24 08:43:06.927708');
INSERT INTO "public"."role_menu" ("id", "role_id", "menu_id", "created_at") VALUES ('c5bfad86-464a-4fd0-90f5-015d1dd71e81', '00000000-0000-0000-0000-000000000012', '00000000-0000-0000-0000-000000000021', '2026-08-24 08:43:06.927708');
INSERT INTO "public"."role_menu" ("id", "role_id", "menu_id", "created_at") VALUES ('bcefce6c-3b6b-4b21-8c26-cdbd16898f19', '00000000-0000-0000-0000-000000000013', '00000000-0000-0000-0000-0a0000000001', '2026-08-24 08:43:06.927708');
INSERT INTO "public"."role_menu" ("id", "role_id", "menu_id", "created_at") VALUES ('5df2bde9-1962-4c76-a49a-02166dc9b5e4', '00000000-0000-0000-0000-000000000013', '00000000-0000-0000-0000-0a0000000002', '2026-08-24 08:43:06.927708');
INSERT INTO "public"."role_menu" ("id", "role_id", "menu_id", "created_at") VALUES ('6ec6ce6a-e326-4da4-bedd-c447625ebc97', '00000000-0000-0000-0000-000000000013', '00000000-0000-0000-0000-0a0000000003', '2026-08-24 08:43:06.927708');
INSERT INTO "public"."role_menu" ("id", "role_id", "menu_id", "created_at") VALUES ('530437c0-3564-41aa-9369-93b155252978', '00000000-0000-0000-0000-000000000013', '00000000-0000-0000-0000-0a0000000004', '2026-08-24 08:43:06.927708');
INSERT INTO "public"."role_menu" ("id", "role_id", "menu_id", "created_at") VALUES ('3e8d8d27-f28c-4f69-9b5d-b04bd52cc6c4', '00000000-0000-0000-0000-000000000013', '00000000-0000-0000-0000-000000000022', '2026-08-24 08:43:06.927708');
INSERT INTO "public"."role_menu" ("id", "role_id", "menu_id", "created_at") VALUES ('8cef0641-0e07-4df6-99b3-67735a634125', '00000000-0000-0000-0000-000000000013', '00000000-0000-0000-0000-000000000021', '2026-08-24 08:43:06.927708');
INSERT INTO "public"."role_menu" ("id", "role_id", "menu_id", "created_at") VALUES ('ef0d784b-0e3e-44ae-a5c1-470a512a0455', '00000000-0000-0000-0000-000000000014', '00000000-0000-0000-0000-0a0000000001', '2026-08-24 08:43:06.927708');
INSERT INTO "public"."role_menu" ("id", "role_id", "menu_id", "created_at") VALUES ('a3c27c3b-d870-41c1-b2ba-0e8cf212b066', '00000000-0000-0000-0000-000000000014', '00000000-0000-0000-0000-0a0000000002', '2026-08-24 08:43:06.927708');
INSERT INTO "public"."role_menu" ("id", "role_id", "menu_id", "created_at") VALUES ('7ce3dc2f-4a84-4895-b65d-07e46a040ef9', '00000000-0000-0000-0000-000000000014', '00000000-0000-0000-0000-0a0000000003', '2026-08-24 08:43:06.927708');
INSERT INTO "public"."role_menu" ("id", "role_id", "menu_id", "created_at") VALUES ('ce86e574-3324-4735-9701-744590694407', '00000000-0000-0000-0000-000000000014', '00000000-0000-0000-0000-0a0000000004', '2026-08-24 08:43:06.927708');
INSERT INTO "public"."role_menu" ("id", "role_id", "menu_id", "created_at") VALUES ('4499da74-6c0c-4622-ad93-2f4e0496af55', '00000000-0000-0000-0000-000000000014', '00000000-0000-0000-0000-000000000022', '2026-08-24 08:43:06.927708');
INSERT INTO "public"."role_menu" ("id", "role_id", "menu_id", "created_at") VALUES ('ab94ff13-fd46-4386-a76c-5184601dca31', '00000000-0000-0000-0000-000000000014', '00000000-0000-0000-0000-000000000021', '2026-08-24 08:43:06.927708');
INSERT INTO "public"."role_menu" ("id", "role_id", "menu_id", "created_at") VALUES ('c76b446b-4c89-45be-8c0b-2b993ebe7b20', '00000000-0000-0000-0000-000000000015', '00000000-0000-0000-0000-0a0000000001', '2026-08-24 08:43:06.927708');
INSERT INTO "public"."role_menu" ("id", "role_id", "menu_id", "created_at") VALUES ('24372e4b-92b8-4b28-9dd4-8ea97b48c17b', '00000000-0000-0000-0000-000000000015', '00000000-0000-0000-0000-0a0000000002', '2026-08-24 08:43:06.927708');
INSERT INTO "public"."role_menu" ("id", "role_id", "menu_id", "created_at") VALUES ('ec3cd155-fb14-43a6-9a47-30699dc7ba5e', '00000000-0000-0000-0000-000000000015', '00000000-0000-0000-0000-0a0000000003', '2026-08-24 08:43:06.927708');
INSERT INTO "public"."role_menu" ("id", "role_id", "menu_id", "created_at") VALUES ('caa15eb8-f4e6-449e-a293-7c9a86417ca9', '00000000-0000-0000-0000-000000000015', '00000000-0000-0000-0000-0a0000000004', '2026-08-24 08:43:06.927708');
INSERT INTO "public"."role_menu" ("id", "role_id", "menu_id", "created_at") VALUES ('20d8477f-0330-4b41-a12f-0285b70ad652', '00000000-0000-0000-0000-000000000015', '00000000-0000-0000-0000-000000000022', '2026-08-24 08:43:06.927708');
INSERT INTO "public"."role_menu" ("id", "role_id", "menu_id", "created_at") VALUES ('e36dfecf-9d9b-4192-8ecb-f13a88c64e34', '00000000-0000-0000-0000-000000000015', '00000000-0000-0000-0000-000000000021', '2026-08-24 08:43:06.927708');
INSERT INTO "public"."role_menu" ("id", "role_id", "menu_id", "created_at") VALUES ('3651ec5d-ded1-426e-acbf-fd45a207b7ec', '00000000-0000-0000-0000-000000000017', '00000000-0000-0000-0000-0a0000000001', '2026-08-24 08:43:06.927708');
INSERT INTO "public"."role_menu" ("id", "role_id", "menu_id", "created_at") VALUES ('62eb7c39-28a6-45af-a697-06969757843c', '00000000-0000-0000-0000-000000000017', '00000000-0000-0000-0000-0a0000000002', '2026-08-24 08:43:06.927708');
INSERT INTO "public"."role_menu" ("id", "role_id", "menu_id", "created_at") VALUES ('775928cc-9227-48b8-b532-39eeab0d0a15', '00000000-0000-0000-0000-000000000017', '00000000-0000-0000-0000-0a0000000003', '2026-08-24 08:43:06.927708');
INSERT INTO "public"."role_menu" ("id", "role_id", "menu_id", "created_at") VALUES ('8a843f31-2ed6-4a82-b87f-94cbd9383f60', '00000000-0000-0000-0000-000000000017', '00000000-0000-0000-0000-0a0000000004', '2026-08-24 08:43:06.927708');
INSERT INTO "public"."role_menu" ("id", "role_id", "menu_id", "created_at") VALUES ('f600f948-759c-4fa8-a0e0-943ef987a473', '00000000-0000-0000-0000-000000000017', '00000000-0000-0000-0000-000000000022', '2026-08-24 08:43:06.927708');
INSERT INTO "public"."role_menu" ("id", "role_id", "menu_id", "created_at") VALUES ('fe8e5753-1905-455c-8418-c6ec6223ace6', '00000000-0000-0000-0000-000000000017', '00000000-0000-0000-0000-000000000021', '2026-08-24 08:43:06.927708');

-- Records of role_permission
INSERT INTO "public"."role_permission" ("id", "role_id", "permission_code", "created_at") VALUES ('6f6e2a88-8f8c-4f45-871b-b921a3f2051c', '00000000-0000-0000-0000-000000000011', 'issue:view', '2026-08-21 11:17:17.81769');
INSERT INTO "public"."role_permission" ("id", "role_id", "permission_code", "created_at") VALUES ('70825616-aff9-48ce-96ef-e36b5d70bf33', '00000000-0000-0000-0000-000000000011', 'issue:create', '2026-08-21 11:17:17.81769');
INSERT INTO "public"."role_permission" ("id", "role_id", "permission_code", "created_at") VALUES ('c38a2d16-e32b-475c-b76a-7cf623c05cb8', '00000000-0000-0000-0000-000000000011', 'issue:edit', '2026-08-21 11:17:17.81769');
INSERT INTO "public"."role_permission" ("id", "role_id", "permission_code", "created_at") VALUES ('f0756497-775a-49d4-bf67-3d0d4ddf09b5', '00000000-0000-0000-0000-000000000011', 'issue:delete', '2026-08-21 11:17:17.81769');
INSERT INTO "public"."role_permission" ("id", "role_id", "permission_code", "created_at") VALUES ('5d5284c8-b488-4f24-a717-1a82d04ecf85', '00000000-0000-0000-0000-000000000011', 'issue:comment', '2026-08-21 11:17:17.81769');
INSERT INTO "public"."role_permission" ("id", "role_id", "permission_code", "created_at") VALUES ('560ea4de-24af-4d04-9487-dbd079c3da65', '00000000-0000-0000-0000-000000000011', 'issue:status', '2026-08-21 11:17:17.81769');
INSERT INTO "public"."role_permission" ("id", "role_id", "permission_code", "created_at") VALUES ('ecc4afb4-5b88-41bc-bd14-b690efc6a298', '00000000-0000-0000-0000-000000000011', 'issue:assign', '2026-08-21 11:17:17.81769');
INSERT INTO "public"."role_permission" ("id", "role_id", "permission_code", "created_at") VALUES ('c0524c15-a3fb-4317-a289-827a1024d585', '00000000-0000-0000-0000-000000000011', 'issue:export', '2026-08-21 11:17:17.81769');
INSERT INTO "public"."role_permission" ("id", "role_id", "permission_code", "created_at") VALUES ('d789691d-e046-42d1-9b16-1a444e189586', '00000000-0000-0000-0000-000000000011', 'sprint:view', '2026-08-21 11:17:17.81769');
INSERT INTO "public"."role_permission" ("id", "role_id", "permission_code", "created_at") VALUES ('c4ac2bc0-34ee-4675-8cc7-db49f4c6cc7a', '00000000-0000-0000-0000-000000000011', 'sprint:manage', '2026-08-21 11:17:17.81769');
INSERT INTO "public"."role_permission" ("id", "role_id", "permission_code", "created_at") VALUES ('6d9e8556-827a-4143-8330-840e864c5648', '00000000-0000-0000-0000-000000000011', 'milestone:view', '2026-08-21 11:17:17.81769');
INSERT INTO "public"."role_permission" ("id", "role_id", "permission_code", "created_at") VALUES ('05abc5cd-ff74-42e3-b329-9c500df85a5c', '00000000-0000-0000-0000-000000000011', 'milestone:manage', '2026-08-21 11:17:17.81769');
INSERT INTO "public"."role_permission" ("id", "role_id", "permission_code", "created_at") VALUES ('af8121bc-d566-42c0-ae2d-0f32d3b9bab1', '00000000-0000-0000-0000-000000000011', 'board:view', '2026-08-21 11:17:17.81769');
INSERT INTO "public"."role_permission" ("id", "role_id", "permission_code", "created_at") VALUES ('d3ddc024-2a8e-44de-9fcc-fdbc3db0cc44', '00000000-0000-0000-0000-000000000011', 'board:manage', '2026-08-21 11:17:17.81769');
INSERT INTO "public"."role_permission" ("id", "role_id", "permission_code", "created_at") VALUES ('28e543f7-ba7c-4548-90ab-709ca838922b', '00000000-0000-0000-0000-000000000011', 'risk:view', '2026-08-21 11:17:17.81769');
INSERT INTO "public"."role_permission" ("id", "role_id", "permission_code", "created_at") VALUES ('b016fc91-e792-4b55-99f0-955072925357', '00000000-0000-0000-0000-000000000011', 'risk:manage', '2026-08-21 11:17:17.81769');
INSERT INTO "public"."role_permission" ("id", "role_id", "permission_code", "created_at") VALUES ('c05420fa-2640-4cd3-afd0-d01d373d8e24', '00000000-0000-0000-0000-000000000011', 'project:setting', '2026-08-21 11:17:17.81769');
INSERT INTO "public"."role_permission" ("id", "role_id", "permission_code", "created_at") VALUES ('247c4937-e376-4b56-b04d-32414075b938', '00000000-0000-0000-0000-000000000011', 'project:member', '2026-08-21 11:17:17.81769');
INSERT INTO "public"."role_permission" ("id", "role_id", "permission_code", "created_at") VALUES ('beafcd87-dd9b-4fb5-9fc9-69fe25cc10ed', '00000000-0000-0000-0000-000000000011', 'project:role', '2026-08-21 11:17:17.81769');
INSERT INTO "public"."role_permission" ("id", "role_id", "permission_code", "created_at") VALUES ('6e84ce1f-05cf-450e-8b81-a57ceb28d720', '00000000-0000-0000-0000-000000000011', 'project:label', '2026-08-21 11:17:17.81769');
INSERT INTO "public"."role_permission" ("id", "role_id", "permission_code", "created_at") VALUES ('7c6ecbc6-a5ae-4bfc-993a-abc44614c292', '00000000-0000-0000-0000-000000000011', 'project:workflow', '2026-08-21 11:17:17.81769');
INSERT INTO "public"."role_permission" ("id", "role_id", "permission_code", "created_at") VALUES ('0d34a250-2e0c-471c-a060-1a1a7abb661c', '00000000-0000-0000-0000-000000000011', 'announcement:manage', '2026-08-21 11:17:17.81769');
INSERT INTO "public"."role_permission" ("id", "role_id", "permission_code", "created_at") VALUES ('8064b1eb-133c-477f-9597-6e122f513ad9', '00000000-0000-0000-0000-000000000012', 'issue:view', '2026-08-21 11:17:17.81769');
INSERT INTO "public"."role_permission" ("id", "role_id", "permission_code", "created_at") VALUES ('ed930ab9-2fc2-468b-86f8-8b0c86068bb7', '00000000-0000-0000-0000-000000000012', 'issue:create', '2026-08-21 11:17:17.81769');
INSERT INTO "public"."role_permission" ("id", "role_id", "permission_code", "created_at") VALUES ('be4583f3-f0b6-434f-9452-695a03f5f3c1', '00000000-0000-0000-0000-000000000012', 'issue:edit', '2026-08-21 11:17:17.81769');
INSERT INTO "public"."role_permission" ("id", "role_id", "permission_code", "created_at") VALUES ('ca166d55-7827-43ed-8828-6a9652185f96', '00000000-0000-0000-0000-000000000012', 'issue:delete', '2026-08-21 11:17:17.81769');
INSERT INTO "public"."role_permission" ("id", "role_id", "permission_code", "created_at") VALUES ('49788e9a-cb1e-4bd1-923e-b5d64cac1933', '00000000-0000-0000-0000-000000000012', 'issue:comment', '2026-08-21 11:17:17.81769');
INSERT INTO "public"."role_permission" ("id", "role_id", "permission_code", "created_at") VALUES ('47fc1994-4906-4f91-b849-4970eeb7eac4', '00000000-0000-0000-0000-000000000012', 'issue:status', '2026-08-21 11:17:17.81769');
INSERT INTO "public"."role_permission" ("id", "role_id", "permission_code", "created_at") VALUES ('0be79749-2724-4ac1-9ecd-fca13d0e7ee3', '00000000-0000-0000-0000-000000000012', 'issue:assign', '2026-08-21 11:17:17.81769');
INSERT INTO "public"."role_permission" ("id", "role_id", "permission_code", "created_at") VALUES ('ed46cbb4-e152-4b58-8dbd-d859b88d61af', '00000000-0000-0000-0000-000000000012', 'issue:export', '2026-08-21 11:17:17.81769');
INSERT INTO "public"."role_permission" ("id", "role_id", "permission_code", "created_at") VALUES ('9f8c2bdf-1f37-4fc2-9976-168562d3aed4', '00000000-0000-0000-0000-000000000012', 'sprint:view', '2026-08-21 11:17:17.81769');
INSERT INTO "public"."role_permission" ("id", "role_id", "permission_code", "created_at") VALUES ('8aa7e67c-d0d4-4531-b1c6-1a8e615aa69a', '00000000-0000-0000-0000-000000000012', 'sprint:manage', '2026-08-21 11:17:17.81769');
INSERT INTO "public"."role_permission" ("id", "role_id", "permission_code", "created_at") VALUES ('d8323daf-0bbb-40b4-b21f-76fffb0aead0', '00000000-0000-0000-0000-000000000012', 'milestone:view', '2026-08-21 11:17:17.81769');
INSERT INTO "public"."role_permission" ("id", "role_id", "permission_code", "created_at") VALUES ('57fdd4a7-b8ce-4cf0-af05-acba29c2d356', '00000000-0000-0000-0000-000000000012', 'milestone:manage', '2026-08-21 11:17:17.81769');
INSERT INTO "public"."role_permission" ("id", "role_id", "permission_code", "created_at") VALUES ('54780932-a910-4bfb-be67-112dfd716953', '00000000-0000-0000-0000-000000000012', 'board:view', '2026-08-21 11:17:17.81769');
INSERT INTO "public"."role_permission" ("id", "role_id", "permission_code", "created_at") VALUES ('65a28397-3b62-46ba-acab-f3fec1105a89', '00000000-0000-0000-0000-000000000012', 'board:manage', '2026-08-21 11:17:17.81769');
INSERT INTO "public"."role_permission" ("id", "role_id", "permission_code", "created_at") VALUES ('35f645b0-0ed6-44a1-90c2-d027d8b07a04', '00000000-0000-0000-0000-000000000012', 'risk:view', '2026-08-21 11:17:17.81769');
INSERT INTO "public"."role_permission" ("id", "role_id", "permission_code", "created_at") VALUES ('4d570cd0-c200-4b12-8adf-6d2f94c8ffce', '00000000-0000-0000-0000-000000000012', 'risk:manage', '2026-08-21 11:17:17.81769');
INSERT INTO "public"."role_permission" ("id", "role_id", "permission_code", "created_at") VALUES ('486e269f-1a5c-4248-9b8f-e6ede2cd5901', '00000000-0000-0000-0000-000000000012', 'project:setting', '2026-08-21 11:17:17.81769');
INSERT INTO "public"."role_permission" ("id", "role_id", "permission_code", "created_at") VALUES ('f9a87afe-348e-4fd8-8e9a-029e778f8e1d', '00000000-0000-0000-0000-000000000012', 'project:member', '2026-08-21 11:17:17.81769');
INSERT INTO "public"."role_permission" ("id", "role_id", "permission_code", "created_at") VALUES ('a49ae631-1128-4c8d-a924-7a31db0180f6', '00000000-0000-0000-0000-000000000012', 'project:role', '2026-08-21 11:17:17.81769');
INSERT INTO "public"."role_permission" ("id", "role_id", "permission_code", "created_at") VALUES ('87750434-1b27-4af8-a495-d692906fb0d1', '00000000-0000-0000-0000-000000000012', 'project:label', '2026-08-21 11:17:17.81769');
INSERT INTO "public"."role_permission" ("id", "role_id", "permission_code", "created_at") VALUES ('2db72fe2-f0c9-4537-b324-28665814bc69', '00000000-0000-0000-0000-000000000012', 'project:workflow', '2026-08-21 11:17:17.81769');
INSERT INTO "public"."role_permission" ("id", "role_id", "permission_code", "created_at") VALUES ('7f2c63d5-ad7f-4c76-befd-894e353cc05e', '00000000-0000-0000-0000-000000000012', 'announcement:manage', '2026-08-21 11:17:17.81769');
INSERT INTO "public"."role_permission" ("id", "role_id", "permission_code", "created_at") VALUES ('6f29960f-162c-4ec1-8dac-ccbed530c822', '00000000-0000-0000-0000-000000000013', 'issue:view', '2026-08-21 11:17:17.81769');
INSERT INTO "public"."role_permission" ("id", "role_id", "permission_code", "created_at") VALUES ('caac6fae-dee8-4eb5-98be-ba604ede812e', '00000000-0000-0000-0000-000000000013', 'issue:create', '2026-08-21 11:17:17.81769');
INSERT INTO "public"."role_permission" ("id", "role_id", "permission_code", "created_at") VALUES ('76dde4da-28f7-4ba8-8205-a6ca4b0fdff1', '00000000-0000-0000-0000-000000000013', 'issue:edit', '2026-08-21 11:17:17.81769');
INSERT INTO "public"."role_permission" ("id", "role_id", "permission_code", "created_at") VALUES ('4cae7f3c-ee22-4b4f-b159-3c288dba0dc8', '00000000-0000-0000-0000-000000000013', 'issue:delete', '2026-08-21 11:17:17.81769');
INSERT INTO "public"."role_permission" ("id", "role_id", "permission_code", "created_at") VALUES ('b75a7736-3f24-48ff-81d5-1410f16b9d28', '00000000-0000-0000-0000-000000000013', 'issue:comment', '2026-08-21 11:17:17.81769');
INSERT INTO "public"."role_permission" ("id", "role_id", "permission_code", "created_at") VALUES ('ace72337-59dd-48bf-a162-cd49b78389a8', '00000000-0000-0000-0000-000000000013', 'issue:status', '2026-08-21 11:17:17.81769');
INSERT INTO "public"."role_permission" ("id", "role_id", "permission_code", "created_at") VALUES ('455203a3-b0b4-4a5f-b450-896bd28b1fe3', '00000000-0000-0000-0000-000000000013', 'issue:assign', '2026-08-21 11:17:17.81769');
INSERT INTO "public"."role_permission" ("id", "role_id", "permission_code", "created_at") VALUES ('74fb01ad-c449-40c0-8b51-e231eddb2b1d', '00000000-0000-0000-0000-000000000013', 'issue:export', '2026-08-21 11:17:17.81769');
INSERT INTO "public"."role_permission" ("id", "role_id", "permission_code", "created_at") VALUES ('99b65f13-3de4-4a57-b5f5-c8d4c86aa0d5', '00000000-0000-0000-0000-000000000013', 'sprint:view', '2026-08-21 11:17:17.81769');
INSERT INTO "public"."role_permission" ("id", "role_id", "permission_code", "created_at") VALUES ('db271d39-392f-401e-ba6f-d4ac6d86bd16', '00000000-0000-0000-0000-000000000013', 'sprint:manage', '2026-08-21 11:17:17.81769');
INSERT INTO "public"."role_permission" ("id", "role_id", "permission_code", "created_at") VALUES ('cc95c219-562a-48ef-9041-98f9001efdb7', '00000000-0000-0000-0000-000000000013', 'milestone:view', '2026-08-21 11:17:17.81769');
INSERT INTO "public"."role_permission" ("id", "role_id", "permission_code", "created_at") VALUES ('1a86a827-1c24-4b21-9772-75b2933bd590', '00000000-0000-0000-0000-000000000013', 'milestone:manage', '2026-08-21 11:17:17.81769');
INSERT INTO "public"."role_permission" ("id", "role_id", "permission_code", "created_at") VALUES ('857ea775-36f1-4aae-8c5a-8a370e6812b3', '00000000-0000-0000-0000-000000000013', 'board:view', '2026-08-21 11:17:17.81769');
INSERT INTO "public"."role_permission" ("id", "role_id", "permission_code", "created_at") VALUES ('9a582dee-42ed-4c60-8a9d-e8911f49b5e5', '00000000-0000-0000-0000-000000000013', 'board:manage', '2026-08-21 11:17:17.81769');
INSERT INTO "public"."role_permission" ("id", "role_id", "permission_code", "created_at") VALUES ('94690614-be59-4b74-86ac-a53fe3bd5fc4', '00000000-0000-0000-0000-000000000013', 'risk:view', '2026-08-21 11:17:17.81769');
INSERT INTO "public"."role_permission" ("id", "role_id", "permission_code", "created_at") VALUES ('4b3d29df-b2c5-426d-afef-9f5e77dd6903', '00000000-0000-0000-0000-000000000013', 'risk:manage', '2026-08-21 11:17:17.81769');
INSERT INTO "public"."role_permission" ("id", "role_id", "permission_code", "created_at") VALUES ('7133146a-d197-4fe8-a805-d809df53e4c6', '00000000-0000-0000-0000-000000000013', 'project:setting', '2026-08-21 11:17:17.81769');
INSERT INTO "public"."role_permission" ("id", "role_id", "permission_code", "created_at") VALUES ('93e7404e-e767-4376-a2a3-a061b273f5f8', '00000000-0000-0000-0000-000000000013', 'project:member', '2026-08-21 11:17:17.81769');
INSERT INTO "public"."role_permission" ("id", "role_id", "permission_code", "created_at") VALUES ('d1a8d5e0-15df-4969-a54a-2d08968d14b6', '00000000-0000-0000-0000-000000000013', 'project:label', '2026-08-21 11:17:17.81769');
INSERT INTO "public"."role_permission" ("id", "role_id", "permission_code", "created_at") VALUES ('40631a27-693d-4bba-ad70-fec1a9921e66', '00000000-0000-0000-0000-000000000013', 'project:workflow', '2026-08-21 11:17:17.81769');
INSERT INTO "public"."role_permission" ("id", "role_id", "permission_code", "created_at") VALUES ('8098fbbd-5de5-44b0-9d12-eb0022162f5d', '00000000-0000-0000-0000-000000000013', 'announcement:manage', '2026-08-21 11:17:17.81769');
INSERT INTO "public"."role_permission" ("id", "role_id", "permission_code", "created_at") VALUES ('96e8e308-4a11-480d-8dff-924c3de294cf', '00000000-0000-0000-0000-000000000014', 'issue:view', '2026-08-21 11:17:17.81769');
INSERT INTO "public"."role_permission" ("id", "role_id", "permission_code", "created_at") VALUES ('c3c91f1d-30a9-463e-9677-74d94f368f35', '00000000-0000-0000-0000-000000000014', 'issue:create', '2026-08-21 11:17:17.81769');
INSERT INTO "public"."role_permission" ("id", "role_id", "permission_code", "created_at") VALUES ('506a4f04-2033-4248-8623-435f12b0f2b2', '00000000-0000-0000-0000-000000000014', 'issue:edit', '2026-08-21 11:17:17.81769');
INSERT INTO "public"."role_permission" ("id", "role_id", "permission_code", "created_at") VALUES ('3db82615-ccb1-430c-8fbd-97e7609c2131', '00000000-0000-0000-0000-000000000014', 'issue:delete', '2026-08-21 11:17:17.81769');
INSERT INTO "public"."role_permission" ("id", "role_id", "permission_code", "created_at") VALUES ('a463ebb9-57de-4522-96e6-9df7bce50f8e', '00000000-0000-0000-0000-000000000014', 'issue:comment', '2026-08-21 11:17:17.81769');
INSERT INTO "public"."role_permission" ("id", "role_id", "permission_code", "created_at") VALUES ('c18fca5b-9c3d-4995-b42a-a092c1b55be8', '00000000-0000-0000-0000-000000000014', 'issue:status', '2026-08-21 11:17:17.81769');
INSERT INTO "public"."role_permission" ("id", "role_id", "permission_code", "created_at") VALUES ('b2535f01-de98-4f00-90eb-64a86ea3fb85', '00000000-0000-0000-0000-000000000014', 'issue:assign', '2026-08-21 11:17:17.81769');
INSERT INTO "public"."role_permission" ("id", "role_id", "permission_code", "created_at") VALUES ('3b084caf-e92c-4ef8-bd57-e03402f12c10', '00000000-0000-0000-0000-000000000014', 'issue:export', '2026-08-21 11:17:17.81769');
INSERT INTO "public"."role_permission" ("id", "role_id", "permission_code", "created_at") VALUES ('0d6a947d-23d2-4b58-9672-0d8012d12d5e', '00000000-0000-0000-0000-000000000014', 'sprint:view', '2026-08-21 11:17:17.81769');
INSERT INTO "public"."role_permission" ("id", "role_id", "permission_code", "created_at") VALUES ('4be3e9cc-28f2-45c3-8cf6-e842e1dbb23f', '00000000-0000-0000-0000-000000000014', 'milestone:view', '2026-08-21 11:17:17.81769');
INSERT INTO "public"."role_permission" ("id", "role_id", "permission_code", "created_at") VALUES ('341cdadf-b23d-41ac-8449-55cff1a3425a', '00000000-0000-0000-0000-000000000014', 'board:view', '2026-08-21 11:17:17.81769');
INSERT INTO "public"."role_permission" ("id", "role_id", "permission_code", "created_at") VALUES ('eab0f6b8-2d61-4fdc-afac-498e9996469b', '00000000-0000-0000-0000-000000000014', 'board:manage', '2026-08-21 11:17:17.81769');
INSERT INTO "public"."role_permission" ("id", "role_id", "permission_code", "created_at") VALUES ('e582768c-021d-450d-aceb-fd21e13ba3f2', '00000000-0000-0000-0000-000000000014', 'risk:view', '2026-08-21 11:17:17.81769');
INSERT INTO "public"."role_permission" ("id", "role_id", "permission_code", "created_at") VALUES ('2e9772ba-2310-411d-91d4-c4492da33ce9', '00000000-0000-0000-0000-000000000015', 'issue:view', '2026-08-21 11:17:17.81769');
INSERT INTO "public"."role_permission" ("id", "role_id", "permission_code", "created_at") VALUES ('d0f704a5-560d-4e39-9e21-21e0ba11158f', '00000000-0000-0000-0000-000000000015', 'issue:create', '2026-08-21 11:17:17.81769');
INSERT INTO "public"."role_permission" ("id", "role_id", "permission_code", "created_at") VALUES ('478e7cea-b3b3-45ec-9321-bbc82c3f8f81', '00000000-0000-0000-0000-000000000015', 'issue:edit', '2026-08-21 11:17:17.81769');
INSERT INTO "public"."role_permission" ("id", "role_id", "permission_code", "created_at") VALUES ('d7044c41-6039-4f4f-8834-d0ed9d463c46', '00000000-0000-0000-0000-000000000015', 'issue:delete', '2026-08-21 11:17:17.81769');
INSERT INTO "public"."role_permission" ("id", "role_id", "permission_code", "created_at") VALUES ('0b80fa3b-2376-44c1-b3bd-b21071478485', '00000000-0000-0000-0000-000000000015', 'issue:comment', '2026-08-21 11:17:17.81769');
INSERT INTO "public"."role_permission" ("id", "role_id", "permission_code", "created_at") VALUES ('4a750042-be73-4f42-bf45-a5939afaf10f', '00000000-0000-0000-0000-000000000015', 'issue:status', '2026-08-21 11:17:17.81769');
INSERT INTO "public"."role_permission" ("id", "role_id", "permission_code", "created_at") VALUES ('5adfbf5b-1c09-49c1-832d-a5ab3f723751', '00000000-0000-0000-0000-000000000015', 'issue:assign', '2026-08-21 11:17:17.81769');
INSERT INTO "public"."role_permission" ("id", "role_id", "permission_code", "created_at") VALUES ('ad5220ed-f031-4c3c-92cf-a2a80a100ed0', '00000000-0000-0000-0000-000000000015', 'issue:export', '2026-08-21 11:17:17.81769');
INSERT INTO "public"."role_permission" ("id", "role_id", "permission_code", "created_at") VALUES ('ef7af10e-c5c4-4cab-836c-9725073f6a7d', '00000000-0000-0000-0000-000000000015', 'sprint:view', '2026-08-21 11:17:17.81769');
INSERT INTO "public"."role_permission" ("id", "role_id", "permission_code", "created_at") VALUES ('077c8db4-4cb2-407b-8611-f6f93885a1a9', '00000000-0000-0000-0000-000000000015', 'milestone:view', '2026-08-21 11:17:17.81769');
INSERT INTO "public"."role_permission" ("id", "role_id", "permission_code", "created_at") VALUES ('f850c045-792f-4c40-853d-6ed687e71662', '00000000-0000-0000-0000-000000000015', 'board:view', '2026-08-21 11:17:17.81769');
INSERT INTO "public"."role_permission" ("id", "role_id", "permission_code", "created_at") VALUES ('1b6f2f4d-2efc-4610-88c7-b452e0e63835', '00000000-0000-0000-0000-000000000015', 'board:manage', '2026-08-21 11:17:17.81769');
INSERT INTO "public"."role_permission" ("id", "role_id", "permission_code", "created_at") VALUES ('095d15d8-3058-4c79-ba90-51dc3ce2af78', '00000000-0000-0000-0000-000000000015', 'risk:view', '2026-08-21 11:17:17.81769');
INSERT INTO "public"."role_permission" ("id", "role_id", "permission_code", "created_at") VALUES ('a4b3d208-7303-47e1-9ff5-89441b92e513', '00000000-0000-0000-0000-000000000017', 'issue:view', '2026-08-21 11:17:17.81769');
INSERT INTO "public"."role_permission" ("id", "role_id", "permission_code", "created_at") VALUES ('e7620baa-5e18-4cf2-ac74-68671d9158f6', '00000000-0000-0000-0000-000000000017', 'sprint:view', '2026-08-21 11:17:17.81769');
INSERT INTO "public"."role_permission" ("id", "role_id", "permission_code", "created_at") VALUES ('2038b08a-bdd7-4fe0-85ab-53bc5a7e3b30', '00000000-0000-0000-0000-000000000017', 'milestone:view', '2026-08-21 11:17:17.81769');
INSERT INTO "public"."role_permission" ("id", "role_id", "permission_code", "created_at") VALUES ('2622e42c-6f4d-4fbf-80bf-e875567a7964', '00000000-0000-0000-0000-000000000017', 'board:view', '2026-08-21 11:17:17.81769');
INSERT INTO "public"."role_permission" ("id", "role_id", "permission_code", "created_at") VALUES ('c958e7c9-416c-4259-a7e7-3a02e6370658', '00000000-0000-0000-0000-000000000017', 'risk:view', '2026-08-21 11:17:17.81769');
INSERT INTO "public"."role_permission" ("id", "role_id", "permission_code", "created_at") VALUES ('908bcaaa-a578-458d-bb8b-6d9404381110', '00000000-0000-0000-0000-000000000011', 'issue:archive', '2026-08-24 13:25:21.63478');
INSERT INTO "public"."role_permission" ("id", "role_id", "permission_code", "created_at") VALUES ('0a042277-87ae-4469-8f88-afb0e0b587f5', '00000000-0000-0000-0000-000000000011', 'project:archive', '2026-08-24 13:25:21.63478');
INSERT INTO "public"."role_permission" ("id", "role_id", "permission_code", "created_at") VALUES ('e260bf47-5b95-4fbc-8b43-c8e6475b02cd', '00000000-0000-0000-0000-000000000011', 'project:delete', '2026-08-24 13:25:21.63478');
INSERT INTO "public"."role_permission" ("id", "role_id", "permission_code", "created_at") VALUES ('c558c9e6-d9c0-44eb-8582-63a5ab7631c5', '00000000-0000-0000-0000-000000000011', 'project:notify', '2026-08-24 13:25:21.63478');
INSERT INTO "public"."role_permission" ("id", "role_id", "permission_code", "created_at") VALUES ('47b81711-a0c7-43a3-92ae-ac2e7956b724', '00000000-0000-0000-0000-000000000011', 'risk:create', '2026-08-24 13:25:21.63478');
INSERT INTO "public"."role_permission" ("id", "role_id", "permission_code", "created_at") VALUES ('5149836c-2d3a-4286-9134-7d070077d0a3', '00000000-0000-0000-0000-000000000011', 'risk:delete', '2026-08-24 13:25:21.63478');
INSERT INTO "public"."role_permission" ("id", "role_id", "permission_code", "created_at") VALUES ('7f4f0dd7-ab90-42ed-8a69-af2a238bd624', '00000000-0000-0000-0000-000000000011', 'risk:edit', '2026-08-24 13:25:21.63478');
INSERT INTO "public"."role_permission" ("id", "role_id", "permission_code", "created_at") VALUES ('4aea1a6b-454c-48c6-9ec0-de39e06b4556', '00000000-0000-0000-0000-000000000011', 'view:manage', '2026-08-24 13:25:21.63478');
INSERT INTO "public"."role_permission" ("id", "role_id", "permission_code", "created_at") VALUES ('504a0c6e-c37f-43b5-b6c6-b9512c3a0ede', '00000000-0000-0000-0000-000000000012', 'issue:archive', '2026-08-24 13:25:21.63478');
INSERT INTO "public"."role_permission" ("id", "role_id", "permission_code", "created_at") VALUES ('b34aa135-681e-43fc-baf1-1c37bae16f21', '00000000-0000-0000-0000-000000000012', 'project:archive', '2026-08-24 13:25:21.63478');
INSERT INTO "public"."role_permission" ("id", "role_id", "permission_code", "created_at") VALUES ('e8d43f6d-f672-49ca-a55b-21aa7cb356a6', '00000000-0000-0000-0000-000000000012', 'project:delete', '2026-08-24 13:25:21.63478');
INSERT INTO "public"."role_permission" ("id", "role_id", "permission_code", "created_at") VALUES ('df8d8f28-0681-485a-8de0-735a6decf3ca', '00000000-0000-0000-0000-000000000012', 'project:notify', '2026-08-24 13:25:21.63478');
INSERT INTO "public"."role_permission" ("id", "role_id", "permission_code", "created_at") VALUES ('f212108b-0ff1-49f7-8a38-32a746a43e7a', '00000000-0000-0000-0000-000000000012', 'risk:create', '2026-08-24 13:25:21.63478');
INSERT INTO "public"."role_permission" ("id", "role_id", "permission_code", "created_at") VALUES ('60cae18d-3f0e-49ff-addc-eabb5f11b020', '00000000-0000-0000-0000-000000000012', 'risk:delete', '2026-08-24 13:25:21.63478');
INSERT INTO "public"."role_permission" ("id", "role_id", "permission_code", "created_at") VALUES ('d68faa9b-d4fb-49d6-b376-a0f7709543ee', '00000000-0000-0000-0000-000000000012', 'risk:edit', '2026-08-24 13:25:21.63478');
INSERT INTO "public"."role_permission" ("id", "role_id", "permission_code", "created_at") VALUES ('0cdb0e1d-b7e2-4fe8-bd86-56242f2ea505', '00000000-0000-0000-0000-000000000012', 'view:manage', '2026-08-24 13:25:21.63478');
INSERT INTO "public"."role_permission" ("id", "role_id", "permission_code", "created_at") VALUES ('1b962096-242f-4409-8301-636a5a104808', '00000000-0000-0000-0000-000000000013', 'issue:archive', '2026-08-24 13:25:21.63478');
INSERT INTO "public"."role_permission" ("id", "role_id", "permission_code", "created_at") VALUES ('a1b553cb-3a5d-43f4-a42c-8bf65ec1afc3', '00000000-0000-0000-0000-000000000013', 'view:manage', '2026-08-24 13:25:21.63478');
INSERT INTO "public"."role_permission" ("id", "role_id", "permission_code", "created_at") VALUES ('79b98dfc-c3c7-45cf-86dc-68732be09c4a', '00000000-0000-0000-0000-000000000014', 'risk:manage', '2026-08-24 13:25:21.63478');
INSERT INTO "public"."role_permission" ("id", "role_id", "permission_code", "created_at") VALUES ('c2597d1b-d16b-4153-909b-1665bffe960d', '00000000-0000-0000-0000-000000000014', 'sprint:manage', '2026-08-24 13:25:21.63478');
INSERT INTO "public"."role_permission" ("id", "role_id", "permission_code", "created_at") VALUES ('54e3667f-ad30-4639-875e-dfd415e04f0b', '00000000-0000-0000-0000-000000000015', 'issue:archive', '2026-08-24 13:25:21.63478');
INSERT INTO "public"."role_permission" ("id", "role_id", "permission_code", "created_at") VALUES ('0dd886bb-e2ad-455c-8116-756902f1502f', '00000000-0000-0000-0000-000000000015', 'risk:manage', '2026-08-24 13:25:21.63478');
INSERT INTO "public"."role_permission" ("id", "role_id", "permission_code", "created_at") VALUES ('f1032e9f-e82e-4c60-96ba-fa5edbf3d7b1', '00000000-0000-0000-0000-000000000015', 'sprint:manage', '2026-08-24 13:25:21.63478');
INSERT INTO "public"."role_permission" ("id", "role_id", "permission_code", "created_at") VALUES ('9b027384-8965-455c-99df-f867301ffdbe', '00000000-0000-0000-0000-000000000016', 'issue:view', '2026-10-21 08:56:49.776045');
INSERT INTO "public"."role_permission" ("id", "role_id", "permission_code", "created_at") VALUES ('f9eeb34c-227c-4036-862f-e81277feb733', '00000000-0000-0000-0000-000000000016', 'issue:create', '2026-10-21 08:56:49.776045');
INSERT INTO "public"."role_permission" ("id", "role_id", "permission_code", "created_at") VALUES ('4cd95351-ffde-46fa-bd0f-3be74985141d', '00000000-0000-0000-0000-000000000016', 'issue:edit', '2026-10-21 08:56:49.776045');
INSERT INTO "public"."role_permission" ("id", "role_id", "permission_code", "created_at") VALUES ('741e7b7c-d6db-4856-8347-517a5e5e9bbe', '00000000-0000-0000-0000-000000000016', 'issue:delete', '2026-10-21 08:56:49.776045');
INSERT INTO "public"."role_permission" ("id", "role_id", "permission_code", "created_at") VALUES ('e0722e2d-39dc-4863-ac76-daa916068cbb', '00000000-0000-0000-0000-000000000016', 'issue:comment', '2026-10-21 08:56:49.776045');
INSERT INTO "public"."role_permission" ("id", "role_id", "permission_code", "created_at") VALUES ('dfc30e99-81e1-4071-8112-5eabe57d87b9', '00000000-0000-0000-0000-000000000016', 'issue:status', '2026-10-21 08:56:49.776045');
INSERT INTO "public"."role_permission" ("id", "role_id", "permission_code", "created_at") VALUES ('5d2ed0e4-c246-445a-bd3b-bbf0b503ce1a', '00000000-0000-0000-0000-000000000016', 'issue:assign', '2026-10-21 08:56:49.776045');
INSERT INTO "public"."role_permission" ("id", "role_id", "permission_code", "created_at") VALUES ('c06889f9-a404-4c95-8d78-de3a02350c6f', '00000000-0000-0000-0000-000000000016', 'sprint:view', '2026-10-21 08:56:49.776045');
INSERT INTO "public"."role_permission" ("id", "role_id", "permission_code", "created_at") VALUES ('d896ca85-2419-4d42-9f65-83c497941bd3', '00000000-0000-0000-0000-000000000016', 'milestone:view', '2026-10-21 08:56:49.776045');
INSERT INTO "public"."role_permission" ("id", "role_id", "permission_code", "created_at") VALUES ('885cba16-44b5-4689-8e7c-ab09b52badcd', '00000000-0000-0000-0000-000000000016', 'board:view', '2026-10-21 08:56:49.776045');
INSERT INTO "public"."role_permission" ("id", "role_id", "permission_code", "created_at") VALUES ('fb3b9d33-8809-4a40-b733-600d71864464', '00000000-0000-0000-0000-000000000016', 'risk:manage', '2026-10-21 08:56:49.776045');
INSERT INTO "public"."role_permission" ("id", "role_id", "permission_code", "created_at") VALUES ('3651f00a-5565-40ec-84fc-663138eb7bf8', '00000000-0000-0000-0000-000000000016', 'sprint:manage', '2026-10-21 08:56:49.776045');
INSERT INTO "public"."role_permission" ("id", "role_id", "permission_code", "created_at") VALUES ('1a4d3c4d-c9e8-4828-bc9c-d34452a9bef5', '00000000-0000-0000-0000-000000000016', 'milestone:manage', '2026-10-21 08:56:49.776045');
INSERT INTO "public"."role_permission" ("id", "role_id", "permission_code", "created_at") VALUES ('6693f44d-9850-4a8b-a716-935fc34aff06', '00000000-0000-0000-0000-000000000016', 'risk:view', '2026-10-21 08:56:49.776045');
INSERT INTO "public"."role_permission" ("id", "role_id", "permission_code", "created_at") VALUES ('4c884590-6483-4308-8571-7db8316d81b1', '00000000-0000-0000-0000-000000000016', 'announcement:manage', '2026-10-21 08:56:49.776045');

-- ----------------------------
-- 8. 序列同步（新库从 1 开始编号）
-- ----------------------------
SELECT setval('"public"."issue_number_seq"', 1, false);

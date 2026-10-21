-- ============================================================================
-- V1.1.0  将单表 issue 拆分为 task / requirement / bug 三张独立物理表
-- ----------------------------------------------------------------------------
-- 目的：从存储层彻底隔离「缺陷 / 需求 / 任务」三类数据，杜绝任务列表混入缺陷。
-- 策略：
--   1) 新建 task / requirement / bug 三表（通用字段 + 各自专属字段）
--   2) 子表（issue_comment / attachment / issue_cc / issue_participant /
--      issue_activity / issue_relation / issue_label）增加 category 列用于隔离
--   3) 按 issue_type.code 将旧 issue 数据迁移到三表，并回填子表 category
--   4) 旧 issue 表保留不动（观察期结束后再 DROP，本脚本不删除）
-- 幂等：所有 INSERT 使用 NOT EXISTS 防重复；可安全重复执行。
-- 注意：执行前请备份数据库。
-- ============================================================================

-- ---------------------------------------------------------------------------
-- 一、三张主表
-- ---------------------------------------------------------------------------

-- ---------- task（任务） ----------
CREATE TABLE IF NOT EXISTS "public"."task" (
    "id"            uuid NOT NULL DEFAULT gen_random_uuid(),
    "project_id"    uuid NOT NULL,
    "issue_number"  varchar(64) NOT NULL,
    "parent_id"     uuid,
    "issue_type_id" uuid NOT NULL,
    "status_id"     uuid NOT NULL,
    "title"         varchar(500) NOT NULL,
    "description"   text,
    "priority"      varchar(20) NOT NULL DEFAULT 'P2',
    "reporter_id"   uuid,
    "assignee_id"   uuid,
    "verifier_id"   uuid,
    "sprint_id"     uuid,
    "estimate"      numeric(10,2),
    "due_date"      date,
    "sort_key"      varchar(255) NOT NULL,
    "archived"      boolean NOT NULL DEFAULT false,
    "iteration"     varchar(100),
    "created_by"    uuid,
    "updated_by"    uuid,
    "created_at"    timestamp(6) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "updated_at"    timestamp(6) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT "pk_task" PRIMARY KEY ("id"),
    CONSTRAINT "uk_task_number" UNIQUE ("project_id", "issue_number"),
    CONSTRAINT "fk_task_project"   FOREIGN KEY ("project_id")  REFERENCES "public"."project" ("id") ON DELETE RESTRICT,
    CONSTRAINT "fk_task_parent"    FOREIGN KEY ("parent_id")   REFERENCES "public"."task" ("id") ON DELETE CASCADE,
    CONSTRAINT "fk_task_status"    FOREIGN KEY ("status_id")   REFERENCES "public"."issue_status" ("id") ON DELETE RESTRICT,
    CONSTRAINT "fk_task_type"      FOREIGN KEY ("issue_type_id") REFERENCES "public"."issue_type" ("id") ON DELETE RESTRICT,
    CONSTRAINT "fk_task_assignee"  FOREIGN KEY ("assignee_id") REFERENCES "public"."sys_user" ("id") ON DELETE SET NULL,
    CONSTRAINT "fk_task_reporter"  FOREIGN KEY ("reporter_id") REFERENCES "public"."sys_user" ("id") ON DELETE SET NULL,
    CONSTRAINT "fk_task_verifier"  FOREIGN KEY ("verifier_id") REFERENCES "public"."sys_user" ("id") ON DELETE SET NULL,
    CONSTRAINT "fk_task_sprint"    FOREIGN KEY ("sprint_id")   REFERENCES "public"."sprint" ("id") ON DELETE SET NULL
);
CREATE INDEX IF NOT EXISTS "idx_task_project"  ON "public"."task" ("project_id");
CREATE INDEX IF NOT EXISTS "idx_task_status"   ON "public"."task" ("status_id");
CREATE INDEX IF NOT EXISTS "idx_task_assignee" ON "public"."task" ("assignee_id");
CREATE INDEX IF NOT EXISTS "idx_task_type"     ON "public"."task" ("issue_type_id");

-- ---------- requirement（需求） ----------
CREATE TABLE IF NOT EXISTS "public"."requirement" (
    "id"            uuid NOT NULL DEFAULT gen_random_uuid(),
    "project_id"    uuid NOT NULL,
    "issue_number"  varchar(64) NOT NULL,
    "parent_id"     uuid,
    "issue_type_id" uuid NOT NULL,
    "status_id"     uuid NOT NULL,
    "title"         varchar(500) NOT NULL,
    "description"   text,
    "acceptance_criteria" text,
    "module"        varchar(100),
    "priority"      varchar(20) NOT NULL DEFAULT 'P2',
    "reporter_id"   uuid,
    "assignee_id"   uuid,
    "verifier_id"   uuid,
    "sprint_id"     uuid,
    "estimate"      numeric(10,2),
    "due_date"      date,
    "sort_key"      varchar(255) NOT NULL,
    "archived"      boolean NOT NULL DEFAULT false,
    "created_by"    uuid,
    "updated_by"    uuid,
    "created_at"    timestamp(6) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "updated_at"    timestamp(6) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT "pk_requirement" PRIMARY KEY ("id"),
    CONSTRAINT "uk_requirement_number" UNIQUE ("project_id", "issue_number"),
    CONSTRAINT "fk_requirement_project"   FOREIGN KEY ("project_id")  REFERENCES "public"."project" ("id") ON DELETE RESTRICT,
    CONSTRAINT "fk_requirement_parent"    FOREIGN KEY ("parent_id")   REFERENCES "public"."requirement" ("id") ON DELETE CASCADE,
    CONSTRAINT "fk_requirement_status"    FOREIGN KEY ("status_id")   REFERENCES "public"."issue_status" ("id") ON DELETE RESTRICT,
    CONSTRAINT "fk_requirement_type"      FOREIGN KEY ("issue_type_id") REFERENCES "public"."issue_type" ("id") ON DELETE RESTRICT,
    CONSTRAINT "fk_requirement_assignee"  FOREIGN KEY ("assignee_id") REFERENCES "public"."sys_user" ("id") ON DELETE SET NULL,
    CONSTRAINT "fk_requirement_reporter"  FOREIGN KEY ("reporter_id") REFERENCES "public"."sys_user" ("id") ON DELETE SET NULL,
    CONSTRAINT "fk_requirement_verifier"  FOREIGN KEY ("verifier_id") REFERENCES "public"."sys_user" ("id") ON DELETE SET NULL,
    CONSTRAINT "fk_requirement_sprint"    FOREIGN KEY ("sprint_id")   REFERENCES "public"."sprint" ("id") ON DELETE SET NULL
);
CREATE INDEX IF NOT EXISTS "idx_requirement_project"  ON "public"."requirement" ("project_id");
CREATE INDEX IF NOT EXISTS "idx_requirement_status"   ON "public"."requirement" ("status_id");
CREATE INDEX IF NOT EXISTS "idx_requirement_assignee" ON "public"."requirement" ("assignee_id");
CREATE INDEX IF NOT EXISTS "idx_requirement_type"     ON "public"."requirement" ("issue_type_id");

-- ---------- bug（缺陷） ----------
CREATE TABLE IF NOT EXISTS "public"."bug" (
    "id"            uuid NOT NULL DEFAULT gen_random_uuid(),
    "project_id"    uuid NOT NULL,
    "issue_number"  varchar(64) NOT NULL,
    "parent_id"     uuid,
    "issue_type_id" uuid NOT NULL,
    "status_id"     uuid NOT NULL,
    "title"         varchar(500) NOT NULL,
    "description"   text,
    "priority"      varchar(20) NOT NULL DEFAULT 'P2',
    "reporter_id"   uuid,
    "assignee_id"   uuid,
    "verifier_id"   uuid,
    "sprint_id"     uuid,
    "estimate"      numeric(10,2),
    "due_date"      date,
    "sort_key"      varchar(255) NOT NULL,
    "archived"      boolean NOT NULL DEFAULT false,
    "severity"      varchar(20) NOT NULL DEFAULT 'S3',
    "module"        varchar(100),
    "fix_version"   varchar(60),
    "source"        varchar(60),
    "iteration"     varchar(100),
    "plan_start"    date,
    "no_fix_reason" varchar(1000),
    "created_by"    uuid,
    "updated_by"    uuid,
    "created_at"    timestamp(6) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "updated_at"    timestamp(6) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT "pk_bug" PRIMARY KEY ("id"),
    CONSTRAINT "uk_bug_number" UNIQUE ("project_id", "issue_number"),
    CONSTRAINT "fk_bug_project"   FOREIGN KEY ("project_id")  REFERENCES "public"."project" ("id") ON DELETE RESTRICT,
    CONSTRAINT "fk_bug_parent"    FOREIGN KEY ("parent_id")   REFERENCES "public"."bug" ("id") ON DELETE CASCADE,
    CONSTRAINT "fk_bug_status"    FOREIGN KEY ("status_id")   REFERENCES "public"."issue_status" ("id") ON DELETE RESTRICT,
    CONSTRAINT "fk_bug_type"      FOREIGN KEY ("issue_type_id") REFERENCES "public"."issue_type" ("id") ON DELETE RESTRICT,
    CONSTRAINT "fk_bug_assignee"  FOREIGN KEY ("assignee_id") REFERENCES "public"."sys_user" ("id") ON DELETE SET NULL,
    CONSTRAINT "fk_bug_reporter"  FOREIGN KEY ("reporter_id") REFERENCES "public"."sys_user" ("id") ON DELETE SET NULL,
    CONSTRAINT "fk_bug_verifier"  FOREIGN KEY ("verifier_id") REFERENCES "public"."sys_user" ("id") ON DELETE SET NULL,
    CONSTRAINT "fk_bug_sprint"    FOREIGN KEY ("sprint_id")   REFERENCES "public"."sprint" ("id") ON DELETE SET NULL
);
CREATE INDEX IF NOT EXISTS "idx_bug_project"  ON "public"."bug" ("project_id");
CREATE INDEX IF NOT EXISTS "idx_bug_status"   ON "public"."bug" ("status_id");
CREATE INDEX IF NOT EXISTS "idx_bug_assignee" ON "public"."bug" ("assignee_id");
CREATE INDEX IF NOT EXISTS "idx_bug_type"     ON "public"."bug" ("issue_type_id");
CREATE INDEX IF NOT EXISTS "idx_bug_severity" ON "public"."bug" ("severity");

-- ---------------------------------------------------------------------------
-- 二、子表增加 category 列（隔离维度：TASK / REQUIREMENT / BUG）
-- ---------------------------------------------------------------------------
ALTER TABLE "public"."issue_comment"      ADD COLUMN IF NOT EXISTS "category" varchar(16) NOT NULL DEFAULT 'TASK';
ALTER TABLE "public"."attachment"          ADD COLUMN IF NOT EXISTS "category" varchar(16) NOT NULL DEFAULT 'TASK';
ALTER TABLE "public"."issue_cc"            ADD COLUMN IF NOT EXISTS "category" varchar(16) NOT NULL DEFAULT 'TASK';
ALTER TABLE "public"."issue_participant"   ADD COLUMN IF NOT EXISTS "category" varchar(16) NOT NULL DEFAULT 'TASK';
ALTER TABLE "public"."issue_activity"      ADD COLUMN IF NOT EXISTS "category" varchar(16) NOT NULL DEFAULT 'TASK';
ALTER TABLE "public"."issue_relation"      ADD COLUMN IF NOT EXISTS "category" varchar(16) NOT NULL DEFAULT 'TASK';
ALTER TABLE "public"."issue_label"         ADD COLUMN IF NOT EXISTS "category" varchar(16) NOT NULL DEFAULT 'TASK';

-- ---------------------------------------------------------------------------
-- 三、数据迁移（幂等）
-- ---------------------------------------------------------------------------

-- 任务：issue_type.code = 'TASK'
INSERT INTO "public"."task"
    (id, project_id, issue_number, parent_id, issue_type_id, status_id, title, description,
     priority, reporter_id, assignee_id, verifier_id, sprint_id, estimate, due_date, sort_key,
     archived, iteration, created_by, updated_by, created_at, updated_at)
SELECT i.id, i.project_id, i.issue_number, i.parent_id, i.issue_type_id, i.status_id, i.title, i.description,
       i.priority, i.reporter_id, i.assignee_id, i.verifier_id, i.sprint_id, i.estimate, i.due_date, i.sort_key,
       i.archived, i.iteration, i.created_by, i.updated_by, i.created_at, i.updated_at
FROM "public"."issue" i
WHERE i.issue_type_id IN (SELECT id FROM "public"."issue_type" WHERE code = 'TASK')
  AND NOT EXISTS (SELECT 1 FROM "public"."task" t WHERE t.id = i.id);

-- 需求：issue_type.code IN ('STORY','FEATURE')
INSERT INTO "public"."requirement"
    (id, project_id, issue_number, parent_id, issue_type_id, status_id, title, description,
     acceptance_criteria, module, priority, reporter_id, assignee_id, verifier_id, sprint_id,
     estimate, due_date, sort_key, archived, created_by, updated_by, created_at, updated_at)
SELECT i.id, i.project_id, i.issue_number, i.parent_id, i.issue_type_id, i.status_id, i.title, i.description,
       NULL, i.module, i.priority, i.reporter_id, i.assignee_id, i.verifier_id, i.sprint_id,
       i.estimate, i.due_date, i.sort_key, i.archived, i.created_by, i.updated_by, i.created_at, i.updated_at
FROM "public"."issue" i
WHERE i.issue_type_id IN (SELECT id FROM "public"."issue_type" WHERE code IN ('STORY','FEATURE'))
  AND NOT EXISTS (SELECT 1 FROM "public"."requirement" r WHERE r.id = i.id);

-- 缺陷：issue_type.code IN ('BUG','FUNC_DEFECT','PERF_DEFECT','SEC_DEFECT','COMPAT_DEFECT','UI_DEFECT')
INSERT INTO "public"."bug"
    (id, project_id, issue_number, parent_id, issue_type_id, status_id, title, description,
     priority, reporter_id, assignee_id, verifier_id, sprint_id, estimate, due_date, sort_key,
     archived, severity, module, fix_version, source, iteration, plan_start, no_fix_reason,
     created_by, updated_by, created_at, updated_at)
SELECT i.id, i.project_id, i.issue_number, i.parent_id, i.issue_type_id, i.status_id, i.title, i.description,
       i.priority, i.reporter_id, i.assignee_id, i.verifier_id, i.sprint_id, i.estimate, i.due_date, i.sort_key,
       i.archived, i.severity, i.module, i.fix_version, i.source, i.iteration, i.plan_start, i.no_fix_reason,
       i.created_by, i.updated_by, i.created_at, i.updated_at
FROM "public"."issue" i
WHERE i.issue_type_id IN (SELECT id FROM "public"."issue_type" WHERE code IN ('BUG','FUNC_DEFECT','PERF_DEFECT','SEC_DEFECT','COMPAT_DEFECT','UI_DEFECT'))
  AND NOT EXISTS (SELECT 1 FROM "public"."bug" b WHERE b.id = i.id);

-- 子表 category 回填
UPDATE "public"."issue_comment"    c SET category = 'TASK'       FROM "public"."task" t        WHERE c.issue_id = t.id AND c.category = 'TASK';
UPDATE "public"."issue_comment"    c SET category = 'REQUIREMENT' FROM "public"."requirement" r WHERE c.issue_id = r.id;
UPDATE "public"."issue_comment"    c SET category = 'BUG'        FROM "public"."bug" b         WHERE c.issue_id = b.id;

UPDATE "public"."attachment"        a SET category = 'TASK'       FROM "public"."task" t        WHERE a.issue_id = t.id AND a.category = 'TASK';
UPDATE "public"."attachment"        a SET category = 'REQUIREMENT' FROM "public"."requirement" r WHERE a.issue_id = r.id;
UPDATE "public"."attachment"        a SET category = 'BUG'        FROM "public"."bug" b         WHERE a.issue_id = b.id;

UPDATE "public"."issue_cc"          cc SET category = 'TASK'       FROM "public"."task" t        WHERE cc.issue_id = t.id AND cc.category = 'TASK';
UPDATE "public"."issue_cc"          cc SET category = 'REQUIREMENT' FROM "public"."requirement" r WHERE cc.issue_id = r.id;
UPDATE "public"."issue_cc"          cc SET category = 'BUG'        FROM "public"."bug" b         WHERE cc.issue_id = b.id;

UPDATE "public"."issue_participant" p SET category = 'TASK'       FROM "public"."task" t        WHERE p.issue_id = t.id AND p.category = 'TASK';
UPDATE "public"."issue_participant" p SET category = 'REQUIREMENT' FROM "public"."requirement" r WHERE p.issue_id = r.id;
UPDATE "public"."issue_participant" p SET category = 'BUG'        FROM "public"."bug" b         WHERE p.issue_id = b.id;

UPDATE "public"."issue_activity"    ac SET category = 'TASK'       FROM "public"."task" t        WHERE ac.issue_id = t.id AND ac.category = 'TASK';
UPDATE "public"."issue_activity"    ac SET category = 'REQUIREMENT' FROM "public"."requirement" r WHERE ac.issue_id = r.id;
UPDATE "public"."issue_activity"    ac SET category = 'BUG'        FROM "public"."bug" b         WHERE ac.issue_id = b.id;

UPDATE "public"."issue_relation"    rl SET category = 'TASK'       FROM "public"."task" t        WHERE rl.issue_id = t.id AND rl.category = 'TASK';
UPDATE "public"."issue_relation"    rl SET category = 'REQUIREMENT' FROM "public"."requirement" r WHERE rl.issue_id = r.id;
UPDATE "public"."issue_relation"    rl SET category = 'BUG'        FROM "public"."bug" b         WHERE rl.issue_id = b.id;

UPDATE "public"."issue_label"       lb SET category = 'TASK'       FROM "public"."task" t        WHERE lb.issue_id = t.id AND lb.category = 'TASK';
UPDATE "public"."issue_label"       lb SET category = 'REQUIREMENT' FROM "public"."requirement" r WHERE lb.issue_id = r.id;
UPDATE "public"."issue_label"       lb SET category = 'BUG'        FROM "public"."bug" b         WHERE lb.issue_id = b.id;

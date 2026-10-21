-- ============================================================================
-- V27  补迁 IMPROVEMENT 类型并删除遗留的旧 issue 表
-- ----------------------------------------------------------------------------
-- 背景：
--   V25 将单表 issue 拆分为 task/requirement/bug，但遗漏了 IMPROVEMENT（改进）
--   类型；同时按"观察期"保留了旧 issue 表未删。V26 已删除子表指向 issue 的
--   外键。旧 issue 表残留数据（主要为 IMPROVEMENT）导致删除项目时触发
--   fk_issue_project（ON DELETE RESTRICT）报错。
-- 本脚本：
--   1) 补迁 IMPROVEMENT 类型数据到 task 表（路由逻辑中 IMPROVEMENT -> task）
--   2) DROP 旧 issue 表（数据已全部迁移，Java 代码已无任何引用）
-- 幂等：INSERT 使用 NOT EXISTS；DROP 使用 IF EXISTS。
-- ============================================================================

-- ---------------------------------------------------------------------------
-- 一、补迁 IMPROVEMENT（改进）类型数据到 task 表
-- ---------------------------------------------------------------------------
INSERT INTO "public"."task"
    (id, project_id, issue_number, parent_id, issue_type_id, status_id, title, description,
     priority, reporter_id, assignee_id, verifier_id, sprint_id, estimate, due_date, sort_key,
     archived, iteration, created_by, updated_by, created_at, updated_at)
SELECT i.id, i.project_id, i.issue_number, i.parent_id, i.issue_type_id, i.status_id, i.title, i.description,
       i.priority, i.reporter_id, i.assignee_id, i.verifier_id, i.sprint_id, i.estimate, i.due_date, i.sort_key,
       i.archived, i.iteration, i.created_by, i.updated_by, i.created_at, i.updated_at
FROM "public"."issue" i
WHERE i.issue_type_id IN (SELECT id FROM "public"."issue_type" WHERE code = 'IMPROVEMENT')
  AND NOT EXISTS (SELECT 1 FROM "public"."task" t WHERE t.id = i.id);

-- ---------------------------------------------------------------------------
-- 二、DROP 旧 issue 表
--     V26 已删除子表指向 issue 的外键，此处 CASCADE 用于兜底删除 issue 表
--     自身遗留的约束（fk_issue_project 等），不影响 task/requirement/bug。
-- ---------------------------------------------------------------------------
DROP TABLE IF EXISTS "public"."issue" CASCADE;

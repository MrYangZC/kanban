-- ----------------------------
-- V1.0.4 度量口径修正
--
-- 1. task/requirement/bug 增加 done_at：记录"首次进入已完成类别状态"的时间。
--    之前周期时间用 updated_at - created_at，任何一次编辑（改标题、加标签）都会把
--    "解决周期"拉长，指标完全失真。
-- 2. 回填：当前处于 DONE 类别的行用 updated_at 近似（历史数据没有更准确的时间来源）。
-- 3. 索引：周期时间/燃尽按 (project_id, done_at) 聚合。
-- 全部语句幂等，可重复执行。
-- ----------------------------

ALTER TABLE "public"."task" ADD COLUMN IF NOT EXISTS "done_at" timestamp(6) without time zone;
ALTER TABLE "public"."requirement" ADD COLUMN IF NOT EXISTS "done_at" timestamp(6) without time zone;
ALTER TABLE "public"."bug" ADD COLUMN IF NOT EXISTS "done_at" timestamp(6) without time zone;

UPDATE "public"."task" t
SET "done_at" = t."updated_at"
FROM "public"."issue_status" s
WHERE s."id" = t."status_id"
  AND s."category" = 'DONE'
  AND t."done_at" IS NULL;

UPDATE "public"."requirement" r
SET "done_at" = r."updated_at"
FROM "public"."issue_status" s
WHERE s."id" = r."status_id"
  AND s."category" = 'DONE'
  AND r."done_at" IS NULL;

UPDATE "public"."bug" b
SET "done_at" = b."updated_at"
FROM "public"."issue_status" s
WHERE s."id" = b."status_id"
  AND s."category" = 'DONE'
  AND b."done_at" IS NULL;

CREATE INDEX IF NOT EXISTS "idx_task_done_at" ON "public"."task" ("project_id", "done_at");
CREATE INDEX IF NOT EXISTS "idx_requirement_done_at" ON "public"."requirement" ("project_id", "done_at");
CREATE INDEX IF NOT EXISTS "idx_bug_done_at" ON "public"."bug" ("project_id", "done_at");

-- ============================================================================
-- V26  修复 V25 拆表后的遗留问题
-- ----------------------------------------------------------------------------
-- 1) menu 表补充 created_by / updated_by 审计列（BaseEntity 契约）
-- 2) 删除子表指向旧 issue 表的外键约束（拆表后 issue_id 指向 task/requirement/bug）
-- 幂等：全部使用 IF EXISTS，可安全重复执行。
-- ============================================================================

-- ---------------------------------------------------------------------------
-- 一、menu 表补充审计列
-- ---------------------------------------------------------------------------
ALTER TABLE "public"."menu" ADD COLUMN IF NOT EXISTS "created_by" uuid;
ALTER TABLE "public"."menu" ADD COLUMN IF NOT EXISTS "updated_by" uuid;

-- ---------------------------------------------------------------------------
-- 二、删除子表指向旧 issue 表的外键（拆表后 issue_id 指向 task/requirement/bug）
-- ---------------------------------------------------------------------------
ALTER TABLE "public"."attachment"      DROP CONSTRAINT IF EXISTS "fk_attachment_issue";
ALTER TABLE "public"."issue_activity"  DROP CONSTRAINT IF EXISTS "fk_issue_activity_issue";
ALTER TABLE "public"."issue_cc"        DROP CONSTRAINT IF EXISTS "fk_issue_cc_issue";
ALTER TABLE "public"."issue_comment"   DROP CONSTRAINT IF EXISTS "fk_issue_comment_issue";
ALTER TABLE "public"."issue_label"     DROP CONSTRAINT IF EXISTS "fk_issue_label_issue";
ALTER TABLE "public"."issue_participant" DROP CONSTRAINT IF EXISTS "fk_issue_participant_issue";
ALTER TABLE "public"."issue_relation"  DROP CONSTRAINT IF EXISTS "fk_issue_relation_issue";
ALTER TABLE "public"."issue_relation"  DROP CONSTRAINT IF EXISTS "fk_issue_relation_target";
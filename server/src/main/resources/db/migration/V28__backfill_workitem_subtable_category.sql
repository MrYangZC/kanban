-- ============================================================================
-- V28  回填工作项子表的 category（修复拆表后新增数据落默认 TASK）
-- ----------------------------------------------------------------------------
-- 背景：
--   V25 给 issue_comment / attachment / issue_cc / issue_participant /
--   issue_activity / issue_relation / issue_label 增加 category 列时，
--   列定义为 NOT NULL DEFAULT 'TASK'，并在脚本末尾对当时存量数据做过回填。
--   但拆表后应用侧 IssueCommentService / IssueAttachmentService /
--   IssueLinkService 三处 INSERT 未显式设置 category，导致此后在「需求/缺陷」
--   下新增的评论、附件、关联都被静默写成 TASK，表现为：
--     · 需求/缺陷详情页的评论数、附件数恒为 0（toVO 按 category 统计）
--     · 删除需求/缺陷时按 category 清理不掉，残留孤儿记录
--   本次已修复上述三处 INSERT 显式落类别。
-- 本脚本：
--   对七张子表按 issue_id 归属（task/requirement/bug）重新回填 category，
--   修正本脚本执行前产生的存量脏数据。
--   由于工作项 id 是 UUID 且唯一存在于三张表之一，按表 join 判定无歧义。
-- 幂等：UPDATE 按 id join 覆盖写入，可安全重复执行。
-- ============================================================================

-- ---------- 需求类（requirement） ----------
UPDATE "public"."issue_comment"    c SET category = 'REQUIREMENT' FROM "public"."requirement" r WHERE c.issue_id = r.id;
UPDATE "public"."attachment"        a SET category = 'REQUIREMENT' FROM "public"."requirement" r WHERE a.issue_id = r.id;
UPDATE "public"."issue_cc"          cc SET category = 'REQUIREMENT' FROM "public"."requirement" r WHERE cc.issue_id = r.id;
UPDATE "public"."issue_participant" p SET category = 'REQUIREMENT' FROM "public"."requirement" r WHERE p.issue_id = r.id;
UPDATE "public"."issue_activity"    ac SET category = 'REQUIREMENT' FROM "public"."requirement" r WHERE ac.issue_id = r.id;
UPDATE "public"."issue_relation"    rl SET category = 'REQUIREMENT' FROM "public"."requirement" r WHERE rl.issue_id = r.id;
UPDATE "public"."issue_label"       lb SET category = 'REQUIREMENT' FROM "public"."requirement" r WHERE lb.issue_id = r.id;

-- ---------- 缺陷类（bug） ----------
UPDATE "public"."issue_comment"    c SET category = 'BUG' FROM "public"."bug" b WHERE c.issue_id = b.id;
UPDATE "public"."attachment"        a SET category = 'BUG' FROM "public"."bug" b WHERE a.issue_id = b.id;
UPDATE "public"."issue_cc"          cc SET category = 'BUG' FROM "public"."bug" b WHERE cc.issue_id = b.id;
UPDATE "public"."issue_participant" p SET category = 'BUG' FROM "public"."bug" b WHERE p.issue_id = b.id;
UPDATE "public"."issue_activity"    ac SET category = 'BUG' FROM "public"."bug" b WHERE ac.issue_id = b.id;
UPDATE "public"."issue_relation"    rl SET category = 'BUG' FROM "public"."bug" b WHERE rl.issue_id = b.id;
UPDATE "public"."issue_label"       lb SET category = 'BUG' FROM "public"."bug" b WHERE lb.issue_id = b.id;

-- ---------- 任务类（task）放在最后，确保前面两步改动不会被误覆盖 ----------
UPDATE "public"."issue_comment"    c SET category = 'TASK' FROM "public"."task" t WHERE c.issue_id = t.id;
UPDATE "public"."attachment"        a SET category = 'TASK' FROM "public"."task" t WHERE a.issue_id = t.id;
UPDATE "public"."issue_cc"          cc SET category = 'TASK' FROM "public"."task" t WHERE cc.issue_id = t.id;
UPDATE "public"."issue_participant" p SET category = 'TASK' FROM "public"."task" t WHERE p.issue_id = t.id;
UPDATE "public"."issue_activity"    ac SET category = 'TASK' FROM "public"."task" t WHERE ac.issue_id = t.id;
UPDATE "public"."issue_relation"    rl SET category = 'TASK' FROM "public"."task" t WHERE rl.issue_id = t.id;
UPDATE "public"."issue_label"       lb SET category = 'TASK' FROM "public"."task" t WHERE lb.issue_id = t.id;

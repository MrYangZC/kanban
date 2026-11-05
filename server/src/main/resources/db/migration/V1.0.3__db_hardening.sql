-- ----------------------------
-- V1.0.3 数据库加固
--
-- 只做"加了不会丢数据"的改动：外键语义、category 默认值、热路径索引、sort_key 排序稳定性。
-- 可能因历史脏数据失败的唯一约束（board(project_id)、attachment(storage_key) 等）刻意不在此处添加。
-- 全部语句可重复执行（幂等），便于迁移失败后手工续跑。
-- ----------------------------

-- ----------------------------
-- 1. 外键语义修正
--
-- issue_comment.user_id 原本是 NOT NULL + ON DELETE SET NULL：两者互相矛盾，
-- 删用户时必然触发 NOT NULL 冲突（应用层只能报"提交的数据不符合约束"，与实际原因无关）。
-- 评论属于审计信息，应当保留，因此把列改为可空让 SET NULL 生效；前端对空作者显示为"未知"。
-- ----------------------------
ALTER TABLE "public"."issue_comment" ALTER COLUMN "user_id" DROP NOT NULL;

-- issue_cc / issue_participant 的 user_id 没有"空值语义"（不知道抄送给谁的行没有意义），
-- 改为随用户级联删除，同样消除 NOT NULL 冲突。
ALTER TABLE "public"."issue_cc" DROP CONSTRAINT IF EXISTS "fk_issue_cc_user";
ALTER TABLE "public"."issue_cc" ADD CONSTRAINT "fk_issue_cc_user"
    FOREIGN KEY ("user_id") REFERENCES "public"."sys_user" ("id") ON DELETE CASCADE;

ALTER TABLE "public"."issue_participant" DROP CONSTRAINT IF EXISTS "fk_issue_participant_user";
ALTER TABLE "public"."issue_participant" ADD CONSTRAINT "fk_issue_participant_user"
    FOREIGN KEY ("user_id") REFERENCES "public"."sys_user" ("id") ON DELETE CASCADE;

-- ----------------------------
-- 2. category 默认值
--
-- 子表的 category 决定"这条数据属于哪张工作项表"。默认 'TASK' 会让漏写 setCategory 的插入
-- 静默变成任务数据（曾导致缺陷评论计数不到、删不掉，需求附件永远清理不掉）。
-- 去掉默认值：漏写直接失败，而不是产生"看起来正常"的错数据。
-- 再用 NOT VALID 的 CHECK 约束兜住新数据，历史行不做校验，避免迁移因脏数据整体失败。
-- ----------------------------
ALTER TABLE "public"."issue_comment"     ALTER COLUMN "category" DROP DEFAULT;
ALTER TABLE "public"."issue_label"       ALTER COLUMN "category" DROP DEFAULT;
ALTER TABLE "public"."issue_cc"          ALTER COLUMN "category" DROP DEFAULT;
ALTER TABLE "public"."issue_participant" ALTER COLUMN "category" DROP DEFAULT;
ALTER TABLE "public"."issue_activity"    ALTER COLUMN "category" DROP DEFAULT;
ALTER TABLE "public"."attachment"        ALTER COLUMN "category" DROP DEFAULT;

ALTER TABLE "public"."issue_comment" DROP CONSTRAINT IF EXISTS "ck_issue_comment_category";
ALTER TABLE "public"."issue_comment" ADD CONSTRAINT "ck_issue_comment_category"
    CHECK ("category" IN ('TASK', 'REQUIREMENT', 'BUG')) NOT VALID;

ALTER TABLE "public"."issue_label" DROP CONSTRAINT IF EXISTS "ck_issue_label_category";
ALTER TABLE "public"."issue_label" ADD CONSTRAINT "ck_issue_label_category"
    CHECK ("category" IN ('TASK', 'REQUIREMENT', 'BUG')) NOT VALID;

ALTER TABLE "public"."issue_cc" DROP CONSTRAINT IF EXISTS "ck_issue_cc_category";
ALTER TABLE "public"."issue_cc" ADD CONSTRAINT "ck_issue_cc_category"
    CHECK ("category" IN ('TASK', 'REQUIREMENT', 'BUG')) NOT VALID;

ALTER TABLE "public"."issue_participant" DROP CONSTRAINT IF EXISTS "ck_issue_participant_category";
ALTER TABLE "public"."issue_participant" ADD CONSTRAINT "ck_issue_participant_category"
    CHECK ("category" IN ('TASK', 'REQUIREMENT', 'BUG')) NOT VALID;

ALTER TABLE "public"."issue_activity" DROP CONSTRAINT IF EXISTS "ck_issue_activity_category";
ALTER TABLE "public"."issue_activity" ADD CONSTRAINT "ck_issue_activity_category"
    CHECK ("category" IN ('TASK', 'REQUIREMENT', 'BUG')) NOT VALID;

ALTER TABLE "public"."attachment" DROP CONSTRAINT IF EXISTS "ck_attachment_category";
ALTER TABLE "public"."attachment" ADD CONSTRAINT "ck_attachment_category"
    CHECK ("category" IN ('TASK', 'REQUIREMENT', 'BUG')) NOT VALID;

-- ----------------------------
-- 3. 热路径索引
--
-- 看板：WHERE project_id = ? AND archived = false，按 status_id 分组、sort_key 排序 → 部分复合索引
-- 列表：WHERE project_id = ? + ORDER BY updated_at（表格视图默认排序）
-- 动态：详情按 issue_id + created_at 排序；项目动态按 created_at 倒序扫描
-- ----------------------------
CREATE INDEX IF NOT EXISTS "idx_task_board"
    ON "public"."task" ("project_id", "status_id", "sort_key") WHERE "archived" = false;
CREATE INDEX IF NOT EXISTS "idx_requirement_board"
    ON "public"."requirement" ("project_id", "status_id", "sort_key") WHERE "archived" = false;
CREATE INDEX IF NOT EXISTS "idx_bug_board"
    ON "public"."bug" ("project_id", "status_id", "sort_key") WHERE "archived" = false;

CREATE INDEX IF NOT EXISTS "idx_task_list" ON "public"."task" ("project_id", "updated_at" DESC);
CREATE INDEX IF NOT EXISTS "idx_requirement_list" ON "public"."requirement" ("project_id", "updated_at" DESC);
CREATE INDEX IF NOT EXISTS "idx_bug_list" ON "public"."bug" ("project_id", "updated_at" DESC);

CREATE INDEX IF NOT EXISTS "idx_issue_activity_issue_created"
    ON "public"."issue_activity" ("issue_id", "created_at" DESC);
CREATE INDEX IF NOT EXISTS "idx_issue_activity_created"
    ON "public"."issue_activity" ("created_at" DESC);

-- ----------------------------
-- 4. sort_key 排序稳定性
--
-- sort_key 由 Java 的 String.compareTo（ASCII/UTF-16 码元）生成，而 ORDER BY 走数据库默认 collation。
-- 在 ICU/libc 的本地化排序下，'a' 与 'B' 的相对顺序与 ASCII 相反，base62 键会出现
-- "Java 认为是 A<B、数据库认为是 B<A" 的分歧，表现为拖拽后顺序偶发错乱。
-- 统一按 C（字节序）比较，与 SortKeyUtils 的字符集顺序严格一致。
-- ----------------------------
ALTER TABLE "public"."task" ALTER COLUMN "sort_key" TYPE character varying(255) COLLATE "C";
ALTER TABLE "public"."requirement" ALTER COLUMN "sort_key" TYPE character varying(255) COLLATE "C";
ALTER TABLE "public"."bug" ALTER COLUMN "sort_key" TYPE character varying(255) COLLATE "C";

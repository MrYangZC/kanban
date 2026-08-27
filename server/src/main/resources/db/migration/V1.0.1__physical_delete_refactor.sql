-- ============================================================
-- V1.0.1: 物理删除重构 + ON DELETE 重设 + 索引补全 + CHECK 约束
-- 决策: 废弃逻辑删除，采用物理删除；不补全新外键(应用层保障)
-- ============================================================

-- ============================================================
-- 一、移除 deleted 字段（10 张表）
-- ============================================================
ALTER TABLE "public"."announcement" DROP COLUMN "deleted";
ALTER TABLE "public"."issue" DROP COLUMN "deleted";
ALTER TABLE "public"."issue_comment" DROP COLUMN "deleted";
ALTER TABLE "public"."menu" DROP COLUMN "deleted";
ALTER TABLE "public"."milestone" DROP COLUMN "deleted";
ALTER TABLE "public"."project" DROP COLUMN "deleted";
ALTER TABLE "public"."risk" DROP COLUMN "deleted";
ALTER TABLE "public"."role" DROP COLUMN "deleted";
ALTER TABLE "public"."sprint" DROP COLUMN "deleted";
ALTER TABLE "public"."sys_user" DROP COLUMN "deleted";

-- ============================================================
-- 二、部分唯一索引 → 普通唯一约束（5 个），先删后建
-- ============================================================

-- 2.1 issue: uk_issue_number_undeleted → uk_issue_number
DROP INDEX IF EXISTS "public"."uk_issue_number_undeleted";
ALTER TABLE "public"."issue" ADD CONSTRAINT "uk_issue_number" UNIQUE ("project_id", "issue_number");

-- 2.2 project: uk_project_key_undeleted → uk_project_key
DROP INDEX IF EXISTS "public"."uk_project_key_undeleted";
ALTER TABLE "public"."project" ADD CONSTRAINT "uk_project_key" UNIQUE ("project_key");

-- 2.3 sys_user: uk_sys_user_username_undeleted → uk_sys_user_username
DROP INDEX IF EXISTS "public"."uk_sys_user_username_undeleted";
ALTER TABLE "public"."sys_user" ADD CONSTRAINT "uk_sys_user_username" UNIQUE ("username");

-- 2.4 role: uk_role_code_undeleted → uk_role_code
DROP INDEX IF EXISTS "public"."uk_role_code_undeleted";
ALTER TABLE "public"."role" ADD CONSTRAINT "uk_role_code" UNIQUE ("code");

-- 2.5 menu: uk_menu_code_undeleted → uk_menu_code
DROP INDEX IF EXISTS "public"."uk_menu_code_undeleted";
ALTER TABLE "public"."menu" ADD CONSTRAINT "uk_menu_code" UNIQUE ("code");

-- ============================================================
-- 三、移除 WHERE deleted=0 条件的索引，重建为普通索引（7 个）
--    注意: 移除了 deleted 列后这些索引已失效，需重建
-- ============================================================

-- 3.1 announcement
DROP INDEX IF EXISTS "public"."idx_announcement_project";
CREATE INDEX "idx_announcement_project" ON "public"."announcement" USING btree ("project_id");

-- 3.2 milestone
DROP INDEX IF EXISTS "public"."idx_milestone_project";
CREATE INDEX "idx_milestone_project" ON "public"."milestone" USING btree ("project_id");

-- 3.3 risk
DROP INDEX IF EXISTS "public"."idx_risk_level";
DROP INDEX IF EXISTS "public"."idx_risk_project";
DROP INDEX IF EXISTS "public"."idx_risk_status";
CREATE INDEX "idx_risk_level" ON "public"."risk" USING btree ("level");
CREATE INDEX "idx_risk_project" ON "public"."risk" USING btree ("project_id");
CREATE INDEX "idx_risk_status" ON "public"."risk" USING btree ("status");

-- 3.4 sprint
DROP INDEX IF EXISTS "public"."idx_sprint_project";
DROP INDEX IF EXISTS "public"."idx_sprint_status";
CREATE INDEX "idx_sprint_project" ON "public"."sprint" USING btree ("project_id");
CREATE INDEX "idx_sprint_status" ON "public"."sprint" USING btree ("status");

-- ============================================================
-- 四、外键 ON DELETE 策略重设（先删后建）
--     原则: 业务主体 RESTRICT / 从属数据 CASCADE / 人员引用 SET NULL
-- ============================================================

-- 4.1 board → project: NO ACTION → RESTRICT
ALTER TABLE "public"."board" DROP CONSTRAINT IF EXISTS "fk_board_project";
ALTER TABLE "public"."board" ADD CONSTRAINT "fk_board_project" FOREIGN KEY ("project_id") REFERENCES "public"."project" ("id") ON DELETE RESTRICT ON UPDATE NO ACTION;

-- 4.2 board_column → board: NO ACTION → CASCADE
ALTER TABLE "public"."board_column" DROP CONSTRAINT IF EXISTS "fk_board_column_board";
ALTER TABLE "public"."board_column" ADD CONSTRAINT "fk_board_column_board" FOREIGN KEY ("board_id") REFERENCES "public"."board" ("id") ON DELETE CASCADE ON UPDATE NO ACTION;

-- 4.3 board_column → status: NO ACTION → RESTRICT
ALTER TABLE "public"."board_column" DROP CONSTRAINT IF EXISTS "fk_board_column_status";
ALTER TABLE "public"."board_column" ADD CONSTRAINT "fk_board_column_status" FOREIGN KEY ("status_id") REFERENCES "public"."issue_status" ("id") ON DELETE RESTRICT ON UPDATE NO ACTION;

-- 4.4 issue → assignee: NO ACTION → SET NULL
ALTER TABLE "public"."issue" DROP CONSTRAINT IF EXISTS "fk_issue_assignee";
ALTER TABLE "public"."issue" ADD CONSTRAINT "fk_issue_assignee" FOREIGN KEY ("assignee_id") REFERENCES "public"."sys_user" ("id") ON DELETE SET NULL ON UPDATE NO ACTION;

-- 4.5 issue → parent: NO ACTION → CASCADE
ALTER TABLE "public"."issue" DROP CONSTRAINT IF EXISTS "fk_issue_parent";
ALTER TABLE "public"."issue" ADD CONSTRAINT "fk_issue_parent" FOREIGN KEY ("parent_id") REFERENCES "public"."issue" ("id") ON DELETE CASCADE ON UPDATE NO ACTION;

-- 4.6 issue → project: NO ACTION → RESTRICT
ALTER TABLE "public"."issue" DROP CONSTRAINT IF EXISTS "fk_issue_project";
ALTER TABLE "public"."issue" ADD CONSTRAINT "fk_issue_project" FOREIGN KEY ("project_id") REFERENCES "public"."project" ("id") ON DELETE RESTRICT ON UPDATE NO ACTION;

-- 4.7 issue → reporter: NO ACTION → SET NULL
ALTER TABLE "public"."issue" DROP CONSTRAINT IF EXISTS "fk_issue_reporter";
ALTER TABLE "public"."issue" ADD CONSTRAINT "fk_issue_reporter" FOREIGN KEY ("reporter_id") REFERENCES "public"."sys_user" ("id") ON DELETE SET NULL ON UPDATE NO ACTION;

-- 4.8 issue → sprint: NO ACTION → SET NULL
ALTER TABLE "public"."issue" DROP CONSTRAINT IF EXISTS "fk_issue_sprint";
ALTER TABLE "public"."issue" ADD CONSTRAINT "fk_issue_sprint" FOREIGN KEY ("sprint_id") REFERENCES "public"."sprint" ("id") ON DELETE SET NULL ON UPDATE NO ACTION;

-- 4.9 issue → status: NO ACTION → RESTRICT
ALTER TABLE "public"."issue" DROP CONSTRAINT IF EXISTS "fk_issue_status";
ALTER TABLE "public"."issue" ADD CONSTRAINT "fk_issue_status" FOREIGN KEY ("status_id") REFERENCES "public"."issue_status" ("id") ON DELETE RESTRICT ON UPDATE NO ACTION;

-- 4.10 issue → type: NO ACTION → RESTRICT
ALTER TABLE "public"."issue" DROP CONSTRAINT IF EXISTS "fk_issue_type";
ALTER TABLE "public"."issue" ADD CONSTRAINT "fk_issue_type" FOREIGN KEY ("issue_type_id") REFERENCES "public"."issue_type" ("id") ON DELETE RESTRICT ON UPDATE NO ACTION;

-- 4.11 issue → verifier: NO ACTION → SET NULL
ALTER TABLE "public"."issue" DROP CONSTRAINT IF EXISTS "fk_issue_verifier";
ALTER TABLE "public"."issue" ADD CONSTRAINT "fk_issue_verifier" FOREIGN KEY ("verifier_id") REFERENCES "public"."sys_user" ("id") ON DELETE SET NULL ON UPDATE NO ACTION;

-- 4.12 issue_activity → actor: NO ACTION → SET NULL
ALTER TABLE "public"."issue_activity" DROP CONSTRAINT IF EXISTS "fk_issue_activity_actor";
ALTER TABLE "public"."issue_activity" ADD CONSTRAINT "fk_issue_activity_actor" FOREIGN KEY ("actor_id") REFERENCES "public"."sys_user" ("id") ON DELETE SET NULL ON UPDATE NO ACTION;

-- 4.13 issue_cc → user: CASCADE → SET NULL (保留抄送记录)
ALTER TABLE "public"."issue_cc" DROP CONSTRAINT IF EXISTS "fk_issue_cc_user";
ALTER TABLE "public"."issue_cc" ADD CONSTRAINT "fk_issue_cc_user" FOREIGN KEY ("user_id") REFERENCES "public"."sys_user" ("id") ON DELETE SET NULL ON UPDATE NO ACTION;

-- 4.14 issue_comment → user: NO ACTION → SET NULL (评论保留，作者置空)
ALTER TABLE "public"."issue_comment" DROP CONSTRAINT IF EXISTS "fk_issue_comment_user";
ALTER TABLE "public"."issue_comment" ADD CONSTRAINT "fk_issue_comment_user" FOREIGN KEY ("user_id") REFERENCES "public"."sys_user" ("id") ON DELETE SET NULL ON UPDATE NO ACTION;

-- 4.15 issue_label → label: NO ACTION → CASCADE
ALTER TABLE "public"."issue_label" DROP CONSTRAINT IF EXISTS "fk_issue_label_label";
ALTER TABLE "public"."issue_label" ADD CONSTRAINT "fk_issue_label_label" FOREIGN KEY ("label_id") REFERENCES "public"."label" ("id") ON DELETE CASCADE ON UPDATE NO ACTION;

-- 4.16 issue_participant → user: CASCADE → SET NULL (保留参与记录)
ALTER TABLE "public"."issue_participant" DROP CONSTRAINT IF EXISTS "fk_issue_participant_user";
ALTER TABLE "public"."issue_participant" ADD CONSTRAINT "fk_issue_participant_user" FOREIGN KEY ("user_id") REFERENCES "public"."sys_user" ("id") ON DELETE SET NULL ON UPDATE NO ACTION;

-- 4.17 project → lead: NO ACTION → SET NULL
ALTER TABLE "public"."project" DROP CONSTRAINT IF EXISTS "fk_project_lead";
ALTER TABLE "public"."project" ADD CONSTRAINT "fk_project_lead" FOREIGN KEY ("lead_user_id") REFERENCES "public"."sys_user" ("id") ON DELETE SET NULL ON UPDATE NO ACTION;

-- 4.18 project → workflow: NO ACTION → RESTRICT
ALTER TABLE "public"."project" DROP CONSTRAINT IF EXISTS "fk_project_workflow";
ALTER TABLE "public"."project" ADD CONSTRAINT "fk_project_workflow" FOREIGN KEY ("workflow_id") REFERENCES "public"."workflow" ("id") ON DELETE RESTRICT ON UPDATE NO ACTION;

-- 4.19 project_member → project: NO ACTION → CASCADE
ALTER TABLE "public"."project_member" DROP CONSTRAINT IF EXISTS "fk_pm_project";
ALTER TABLE "public"."project_member" ADD CONSTRAINT "fk_pm_project" FOREIGN KEY ("project_id") REFERENCES "public"."project" ("id") ON DELETE CASCADE ON UPDATE NO ACTION;

-- 4.20 project_member → user: NO ACTION → CASCADE
ALTER TABLE "public"."project_member" DROP CONSTRAINT IF EXISTS "fk_pm_user";
ALTER TABLE "public"."project_member" ADD CONSTRAINT "fk_pm_user" FOREIGN KEY ("user_id") REFERENCES "public"."sys_user" ("id") ON DELETE CASCADE ON UPDATE NO ACTION;

-- 4.21 risk → project: NO ACTION → CASCADE
ALTER TABLE "public"."risk" DROP CONSTRAINT IF EXISTS "risk_project_id_fkey";
ALTER TABLE "public"."risk" ADD CONSTRAINT "risk_project_id_fkey" FOREIGN KEY ("project_id") REFERENCES "public"."project" ("id") ON DELETE CASCADE ON UPDATE NO ACTION;

-- 4.22 role_menu → menu: NO ACTION → CASCADE
ALTER TABLE "public"."role_menu" DROP CONSTRAINT IF EXISTS "fk_rm_menu";
ALTER TABLE "public"."role_menu" ADD CONSTRAINT "fk_rm_menu" FOREIGN KEY ("menu_id") REFERENCES "public"."menu" ("id") ON DELETE CASCADE ON UPDATE NO ACTION;

-- 4.23 role_menu → role: NO ACTION → CASCADE
ALTER TABLE "public"."role_menu" DROP CONSTRAINT IF EXISTS "fk_rm_role";
ALTER TABLE "public"."role_menu" ADD CONSTRAINT "fk_rm_role" FOREIGN KEY ("role_id") REFERENCES "public"."role" ("id") ON DELETE CASCADE ON UPDATE NO ACTION;

-- 4.24 sprint → project: NO ACTION → CASCADE
ALTER TABLE "public"."sprint" DROP CONSTRAINT IF EXISTS "fk_sprint_project";
ALTER TABLE "public"."sprint" ADD CONSTRAINT "fk_sprint_project" FOREIGN KEY ("project_id") REFERENCES "public"."project" ("id") ON DELETE CASCADE ON UPDATE NO ACTION;

-- 4.25 user_role → project: NO ACTION → CASCADE
ALTER TABLE "public"."user_role" DROP CONSTRAINT IF EXISTS "fk_ur_project";
ALTER TABLE "public"."user_role" ADD CONSTRAINT "fk_ur_project" FOREIGN KEY ("project_id") REFERENCES "public"."project" ("id") ON DELETE CASCADE ON UPDATE NO ACTION;

-- 4.26 user_role → role: NO ACTION → CASCADE
ALTER TABLE "public"."user_role" DROP CONSTRAINT IF EXISTS "fk_ur_role";
ALTER TABLE "public"."user_role" ADD CONSTRAINT "fk_ur_role" FOREIGN KEY ("role_id") REFERENCES "public"."role" ("id") ON DELETE CASCADE ON UPDATE NO ACTION;

-- 4.27 user_role → user: NO ACTION → CASCADE
ALTER TABLE "public"."user_role" DROP CONSTRAINT IF EXISTS "fk_ur_user";
ALTER TABLE "public"."user_role" ADD CONSTRAINT "fk_ur_user" FOREIGN KEY ("user_id") REFERENCES "public"."sys_user" ("id") ON DELETE CASCADE ON UPDATE NO ACTION;

-- 4.28 workflow_status → status: NO ACTION → RESTRICT
ALTER TABLE "public"."workflow_status" DROP CONSTRAINT IF EXISTS "fk_ws_status";
ALTER TABLE "public"."workflow_status" ADD CONSTRAINT "fk_ws_status" FOREIGN KEY ("status_id") REFERENCES "public"."issue_status" ("id") ON DELETE RESTRICT ON UPDATE NO ACTION;

-- 4.29 workflow_transition → from_status: NO ACTION → RESTRICT
ALTER TABLE "public"."workflow_transition" DROP CONSTRAINT IF EXISTS "fk_wt_from";
ALTER TABLE "public"."workflow_transition" ADD CONSTRAINT "fk_wt_from" FOREIGN KEY ("from_status_id") REFERENCES "public"."issue_status" ("id") ON DELETE RESTRICT ON UPDATE NO ACTION;

-- 4.30 workflow_transition → to_status: NO ACTION → RESTRICT
ALTER TABLE "public"."workflow_transition" DROP CONSTRAINT IF EXISTS "fk_wt_to";
ALTER TABLE "public"."workflow_transition" ADD CONSTRAINT "fk_wt_to" FOREIGN KEY ("to_status_id") REFERENCES "public"."issue_status" ("id") ON DELETE RESTRICT ON UPDATE NO ACTION;

-- ============================================================
-- 五、补全关键索引（9 处）
-- ============================================================

-- 5.1 issue_activity.issue_id: 查看工作项活动日志
CREATE INDEX "idx_issue_activity_issue" ON "public"."issue_activity" USING btree ("issue_id");

-- 5.2 issue_comment.issue_id: 查看工作项评论列表
CREATE INDEX "idx_issue_comment_issue" ON "public"."issue_comment" USING btree ("issue_id");

-- 5.3 attachment.issue_id: 查看工作项附件
CREATE INDEX "idx_attachment_issue" ON "public"."attachment" USING btree ("issue_id");

-- 5.4 issue_relation.target_issue_id: 反向查询关联关系
CREATE INDEX "idx_issue_relation_target" ON "public"."issue_relation" USING btree ("target_issue_id");

-- 5.5 project_member.user_id: 查询用户参与的项目
CREATE INDEX "idx_project_member_user" ON "public"."project_member" USING btree ("user_id");

-- 5.6 issue_label.label_id: 查询标签被哪些工作项使用
CREATE INDEX "idx_issue_label_label" ON "public"."issue_label" USING btree ("label_id");

-- 5.7 project_label.label_id: 查询标签被哪些项目使用
CREATE INDEX "idx_project_label_label" ON "public"."project_label" USING btree ("label_id");

-- 5.8 role_permission.permission_code: 查询某权限被哪些角色拥有
CREATE INDEX "idx_role_permission_code" ON "public"."role_permission" USING btree ("permission_code");

-- 5.9 issue.issue_type_id: 按类型筛选工作项
CREATE INDEX "idx_issue_type" ON "public"."issue" USING btree ("issue_type_id");

-- ============================================================
-- 六、CHECK 约束
-- ============================================================

-- 6.1 milestone.progress 范围 0-100
ALTER TABLE "public"."milestone" ADD CONSTRAINT "chk_milestone_progress" CHECK ("progress" >= 0 AND "progress" <= 100);

-- 6.2 attachment.file_size >= 0
ALTER TABLE "public"."attachment" ADD CONSTRAINT "chk_attachment_file_size" CHECK ("file_size" >= 0);

-- 6.3 issue.estimate >= 0
ALTER TABLE "public"."issue" ADD CONSTRAINT "chk_issue_estimate" CHECK ("estimate" >= 0 OR "estimate" IS NULL);

-- 6.4 board_column.wip_limit > 0（当非 NULL）
ALTER TABLE "public"."board_column" ADD CONSTRAINT "chk_board_column_wip_limit" CHECK ("wip_limit" > 0 OR "wip_limit" IS NULL);

-- ============================================================
-- 七、日期类型统一：issue.due_date / issue.plan_start → date
--    与 risk.due_date、milestone.due_date、sprint.start_date/end_date 统一
-- ============================================================
ALTER TABLE "public"."issue"
    ALTER COLUMN "due_date" TYPE date USING "due_date"::date,
    ALTER COLUMN "plan_start" TYPE date USING "plan_start"::date;

-- ============================================================
-- 八、布尔字段类型统一：archived / pinned → bool
-- ============================================================
ALTER TABLE "public"."issue"
    ALTER COLUMN "archived" DROP DEFAULT,
    ALTER COLUMN "archived" TYPE bool USING CASE WHEN "archived" = 1 THEN true ELSE false END,
    ALTER COLUMN "archived" SET DEFAULT false;

ALTER TABLE "public"."announcement"
    ALTER COLUMN "pinned" DROP DEFAULT,
    ALTER COLUMN "pinned" TYPE bool USING CASE WHEN "pinned" = 1 THEN true ELSE false END,
    ALTER COLUMN "pinned" SET DEFAULT false;
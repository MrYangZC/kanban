-- ----------------------------
-- V1.0.1 工作流交接（handoff）与站内通知
--
-- 1. project_handoff：项目级「进入某个状态列后交给谁」的配置。
--    刻意不写在 workflow_status 上：workflowOf() 只按 applies_to + is_default 取
--    全局默认模板（三套模板被所有项目共享），写在节点表上会让 A 项目的配置污染 B 项目。
-- 2. notification：站内通知，本期仅用于交接场景。
--
-- 默认值 NONE，不插入任何种子数据 —— 能力上线后对既有行为零影响。
-- ----------------------------

CREATE TABLE "public"."project_handoff" (
    "id" uuid NOT NULL DEFAULT gen_random_uuid(),
    "project_id" uuid NOT NULL,
    "category" character varying(32) NOT NULL,
    "status_id" uuid NOT NULL,
    "handoff_type" character varying(16) NOT NULL DEFAULT 'NONE'::character varying,
    "handoff_user_id" uuid,
    "handoff_note" character varying(500),
    "updated_by" uuid,
    "updated_at" timestamp(6) without time zone NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE "public"."notification" (
    "id" uuid NOT NULL DEFAULT gen_random_uuid(),
    "user_id" uuid NOT NULL,
    "type" character varying(32) NOT NULL,
    "title" character varying(200) NOT NULL,
    "body" character varying(500),
    "project_id" uuid,
    "issue_id" uuid,
    "category" character varying(32),
    "read_at" timestamp(6) without time zone,
    "created_at" timestamp(6) without time zone NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- ----------------------------
-- 主键 / 唯一 / 检查约束
-- ----------------------------

ALTER TABLE "public"."project_handoff" ADD CONSTRAINT "project_handoff_pkey" PRIMARY KEY (id);

-- 同一项目同一类别下，一个状态列只能有一份交接配置；该唯一索引同时承担
-- (project_id, category) 前缀查询，无需再单独建索引
ALTER TABLE "public"."project_handoff" ADD CONSTRAINT "uk_project_handoff" UNIQUE (project_id, category, status_id);

ALTER TABLE "public"."project_handoff" ADD CONSTRAINT "ck_project_handoff_type"
    CHECK (handoff_type IN ('NONE', 'MEMBER', 'PROJECT_LEAD'));

ALTER TABLE "public"."project_handoff" ADD CONSTRAINT "ck_project_handoff_member"
    CHECK (handoff_type <> 'MEMBER' OR handoff_user_id IS NOT NULL);

ALTER TABLE "public"."notification" ADD CONSTRAINT "notification_pkey" PRIMARY KEY (id);

-- ----------------------------
-- 索引
-- ----------------------------

CREATE INDEX "idx_notification_user_created"
    ON "public"."notification" ("user_id", "created_at" DESC);

-- 未读数走部分索引，避免全表扫描已读历史
CREATE INDEX "idx_notification_user_unread"
    ON "public"."notification" ("user_id") WHERE "read_at" IS NULL;

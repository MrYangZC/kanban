-- ----------------------------
-- V1.0.2 交接落点：进入某一列后，除了换负责人，还可以把工作项放进指定迭代、打上指定标签
--
-- 1. 两列均可空：都不配就是「只换人」，与 V1.0.1 的行为完全一致，不改变既有数据。
-- 2. 外键用 ON DELETE SET NULL，不用 RESTRICT：迭代/标签被删掉时把落点清空即可，
--    不该因此让「删迭代」「删标签」直接失败；清空后这条配置仍是有效的「只换人」配置，
--    不会留下永远触发不了的残缺配置。
-- 3. 不建额外索引：只按主键回查（id 等值），唯一的读取路径已经由 uk_project_handoff 覆盖。
-- ----------------------------

ALTER TABLE "public"."project_handoff"
    ADD COLUMN "target_sprint_id" uuid,
    ADD COLUMN "target_label_id" uuid;

ALTER TABLE "public"."project_handoff"
    ADD CONSTRAINT "fk_project_handoff_sprint" FOREIGN KEY ("target_sprint_id")
        REFERENCES "public"."sprint" ("id") ON DELETE SET NULL,
    ADD CONSTRAINT "fk_project_handoff_label" FOREIGN KEY ("target_label_id")
        REFERENCES "public"."label" ("id") ON DELETE SET NULL;

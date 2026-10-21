-- V33：需求工作流扩展为 6 节点「待处理 → 设计中 → 开发中 → 测试中 → 已完成 / 已取消」
--
-- 背景：V31 建立的需求流为「待评审 → 已排期 → 开发中 → 已完成 / 已取消」（5 节点 8 边），
-- 缺少「测试中」，且前两个节点（待评审 / 已排期）与当前需求管理口径不符。
-- 按确认口径调整为：待处理 / 设计中 / 开发中 / 测试中 / 已完成 / 已取消。
--
-- 变更范围（只影响需求模板，任务 / 缺陷流不动）：
--   1. 节点更名 + code 对齐：待评审 REQ_TO_REVIEW → 待处理 REQ_TODO；已排期 REQ_SCHEDULED → 设计中 REQ_DESIGNING
--      （复用原 id，存量 requirement.status_id 无需迁移，自动落到新名称）；
--   2. 新增节点：测试中 REQ_TESTING（插在开发中之后、已完成之前）；
--   3. 工作流节点顺序重排为 待处理 0 / 设计中 1 / 开发中 2 / 测试中 3 / 已完成 4 / 已取消 5；
--   4. 流转边重写为 9 条（含「测试中 → 已完成 测试通过」这条新边）；
--   5. 模板描述同步。
--
-- 幂等：节点按固定 id 更新 / 按 code 插入并 ON CONFLICT DO NOTHING；边按固定 id 更新，
-- 新边按 (workflow_id, from_status_id, to_status_id) 去重，重复执行无副作用。

-- ---------- 1. 节点更名与 code 对齐 ----------
UPDATE "public"."issue_status"
SET "name" = '待处理', "code" = 'REQ_TODO', "category" = 'TODO', "color" = '#8F959E', "sort_order" = 20
WHERE "id" = 'b1000000-0000-4000-8000-000000000001';

-- 「设计中」属进行中，category 由 TODO 调整为 IN_PROGRESS（影响概览的进行中统计口径）
UPDATE "public"."issue_status"
SET "name" = '设计中', "code" = 'REQ_DESIGNING', "category" = 'IN_PROGRESS', "color" = '#1677FF', "sort_order" = 21
WHERE "id" = 'b1000000-0000-4000-8000-000000000002';

-- ---------- 2. 新增「测试中」 ----------
INSERT INTO "public"."issue_status" ("id", "name", "code", "category", "color", "sort_order") VALUES
    ('b1000000-0000-4000-8000-000000000006', '测试中', 'REQ_TESTING', 'IN_PROGRESS', '#722ED1', 23)
ON CONFLICT ("code") DO NOTHING;

-- 已完成 / 已取消 的字典排序后移，给「测试中」让位
UPDATE "public"."issue_status" SET "sort_order" = 24 WHERE "id" = 'b1000000-0000-4000-8000-000000000004';
UPDATE "public"."issue_status" SET "sort_order" = 25 WHERE "id" = 'b1000000-0000-4000-8000-000000000005';

-- ---------- 3. 工作流节点顺序 ----------
-- 待处理 0 / 设计中 1 / 开发中 2（不变），测试中 3（新增），已完成 3 → 4，已取消 4 → 5
UPDATE "public"."workflow_status" SET "sort_order" = 4 WHERE "id" = 'd2000000-0000-4000-8000-000000000004';
UPDATE "public"."workflow_status" SET "sort_order" = 5 WHERE "id" = 'd2000000-0000-4000-8000-000000000005';

INSERT INTO "public"."workflow_status" ("id", "workflow_id", "status_id", "sort_order") VALUES
    ('d2000000-0000-4000-8000-000000000006', 'c1000000-0000-4000-8000-000000000002', 'b1000000-0000-4000-8000-000000000006', 3)
ON CONFLICT ("workflow_id", "status_id") DO NOTHING;

-- ---------- 4. 流转边重写（9 条）----------
-- e2000000-...-01 待处理 → 设计中（开始设计）
UPDATE "public"."workflow_transition"
SET "to_status_id" = 'b1000000-0000-4000-8000-000000000002', "name" = '开始设计'
WHERE "id" = 'e2000000-0000-4000-8000-000000000001';

-- e2000000-...-02 待处理 → 已取消（取消需求，from/to 不变，仅名称对齐）
UPDATE "public"."workflow_transition"
SET "name" = '取消需求'
WHERE "id" = 'e2000000-0000-4000-8000-000000000002';

-- e2000000-...-03 设计中 → 开发中（开始开发）
UPDATE "public"."workflow_transition"
SET "from_status_id" = 'b1000000-0000-4000-8000-000000000002',
    "to_status_id" = 'b1000000-0000-4000-8000-000000000003',
    "name" = '开始开发'
WHERE "id" = 'e2000000-0000-4000-8000-000000000003';

-- e2000000-...-04 开发中 → 测试中（提交测试）
UPDATE "public"."workflow_transition"
SET "to_status_id" = 'b1000000-0000-4000-8000-000000000006', "name" = '提交测试'
WHERE "id" = 'e2000000-0000-4000-8000-000000000004';

-- e2000000-...-05 设计中 → 待处理（退回待处理）
UPDATE "public"."workflow_transition"
SET "from_status_id" = 'b1000000-0000-4000-8000-000000000002',
    "to_status_id" = 'b1000000-0000-4000-8000-000000000001',
    "name" = '退回待处理'
WHERE "id" = 'e2000000-0000-4000-8000-000000000005';

-- e2000000-...-06 设计中 → 已取消（取消需求）
UPDATE "public"."workflow_transition"
SET "from_status_id" = 'b1000000-0000-4000-8000-000000000002',
    "to_status_id" = 'b1000000-0000-4000-8000-000000000005',
    "name" = '取消需求'
WHERE "id" = 'e2000000-0000-4000-8000-000000000006';

-- e2000000-...-07 测试中 → 开发中（测试不通过）
UPDATE "public"."workflow_transition"
SET "from_status_id" = 'b1000000-0000-4000-8000-000000000006',
    "to_status_id" = 'b1000000-0000-4000-8000-000000000003',
    "name" = '测试不通过'
WHERE "id" = 'e2000000-0000-4000-8000-000000000007';

-- e2000000-...-08 已取消 → 待处理（重新打开，V32 的「已取消 → 待评审」随节点更名调整）
UPDATE "public"."workflow_transition"
SET "to_status_id" = 'b1000000-0000-4000-8000-000000000001', "name" = '重新打开'
WHERE "id" = 'e2000000-0000-4000-8000-000000000008';

-- e2000000-...-09 测试中 → 已完成（测试通过，新边）
INSERT INTO "public"."workflow_transition" ("id", "workflow_id", "from_status_id", "to_status_id", "name") VALUES
    ('e2000000-0000-4000-8000-000000000009', 'c1000000-0000-4000-8000-000000000002', 'b1000000-0000-4000-8000-000000000006', 'b1000000-0000-4000-8000-000000000004', '测试通过')
ON CONFLICT ("workflow_id", "from_status_id", "to_status_id") DO NOTHING;

-- ---------- 5. 模板描述 ----------
UPDATE "public"."workflow"
SET "description" = '待处理 → 设计中 → 开发中 → 测试中 → 已完成（已取消可重新打开）'
WHERE "id" = 'c1000000-0000-4000-8000-000000000002';

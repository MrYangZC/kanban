-- V36：历史动态里的旧状态 code 改写为现行 code
--
-- V33 把需求流节点改名为「待处理 / 设计中」并换了 code：
--   待评审 REQ_TO_REVIEW  → 待处理 REQ_TODO
--   已排期 REQ_SCHEDULED → 设计中 REQ_DESIGNING
-- 但 `issue_activity.old_value / new_value` 里存的是当时的 code（{"status":"REQ_TO_REVIEW"}），
-- 改名后字典里查不到这些 code，动态就只能显示英文 code（用户反馈「看不懂」）。
-- 这里把历史值就地改成现行 code，让动态能翻出中文名。
--
-- 注意：REQ_REJECTED（已拒绝）对应的节点在 V33 已被移除，无现行 code 可映射，
-- 故不动数据，由 ActivityTextResolver 的兜底表显示为「已拒绝」。
-- 幂等：按 IN 条件过滤，重复执行无副作用。

UPDATE "public"."issue_activity"
SET "old_value" = jsonb_build_object('status', CASE "old_value" ->> 'status'
        WHEN 'REQ_TO_REVIEW' THEN 'REQ_TODO'
        WHEN 'REQ_SCHEDULED' THEN 'REQ_DESIGNING'
        ELSE "old_value" ->> 'status' END)
WHERE "old_value" ->> 'status' IN ('REQ_TO_REVIEW', 'REQ_SCHEDULED');

UPDATE "public"."issue_activity"
SET "new_value" = jsonb_build_object('status', CASE "new_value" ->> 'status'
        WHEN 'REQ_TO_REVIEW' THEN 'REQ_TODO'
        WHEN 'REQ_SCHEDULED' THEN 'REQ_DESIGNING'
        ELSE "new_value" ->> 'status' END)
WHERE "new_value" ->> 'status' IN ('REQ_TO_REVIEW', 'REQ_SCHEDULED');

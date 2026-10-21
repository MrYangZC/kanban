-- ============================================================================
-- 工作项类型与物理表一致性体检（只读，可随时在生产库执行）
--
-- 背景：BUG-05 修复前，未知 / 未定义的 issueTypeCode 会被静默兜底为 TASK，
--       导致「类型码显示 TASK、数据却落在 requirement/bug 表」的追溯性错乱，
--       表现为看板与列表条数不一致（BUG-16）。
-- 修复后新建数据已被拦截，历史数据需用本脚本体检 + fix_workitem_type_consistency.sql 校正。
--
-- 期望结果：三段查询均返回 0 行。
-- ============================================================================

-- 1) requirement 表：类型码必须是 STORY / FEATURE
SELECT 'requirement' AS tbl,
       r.id::text       AS id,
       p.project_key,
       r.issue_number::text AS issue_number,
       COALESCE(it.code, '<null>') AS type_code,
       r.title,
       r.created_at::text AS created_at
FROM requirement r
         LEFT JOIN issue_type it ON it.id = r.issue_type_id
         LEFT JOIN project p ON p.id = r.project_id
WHERE it.code IS NULL
   OR it.code NOT IN ('STORY', 'FEATURE')
ORDER BY r.created_at;

-- 2) task 表：类型码必须是 TASK / IMPROVEMENT
SELECT 'task' AS tbl,
       t.id::text       AS id,
       p.project_key,
       t.issue_number::text AS issue_number,
       COALESCE(it.code, '<null>') AS type_code,
       t.title,
       t.created_at::text AS created_at
FROM task t
         LEFT JOIN issue_type it ON it.id = t.issue_type_id
         LEFT JOIN project p ON p.id = t.project_id
WHERE it.code IS NULL
   OR it.code NOT IN ('TASK', 'IMPROVEMENT')
ORDER BY t.created_at;

-- 3) bug 表：类型码必须是 6 个缺陷类型之一
SELECT 'bug' AS tbl,
       b.id::text       AS id,
       p.project_key,
       b.issue_number::text AS issue_number,
       COALESCE(it.code, '<null>') AS type_code,
       b.title,
       b.created_at::text AS created_at
FROM bug b
         LEFT JOIN issue_type it ON it.id = b.issue_type_id
         LEFT JOIN project p ON p.id = b.project_id
WHERE it.code IS NULL
   OR it.code NOT IN ('BUG', 'FUNC_DEFECT', 'PERF_DEFECT', 'SEC_DEFECT', 'COMPAT_DEFECT', 'UI_DEFECT')
ORDER BY b.created_at;

-- ============================================================================
-- 工作项类型错乱数据校正（一次性修数据，幂等，可重复执行）
--
-- 前置：先执行 check_workitem_type_consistency.sql，确认待修条数与影响范围。
-- 口径：
--   * requirement 表中的非需求类型 → 校正为 STORY（= requirement 类别中 sort_order 最小的真实类型，
--     与 BUG-05 修复后「REQUIREMENT 别名」的解析结果保持一致）
--   * task 表中的非任务类型 → 校正为 TASK
--   * bug 表中的非缺陷类型 → 校正为 BUG
--   * 只改 issue_type_id，不动 issue_number / status / 其他业务字段
--     （issue_number 由序列消耗，回收会造成编号重复，历史编号保留）
--
-- 注意：本脚本只修正「类型码与物理表不一致」，不会把记录搬到别的物理表。
--       若确认某条记录本应是其他类别的实体（而非类型码错误），需单独迁移行数据。
-- ============================================================================

BEGIN;

-- 校正前快照（便于回滚比对，输出到 psql 客户端）
SELECT 'before' AS stage,
       (SELECT count(*) FROM requirement r
                 JOIN issue_type it ON it.id = r.issue_type_id
        WHERE it.code NOT IN ('STORY', 'FEATURE')) AS requirement_mismatch,
       (SELECT count(*) FROM task t
                 JOIN issue_type it ON it.id = t.issue_type_id
        WHERE it.code NOT IN ('TASK', 'IMPROVEMENT')) AS task_mismatch,
       (SELECT count(*) FROM bug b
                 JOIN issue_type it ON it.id = b.issue_type_id
        WHERE it.code NOT IN ('BUG', 'FUNC_DEFECT', 'PERF_DEFECT',
                              'SEC_DEFECT', 'COMPAT_DEFECT', 'UI_DEFECT')) AS bug_mismatch;

-- 1) requirement 表 → STORY
UPDATE requirement r
SET issue_type_id = (SELECT id FROM issue_type WHERE code = 'STORY'),
    updated_at    = now()
WHERE r.issue_type_id IS NULL
   OR r.issue_type_id NOT IN (SELECT id FROM issue_type WHERE code IN ('STORY', 'FEATURE'));

-- 2) task 表 → TASK
UPDATE task t
SET issue_type_id = (SELECT id FROM issue_type WHERE code = 'TASK'),
    updated_at    = now()
WHERE t.issue_type_id IS NULL
   OR t.issue_type_id NOT IN (SELECT id FROM issue_type WHERE code IN ('TASK', 'IMPROVEMENT'));

-- 3) bug 表 → BUG
UPDATE bug b
SET issue_type_id = (SELECT id FROM issue_type WHERE code = 'BUG'),
    updated_at    = now()
WHERE b.issue_type_id IS NULL
   OR b.issue_type_id NOT IN (SELECT id FROM issue_type
                              WHERE code IN ('BUG', 'FUNC_DEFECT', 'PERF_DEFECT',
                                             'SEC_DEFECT', 'COMPAT_DEFECT', 'UI_DEFECT'));

-- 校正后校验（三段应全为 0）
SELECT 'after' AS stage,
       (SELECT count(*) FROM requirement r
                 JOIN issue_type it ON it.id = r.issue_type_id
        WHERE it.code NOT IN ('STORY', 'FEATURE')) AS requirement_mismatch,
       (SELECT count(*) FROM task t
                 JOIN issue_type it ON it.id = t.issue_type_id
        WHERE it.code NOT IN ('TASK', 'IMPROVEMENT')) AS task_mismatch,
       (SELECT count(*) FROM bug b
                 JOIN issue_type it ON it.id = b.issue_type_id
        WHERE it.code NOT IN ('BUG', 'FUNC_DEFECT', 'PERF_DEFECT',
                              'SEC_DEFECT', 'COMPAT_DEFECT', 'UI_DEFECT')) AS bug_mismatch;

COMMIT;

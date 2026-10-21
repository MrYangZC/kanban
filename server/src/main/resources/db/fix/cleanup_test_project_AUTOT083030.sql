-- ============================================================================
-- 清理 2026-10-21 功能测试遗留数据（幂等，可重复执行）
--
-- 范围：仅限测试项目 AUTOT083030「AUTO自动化测试项目」及其下工作项与子表数据。
--       该项目由测试脚本于 2026-10-21 08:30 创建，不含任何业务数据。
--
-- 不做什么：不触碰其他项目（禹舜 P161_* / P289 / P801 / 2026 / P903 等业务数据），
--           不触碰全局配置（workflow / issue_status / issue_type / menu / role / sys_user）。
--
-- 用法：psql -h <host> -p <port> -U postgres -d kanban -f cleanup_test_project_AUTOT083030.sql
-- 如只想清空工作项而保留项目本身，注释掉最后一段「删除项目本体」即可。
-- ============================================================================

BEGIN;

CREATE TEMP TABLE _test_proj ON COMMIT DROP AS
SELECT id FROM project WHERE project_key = 'AUTOT083030';

CREATE TEMP TABLE _test_wi ON COMMIT DROP AS
SELECT id FROM task WHERE project_id = (SELECT id FROM _test_proj)
UNION ALL SELECT id FROM requirement WHERE project_id = (SELECT id FROM _test_proj)
UNION ALL SELECT id FROM bug WHERE project_id = (SELECT id FROM _test_proj);

-- 清理前统计
SELECT 'before' AS stage,
       (SELECT count(*) FROM _test_wi)                    AS work_items,
       (SELECT count(*) FROM issue_comment WHERE issue_id IN (SELECT id FROM _test_wi)) AS comments,
       (SELECT count(*) FROM issue_activity WHERE issue_id IN (SELECT id FROM _test_wi)) AS activities,
       (SELECT count(*) FROM issue_relation
        WHERE issue_id IN (SELECT id FROM _test_wi) OR target_issue_id IN (SELECT id FROM _test_wi)) AS relations,
       (SELECT count(*) FROM issue_label WHERE issue_id IN (SELECT id FROM _test_wi)) AS labels,
       (SELECT count(*) FROM issue_cc WHERE issue_id IN (SELECT id FROM _test_wi)) AS cc_users,
       (SELECT count(*) FROM issue_participant WHERE issue_id IN (SELECT id FROM _test_wi)) AS participants,
       (SELECT count(*) FROM attachment WHERE issue_id IN (SELECT id FROM _test_wi)) AS attachments;

-- 1) 子表（先删子后删主，避免外键阻塞）
DELETE FROM issue_comment  WHERE issue_id IN (SELECT id FROM _test_wi);
DELETE FROM issue_activity WHERE issue_id IN (SELECT id FROM _test_wi);
DELETE FROM issue_relation
WHERE issue_id IN (SELECT id FROM _test_wi)
   OR target_issue_id IN (SELECT id FROM _test_wi);
DELETE FROM issue_label   WHERE issue_id IN (SELECT id FROM _test_wi);
DELETE FROM issue_cc      WHERE issue_id IN (SELECT id FROM _test_wi);
DELETE FROM issue_participant WHERE issue_id IN (SELECT id FROM _test_wi);
DELETE FROM attachment    WHERE issue_id IN (SELECT id FROM _test_wi);

-- 2) 工作项主表
DELETE FROM bug         WHERE project_id = (SELECT id FROM _test_proj);
DELETE FROM requirement WHERE project_id = (SELECT id FROM _test_proj);
DELETE FROM task        WHERE project_id = (SELECT id FROM _test_proj);

-- 3) 迭代 / 里程碑 / 看板 / 成员
DELETE FROM sprint     WHERE project_id = (SELECT id FROM _test_proj);
DELETE FROM milestone  WHERE project_id = (SELECT id FROM _test_proj);
DELETE FROM board      WHERE project_id = (SELECT id FROM _test_proj);
DELETE FROM project_member WHERE project_id = (SELECT id FROM _test_proj);
DELETE FROM project_favorite WHERE project_id = (SELECT id FROM _test_proj);

-- 4) 删除项目本体（想保留项目请注释本段）
DELETE FROM project WHERE id = (SELECT id FROM _test_proj);

-- 清理后校验：应全部为 0，且 AUTOT083030 项目不再存在
SELECT 'after' AS stage,
       (SELECT count(*) FROM project WHERE project_key = 'AUTOT083030') AS project_rows,
       (SELECT count(*) FROM task t JOIN project p ON p.id = t.project_id WHERE p.project_key = 'AUTOT083030') AS task_rows,
       (SELECT count(*) FROM requirement r JOIN project p ON p.id = r.project_id WHERE p.project_key = 'AUTOT083030') AS requirement_rows,
       (SELECT count(*) FROM bug b JOIN project p ON p.id = b.project_id WHERE p.project_key = 'AUTOT083030') AS bug_rows;

COMMIT;

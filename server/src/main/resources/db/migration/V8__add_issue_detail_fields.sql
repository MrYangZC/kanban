-- 缺陷详情页（云效风格）基础字段扩展
ALTER TABLE issue ADD COLUMN IF NOT EXISTS iteration VARCHAR(100);
ALTER TABLE issue ADD COLUMN IF NOT EXISTS participants JSONB NOT NULL DEFAULT '[]';
ALTER TABLE issue ADD COLUMN IF NOT EXISTS cc_users JSONB NOT NULL DEFAULT '[]';
ALTER TABLE issue ADD COLUMN IF NOT EXISTS plan_start TIMESTAMP;
ALTER TABLE issue ADD COLUMN IF NOT EXISTS no_fix_reason VARCHAR(1000);

-- 状态流转矩阵调整：暂不修复 → 已关闭 禁止（与看板拖拽约束一致）
DELETE FROM workflow_transition wt
USING issue_status fs, issue_status ts
WHERE wt.from_status_id = fs.id
  AND wt.to_status_id = ts.id
  AND fs.code = 'WONT_FIX'
  AND ts.code = 'CLOSED';

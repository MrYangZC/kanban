-- V10: 约束修正与附件真实上传支持
-- 注：V9 已被 RBAC 重构使用，本脚本编号顺延为 V10

-- 1. 清理历史数据：每个项目仅保留一条默认工作流（避免唯一索引建失败）
DELETE FROM workflow
WHERE id IN (
    SELECT id FROM (
        SELECT id,
               ROW_NUMBER() OVER (PARTITION BY project_id ORDER BY created_at) AS rn
        FROM workflow
        WHERE is_default = TRUE
    ) t
    WHERE t.rn > 1
);

-- 2. 每个项目最多一个默认工作流（部分唯一索引，幂等）
CREATE UNIQUE INDEX IF NOT EXISTS uk_workflow_default_per_project
    ON workflow (project_id)
    WHERE is_default = TRUE;

-- 3. 附件真实上传：url 字段承载可访问地址（兼容存量数据，允许 NULL）
ALTER TABLE attachment ADD COLUMN IF NOT EXISTS url VARCHAR(1000);

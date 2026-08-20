-- V14: 迭代(Sprint)模块
-- 1) issue 表补 sprint_id(关联迭代),沿用已有 iteration 自由文本字段保留兼容
ALTER TABLE issue ADD COLUMN IF NOT EXISTS sprint_id UUID;
-- 防御性补齐逻辑删除列（部分历史库 issue 表可能缺 deleted，导致下方部分索引报错）
ALTER TABLE issue ADD COLUMN IF NOT EXISTS deleted INTEGER NOT NULL DEFAULT 0;
CREATE INDEX IF NOT EXISTS idx_issue_sprint ON issue (sprint_id) WHERE deleted = 0;

-- 2) sprint 表（兼容旧库：若表已存在则补齐缺失列，而非重建）
CREATE TABLE IF NOT EXISTS sprint (
    id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    project_id    UUID NOT NULL REFERENCES project (id),
    name          VARCHAR(100) NOT NULL,
    goal          TEXT,
    status        VARCHAR(20)  NOT NULL DEFAULT 'PLANNED',
    start_date    DATE,
    end_date      DATE,
    created_at    TIMESTAMP NOT NULL DEFAULT now(),
    updated_at    TIMESTAMP NOT NULL DEFAULT now(),
    created_by    UUID,
    updated_by    UUID,
    deleted       INTEGER   NOT NULL DEFAULT 0
);

-- 防御性补齐：部分历史库 sprint 表可能缺下列字段（CREATE TABLE IF NOT EXISTS 不会修改已有表）
ALTER TABLE sprint ADD COLUMN IF NOT EXISTS goal        TEXT;
ALTER TABLE sprint ADD COLUMN IF NOT EXISTS status      VARCHAR(20)  NOT NULL DEFAULT 'PLANNED';
ALTER TABLE sprint ADD COLUMN IF NOT EXISTS start_date  DATE;
ALTER TABLE sprint ADD COLUMN IF NOT EXISTS end_date    DATE;
ALTER TABLE sprint ADD COLUMN IF NOT EXISTS created_at  TIMESTAMP NOT NULL DEFAULT now();
ALTER TABLE sprint ADD COLUMN IF NOT EXISTS updated_at  TIMESTAMP NOT NULL DEFAULT now();
ALTER TABLE sprint ADD COLUMN IF NOT EXISTS created_by  UUID;
ALTER TABLE sprint ADD COLUMN IF NOT EXISTS updated_by  UUID;
ALTER TABLE sprint ADD COLUMN IF NOT EXISTS deleted     INTEGER   NOT NULL DEFAULT 0;

CREATE INDEX IF NOT EXISTS idx_sprint_project ON sprint (project_id) WHERE deleted = 0;
CREATE INDEX IF NOT EXISTS idx_sprint_status  ON sprint (status) WHERE deleted = 0;

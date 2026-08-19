-- 里程碑表
CREATE TABLE IF NOT EXISTS milestone (
    id           UUID PRIMARY KEY,
    project_id   UUID NOT NULL,
    name         VARCHAR(200) NOT NULL,
    description  TEXT,
    due_date     DATE,
    status       VARCHAR(20) NOT NULL DEFAULT 'OPEN',
    progress     INTEGER NOT NULL DEFAULT 0,
    created_by   UUID,
    updated_by   UUID,
    created_at   TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at   TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted      INTEGER NOT NULL DEFAULT 0
);
CREATE INDEX IF NOT EXISTS idx_milestone_project ON milestone (project_id) WHERE deleted = 0;

-- 项目公告表
CREATE TABLE IF NOT EXISTS announcement (
    id           UUID PRIMARY KEY,
    project_id   UUID NOT NULL,
    title        VARCHAR(200) NOT NULL,
    content      TEXT,
    pinned       INTEGER NOT NULL DEFAULT 0,
    created_by   UUID,
    updated_by   UUID,
    created_at   TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at   TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted      INTEGER NOT NULL DEFAULT 0
);
CREATE INDEX IF NOT EXISTS idx_announcement_project ON announcement (project_id) WHERE deleted = 0;
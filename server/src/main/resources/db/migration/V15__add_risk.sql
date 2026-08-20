-- V15: 风险(Risk)模块(项目级风险)
CREATE TABLE IF NOT EXISTS risk (
    id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    project_id    UUID NOT NULL REFERENCES project (id),
    title         VARCHAR(200) NOT NULL,
    description   TEXT,
    level         VARCHAR(20)  NOT NULL DEFAULT 'MEDIUM',
    status        VARCHAR(20)  NOT NULL DEFAULT 'OPEN',
    owner_id      UUID,
    due_date      DATE,
    created_at    TIMESTAMP NOT NULL DEFAULT now(),
    updated_at    TIMESTAMP NOT NULL DEFAULT now(),
    created_by    UUID,
    updated_by    UUID,
    deleted       INTEGER   NOT NULL DEFAULT 0
);

CREATE INDEX IF NOT EXISTS idx_risk_project ON risk (project_id) WHERE deleted = 0;
CREATE INDEX IF NOT EXISTS idx_risk_status  ON risk (status) WHERE deleted = 0;
CREATE INDEX IF NOT EXISTS idx_risk_level   ON risk (level) WHERE deleted = 0;

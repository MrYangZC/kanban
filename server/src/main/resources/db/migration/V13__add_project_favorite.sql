-- V13: 项目收藏关系表
-- 支持用户收藏项目，用于项目列表页"我收藏的"分类筛选。
-- 唯一约束 (project_id, user_id) 防止重复收藏；idx_user_id 加速按用户查询收藏列表。

CREATE TABLE IF NOT EXISTS project_favorite (
    id          UUID PRIMARY KEY,
    project_id  UUID NOT NULL,
    user_id     UUID NOT NULL,
    created_at  TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- 唯一约束：同一用户对同一项目仅可收藏一次（幂等）
DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint
        WHERE conname = 'uk_project_favorite'
    ) THEN
        ALTER TABLE project_favorite
            ADD CONSTRAINT uk_project_favorite UNIQUE (project_id, user_id);
    END IF;
END $$;

-- 外键：收藏记录指向真实存在的项目（ON DELETE CASCADE 保证物理删除时自动清理，逻辑删除不触发）
DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint
        WHERE conname = 'fk_project_favorite_project'
    ) THEN
        ALTER TABLE project_favorite
            ADD CONSTRAINT fk_project_favorite_project
            FOREIGN KEY (project_id) REFERENCES project (id)
            ON DELETE CASCADE;
    END IF;
END $$;

-- 索引：按用户查询其收藏列表（高频查询路径）
CREATE INDEX IF NOT EXISTS idx_project_favorite_user
    ON project_favorite (user_id);

-- 索引：按项目查询收藏该项目的用户列表（用于项目删除时清理等场景）
CREATE INDEX IF NOT EXISTS idx_project_favorite_project
    ON project_favorite (project_id);
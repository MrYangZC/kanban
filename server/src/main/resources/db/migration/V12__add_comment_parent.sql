-- V12: 评论嵌套回复支持
-- 新增 parent_id 自引用字段；顶楼评论为 NULL，回复指向父评论。
-- ON DELETE RESTRICT：即使误物理删父评论，子回复也不会被级联删除（本项目用逻辑删除，双保险）。

ALTER TABLE issue_comment ADD COLUMN IF NOT EXISTS parent_id UUID;

-- 自引用外键（幂等）
DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint
        WHERE conname = 'fk_issue_comment_parent'
    ) THEN
        ALTER TABLE issue_comment
            ADD CONSTRAINT fk_issue_comment_parent
            FOREIGN KEY (parent_id) REFERENCES issue_comment (id)
            ON DELETE RESTRICT;
    END IF;
END $$;

CREATE INDEX IF NOT EXISTS idx_issue_comment_parent
    ON issue_comment (parent_id);

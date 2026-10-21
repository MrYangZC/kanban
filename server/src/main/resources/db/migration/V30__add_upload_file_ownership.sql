-- ============================================================================
-- V30  通用图片上传的归属登记（修复 /api/uploads/** 横向越权）
-- ----------------------------------------------------------------------------
-- 背景：
--   /v1/upload/image 产生的图片（评论/描述内嵌图、头像）没有登记归属，
--   UploadsAuthInterceptor 只能退化为「登录即可访问」，任何登录用户拿到直链
--   就能读取他人上传的图片。
-- 本脚本：
--   新增 upload_file 表记录文件名、上传者与可选项目归属，供拦截器按
--   「项目成员」维度校验；project_id 为空表示全站共享资源（如头像）。
-- 幂等：CREATE TABLE IF NOT EXISTS + CREATE INDEX IF NOT EXISTS。
-- ============================================================================

CREATE TABLE IF NOT EXISTS "public"."upload_file" (
    id           UUID PRIMARY KEY,
    filename     VARCHAR(160) NOT NULL,
    uploader_id  UUID NOT NULL,
    project_id   UUID,
    created_at   TIMESTAMP NOT NULL DEFAULT now()
);

-- 文件名唯一：同一文件名只允许一条归属记录
CREATE UNIQUE INDEX IF NOT EXISTS uk_upload_file_filename
    ON "public"."upload_file" (filename);

CREATE INDEX IF NOT EXISTS idx_upload_file_project
    ON "public"."upload_file" (project_id);

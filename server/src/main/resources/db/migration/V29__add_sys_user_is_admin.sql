-- 系统管理员标识：修复 @RequireAdmin 误用"任一项目 OWNER/ADMIN"导致的提权漏洞。
-- 系统管理员改为 sys_user.is_admin 独立标识，与项目级角色彻底解耦。
ALTER TABLE "public"."sys_user" ADD COLUMN "is_admin" bool NOT NULL DEFAULT false;

-- 种子管理员置为系统管理员
UPDATE "public"."sys_user" SET "is_admin" = true WHERE "username" = 'admin';
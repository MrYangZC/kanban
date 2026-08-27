-- ============================================================
-- V24: 修复 RBAC 权限种子数据
-- 1. MEMBER 角色补充: sprint:manage, milestone:manage, risk:view, announcement:manage
-- 2. VIEWER 角色移除错误授予的: risk:manage, sprint:manage
-- ============================================================

-- 1. MEMBER (role_id = 00000000-0000-0000-0000-000000000016) 补充缺失权限
INSERT INTO "public"."role_permission" (role_id, permission_code, created_at)
SELECT '00000000-0000-0000-0000-000000000016', 'sprint:manage', now()
WHERE NOT EXISTS (
    SELECT 1 FROM "public"."role_permission"
    WHERE role_id = '00000000-0000-0000-0000-000000000016' AND permission_code = 'sprint:manage'
);

INSERT INTO "public"."role_permission" (role_id, permission_code, created_at)
SELECT '00000000-0000-0000-0000-000000000016', 'milestone:manage', now()
WHERE NOT EXISTS (
    SELECT 1 FROM "public"."role_permission"
    WHERE role_id = '00000000-0000-0000-0000-000000000016' AND permission_code = 'milestone:manage'
);

INSERT INTO "public"."role_permission" (role_id, permission_code, created_at)
SELECT '00000000-0000-0000-0000-000000000016', 'risk:view', now()
WHERE NOT EXISTS (
    SELECT 1 FROM "public"."role_permission"
    WHERE role_id = '00000000-0000-0000-0000-000000000016' AND permission_code = 'risk:view'
);

INSERT INTO "public"."role_permission" (role_id, permission_code, created_at)
SELECT '00000000-0000-0000-0000-000000000016', 'announcement:manage', now()
WHERE NOT EXISTS (
    SELECT 1 FROM "public"."role_permission"
    WHERE role_id = '00000000-0000-0000-0000-000000000016' AND permission_code = 'announcement:manage'
);

-- 2. VIEWER (role_id = 00000000-0000-0000-0000-000000000017) 移除错误权限
DELETE FROM "public"."role_permission"
WHERE role_id = '00000000-0000-0000-0000-000000000017'
  AND permission_code IN ('risk:manage', 'sprint:manage');
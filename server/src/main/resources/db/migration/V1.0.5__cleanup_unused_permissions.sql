-- ----------------------------
-- V1.0.5 清理"定义了但从未生效"的权限码
--
-- 背景：RBAC 权限矩阵里有一批权限码能勾选、能保存，但没有任何接口引用它们，
-- 勾了也没有任何效果（管理员以为收回了权限，实际什么也没变）。
-- 修复方式：要么补功能，要么删码。本迁移删除确实没有对应功能的码；
-- 有功能的已经接上真实校验（issue:status / issue:assign / issue:archive /
-- project:archive / project:delete / board:view / issue:export）。
--
-- 删除的码与原因：
--   board:manage  看板列配置接口不存在（看板列由工作流模板决定）
--   project:role  项目级角色权限配置不对外开放，RBAC 是系统管理员专属
--   risk:create / risk:edit / risk:delete  风险增删改统一用 risk:manage
--   project:notify  通知设置功能不存在
--   view:manage   保存筛选/自定义视图功能不存在
--
-- 语句幂等，可重复执行。
-- ----------------------------

DELETE FROM "public"."role_permission"
WHERE "permission_code" IN (
    'board:manage',
    'project:role',
    'risk:create',
    'risk:edit',
    'risk:delete',
    'project:notify',
    'view:manage'
);

DELETE FROM "public"."permission"
WHERE "code" IN (
    'board:manage',
    'project:role',
    'risk:create',
    'risk:edit',
    'risk:delete',
    'project:notify',
    'view:manage'
);

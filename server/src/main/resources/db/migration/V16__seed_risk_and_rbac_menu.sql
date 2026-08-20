-- V16: 补充风险模块权限点与全局 RBAC 管理后台菜单种子
-- 1) 风险模块权限点
INSERT INTO menu (id, code, name, parent_id, type, sort_order, created_at, updated_at, deleted)
VALUES
    ('00000000-0000-0000-0000-000000000030', 'RISK_MANAGE', '风险管理', NULL, 'BUTTON', 10, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0)
ON CONFLICT (code) DO NOTHING;

INSERT INTO role_menu (id, role_id, menu_id, created_at)
SELECT gen_random_uuid(), r.id, m.id, CURRENT_TIMESTAMP
FROM role r
JOIN menu m ON m.code = 'RISK_MANAGE'
WHERE r.code IN ('OWNER', 'ADMIN', 'PM')
ON CONFLICT DO NOTHING;

-- 2) 全局 RBAC 管理后台菜单（系统管理员可见，用于配置角色/菜单/权限）
INSERT INTO menu (id, code, name, parent_id, type, sort_order, created_at, updated_at, deleted)
VALUES
    ('00000000-0000-0000-0000-000000000040', 'RBAC_MANAGE', '权限管理', NULL, 'MENU',   50, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0),
    ('00000000-0000-0000-0000-000000000041', 'RBAC_ROLE',   '角色管理', '00000000-0000-0000-0000-000000000040', 'MENU', 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0),
    ('00000000-0000-0000-0000-000000000042', 'RBAC_MENU',   '菜单与权限', '00000000-0000-0000-0000-000000000040', 'MENU', 2, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0),
    ('00000000-0000-0000-0000-000000000043', 'RBAC_USER_ROLE', '用户角色', '00000000-0000-0000-0000-000000000040', 'MENU', 3, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0)
ON CONFLICT (code) DO NOTHING;

INSERT INTO role_menu (id, role_id, menu_id, created_at)
SELECT gen_random_uuid(), r.id, m.id, CURRENT_TIMESTAMP
FROM role r
JOIN menu m ON m.parent_id = '00000000-0000-0000-0000-000000000040'
WHERE r.code IN ('OWNER', 'ADMIN')
ON CONFLICT DO NOTHING;

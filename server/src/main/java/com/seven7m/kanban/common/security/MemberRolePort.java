package com.seven7m.kanban.common.security;

import java.util.Set;
import java.util.UUID;

/**
 * 项目成员角色的只读端口。
 *
 * <p>`common`（安全层）不应该知道 `service.project` 这层业务实现：此前
 * {@link PermissionService} 直接依赖 `ProjectMemberService`，导致公共层反向引用业务层，
 * 单元测试与后续拆分都会被牵着走。这里只声明"我需要某个用户在某个项目下的角色 id 集合"，
 * 由业务层实现（见 {@code ProjectMemberService}）。
 */
public interface MemberRolePort {

    /** 用户在指定项目下的角色 id 集合；非成员返回空集合 */
    Set<UUID> roleIdsOf(UUID projectId, UUID userId);
}

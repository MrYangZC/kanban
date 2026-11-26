package com.seven7m.kanban.service.project;

import com.baomidou.mybatisplus.extension.service.IService;
import com.seven7m.kanban.dto.project.AddMemberRequest;
import com.seven7m.kanban.dto.project.MemberCandidateVO;
import com.seven7m.kanban.dto.project.ProjectMemberVO;
import com.seven7m.kanban.entity.project.ProjectMember;

import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * 项目成员服务：花名册、候选用户、增删成员、改角色，以及角色/角色 id 的读取与缓存失效。
 *
 * <p>继承 {@link IService} 复用 MyBatis-Plus 的通用能力（按条件取、保存、删除、计数），
 * 领域方法（成员增删改、角色缓存、授权）仍在此声明。
 *
 * <p>{@code common} 安全层通过 {@code MemberRolePort} 端口取角色 id，实现
 * {@code ProjectMemberServiceImpl} 同时实现该端口——端口不在此接口上，避免公共层反向依赖业务接口。
 *
 * <p>实现见 {@code com.seven7m.kanban.service.project.impl.ProjectMemberServiceImpl}。
 */
public interface ProjectMemberService extends IService<ProjectMember> {

    /** 项目成员列表（含用户展示信息与角色集合），按加入时间排序 */
    List<ProjectMemberVO> list(UUID projectId);

    /**
     * 「添加成员」的可选用户：尚未加入该项目的用户，按关键字（账号/显示名）模糊匹配。
     * limit 会被夹到 [1, 50]。
     */
    List<MemberCandidateVO> candidates(UUID projectId, String keyword, int limit);

    /** 添加成员并授予角色（需成员维护权限，见 project:member 口径） */
    ProjectMemberVO add(UUID projectId, AddMemberRequest req);

    /** 移除成员，连同其项目角色一起清理；不允许移除最后一个所有者 */
    void remove(UUID projectId, UUID userId);

    /** 全量覆盖成员角色（先删后插）；不允许把最后一个所有者降级 */
    ProjectMemberVO updateRoles(UUID projectId, UUID userId, List<String> roles);

    /** 查询用户在指定项目中的角色编码集合（带短 TTL 缓存），非成员返回空集合 */
    Set<String> getRoles(UUID projectId, UUID userId);

    /** 查询用户在指定项目中的角色 id 集合（供 role_permission 按 role_id 联查权限） */
    Set<UUID> getRoleIds(UUID projectId, UUID userId);

    /** 角色变更后使缓存失效；userId 为 null 表示该项目下全部用户 */
    void evictRoleCache(UUID projectId, UUID userId);

    /** 写入用户-角色关联（RBAC：一人可多角色），供项目创建等内部逻辑调用 */
    void grantRoles(UUID projectId, UUID userId, List<String> roles);
}

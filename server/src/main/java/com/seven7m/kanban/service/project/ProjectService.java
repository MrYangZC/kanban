package com.seven7m.kanban.service.project;

import com.baomidou.mybatisplus.extension.service.IService;
import com.seven7m.kanban.common.domain.PageResult;
import com.seven7m.kanban.dto.project.CreateProjectRequest;
import com.seven7m.kanban.dto.project.ProjectCategoryCountsVO;
import com.seven7m.kanban.dto.project.ProjectFilterRequest;
import com.seven7m.kanban.dto.project.ProjectVO;
import com.seven7m.kanban.dto.project.UpdateProjectRequest;
import com.seven7m.kanban.entity.project.Project;
import com.seven7m.kanban.entity.project.ProjectCategoryKey;

import java.util.List;
import java.util.UUID;

/**
 * 项目（project 表）的读写服务：创建/修改/列表/详情/归档/删除，以及成员与权限判定入口。
 *
 * <p>继承 {@link IService} 是为了复用 MyBatis-Plus 的通用能力（按 id 取、保存、更新、删除、
 * 条件查询、计数），实现里不必再重复 {@code mapper.xxx} 的样板；项目域的领域方法仍在此声明。
 *
 * <p>可见性口径统一由 {@link ProjectQueryScope} 表达，本接口的列表方法都走它，不在实现里另起一套。
 *
 * <p>实现见 {@code com.seven7m.kanban.service.project.impl.ProjectServiceImpl}。
 */
public interface ProjectService extends IService<Project> {

    /** 新建项目：Key 查重 -> 落库 -> 建所有者成员行 + 授权 OWNER -> 初始化默认看板/工作流 */
    ProjectVO create(CreateProjectRequest req);

    /** 修改项目基本信息；需 project:setting（WRITE 及以上），无权限抛 403 */
    ProjectVO update(UUID id, UpdateProjectRequest req);

    /** 查询当前用户可见的全部项目（向后兼容入口，等价于 {@link #listAll(ProjectCategoryKey)} 的 ALL） */
    List<ProjectVO> listAll();

    /** 按分类查询当前用户可见的项目列表（非分页），按创建时间倒序 */
    List<ProjectVO> listAll(ProjectCategoryKey category);

    /** 分页查询当前用户可见的项目，支持分类筛选、关键词模糊匹配与排序白名单 */
    PageResult<ProjectVO> listByFilter(ProjectFilterRequest req);

    /** 查询当前用户五个分类的项目计数，满足包含关系：managed ≤ participated ≤ all */
    ProjectCategoryCountsVO getCategoryCounts();

    /** 项目详情；不存在抛 404，非成员抛 403 */
    ProjectVO get(UUID id);

    /** 归档项目：仅项目管理员（OWNER/ADMIN），项目须为 ACTIVE */
    void archive(UUID projectId, UUID userId);

    /** 取消归档：仅项目管理员（OWNER/ADMIN），项目须为 ARCHIVED */
    void unarchive(UUID projectId, UUID userId);

    /** 项目 Key 是否已被占用（导入等场景用） */
    boolean existsByKey(String projectKey);

    /** 删除项目：仅项目管理员（OWNER/ADMIN）；项目下有工作项时拒绝，避免静默级联清空 */
    void delete(UUID projectId, UUID userId);

    /** 是否真正登记在项目成员名册中（不做超管豁免，供"重复添加/移除成员"判定用） */
    boolean hasMemberRecord(UUID projectId, UUID userId);

    /** 是否有该项目访问权：超管隐式具备成员身份，其余人须在成员名册中 */
    boolean isMember(UUID projectId, UUID userId);

    /** 是否项目所有者（OWNER，超管豁免） */
    boolean isOwner(UUID projectId, UUID userId);

    /** 是否项目管理员（OWNER/ADMIN，超管豁免） */
    boolean isAdmin(UUID projectId, UUID userId);

    /** 是否可写（任一参与型角色，超管豁免） */
    boolean canWrite(UUID projectId, UUID userId);

    /** 是否可管理成员与项目设置（OWNER/ADMIN/PM，超管豁免） */
    boolean canManage(UUID projectId, UUID userId);
}

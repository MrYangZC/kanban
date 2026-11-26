package com.seven7m.kanban.service.workitem;

import com.seven7m.kanban.common.domain.PageResult;
import com.seven7m.kanban.common.enums.ItemCategory;
import com.seven7m.kanban.dto.issue.CreateIssueRequest;
import com.seven7m.kanban.dto.issue.IssueFilterRequest;
import com.seven7m.kanban.dto.issue.IssueVO;
import com.seven7m.kanban.dto.issue.MoveIssueRequest;
import com.seven7m.kanban.dto.issue.UpdateIssueRequest;

import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * 单个工作项类别（任务 / 需求 / 缺陷）的业务接口。
 *
 * <p>三类工作项落在三张物理表（task / requirement / bug），但对外契约完全一致：
 * 列表、详情、创建、更新、拖拽移动、删除、交接撤销。接口定在这里，
 * 具体实现由 {@code impl.AbstractWorkItemService} 提供模板，
 * {@link TaskService} / {@link RequirementService} / {@link BugService} 各自绑定一张表。
 *
 * <p>为什么不直接让调用方注入三个实现类：调用方只关心"拿到某一类工作项的能力"，
 * 依赖接口可以把"哪张表、哪个 Mapper、编号怎么生成"这些细节关在实现里；
 * 测试也能面向接口打桩，不必构造整个 Mapper 依赖链。
 */
public interface WorkItemService {

    /** 本服务负责的类别（决定落哪张表） */
    ItemCategory category();

    /** 按筛选条件分页查询（单类别内分页；跨类别合并由 {@link WorkItemRouter} 负责） */
    PageResult<IssueVO> listByFilter(IssueFilterRequest req);

    /** 详情；不存在抛 404 */
    IssueVO getById(UUID id);

    /** 轻量查询：只回 id/项目/编号/标题，不存在返回 null（关联展示、权限解析等高频场景） */
    IssueVO getBriefById(UUID id);

    /** 工作项所属项目 id，不存在返回 null（跨三张表定位，供权限拦截器使用） */
    UUID getProjectId(UUID issueId);

    /** 子工作项列表（同表同父） */
    List<IssueVO> listChildren(UUID issueId);

    /** 新建工作项 */
    IssueVO create(CreateIssueRequest req);

    /** 更新工作项（非空即更新 + clearFields 显式清空 + 状态流转） */
    IssueVO update(UUID id, UpdateIssueRequest req);

    /** 看板拖拽移动（含列内排序与交接落点） */
    IssueVO move(UUID id, MoveIssueRequest req);

    /** 删除该工作项及其全部子工作项 */
    void delete(UUID id);

    /** 只删给定 id 本身，不连带子孙（批量删除场景，子孙由调用方上提） */
    void deleteExactly(Collection<UUID> ids);

    /** 把"即将被删除节点"下、且不在删除集合里的子任务上提到最近的存活祖先，返回上提数量 */
    int promoteOrphanChildren(Set<UUID> deleting);

    /** 删除某项目下本表的全部工作项，返回删除条数 */
    int deleteAllByProject(UUID projectId);

    /** 看板列数据源：项目下未归档的全部工作项 */
    List<IssueVO> selectByProjectForBoard(UUID projectId);

    /** 撤销最近一次工作流交接 */
    IssueVO revokeHandoff(UUID id);
}

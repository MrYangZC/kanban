package com.seven7m.kanban.service.workitem;

import com.seven7m.kanban.common.domain.PageResult;
import com.seven7m.kanban.dto.issue.CreateIssueRequest;
import com.seven7m.kanban.dto.issue.IssueFilterRequest;
import com.seven7m.kanban.dto.issue.IssueVO;
import com.seven7m.kanban.dto.issue.MoveIssueRequest;
import com.seven7m.kanban.dto.issue.UpdateIssueRequest;
import com.seven7m.kanban.entity.issue.IssueType;
import com.seven7m.kanban.entity.workitem.AbstractWorkItem;
import com.seven7m.kanban.mapper.issue.IssueTypeMapper;
import com.seven7m.kanban.mapper.workitem.BugMapper;
import com.seven7m.kanban.mapper.workitem.RequirementMapper;
import com.seven7m.kanban.mapper.workitem.TaskMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

/**
 * 工作项路由 Facade：根据工作项 id 或过滤条件中的类别，把请求分发到
 * TaskService / RequirementService / BugService，对外保持原有 IssueService 的调用契约。
 * 类别定位逻辑保证"任务只会落到 task 表，缺陷只会落到 bug 表"，从路由层杜绝跨表混数据。
 */
@Service
@RequiredArgsConstructor
public class WorkItemRouter {

    private final TaskService taskService;
    private final RequirementService requirementService;
    private final BugService bugService;
    private final TaskMapper taskMapper;
    private final RequirementMapper requirementMapper;
    private final BugMapper bugMapper;
    private final IssueTypeMapper issueTypeMapper;

    /** 定位某 id 所属类别对应的 Service；不存在返回 null */
    private BaseWorkItemService<? extends AbstractWorkItem> locateOrNull(UUID id) {
        if (taskMapper.selectById(id) != null) return taskService;
        if (requirementMapper.selectById(id) != null) return requirementService;
        if (bugMapper.selectById(id) != null) return bugService;
        return null;
    }

    /** 定位某 id 所属类别对应的 Service；不存在抛 404 */
    private BaseWorkItemService<? extends AbstractWorkItem> locate(UUID id) {
        BaseWorkItemService<? extends AbstractWorkItem> svc = locateOrNull(id);
        if (svc == null) throw new com.seven7m.kanban.common.exception.ServiceException(404, "工作项不存在");
        return svc;
    }

    public IssueVO getById(UUID id) {
        return locate(id).getById(id);
    }

    /** 轻量查询：仅主表 id/projectId/issueNumber/title，不存在返回 null（供关联展示/权限解析） */
    public IssueVO getBriefById(UUID id) {
        BaseWorkItemService<? extends AbstractWorkItem> svc = locateOrNull(id);
        return svc == null ? null : svc.getBriefById(id);
    }

    public IssueVO create(CreateIssueRequest req) {
        return routeByTypeCode(req.getIssueTypeCode()).create(req);
    }

    public IssueVO update(UUID id, UpdateIssueRequest req) {
        return locate(id).update(id, req);
    }

    public IssueVO move(UUID id, MoveIssueRequest req) {
        return locate(id).move(id, req);
    }

    public void delete(UUID id) {
        locate(id).delete(id);
    }

    public PageResult<IssueVO> listByFilter(IssueFilterRequest req) {
        return routeByTypeCodes(req.getIssueTypeCodes()).listByFilter(req);
    }

    public List<IssueVO> listChildren(UUID id) {
        return locate(id).listChildren(id);
    }

    public UUID getProjectId(UUID id) {
        UUID pid;
        if ((pid = taskMapper.selectProjectId(id)) != null) return pid;
        if ((pid = requirementMapper.selectProjectId(id)) != null) return pid;
        if ((pid = bugMapper.selectProjectId(id)) != null) return pid;
        return null;
    }

    /** 返回某工作项所属类别（TASK/REQUIREMENT/BUG），不存在返回 null */
    public com.seven7m.kanban.common.enums.ItemCategory categoryOf(UUID id) {
        if (taskMapper.selectById(id) != null) return com.seven7m.kanban.common.enums.ItemCategory.TASK;
        if (requirementMapper.selectById(id) != null) return com.seven7m.kanban.common.enums.ItemCategory.REQUIREMENT;
        if (bugMapper.selectById(id) != null) return com.seven7m.kanban.common.enums.ItemCategory.BUG;
        return null;
    }

    /** 删除项目时清理该项目下的全部工作项（任务/需求/缺陷），含子表级联清理 */
    @org.springframework.transaction.annotation.Transactional
    public void deleteByProject(UUID projectId) {
        for (UUID id : taskMapper.selectIdsByProject(projectId)) taskService.delete(id);
        for (UUID id : requirementMapper.selectIdsByProject(projectId)) requirementService.delete(id);
        for (UUID id : bugMapper.selectIdsByProject(projectId)) bugService.delete(id);
    }

    /** 根据 typeCode（如 TASK/BUG）路由到对应 Service；无法识别时默认任务（最安全） */
    private BaseWorkItemService<? extends AbstractWorkItem> routeByTypeCode(String code) {
        if (code == null) return taskService;
        return switch (code.toUpperCase()) {
            case "TASK" -> taskService;
            case "STORY", "FEATURE", "REQUIREMENT" -> requirementService;
            case "BUG", "FUNC_DEFECT", "PERF_DEFECT", "SEC_DEFECT", "COMPAT_DEFECT", "UI_DEFECT" -> bugService;
            default -> taskService;
        };
    }

    private BaseWorkItemService<? extends AbstractWorkItem> routeByTypeCodes(List<String> codes) {
        if (codes == null || codes.isEmpty()) return taskService;
        return routeByTypeCode(codes.get(0));
    }
}

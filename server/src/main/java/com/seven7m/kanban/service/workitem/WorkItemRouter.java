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
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * 工作项路由 Facade：根据工作项 id 或过滤条件中的类别，把请求分发到
 * TaskService / RequirementService / BugService，对外保持原有 IssueService 的调用契约。
 * 类别定位逻辑保证"任务只会落到 task 表，缺陷只会落到 bug 表"，从路由层杜绝跨表混数据。
 */
@Slf4j
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
        String code = req.getIssueTypeCode();
        // 只传 issueTypeId 时先反查类型编码，避免落表与非 TASK 类型不一致
        if ((code == null || code.isBlank()) && req.getIssueTypeId() != null) {
            IssueType type = issueTypeMapper.selectById(req.getIssueTypeId());
            if (type == null) throw new com.seven7m.kanban.common.exception.ServiceException(400, "工作项类型不存在");
            code = type.getCode();
        }
        return routeByTypeCode(code).create(req);
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
        List<BaseWorkItemService<? extends AbstractWorkItem>> services = resolveServices(req.getIssueTypeCodes());
        if (services.size() == 1) {
            return services.get(0).listByFilter(req);
        }
        // 涉及多张物理表（含"全部"场景），SQL 层无法跨表分页，交由下面合并处理
        return mergeListByFilter(services, req);
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

    /** 统计项目下工作项总数（含已归档），供删除项目前拦截 */
    public long countByProject(UUID projectId) {
        long total = 0;
        total += taskMapper.selectCount(new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<com.seven7m.kanban.entity.workitem.Task>()
                .eq("project_id", projectId));
        total += requirementMapper.selectCount(new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<com.seven7m.kanban.entity.workitem.Requirement>()
                .eq("project_id", projectId));
        total += bugMapper.selectCount(new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<com.seven7m.kanban.entity.workitem.Bug>()
                .eq("project_id", projectId));
        return total;
    }

    /** 删除项目时清理该项目下的全部工作项（任务/需求/缺陷），含子表级联清理 */
    @org.springframework.transaction.annotation.Transactional
    public void deleteByProject(UUID projectId) {
        // 批量删除：逐条 delete 会按 N+1 展开，大项目极慢
        taskService.deleteAllByProject(projectId);
        requirementService.deleteAllByProject(projectId);
        bugService.deleteAllByProject(projectId);
    }

    /**
     * 类型码 → 物理表类别；null 表示未知编码。
     * 未注册编码不再静默落到任务表（曾导致类型与物理表不一致），由调用方返回 400。
     */
    public static com.seven7m.kanban.common.enums.ItemCategory categoryOfCode(String code) {
        if (code == null || code.isBlank()) return null;
        return switch (code.trim().toUpperCase()) {
            case "TASK", "IMPROVEMENT" -> com.seven7m.kanban.common.enums.ItemCategory.TASK; // V27：改进类归入任务表
            case "STORY", "FEATURE", "REQUIREMENT" -> com.seven7m.kanban.common.enums.ItemCategory.REQUIREMENT;
            case "BUG", "FUNC_DEFECT", "PERF_DEFECT", "SEC_DEFECT", "COMPAT_DEFECT", "UI_DEFECT" ->
                    com.seven7m.kanban.common.enums.ItemCategory.BUG;
            default -> null;
        };
    }

    /** 某类别下已注册的具体类型码（不含 REQUIREMENT 这类类别别名），用于把别名解析到真实类型 */
    public static List<String> codesOf(com.seven7m.kanban.common.enums.ItemCategory category) {
        return switch (category) {
            case TASK -> List.of("TASK", "IMPROVEMENT");
            case REQUIREMENT -> List.of("STORY", "FEATURE");
            case BUG -> List.of("BUG", "FUNC_DEFECT", "PERF_DEFECT", "SEC_DEFECT", "COMPAT_DEFECT", "UI_DEFECT");
        };
    }

    /** 根据 typeCode（如 TASK/BUG）路由到对应 Service；未注册编码直接 400 */
    private BaseWorkItemService<? extends AbstractWorkItem> routeByTypeCode(String code) {
        if (code == null || code.isBlank()) return taskService;
        com.seven7m.kanban.common.enums.ItemCategory category = categoryOfCode(code);
        if (category == null) {
            throw new com.seven7m.kanban.common.exception.ServiceException(400, "未知的工作项类型: " + code);
        }
        return switch (category) {
            case TASK -> taskService;
            case REQUIREMENT -> requirementService;
            case BUG -> bugService;
        };
    }

    /**
     * 解析过滤条件涉及哪几张物理表。
     * codes 为空/未指定表示"全部"，返回三张表；否则按每个 code 路由后去重，
     * 保证像 ["TASK","BUG"] 这类跨类别条件不会只查第一张表而漏数据。
     */
    private List<BaseWorkItemService<? extends AbstractWorkItem>> resolveServices(List<String> codes) {
        if (codes == null || codes.isEmpty()) {
            return List.of(taskService, requirementService, bugService);
        }
        Set<BaseWorkItemService<? extends AbstractWorkItem>> services = new LinkedHashSet<>();
        for (String code : codes) {
            if (code != null && !code.isBlank()) {
                services.add(routeByTypeCode(code));
            }
        }
        return services.isEmpty()
                ? List.of(taskService, requirementService, bugService)
                : new ArrayList<>(services);
    }

    /**
     * 多表合并查询：分别向各表取候选集（各表上限 MERGE_FETCH_SIZE），
     * 汇总后在内存排序并分页。单表场景不会走到这里，仍走 SQL 层分页。
     */
    private PageResult<IssueVO> mergeListByFilter(
            List<BaseWorkItemService<? extends AbstractWorkItem>> services, IssueFilterRequest req) {

        int page = req.getPage() == null || req.getPage() < 1 ? 1 : req.getPage();
        int size = req.getSize() == null || req.getSize() < 1 ? 20 : req.getSize();

        List<IssueVO> merged = new ArrayList<>();
        long total = 0;
        IssueFilterRequest probe = copyForMerge(req);
        for (BaseWorkItemService<? extends AbstractWorkItem> svc : services) {
            PageResult<IssueVO> r = svc.listByFilter(probe);
            if (r == null) {
                continue;
            }
            total += r.getTotal();
            // 候选集被上限截断时跨表排序不完整（total 正确但深页数据缺失），完整修复需 SQL 下推 UNION ALL
            if (r.getTotal() > MERGE_FETCH_SIZE) {
                log.warn("[merge] 表 {} 候选集 {} 条超过 MERGE_FETCH_SIZE={}，跨表排序结果可能不完整",
                        svc.category(), r.getTotal(), MERGE_FETCH_SIZE);
            }
            if (r.getList() != null) {
                merged.addAll(r.getList());
            }
        }

        boolean asc = !"desc".equalsIgnoreCase(req.getSortOrder());
        Comparator<IssueVO> cmp = comparatorFor(req.getSortField());
        merged.sort(asc ? cmp : cmp.reversed());

        int from = Math.min((page - 1) * size, merged.size());
        int to = Math.min(from + size, merged.size());
        return new PageResult<>(total, page, size, new ArrayList<>(merged.subList(from, to)));
    }

    /** 合并场景的探测请求：各表按上限取候选集，页码/条数由内存分页接管 */
    private IssueFilterRequest copyForMerge(IssueFilterRequest req) {
        IssueFilterRequest c = new IssueFilterRequest();
        c.setProjectId(req.getProjectId());
        c.setScope(req.getScope());
        c.setStatusId(req.getStatusId());
        // 跨表合并时这些条件必须一并带过去，否则「全部工作项」视图会静默丢掉筛选
        c.setStatusCategory(req.getStatusCategory());
        c.setOverdue(req.getOverdue());
        c.setPriority(req.getPriority());
        c.setSeverity(req.getSeverity());
        c.setLabelId(req.getLabelId());
        c.setKeyword(req.getKeyword());
        c.setIssueTypeCodes(req.getIssueTypeCodes());
        c.setGroupBy(req.getGroupBy());
        c.setSprintId(req.getSprintId());
        c.setPage(1);
        c.setSize(MERGE_FETCH_SIZE);
        c.setSortField(req.getSortField());
        c.setSortOrder(req.getSortOrder());
        return c;
    }

    /** 内存排序规则需与 BaseWorkItemService#applySort 的 SQL 排序保持一致 */
    private Comparator<IssueVO> comparatorFor(String sortField) {
        if (sortField == null || sortField.isBlank()) {
            return Comparator.comparing(IssueVO::getStatusId, Comparator.nullsLast(Comparator.naturalOrder()))
                    .thenComparing(IssueVO::getSortKey, Comparator.nullsLast(Comparator.naturalOrder()));
        }
        return switch (sortField) {
            case "issueKey" -> Comparator.comparing(IssueVO::getIssueNumber,
                    Comparator.nullsLast(WorkItemRouter::compareIssueNumber));
            case "updatedAt" -> Comparator.comparing(IssueVO::getUpdatedAt,
                    Comparator.nullsLast(Comparator.naturalOrder()));
            case "createdAt" -> Comparator.comparing(IssueVO::getCreatedAt,
                    Comparator.nullsLast(Comparator.naturalOrder()));
            case "priority" -> Comparator.comparing(IssueVO::getPriority,
                    Comparator.nullsLast(Comparator.naturalOrder()));
            case "severity" -> Comparator.comparing(IssueVO::getSeverity,
                    Comparator.nullsLast(Comparator.naturalOrder()));
            case "statusId" -> Comparator.comparing(IssueVO::getStatusId,
                    Comparator.nullsLast(Comparator.naturalOrder()));
            case "sortKey" -> Comparator.comparing(IssueVO::getSortKey,
                    Comparator.nullsLast(Comparator.naturalOrder()));
            default -> Comparator.comparing(IssueVO::getStatusId, Comparator.nullsLast(Comparator.naturalOrder()))
                    .thenComparing(IssueVO::getSortKey, Comparator.nullsLast(Comparator.naturalOrder()));
        };
    }

    /** issue_number 为数字串：按长度+字典序比较，与 SQL 的 LENGTH(issue_number), issue_number 排序一致 */
    private static int compareIssueNumber(String a, String b) {
        if (a == null || b == null) return 0;
        return a.length() != b.length() ? Integer.compare(a.length(), b.length()) : a.compareTo(b);
    }

    /** 单表场景由前端直接传页码；多表合并时每张表最多取这么多条候选 */
    private static final int MERGE_FETCH_SIZE = 1000;
}

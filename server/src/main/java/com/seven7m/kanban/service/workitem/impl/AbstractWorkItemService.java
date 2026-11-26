package com.seven7m.kanban.service.workitem.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.seven7m.kanban.common.activity.ActivityValue;
import com.seven7m.kanban.common.constant.ActivityActions;
import com.seven7m.kanban.common.constant.ClearFields;
import com.seven7m.kanban.common.constant.IssueDefaults;
import com.seven7m.kanban.common.constant.PageLimits;
import com.seven7m.kanban.common.constant.WorkItemColumns;
import com.seven7m.kanban.common.domain.PageResult;
import com.seven7m.kanban.common.enums.IssueScope;
import com.seven7m.kanban.common.enums.IssueStatusCategory;
import com.seven7m.kanban.common.exception.ErrorCode;
import com.seven7m.kanban.common.util.UpdateWrappers;
import com.seven7m.kanban.common.enums.ItemCategory;
import com.seven7m.kanban.common.exception.ServiceException;
import com.seven7m.kanban.common.security.IssueProjectCache;
import com.seven7m.kanban.common.security.Permission;
import com.seven7m.kanban.common.security.PermissionService;
import com.seven7m.kanban.common.security.UserContext;
import com.seven7m.kanban.common.upload.AttachmentFileStore;
import com.seven7m.kanban.common.util.LikeEscaper;
import com.seven7m.kanban.common.util.Uuids;
import com.seven7m.kanban.dto.issue.CreateIssueRequest;
import com.seven7m.kanban.dto.issue.IssueFilterRequest;
import com.seven7m.kanban.dto.issue.IssueVO;
import com.seven7m.kanban.dto.issue.MoveIssueRequest;
import com.seven7m.kanban.dto.issue.UpdateIssueRequest;
import com.seven7m.kanban.entity.issue.IssueActivity;
import com.seven7m.kanban.entity.issue.IssueCc;
import com.seven7m.kanban.entity.issue.IssueParticipant;
import com.seven7m.kanban.entity.issue.IssueType;
import com.seven7m.kanban.entity.issue.attachment.IssueAttachment;
import com.seven7m.kanban.entity.issue.comment.IssueComment;
import com.seven7m.kanban.entity.issue.link.IssueLink;
import com.seven7m.kanban.entity.issuestatus.IssueStatus;
import com.seven7m.kanban.entity.project.Project;
import com.seven7m.kanban.entity.workitem.AbstractWorkItem;
import com.seven7m.kanban.entity.workitem.Bug;
import com.seven7m.kanban.entity.workitem.Requirement;
import com.seven7m.kanban.mapper.issue.IssueActivityMapper;
import com.seven7m.kanban.mapper.issue.IssueCcMapper;
import com.seven7m.kanban.mapper.issue.IssueParticipantMapper;
import com.seven7m.kanban.mapper.issue.IssueTypeMapper;
import com.seven7m.kanban.mapper.issue.attachment.IssueAttachmentMapper;
import com.seven7m.kanban.mapper.issue.comment.IssueCommentMapper;
import com.seven7m.kanban.mapper.issue.link.IssueLinkMapper;
import com.seven7m.kanban.mapper.issue.tag.IssueTagMapper;
import com.seven7m.kanban.mapper.issuestatus.IssueStatusMapper;
import com.seven7m.kanban.mapper.project.ProjectMapper;
import com.seven7m.kanban.mapper.user.SysUserMapper;
import com.seven7m.kanban.service.issue.reference.IssueReferenceGuard;
import com.seven7m.kanban.service.notification.NotificationService;
import com.seven7m.kanban.service.support.ProjectWriteGuard;
import com.seven7m.kanban.service.workflow.WorkflowService;
import com.seven7m.kanban.service.workitem.IssueVoAssembler;
import com.seven7m.kanban.service.workitem.WorkItemDeps;
import com.seven7m.kanban.service.workitem.WorkItemHandoffService;
import com.seven7m.kanban.service.workitem.WorkItemParentGuard;
import com.seven7m.kanban.service.workitem.WorkItemRouter;
import com.seven7m.kanban.service.workitem.WorkItemService;
import com.seven7m.kanban.service.workitem.WorkItemSharedDeps;
import com.seven7m.kanban.service.workitem.WorkItemSort;
import com.seven7m.kanban.service.workitem.WorkItemSortKeyService;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * 工作项共用实现（任务/需求/缺陷三类复用）：方法签名与前端现有 IssueService 完全一致，
 * VO 结构与 IssueVO 完全一致。所有子表读写都强制携带 category 隔离维度，从查询层杜绝跨表串数据。
 *
 * <p>为什么是抽象类而不是直接三个 ServiceImpl：三类工作项的差异只有 4 个
 * （类别、Mapper、编号生成器、实体工厂），其余上千行逻辑完全一致；
 * 用模板方法把差异收敛到构造参数，比"三个类各抄一遍"可维护得多。
 *
 * @param <T> 具体工作项实体（Task / Requirement / Bug）
 */
public abstract class AbstractWorkItemService<T extends AbstractWorkItem> implements WorkItemService {

    /** 本类别的身份与表特异性依赖（类别、Mapper、编号生成、实体工厂） */
    private final WorkItemDeps<T> deps;

    /** 父链上溯的最大深度：防御异常数据形成的环（正常层级不会超过十几层） */
    private static final int MAX_PARENT_DEPTH = 200;

    protected AbstractWorkItemService(WorkItemDeps<T> deps) {
        this.deps = deps;
    }

    // ---- 以下访问器把 deps 摊平成原有调用点的写法，避免全类改 40+ 处调用 ----
    protected final BaseMapper<T> mapper() { return deps.mapper(); }
    @Override
    public final ItemCategory category() { return deps.category(); }
    protected final IssueTypeMapper issueTypeMapper() { return deps.shared().issueTypeMapper(); }
    protected final IssueStatusMapper issueStatusMapper() { return deps.shared().issueStatusMapper(); }
    protected final IssueParticipantMapper issueParticipantMapper() { return deps.shared().issueParticipantMapper(); }
    protected final IssueCcMapper issueCcMapper() { return deps.shared().issueCcMapper(); }
    protected final IssueActivityMapper issueActivityMapper() { return deps.shared().issueActivityMapper(); }
    protected final IssueTagMapper issueTagMapper() { return deps.shared().issueTagMapper(); }
    protected final IssueCommentMapper issueCommentMapper() { return deps.shared().issueCommentMapper(); }
    protected final IssueAttachmentMapper issueAttachmentMapper() { return deps.shared().issueAttachmentMapper(); }
    protected final IssueLinkMapper issueLinkMapper() { return deps.shared().issueLinkMapper(); }
    protected final SysUserMapper sysUserMapper() { return deps.shared().sysUserMapper(); }
    protected final ProjectMapper projectMapper() { return deps.shared().projectMapper(); }
    protected final WorkflowService workflowService() { return deps.shared().workflowService(); }
    protected final NotificationService notificationService() { return deps.shared().notificationService(); }
    protected final String nextIssueNumber() { return deps.nextIssueNumber().get(); }
    protected final T newEntityInstance() { return deps.newEntity().get(); }

    private String categoryName() { return category().name(); }

    /**
     * 跨切面依赖用字段注入：它们不参与类别差异，放进 {@link WorkItemDeps} 只会让
     * 三个子类的构造参数变长，对调用点没有收益。
     */
    /** 细粒度权限校验（issue:status / issue:assign） */
    @Autowired
    protected PermissionService permissionService;

    /** 删除工作项时清理附件物理文件（否则文件永远留在磁盘且可被继续下载） */
    @Autowired
    protected AttachmentFileStore attachmentFileStore;

    /** 迭代 / 标签等外部引用的项目归属校验 */
    @Autowired
    protected IssueReferenceGuard issueReferenceGuard;

    /** 归档项目只读校验 */
    @Autowired
    protected ProjectWriteGuard projectWriteGuard;

    /** 排序键计算与列内重排（算法与业务字段无关，单独成类便于测试） */
    @Autowired
    protected WorkItemSortKeyService sortKeyService;

    /** 交接解析与副作用（与具体表无关，见 {@link WorkItemHandoffService}） */
    @Autowired
    protected WorkItemHandoffService handoffService;

    /** 工作项 VO 装配（批量预取 + 字段映射，与权限/事务无关） */
    @Autowired
    protected IssueVoAssembler issueVoAssembler;

    /** 当前用户是否具备该项目下的指定权限码，否则 403 */
    private void requirePermission(UUID projectId, String code, String action) {
        if (projectId == null || !permissionService.hasPermission(UserContext.getUserId(), projectId, code)) {
            throw new ServiceException(ErrorCode.PERMISSION_DENIED_ACTION, action);
        }
    }

    private boolean isDoneCategory(IssueStatus status) {
        return status != null && IssueStatusCategory.is(status.getCategory(), IssueStatusCategory.DONE);
    }

    /**
     * 归档项目只读：归档后仍能新建/编辑/拖动工作项，会让"归档"仅仅等于从列表里隐藏。
     * 这里在写路径统一拦截（项目自身的归档/取消归档走 ProjectService，不经过本方法）。
     */
    private void requireProjectWritable(UUID projectId) {
        projectWriteGuard.requireWritable(projectId);
    }


    // ========================= 列表/详情 =========================

    @Override
    @Transactional(readOnly = true)
    public PageResult<IssueVO> listByFilter(IssueFilterRequest req) {
        UUID me = UserContext.getUserId();
        int page = req.getPage() == null || req.getPage() < PageLimits.MIN_PAGE
                ? PageLimits.MIN_PAGE : req.getPage();
        int size = req.getSize() == null || req.getSize() < PageLimits.MIN_PAGE
                ? PageLimits.LIST_DEFAULT_SIZE : Math.min(req.getSize(), PageLimits.LIST_MAX_SIZE);

        QueryWrapper<T> q = Wrappers.query();
        q.eq(WorkItemColumns.PROJECT_ID, req.getProjectId());

        if (req.getStatusId() != null) q.eq(WorkItemColumns.STATUS_ID, req.getStatusId());

        // 阶段筛选：状态字典按 category 归类，子查询留在 IssueStatusMapper，这里只拼 id 集合
        IssueStatusCategory statusCategory = IssueStatusCategory.of(req.getStatusCategory());
        if (statusCategory != null) {
            List<UUID> categoryStatusIds = issueStatusMapper().selectIdsByCategory(statusCategory.name());
            if (categoryStatusIds.isEmpty()) return emptyPage(page, size);
            q.in(WorkItemColumns.STATUS_ID, categoryStatusIds);
        }

        if (Boolean.TRUE.equals(req.getOverdue())) {
            List<UUID> doneStatusIds = issueStatusMapper().selectIdsByCategory(IssueStatusCategory.DONE.name());
            q.isNotNull(WorkItemColumns.DUE_DATE).lt(WorkItemColumns.DUE_DATE, LocalDate.now());
            if (!doneStatusIds.isEmpty()) q.notIn(WorkItemColumns.STATUS_ID, doneStatusIds);
        }

        if (req.getSprintId() != null) q.eq(WorkItemColumns.SPRINT_ID, req.getSprintId());
        if (req.getPriority() != null) q.eq(WorkItemColumns.PRIORITY, req.getPriority());
        // 严重级只存在于缺陷表：给任务/需求拼 severity 条件会直接落成 SQL 错误
        if (req.getSeverity() != null && category() == ItemCategory.BUG) {
            q.eq(WorkItemColumns.SEVERITY, req.getSeverity());
        }
        if (req.getKeyword() != null && !req.getKeyword().isBlank()) {
            // 转义 LIKE 通配符：_ / % 是合法搜索字符，不转义会退化成"匹配任意内容"
            q.like(WorkItemColumns.TITLE, LikeEscaper.contains(req.getKeyword()));
        }

        switch (IssueScope.of(req.getScope())) {
            case ASSIGNED -> q.eq(WorkItemColumns.ASSIGNEE_ID, me);
            case REPORTED -> q.eq(WorkItemColumns.REPORTER_ID, me);
            case VERIFIED -> q.eq(WorkItemColumns.VERIFIER_ID, me);
            case SHARED -> {
                // 「共享给我」= 我是参与人；参与关系在子表，id 集合先由 mapper 取回
                List<UUID> sharedIds = issueParticipantMapper()
                        .selectIssueIdsByUserAndCategory(me, categoryName());
                if (sharedIds.isEmpty()) return emptyPage(page, size);
                q.in(WorkItemColumns.ID, sharedIds);
            }
            case ARCHIVED -> q.eq(WorkItemColumns.ARCHIVED, true);
            // ACTIVE、未指定以及无法识别的 scope 一律只查未归档：
            // 原实现只对 null 过滤，传入未知取值时会把归档工作项一并返回
            default -> q.eq(WorkItemColumns.ARCHIVED, false);
        }

        if (req.getLabelId() != null) {
            List<UUID> labeledIds = issueTagMapper()
                    .selectIssueIdsByLabels(List.of(req.getLabelId()), categoryName());
            if (labeledIds.isEmpty()) return emptyPage(page, size);
            q.in(WorkItemColumns.ID, labeledIds);
        }

        // 人员维度（云效筛选面板的「人员」组）：负责人 / 创建者
        if (req.getAssigneeId() != null) q.eq(WorkItemColumns.ASSIGNEE_ID, req.getAssigneeId());
        if (req.getReporterId() != null) q.eq(WorkItemColumns.REPORTER_ID, req.getReporterId());

        // 时间维度（「时间」组）：按天区间，边界都含当天
        applyTimestampRange(q, WorkItemColumns.CREATED_AT, req.getCreatedFrom(), req.getCreatedTo());
        applyTimestampRange(q, WorkItemColumns.UPDATED_AT, req.getUpdatedFrom(), req.getUpdatedTo());
        applyDateRange(q, WorkItemColumns.DUE_DATE, req.getDueFrom(), req.getDueTo());

        // 工作项类型编码过滤（需求/任务/缺陷分类，避免列表互相混入）
        if (req.getIssueTypeCodes() != null && !req.getIssueTypeCodes().isEmpty()) {
            List<UUID> typeIds = issueTypeMapper()
                    .selectList(Wrappers.<IssueType>lambdaQuery().in(IssueType::getCode, req.getIssueTypeCodes()))
                    .stream().map(IssueType::getId).toList();
            if (typeIds.isEmpty()) return emptyPage(page, size);
            q.in(WorkItemColumns.ISSUE_TYPE_ID, typeIds);
        }

        long total = mapper().selectCount(q);
        // 排序规则（SQL 片段）与跨表合并用的内存 Comparator 共用 WorkItemSort 这一份定义
        WorkItemSort.applySql(q, req.getSortField(), req.getSortOrder(), category());
        List<T> issues = mapper().selectPage(new Page<>(page, size, false), q).getRecords();
        Project project = projectMapper().selectById(req.getProjectId());
        String key = project == null ? "" : project.getProjectKey();
        return new PageResult<>(total, page, size, toVOList(issues, key));
    }

    /** 过滤条件注定命中 0 条时直接返回空页：既省一次查询，也避免拼出 {@code IN ()} 这种非法 SQL */
    private PageResult<IssueVO> emptyPage(int page, int size) {
        return new PageResult<>(0, page, size, List.of());
    }

    /**
     * 时间戳列（created_at / updated_at）的按天区间，两端都含当天。
     *
     * <p>上界用 {@code < 次日 00:00} 而不是 {@code <= 当天 23:59:59}：
     * created_at 带毫秒，写死 23:59:59 会漏掉当天最后 1 秒内的记录，
     * 而"次日零点之前"这个写法与列上有没有毫秒、数据库怎么截断都无关。
     */
    private void applyTimestampRange(QueryWrapper<T> q, String column, LocalDate from, LocalDate to) {
        if (from != null) q.ge(column, from.atStartOfDay());
        if (to != null) q.lt(column, to.plusDays(1).atStartOfDay());
    }

    /** 纯日期列（due_date）的按天区间，两端都含当天 */
    private void applyDateRange(QueryWrapper<T> q, String column, LocalDate from, LocalDate to) {
        if (from != null) q.ge(column, from);
        if (to != null) q.le(column, to);
    }

    @Override
    @Transactional(readOnly = true)
    public IssueVO getById(UUID id) {
        T issue = mapper().selectById(id);
        if (issue == null) throw new ServiceException(ErrorCode.WORK_ITEM_NOT_FOUND, category());
        return toVO(issue);
    }

    /** 轻量查询：返回主表 id/projectId/issueNumber/issueKey/title，不加载子资源，供关联展示/权限解析等高频场景使用 */
    @Override
    @Transactional(readOnly = true)
    public IssueVO getBriefById(UUID id) {
        T issue = mapper().selectById(id);
        if (issue == null) return null;
        IssueVO vo = new IssueVO();
        vo.setId(issue.getId());
        vo.setProjectId(issue.getProjectId());
        vo.setIssueNumber(issue.getIssueNumber());
        // issueKey 的口径与列表一致（项目 key + 编号）。必须在这里下发：
        // 否则调用方只能拿到裸编号，自行拼前缀就会造出「ISSUE-123」这类与真实编号不符的显示值
        vo.setIssueKey(resolveProjectKey(issue.getProjectId()) + "-" + issue.getIssueNumber());
        vo.setTitle(issue.getTitle());
        return vo;
    }

    @Override
    public UUID getProjectId(UUID issueId) {
        // 直接按主键取行再读 project_id：比 selectOne + LIMIT 1 少一次包装，
        // 也避免在主键查询上再拼 SQL 片段
        T item = mapper().selectById(issueId);
        return item == null ? null : item.getProjectId();
    }

    @Override
    public List<IssueVO> listChildren(UUID issueId) {
        T parent = mapper().selectById(issueId);
        if (parent == null) return List.of();
        String key = resolveProjectKey(parent.getProjectId());
        List<T> children = mapper().selectList(Wrappers.<T>query()
                .eq(WorkItemColumns.PARENT_ID, issueId).orderByAsc(WorkItemColumns.SORT_KEY));
        return toVOList(children, key);
    }

    // ========================= 创建 =========================

    @Override
    @Transactional
    public IssueVO create(CreateIssueRequest req) {
        Project project = projectMapper().selectById(req.getProjectId());
        if (project == null) throw new ServiceException(ErrorCode.PROJECT_MISSING);
        requireProjectWritable(project.getId());
        IssueStatus status = issueStatusMapper().selectById(req.getStatusId());
        if (status == null) throw new ServiceException(ErrorCode.STATUS_NOT_FOUND);
        // 状态字典是全局的，但任务/需求/缺陷各有自己的工作流节点（V31 起）：
        // 只允许用本类别的节点建单，否则会建出「任务停在已修复」这种跨流程脏数据
        UUID targetStatusId = status.getId();
        boolean inWorkflow = workflowService().statusesOf(req.getProjectId(), category()).stream()
                .anyMatch(st -> st.getId().equals(targetStatusId));
        if (!inWorkflow) throw new ServiceException(ErrorCode.STATUS_NOT_IN_WORKFLOW);

        UUID issueTypeId = resolveIssueTypeId(req);
        // 迭代必须属于本项目，否则会造出挂在别的项目迭代上的工作项
        issueReferenceGuard.requireSprintInProject(req.getProjectId(), req.getSprintId());

        T issue = newEntityInstance();
        BeanUtils.copyProperties(req, issue);
        // 父级校验：只允许挂本表（同类别）同项目的工作项，且不能成环
        if (req.getParentId() != null) {
            WorkItemParentGuard.requireValid(null, req.getParentId(), req.getProjectId(), mapper()::selectById);
        }
        issue.setIssueTypeId(issueTypeId);
        issue.setId(UUID.randomUUID());
        issue.setIssueNumber(nextIssueNumber());
        issue.setReporterId(UserContext.getUserId());
        issue.setPriority(req.getPriority() == null ? IssueDefaults.PRIORITY : req.getPriority());
        if (issue instanceof Bug bug) {
            bug.setSeverity(req.getSeverity() == null ? IssueDefaults.SEVERITY : req.getSeverity());
        }
        issue.setArchived(req.getArchived() == null ? Boolean.FALSE : req.getArchived());
        // 直接以"已完成"状态建单时也要落 done_at，否则周期统计会漏掉这类数据
        // 显式传入的完成时间优先：导入历史数据时不能把"已完成"全部记成导入那一刻
        if (req.getDoneAt() != null) {
            issue.setDoneAt(req.getDoneAt().atStartOfDay());
        } else {
            issue.setDoneAt(isDoneCategory(status) ? LocalDateTime.now() : null);
        }
        // 追加到目标状态列末尾：必须取列内现有最大键，否则所有新卡片 sort_key 相同；
        // 走 computeSortKey 是为了在经过几十次追加、键变长时自动触发列内重排
        issue.setSortKey(computeSortKey(req.getProjectId(), req.getStatusId(), null, null));
        issue.setCreatedBy(UserContext.getUserId());
        issue.setCreatedAt(LocalDateTime.now());
        issue.setUpdatedAt(LocalDateTime.now());
        mapper().insert(issue);
        recordActivity(issue.getId(), ActivityActions.CREATED, null, null);
        return toVO(issue, project.getProjectKey());
    }

    /**
     * 解析工作项类型：真实类型码 → 类别别名（REQUIREMENT/TASK/BUG）解析到该类别 sort_order 最小的类型；
     * 未注册编码返回 400，不再静默取全局第一条类型（曾导致类型回显与物理表不一致）。
     * 解析出的类型必须与当前物理表类别一致，否则返回 400（见 {@link #requireTypeInCategory}）。
     */
    private UUID resolveIssueTypeId(CreateIssueRequest req) {
        UUID typeId = req.getIssueTypeId();
        String code = req.getIssueTypeCode() == null ? null : req.getIssueTypeCode().trim();
        if (typeId != null) {
            IssueType type = issueTypeMapper().selectById(typeId);
            if (type == null) {
                throw new ServiceException(ErrorCode.WORK_ITEM_TYPE_NOT_FOUND, typeId);
            }
            return requireTypeInCategory(type);
        }
        if (code == null || code.isBlank()) {
            throw new ServiceException(ErrorCode.WORK_ITEM_TYPE_REQUIRED);
        }
        IssueType byCode = issueTypeMapper().selectList(Wrappers.<IssueType>lambdaQuery()
                        .eq(IssueType::getCode, code))
                .stream().findFirst().orElse(null);
        if (byCode != null) return requireTypeInCategory(byCode);

        ItemCategory category = WorkItemRouter.categoryOfCode(code);
        if (category == null) throw new ServiceException(ErrorCode.WORK_ITEM_TYPE_UNKNOWN, code);
        IssueType alias = issueTypeMapper().selectList(Wrappers.<IssueType>lambdaQuery()
                        .in(IssueType::getCode, WorkItemRouter.codesOf(category))
                        .orderByAsc(IssueType::getSortOrder))
                .stream().findFirst().orElse(null);
        if (alias == null) throw new ServiceException(ErrorCode.WORK_ITEM_TYPE_UNINITIALIZED, category);
        return requireTypeInCategory(alias);
    }

    /**
     * 校验类型字典项与当前物理表类别一致。
     *
     * <p>类型字典是全局的，而工作项落哪张表由类别决定。若允许把 BUG 类型 id 写到 task 行：
     * 任务列表按 issueTypeCodes=[TASK,IMPROVEMENT] 过滤查不到它，缺陷列表查的是 bug 表也查不到它，
     * 这条工作项就从界面上"消失"了，只能靠 SQL 找回。
     */
    private UUID requireTypeInCategory(IssueType type) {
        ItemCategory typeCategory = WorkItemRouter.categoryOfCode(type.getCode());
        if (typeCategory == null) {
            throw new ServiceException(ErrorCode.WORK_ITEM_TYPE_UNREGISTERED, type.getCode());
        }
        if (typeCategory != category()) {
            throw new ServiceException(ErrorCode.WORK_ITEM_TYPE_CATEGORY_MISMATCH,
                    type.getName(), typeCategory, category());
        }
        return type.getId();
    }

    // ========================= 更新 =========================

    @Override
    @Transactional
    public IssueVO update(UUID id, UpdateIssueRequest req) {
        T issue = requireIssue(id);
        requireProjectWritable(issue.getProjectId());
        requireUpdatePermissions(issue, req);
        requireReferencesInProject(issue, req);

        UpdateWrapper<T> uw = Wrappers.update();
        uw.eq(WorkItemColumns.ID, id);
        applyScalarFields(uw, issue, req);
        applyClearFields(uw, issue, req);
        StatusOutcome status = applyStatusTransition(uw, issue, req);

        if (uw.getSqlSet() != null && !uw.getSqlSet().isEmpty()) {
            uw.set(WorkItemColumns.UPDATED_AT, LocalDateTime.now());
            mapper().update(null, uw);
        }
        recordStatusActivity(id, status);
        recordHandoffIfAny(issue, status, req);
        applyParticipantsAndCc(id, req);
        applyLabels(id, req);
        return toVO(mapper().selectById(id));
    }

    /** 状态变更的结果：用于事后写活动记录与交接落点，避免各段重复解析 */
    private record StatusOutcome(String fromCode, String toCode,
                                 WorkItemHandoffService.Decision handoff, boolean sprintApplied) {
        static final StatusOutcome NONE = new StatusOutcome(null, null, null, false);
    }

    /**
     * 类型变更校验：类型字典是全局的，而工作项落哪张表由类别决定。
     * 若允许把 BUG 类型 id 写到 task 行，任务列表与缺陷列表都会查不到它。
     */
    private void requireUpdatePermissions(T issue, UpdateIssueRequest req) {
        if (req.getIssueTypeId() != null) {
            IssueType type = issueTypeMapper().selectById(req.getIssueTypeId());
            if (type == null) {
                throw new ServiceException(ErrorCode.WORK_ITEM_TYPE_NOT_FOUND, req.getIssueTypeId());
            }
            requireTypeInCategory(type);
        }
        // 细粒度权限：状态变更、负责人变更不只是「编辑」，分别对应 issue:status / issue:assign。
        // 此前这两个权限码定义了却从未校验，管理员在 RBAC 里取消勾选完全不生效（假权限）。
        if (isStatusChanging(issue, req)) {
            requirePermission(issue.getProjectId(), Permission.ISSUE_STATUS, "修改工作项状态");
        }
        boolean assigneeCleared = req.getClearFields() != null
                && req.getClearFields().contains(ClearFields.ASSIGNEE_ID);
        boolean assigneeChanging = (req.getAssigneeId() != null
                && !req.getAssigneeId().equals(issue.getAssigneeId())) || assigneeCleared;
        if (assigneeChanging) {
            requirePermission(issue.getProjectId(), Permission.ISSUE_ASSIGN, "修改负责人");
        }
        // 归档/取消归档工作项是独立权限（issue:archive），不再混在 issue:edit 里
        if (req.getArchived() != null && !req.getArchived().equals(Boolean.TRUE.equals(issue.getArchived()))) {
            requirePermission(issue.getProjectId(), Permission.ISSUE_ARCHIVE,
                    req.getArchived() ? "归档工作项" : "取消归档工作项");
        }
    }

    /** 外部引用必须同项目：迭代、标签 */
    private void requireReferencesInProject(T issue, UpdateIssueRequest req) {
        if (req.getSprintId() != null) {
            issueReferenceGuard.requireSprintInProject(issue.getProjectId(), req.getSprintId());
        }
        if (req.getLabelIds() != null) {
            issueReferenceGuard.requireLabelsInProject(issue.getProjectId(), req.getLabelIds());
        }
    }

    private boolean isStatusChanging(T issue, UpdateIssueRequest req) {
        return req.getStatusId() != null && !req.getStatusId().equals(issue.getStatusId());
    }

    /** 普通字段（非空即更新），含需求/缺陷独有的列 */
    private void applyScalarFields(UpdateWrapper<T> uw, T issue, UpdateIssueRequest req) {
        if (req.getTitle() != null) uw.set(WorkItemColumns.TITLE, req.getTitle());
        if (req.getDescription() != null) uw.set(WorkItemColumns.DESCRIPTION, req.getDescription());
        if (req.getPriority() != null) uw.set(WorkItemColumns.PRIORITY, req.getPriority());
        if (req.getIssueTypeId() != null) uw.set(WorkItemColumns.ISSUE_TYPE_ID, req.getIssueTypeId());
        if (req.getAssigneeId() != null) uw.set(WorkItemColumns.ASSIGNEE_ID, req.getAssigneeId());
        if (req.getVerifierId() != null) uw.set(WorkItemColumns.VERIFIER_ID, req.getVerifierId());
        if (req.getParentId() != null) {
            WorkItemParentGuard.requireValid(
                    issue.getId(), req.getParentId(), issue.getProjectId(), mapper()::selectById);
            uw.set(WorkItemColumns.PARENT_ID, req.getParentId());
        }
        if (req.getDoneAt() != null) uw.set(WorkItemColumns.DONE_AT, req.getDoneAt().atStartOfDay());
        if (req.getSprintId() != null) uw.set(WorkItemColumns.SPRINT_ID, req.getSprintId());
        if (req.getDueDate() != null) uw.set(WorkItemColumns.DUE_DATE, req.getDueDate());
        if (req.getArchived() != null) uw.set(WorkItemColumns.ARCHIVED, req.getArchived());
        if (req.getModule() != null) {
            if (issue instanceof Requirement || issue instanceof Bug) uw.set(WorkItemColumns.MODULE, req.getModule());
        }
        if (issue instanceof Bug) {
            if (req.getSeverity() != null) uw.set(WorkItemColumns.SEVERITY, req.getSeverity());
            if (req.getFixVersion() != null) uw.set(WorkItemColumns.FIX_VERSION, req.getFixVersion());
            if (req.getSource() != null) uw.set(WorkItemColumns.SOURCE, req.getSource());
            if (req.getIteration() != null) uw.set(WorkItemColumns.ITERATION, req.getIteration());
            if (req.getPlanStart() != null) uw.set(WorkItemColumns.PLAN_START, req.getPlanStart());
            if (req.getNoFixReason() != null) uw.set(WorkItemColumns.NO_FIX_REASON, req.getNoFixReason());
        }
    }

    /** 显式清空字段（updateById 会跳过 null，必须在这里写 set null） */
    private void applyClearFields(UpdateWrapper<T> uw, T issue, UpdateIssueRequest req) {
        if (req.getClearFields() == null) {
            return;
        }
        for (String field : req.getClearFields()) {
            switch (field) {
                case ClearFields.ASSIGNEE_ID -> uw.set(WorkItemColumns.ASSIGNEE_ID, null);
                case ClearFields.VERIFIER_ID -> uw.set(WorkItemColumns.VERIFIER_ID, null);
                case ClearFields.PARENT_ID -> uw.set(WorkItemColumns.PARENT_ID, null);
                case ClearFields.DONE_AT -> uw.set(WorkItemColumns.DONE_AT, null);
                case ClearFields.SPRINT_ID -> uw.set(WorkItemColumns.SPRINT_ID, null);
                case ClearFields.DUE_DATE -> uw.set(WorkItemColumns.DUE_DATE, null);
                case ClearFields.PLAN_START -> {
                    if (issue instanceof Bug) uw.set(WorkItemColumns.PLAN_START, null);
                }
                case ClearFields.ITERATION -> {
                    if (issue instanceof Bug) uw.set(WorkItemColumns.ITERATION, null);
                }
                case ClearFields.NO_FIX_REASON -> {
                    if (issue instanceof Bug) uw.set(WorkItemColumns.NO_FIX_REASON, null);
                }
                case ClearFields.MODULE -> {
                    if (issue instanceof Requirement || issue instanceof Bug) {
                        uw.set(WorkItemColumns.MODULE, null);
                    }
                }
                case ClearFields.FIX_VERSION -> {
                    if (issue instanceof Bug) uw.set(WorkItemColumns.FIX_VERSION, null);
                }
                case ClearFields.PARTICIPANTS, ClearFields.CC_USERS -> {
                    // 非标量字段：由 applyParticipantsAndCc 全量替换
                }
                default -> throw new ServiceException(ErrorCode.CLEAR_FIELD_UNSUPPORTED, field);
            }
        }
    }

    /**
     * 状态流转：校验目标状态与流转规则，写入 status_id / sort_key / done_at，并解析交接落点。
     * 请求里显式带了负责人或真正改了迭代时，以用户选择为准，列配置不覆盖。
     */
    private StatusOutcome applyStatusTransition(UpdateWrapper<T> uw, T issue, UpdateIssueRequest req) {
        if (!isStatusChanging(issue, req)) {
            return StatusOutcome.NONE;
        }
        IssueStatus fromStatus = issue.getStatusId() == null
                ? null : issueStatusMapper().selectById(issue.getStatusId());
        IssueStatus target = issueStatusMapper().selectById(req.getStatusId());
        if (target == null) {
            throw new ServiceException(ErrorCode.STATUS_NOT_FOUND);
        }
        if (!workflowService().canTransition(issue.getProjectId(), category(),
                issue.getStatusId(), req.getStatusId())) {
            String fromName = fromStatus == null ? "未知状态" : fromStatus.getName();
            throw new ServiceException(ErrorCode.STATUS_TRANSITION_DENIED, fromName, target.getName());
        }
        uw.set(WorkItemColumns.STATUS_ID, req.getStatusId());
        uw.set(WorkItemColumns.SORT_KEY, computeSortKey(issue.getProjectId(), req.getStatusId(), null, null));
        // 解决周期按 done_at 统计：进入 DONE 记时间，离开 DONE 清空
        if (isDoneCategory(target)) {
            uw.set(WorkItemColumns.DONE_AT, LocalDateTime.now());
        } else if (isDoneCategory(fromStatus)) {
            UpdateWrappers.clearToNull(uw, WorkItemColumns.DONE_AT);
        }

        WorkItemHandoffService.Decision handoff = handoffService.resolve(
                handoffContext(issue), req.getStatusId(), target.getName(), req.getAssigneeId());
        boolean sprintApplied = false;
        if (handoff != null) {
            uw.set(WorkItemColumns.ASSIGNEE_ID, handoff.toUserId());
            // 「真改了迭代」要排除掉「提交的还是当前迭代」——详情页整表单保存会把未变字段一并带上，
            // 若把相同值当成显式指派，落点就永远不会生效（负责人那侧同理）。
            boolean sprintSpecified = req.getSprintId() != null
                    && !req.getSprintId().equals(issue.getSprintId());
            if (handoff.sprintId() != null && !sprintSpecified
                    && !clearsField(req, ClearFields.SPRINT_ID)) {
                uw.set(WorkItemColumns.SPRINT_ID, handoff.sprintId());
                sprintApplied = true;
            }
        }
        String fromCode = fromStatus == null ? null : fromStatus.getCode();
        return new StatusOutcome(fromCode, target.getCode(), handoff, sprintApplied);
    }

    private void recordStatusActivity(UUID id, StatusOutcome status) {
        if (status.toCode() != null) {
            recordActivity(id, ActivityActions.STATUS_CHANGED,
                    jsonStatus(status.fromCode()), jsonStatus(status.toCode()));
        }
    }

    private void recordHandoffIfAny(T issue, StatusOutcome status, UpdateIssueRequest req) {
        if (status.handoff() != null) {
            // labelIds 非空时下方会全量替换标签，交接再插一条等于白做，故只在未显式指定时补标签
            handoffService.record(handoffContext(issue), status.handoff(),
                    status.sprintApplied(), req.getLabelIds() == null);
        }
    }

    private void applyParticipantsAndCc(UUID id, UpdateIssueRequest req) {
        boolean clearParticipants = req.getClearFields() != null
                && req.getClearFields().contains(ClearFields.PARTICIPANTS);
        boolean clearCc = req.getClearFields() != null
                && req.getClearFields().contains(ClearFields.CC_USERS);
        if (req.getParticipants() != null || clearParticipants) {
            replaceParticipants(id, clearParticipants ? List.of() : req.getParticipants());
        }
        if (req.getCcUsers() != null || clearCc) {
            replaceCcUsers(id, clearCc ? List.of() : req.getCcUsers());
        }
    }

    private void applyLabels(UUID id, UpdateIssueRequest req) {
        if (req.getLabelIds() == null) {
            return;
        }
        issueTagMapper().deleteIssueLabelByIssueIdsAndCategory(List.of(id), categoryName());
        for (UUID labelId : req.getLabelIds()) {
            issueTagMapper().insertIssueLabel(id, labelId, categoryName());
        }
    }

    private void replaceParticipants(UUID issueId, List<String> userIds) {
        issueParticipantMapper().delete(Wrappers.<IssueParticipant>lambdaQuery()
                .eq(IssueParticipant::getIssueId, issueId).eq(IssueParticipant::getCategory, categoryName()));
        for (String uid : userIds) {
            if (uid == null || uid.isBlank()) continue;
            IssueParticipant p = new IssueParticipant();
            // 主键必须显式赋值：IdType.ASSIGN_UUID 生成的是 32 位字符串，
            // 反射写入 UUID 字段会抛 ReflectionException（见 RbacAdminService 同类注释）
            p.setId(UUID.randomUUID());
            p.setIssueId(issueId); p.setCategory(categoryName());
            p.setUserId(parseUserId(uid)); p.setCreatedAt(LocalDateTime.now());
            issueParticipantMapper().insert(p);
        }
    }

    private void replaceCcUsers(UUID issueId, List<String> userIds) {
        issueCcMapper().delete(Wrappers.<IssueCc>lambdaQuery()
                .eq(IssueCc::getIssueId, issueId).eq(IssueCc::getCategory, categoryName()));
        for (String uid : userIds) {
            if (uid == null || uid.isBlank()) continue;
            IssueCc c = new IssueCc();
            c.setId(UUID.randomUUID());
            c.setIssueId(issueId); c.setCategory(categoryName());
            c.setUserId(parseUserId(uid)); c.setCreatedAt(LocalDateTime.now());
            issueCcMapper().insert(c);
        }
    }

    /** 参与者/抄送用户 id 解析：非法 UUID 应返回 400 而不是 500 */
    private UUID parseUserId(String uid) {
        return Uuids.parseOrThrow(uid, "用户 ID");
    }

    // ========================= 拖拽移动 =========================

    @Override
    @Transactional
    public IssueVO move(UUID id, MoveIssueRequest req) {
        T issue = requireIssue(id);
        requireProjectWritable(issue.getProjectId());
        UUID fromStatus = issue.getStatusId();
        UUID toStatus = req.getStatusId();
        // 状态为空的历史数据无法做流转校验（from 为空时 canTransition 必失败且原因难懂），
        // 直接给可操作的提示，避免 NPE 或误导性的"不允许流转"
        if (fromStatus == null) {
            throw new ServiceException(ErrorCode.STATUS_MISSING_FOR_MOVE);
        }
        boolean statusChanging = !Objects.equals(fromStatus, toStatus);
        IssueStatus fromStatusEntity = statusChanging ? issueStatusMapper().selectById(fromStatus) : null;
        boolean wasDone = isDoneCategory(fromStatusEntity);
        String statusFromCode = null;
        String statusToCode = null;
        WorkItemHandoffService.Decision handoff = null;
        IssueStatus target = null;
        if (statusChanging) {
            // 跨列拖拽属于「状态变更」，对应 issue:status；同列排序仍只需要 issue:edit（注解已拦）
            requirePermission(issue.getProjectId(), Permission.ISSUE_STATUS, "修改工作项状态");
            target = issueStatusMapper().selectById(toStatus);
            if (target == null) throw new ServiceException(ErrorCode.STATUS_NOT_FOUND);
            if (!workflowService().canTransition(issue.getProjectId(), category(), fromStatus, toStatus)) {
                String fromName = fromStatusEntity == null ? "未知状态" : fromStatusEntity.getName();
                throw new ServiceException(ErrorCode.STATUS_TRANSITION_DENIED, fromName, target.getName());
            }
            statusFromCode = fromStatusEntity == null ? null : fromStatusEntity.getCode();
            statusToCode = target.getCode();
            handoff = handoffService.resolve(handoffContext(issue), toStatus, target.getName(), null);
        }
        issue.setStatusId(toStatus);
        boolean clearDoneAt = false;
        if (handoff != null) {
            // 与 status_id 同一次 UPDATE 落地，避免出现「换了列但人没交接」的中间态
            issue.setAssigneeId(handoff.toUserId());
            if (handoff.sprintId() != null) {
                issue.setSprintId(handoff.sprintId());
            }
        }
        if (statusChanging) {
            boolean nowDone = isDoneCategory(target);
            if (nowDone && !wasDone) {
                issue.setDoneAt(LocalDateTime.now());
            } else if (wasDone && !nowDone) {
                issue.setDoneAt(null);
                clearDoneAt = true;
            }
        }
        String sortKey = computeSortKey(issue.getProjectId(), toStatus, req.getBeforeId(), req.getAfterId());
        issue.setSortKey(sortKey);
        issue.setUpdatedAt(LocalDateTime.now());
        if (clearDoneAt) {
            // updateById 默认跳过 null 字段，清空 done_at 必须显式写 SQL
            UpdateWrapper<T> clear = Wrappers.update();
            clear.eq(WorkItemColumns.ID, id);
            UpdateWrappers.clearToNull(clear, WorkItemColumns.DONE_AT);
            mapper().update(issue, clear);
        } else {
            mapper().updateById(issue);
        }
        if (statusToCode != null) {
            recordActivity(id, ActivityActions.STATUS_CHANGED, jsonStatus(statusFromCode), jsonStatus(statusToCode));
        }
        if (handoff != null) {
            // 拖拽（move）请求不带迭代与标签，落点一律由列配置写入
            handoffService.record(handoffContext(issue), handoff, handoff.sprintId() != null, true);
        }
        Project project = projectMapper().selectById(issue.getProjectId());
        return toVO(issue, project == null ? "" : project.getProjectKey());
    }

    // ========================= 删除（树形递归 + 双向关联 + 附件） =========================

    @Override
    @Transactional
    public void delete(UUID id) {
        T root = mapper().selectById(id);
        if (root == null) {
            // 不存在时保持原语义（幂等删除），不要因为归档校验先抛 NPE
            return;
        }
        // 删除同样受归档只读约束（以根工作项所属项目为准）
        requireProjectWritable(root.getProjectId());
        List<UUID> ids = new ArrayList<>();
        collectDescendantIds(id, ids);
        deleteRows(ids);
    }

    /**
     * 只删除给定 id 本身，**不**连带子孙。
     *
     * <p>批量删除按用户勾选集合删除时用这个：用户明确取消了某几个子任务，
     * 就不能因为"父节点被删"把它们一起带走。被删节点的子任务由
     * {@link #promoteOrphanChildren(Set)} 提前上提。
     */
    @Override
    public void deleteExactly(Collection<UUID> ids) {
        if (ids == null || ids.isEmpty()) {
            return;
        }
        for (UUID id : ids) {
            T item = mapper().selectById(id);
            if (item != null) {
                requireProjectWritable(item.getProjectId());
            }
        }
        deleteRows(new ArrayList<>(ids));
    }

    /**
     * 把「即将被删除的节点」下、且本身不在删除集合里的子任务，上提到最近的存活祖先。
     *
     * <p>语义：删掉父节点但保留其中几个子任务时，这些子任务不能失去挂载点，
     * 因此向上找第一个不会被删的祖先；父节点是顶层则上提为顶层。
     *
     * <p><b>必须用 UpdateWrapper 显式 set</b>：{@code updateById} 默认忽略 null 字段，
     * 而"上提为顶层"恰恰是把 parent_id 置为 null——用 updateById 会静默不生效，
     * 子任务仍指向即将删除的父节点，再被数据库的
     * {@code parent_id REFERENCES ... ON DELETE CASCADE} 一起级联删掉。
     *
     * @return 被上提的子任务数量
     */
    @Override
    public int promoteOrphanChildren(Set<UUID> deleting) {
        if (deleting == null || deleting.isEmpty()) {
            return 0;
        }
        int promoted = 0;
        for (UUID id : deleting) {
            T node = mapper().selectById(id);
            if (node == null) {
                continue;
            }
            UUID target = survivingAncestor(node.getParentId(), deleting);
            List<T> children = mapper().selectList(Wrappers.<T>query().eq(WorkItemColumns.PARENT_ID, id));
            for (T child : children) {
                if (deleting.contains(child.getId())) {
                    continue; // 本来就要删，无需上提
                }
                UpdateWrapper<T> promote = Wrappers.update();
                promote.eq(WorkItemColumns.ID, child.getId());
                promote.set(WorkItemColumns.PARENT_ID, target);
                int updated = mapper().update(null, promote);
                if (updated > 0) {
                    promoted++;
                }
            }
        }
        return promoted;
    }

    /** 沿 parent_id 向上找第一个不在删除集合里的祖先；找不到（父链全被删或本来无父）返回 null（顶层） */
    private UUID survivingAncestor(UUID parentId, Set<UUID> deleting) {
        UUID cursor = parentId;
        // 防御异常数据造成的环：最多上溯 MAX_PARENT_DEPTH 层
        for (int guard = 0; cursor != null && deleting.contains(cursor) && guard < MAX_PARENT_DEPTH; guard++) {
            T parent = mapper().selectById(cursor);
            cursor = parent == null ? null : parent.getParentId();
        }
        return cursor != null && deleting.contains(cursor) ? null : cursor;
    }

    /** 真正落库的删除：主表 + 评论/附件/标签/参与人/抄送/关联/动态，附带清理物理文件与缓存 */
    private void deleteRows(List<UUID> ids) {
        if (ids == null || ids.isEmpty()) {
            return;
        }
        String category = categoryName();

        // 先取附件 storage_key：记录删掉后就无从得知磁盘上该删哪些文件了
        List<String> storageKeys = attachmentStorageKeys(ids, category);

        mapper().delete(Wrappers.<T>query().in(WorkItemColumns.ID, ids));
        issueCommentMapper().delete(Wrappers.<IssueComment>lambdaQuery()
                .in(IssueComment::getIssueId, ids).eq(IssueComment::getCategory, category));
        issueAttachmentMapper().delete(Wrappers.<IssueAttachment>lambdaQuery()
                .in(IssueAttachment::getIssueId, ids).eq(IssueAttachment::getCategory, category));
        issueTagMapper().deleteIssueLabelByIssueIdsAndCategory(ids, category);
        issueParticipantMapper().delete(Wrappers.<IssueParticipant>lambdaQuery()
                .in(IssueParticipant::getIssueId, ids).eq(IssueParticipant::getCategory, category));
        issueCcMapper().delete(Wrappers.<IssueCc>lambdaQuery()
                .in(IssueCc::getIssueId, ids).eq(IssueCc::getCategory, category));
        issueLinkMapper().delete(Wrappers.<IssueLink>lambdaQuery()
                .and(w -> w.in(IssueLink::getIssueId, ids).or().in(IssueLink::getTargetIssueId, ids)));
        issueActivityMapper().delete(Wrappers.<IssueActivity>lambdaQuery()
                .in(IssueActivity::getIssueId, ids).eq(IssueActivity::getCategory, category));
        // 物理文件随记录一起清理：否则删掉的工作项附件仍能被任意登录用户凭 URL 下载（孤儿文件还会一直占盘）
        attachmentFileStore.deleteQuietly(storageKeys);
        // 失效 issue -> project 解析缓存（静态调用，不引入 Bean 依赖），
        // 否则权限拦截器会拿到已删除工作项的陈旧归属
        ids.forEach(IssueProjectCache::invalidate);
    }

    /** 收集一批工作项的附件 storage_key（仅本地上传的，存量 local/ 前缀会被 deleteQuietly 跳过） */
    private List<String> attachmentStorageKeys(List<UUID> issueIds, String category) {
        return issueAttachmentMapper().selectList(Wrappers.<IssueAttachment>lambdaQuery()
                        .in(IssueAttachment::getIssueId, issueIds)
                        .eq(IssueAttachment::getCategory, category))
                .stream().map(IssueAttachment::getStorageKey).filter(Objects::nonNull).toList();
    }

    /**
     * 删除某项目下本表的全部工作项（批量）。
     *
     * 与逐条 {@link #delete(UUID)} 的区别：delete 会对每个工作项做一次递归子孙收集
     * 再加多张子表的删除，属于 N+1 查询，项目较大时删除会明显变慢甚至超时。
     * 整项目删除场景下所有工作项都会被删掉，无需逐条递归，直接按 project_id 批量清理即可。
     *
     * @return 删除的工作项条数
     */
    @Override
    @Transactional
    public int deleteAllByProject(UUID projectId) {
        List<UUID> ids = mapper().selectList(Wrappers.<T>query().eq(WorkItemColumns.PROJECT_ID, projectId))
                .stream().map(AbstractWorkItem::getId).toList();
        if (ids.isEmpty()) {
            return 0;
        }
        String category = categoryName();

        // 同 delete：附件记录删除前先留住 storage_key，否则磁盘文件永远删不掉
        List<String> storageKeys = attachmentStorageKeys(ids, category);

        mapper().delete(Wrappers.<T>query().eq(WorkItemColumns.PROJECT_ID, projectId));
        issueCommentMapper().delete(Wrappers.<IssueComment>lambdaQuery()
                .in(IssueComment::getIssueId, ids).eq(IssueComment::getCategory, category));
        issueAttachmentMapper().delete(Wrappers.<IssueAttachment>lambdaQuery()
                .in(IssueAttachment::getIssueId, ids).eq(IssueAttachment::getCategory, category));
        issueTagMapper().deleteIssueLabelByIssueIdsAndCategory(ids, category);
        issueParticipantMapper().delete(Wrappers.<IssueParticipant>lambdaQuery()
                .in(IssueParticipant::getIssueId, ids).eq(IssueParticipant::getCategory, category));
        issueCcMapper().delete(Wrappers.<IssueCc>lambdaQuery()
                .in(IssueCc::getIssueId, ids).eq(IssueCc::getCategory, category));
        // 关联是双向的，指向被删工作项的反向关联也要清掉，且不带 category 过滤
        issueLinkMapper().delete(Wrappers.<IssueLink>lambdaQuery()
                .and(w -> w.in(IssueLink::getIssueId, ids).or().in(IssueLink::getTargetIssueId, ids)));
        issueActivityMapper().delete(Wrappers.<IssueActivity>lambdaQuery()
                .in(IssueActivity::getIssueId, ids).eq(IssueActivity::getCategory, category));
        attachmentFileStore.deleteQuietly(storageKeys);
        // 失效 issue -> project 解析缓存，避免权限拦截器拿到陈旧归属
        ids.forEach(IssueProjectCache::invalidate);
        return ids.size();
    }

    private void collectDescendantIds(UUID parentId, List<UUID> out) {
        out.add(parentId);
        List<T> children = mapper().selectList(Wrappers.<T>query().eq(WorkItemColumns.PARENT_ID, parentId));
        for (T child : children) collectDescendantIds(child.getId(), out);
    }

    /** 排序键计算与列内重排已抽到 {@link WorkItemSortKeyService}（算法与业务字段无关，可独立测试） */
    private String computeSortKey(UUID projectId, UUID statusId, UUID beforeId, UUID afterId) {
        return sortKeyService.computeSortKey(mapper(),
                new WorkItemSortKeyService.SortKeyRequest(projectId, statusId, beforeId, afterId));
    }

    // ========================= 看板聚合（供 BoardService 调用，已按表隔离） =========================

    @Override
    @Transactional(readOnly = true)
    public List<IssueVO> selectByProjectForBoard(UUID projectId) {
        List<T> issues = mapper().selectList(Wrappers.<T>query()
                .eq(WorkItemColumns.PROJECT_ID, projectId).eq(WorkItemColumns.ARCHIVED, false)
                .orderByAsc(WorkItemColumns.SORT_KEY));
        return toVOList(issues, resolveProjectKey(projectId));
    }

    // ========================= VO 组装（与前端 IssueVO 完全一致） =========================

    /** 批量装配委托给 {@link IssueVoAssembler}（每类关联数据一次查询，避免 9×N） */
    public List<IssueVO> toVOList(List<T> issues, String projectKey) {
        return issueVoAssembler.toVOList(issues, projectKey, category());
    }

    public IssueVO toVO(T issue) {
        return toVO(issue, resolveProjectKey(issue.getProjectId()));
    }

    /**
     * 单条装配统一走批量实现，保证列表页与详情页字段口径完全一致
     * （此前这里是另一套逐字段查询的装配逻辑，与 assembleVO 双轨并存，新增字段极易漏改其一）。
     */
    public IssueVO toVO(T issue, String projectKey) {
        return issueVoAssembler.toVO(issue, projectKey, category());
    }

    // ========================= 内部工具 =========================

    // ========================= 动态记录 =========================

    /**
     * 写入工作项动态（issue_activity）。
     * category 必须显式设置：该列默认值为 TASK，漏设会导致需求/缺陷的动态查不到也删不掉。
     */
    private void recordActivity(UUID issueId, String action, String oldValue, String newValue) {
        IssueActivity act = new IssueActivity();
        act.setId(UUID.randomUUID());
        act.setIssueId(issueId);
        act.setCategory(categoryName());
        act.setActorId(UserContext.getUserId());
        act.setAction(action);
        act.setOldValue(oldValue);
        act.setNewValue(newValue);
        act.setCreatedAt(LocalDateTime.now());
        issueActivityMapper().insert(act);
    }

    /** 动态中的状态值统一用 {"status": "CODE"} 形式，编码实现见 {@link ActivityValue#status} */
    private String jsonStatus(String code) {
        return ActivityValue.status(code);
    }

    // ========================= 交接（handoff） =========================

    /** 交接的解析、动态、落点与撤销副作用都委托给 {@link WorkItemHandoffService}（只依赖值对象，与具体表无关） */
    private WorkItemHandoffService.Context handoffContext(T issue) {
        return new WorkItemHandoffService.Context(issue.getId(), issue.getProjectId(), category(),
                issue.getIssueNumber(), issue.getTitle(), issue.getAssigneeId(), issue.getSprintId());
    }

    /**
     * 撤销最近一次交接：负责人回到交接前的人。
     *
     * <p>Multica 里误交接靠「取消这条 run」兜底；我们没有 run 可取消，
     * 等价能力就是把负责人退回去，并给退回的人补一条通知（否则任务悄悄回到他名下没人知道）。
     *
     * <p>可撤销性与迭代回退的判断在 {@link WorkItemHandoffService#planRevoke}，
     * 本方法只负责落库与回填实体，保证「改库」与「写动态/发通知」在同一事务里。
     *
     * <p>撤销记成 {@code HANDOFF_REVOKED} 而不是再写一条 ASSIGNEE_CHANGED：否则撤销本身
     * 又变成一条「可以撤销的交接」，按钮会永远挂着（撤销的撤销语义上是重新转交，不是撤销）。
     */
    @Override
    @Transactional
    public IssueVO revokeHandoff(UUID id) {
        T issue = requireIssue(id);
        WorkItemHandoffService.Context ctx = handoffContext(issue);
        WorkItemHandoffService.RevokePlan plan = handoffService.planRevoke(ctx);

        UpdateWrapper<T> uw = Wrappers.update();
        uw.eq(WorkItemColumns.ID, id);
        if (plan.fromUserId() == null) {
            UpdateWrappers.clearToNull(uw, WorkItemColumns.ASSIGNEE_ID);
        } else {
            uw.set(WorkItemColumns.ASSIGNEE_ID, plan.fromUserId());
        }
        // 迭代落点一起退回；标签不退（标签可累加，删掉等于覆盖别人的编辑）
        if (plan.sprintRestored()) {
            if (plan.restoredSprintId() == null) {
                UpdateWrappers.clearToNull(uw, WorkItemColumns.SPRINT_ID);
            } else {
                uw.set(WorkItemColumns.SPRINT_ID, plan.restoredSprintId());
            }
        }
        LocalDateTime now = LocalDateTime.now();
        uw.set(WorkItemColumns.UPDATED_AT, now);
        mapper().update(null, uw);

        // 传撤销前的迭代：动态的「旧值」要写它，而下面马上就把实体改掉了
        handoffService.recordRevoke(ctx, plan, issue.getSprintId(), UserContext.getUserId());

        issue.setAssigneeId(plan.fromUserId());
        if (plan.sprintRestored()) {
            issue.setSprintId(plan.restoredSprintId());
        }
        issue.setUpdatedAt(now);
        return toVO(issue, resolveProjectKey(issue.getProjectId()));
    }

    /** clearFields 里是否点名要清空该字段 */
    private boolean clearsField(UpdateIssueRequest req, String field) {
        Set<String> clearFields = req.getClearFields();
        return clearFields != null && clearFields.contains(field);
    }

    private String resolveProjectKey(UUID projectId) {
        Project p = projectMapper().selectById(projectId);
        return p == null ? "" : p.getProjectKey();
    }

    private T requireIssue(UUID id) {
        T issue = mapper().selectById(id);
        if (issue == null) throw new ServiceException(ErrorCode.WORK_ITEM_NOT_FOUND, category());
        return issue;
    }
}

package com.seven7m.kanban.service.workitem;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.seven7m.kanban.common.activity.ActivityValue;
import com.seven7m.kanban.common.domain.PageResult;
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
import com.seven7m.kanban.dto.workflow.HandoffTarget;
import com.seven7m.kanban.entity.issue.IssueActivity;
import com.seven7m.kanban.entity.issue.IssueCc;
import com.seven7m.kanban.entity.issue.IssueParticipant;
import com.seven7m.kanban.entity.issue.IssueType;
import com.seven7m.kanban.entity.issue.attachment.IssueAttachment;
import com.seven7m.kanban.entity.issue.comment.IssueComment;
import com.seven7m.kanban.entity.issue.link.IssueLink;
import com.seven7m.kanban.entity.issuestatus.IssueStatus;
import com.seven7m.kanban.entity.project.Project;
import com.seven7m.kanban.entity.user.SysUser;
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
import com.seven7m.kanban.service.notification.NotificationService;
import com.seven7m.kanban.service.issue.reference.IssueReferenceGuard;
import com.seven7m.kanban.service.support.ProjectWriteGuard;
import com.seven7m.kanban.service.workflow.WorkflowService;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * 工作项共用 Service（任务/需求/缺陷三类复用），方法签名与前端现有 IssueService 完全一致，
 * VO 结构与 IssueVO 完全一致。所有子表读写都强制携带 category 隔离维度，从查询层杜绝跨表串数据。
 *
 * @param <T> 具体工作项实体（Task / Requirement / Bug）
 */
public abstract class BaseWorkItemService<T extends AbstractWorkItem> {

    /** 本类别的身份与表特异性依赖（类别、Mapper、编号生成、实体工厂） */
    private final WorkItemDeps<T> deps;

    protected BaseWorkItemService(WorkItemDeps<T> deps) {
        this.deps = deps;
    }

    // ---- 以下访问器把 deps 摊平成原有调用点的写法，避免全类改 40+ 处调用 ----
    protected final BaseMapper<T> mapper() { return deps.mapper(); }
    protected final ItemCategory category() { return deps.category(); }
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

    /** 工作项 VO 装配（批量预取 + 字段映射，与权限/事务无关） */
    @Autowired
    protected IssueVoAssembler issueVoAssembler;

    /** 当前用户是否具备该项目下的指定权限码，否则 403 */
    private void requirePermission(UUID projectId, String code, String action) {
        if (projectId == null || !permissionService.hasPermission(UserContext.getUserId(), projectId, code)) {
            throw new ServiceException(403, "无权限" + action);
        }
    }

    /** issue_status.category 中表示"已完成"的取值（见 V1.0.0 种子数据） */
    private static final String STATUS_CATEGORY_DONE = "DONE";

    private boolean isDoneCategory(IssueStatus status) {
        return status != null && STATUS_CATEGORY_DONE.equals(status.getCategory());
    }

    /**
     * 归档项目只读：归档后仍能新建/编辑/拖动工作项，会让"归档"仅仅等于从列表里隐藏。
     * 这里在写路径统一拦截（项目自身的归档/取消归档走 ProjectService，不经过本方法）。
     */
    private void requireProjectWritable(UUID projectId) {
        projectWriteGuard.requireWritable(projectId);
    }


    // ========================= 列表/详情 =========================

    @Transactional(readOnly = true)
    public PageResult<IssueVO> listByFilter(IssueFilterRequest req) {
        UUID me = UserContext.getUserId();
        QueryWrapper<T> q = Wrappers.query();
        q.eq("project_id", req.getProjectId());

        if (req.getStatusId() != null) q.eq("status_id", req.getStatusId());
        if (req.getStatusCategory() != null && !req.getStatusCategory().isBlank()) {
            q.apply("status_id IN (SELECT id FROM issue_status WHERE category = {0})", req.getStatusCategory());
        }
        if (Boolean.TRUE.equals(req.getOverdue())) {
            q.isNotNull("due_date")
                    .lt("due_date", java.time.LocalDate.now())
                    .apply("status_id NOT IN (SELECT id FROM issue_status WHERE category = 'DONE')");
        }
        if (req.getSprintId() != null) q.eq("sprint_id", req.getSprintId());
        if (req.getPriority() != null) q.eq("priority", req.getPriority());
        if (req.getSeverity() != null && this instanceof com.seven7m.kanban.service.workitem.BugService)
            q.eq("severity", req.getSeverity());
        if (req.getKeyword() != null && !req.getKeyword().isBlank())
            // 转义 LIKE 通配符：_ / % 是合法搜索字符，不转义会退化成"匹配任意内容"
            q.apply("title LIKE {0} ESCAPE '\\'", LikeEscaper.contains(req.getKeyword()));

        String scope = req.getScope();
        if ("ASSIGNED".equals(scope)) q.eq("assignee_id", me);
        else if ("REPORTED".equals(scope)) q.eq("reporter_id", me);
        else if ("VERIFIED".equals(scope)) q.eq("verifier_id", me);
        else if ("SHARED".equals(scope))
            q.apply("id IN (SELECT issue_id FROM issue_participant WHERE user_id = {0} AND category = {1})", me, categoryName());
        else if ("ARCHIVED".equals(scope)) q.eq("archived", true);
        // ACTIVE、未指定以及无法识别的 scope（拼写错误等）一律只查未归档：
        // 原实现只对 null 过滤，传入未知取值时会把归档工作项一并返回
        else q.eq("archived", false);

        if (req.getLabelId() != null) {
            q.apply("id IN (SELECT issue_id FROM issue_label WHERE label_id = {0} AND category = {1})", req.getLabelId(), categoryName());
        }

        // 工作项类型编码过滤（需求/任务/缺陷分类，避免列表互相混入）
        if (req.getIssueTypeCodes() != null && !req.getIssueTypeCodes().isEmpty()) {
            List<IssueType> types = issueTypeMapper().selectList(Wrappers.<IssueType>lambdaQuery()
                    .in(IssueType::getCode, req.getIssueTypeCodes()));
            List<UUID> typeIds = types.stream().map(IssueType::getId).toList();
            if (typeIds.isEmpty()) q.apply("1 = 0");
            else q.in("issue_type_id", typeIds);
        }

        int page = req.getPage() == null || req.getPage() < 1 ? 1 : req.getPage();
        int size = req.getSize() == null || req.getSize() < 1 ? 1000 : Math.min(req.getSize(), 1000);

        long total = mapper().selectCount(q);
        applySort(q, req.getSortField(), req.getSortOrder());
        q.last("LIMIT " + size + " OFFSET " + ((long) (page - 1) * size));

        List<T> issues = mapper().selectList(q);
        Project project = projectMapper().selectById(req.getProjectId());
        String key = project == null ? "" : project.getProjectKey();
        return new PageResult<>(total, page, size, toVOList(issues, key));
    }

    @Transactional(readOnly = true)
    public IssueVO getById(UUID id) {
        T issue = mapper().selectById(id);
        if (issue == null) throw new ServiceException(404, category() + " 不存在");
        return toVO(issue);
    }

    /** 轻量查询：返回主表 id/projectId/issueNumber/issueKey/title，不加载子资源，供关联展示/权限解析等高频场景使用 */
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

    public UUID getProjectId(UUID issueId) {
        T i = mapper().selectOne(Wrappers.<T>query().eq("id", issueId).last("LIMIT 1"));
        return i == null ? null : i.getProjectId();
    }

    public List<IssueVO> listChildren(UUID issueId) {
        T parent = mapper().selectById(issueId);
        if (parent == null) return List.of();
        String key = resolveProjectKey(parent.getProjectId());
        List<T> children = mapper().selectList(Wrappers.<T>query()
                .eq("parent_id", issueId).orderByAsc("sort_key"));
        return toVOList(children, key);
    }

    // ========================= 创建 =========================

    @Transactional
    public IssueVO create(CreateIssueRequest req) {
        Project project = projectMapper().selectById(req.getProjectId());
        if (project == null) throw new ServiceException(404, "项目不存在");
        requireProjectWritable(project.getId());
        IssueStatus status = issueStatusMapper().selectById(req.getStatusId());
        if (status == null) throw new ServiceException(400, "状态不存在");
        // 状态字典是全局的，但任务/需求/缺陷各有自己的工作流节点（V31 起）：
        // 只允许用本类别的节点建单，否则会建出「任务停在已修复」这种跨流程脏数据
        UUID targetStatusId = status.getId();
        boolean inWorkflow = workflowService().statusesOf(req.getProjectId(), category()).stream()
                .anyMatch(st -> st.getId().equals(targetStatusId));
        if (!inWorkflow) throw new ServiceException(400, "状态不属于该工作项类型的工作流");

        UUID issueTypeId = resolveIssueTypeId(req);
        // 迭代必须属于本项目，否则会造出挂在别的项目迭代上的工作项
        issueReferenceGuard.requireSprintInProject(req.getProjectId(), req.getSprintId());

        T issue = newEntityInstance();
        BeanUtils.copyProperties(req, issue);
        issue.setIssueTypeId(issueTypeId);
        issue.setId(UUID.randomUUID());
        issue.setIssueNumber(nextIssueNumber());
        issue.setReporterId(UserContext.getUserId());
        issue.setPriority(req.getPriority() == null ? "P2" : req.getPriority());
        if (issue instanceof com.seven7m.kanban.entity.workitem.Bug bug) {
            bug.setSeverity(req.getSeverity() == null ? "S3" : req.getSeverity());
        }
        issue.setArchived(req.getArchived() == null ? false : req.getArchived());
        // 直接以"已完成"状态建单时也要落 done_at，否则周期统计会漏掉这类数据
        issue.setDoneAt(isDoneCategory(status) ? LocalDateTime.now() : null);
        // 追加到目标状态列末尾：必须取列内现有最大键，否则所有新卡片 sort_key 相同；
        // 走 computeSortKey 是为了在经过几十次追加、键变长时自动触发列内重排
        issue.setSortKey(computeSortKey(req.getProjectId(), req.getStatusId(), null, null));
        issue.setCreatedBy(UserContext.getUserId());
        issue.setCreatedAt(LocalDateTime.now());
        issue.setUpdatedAt(LocalDateTime.now());
        mapper().insert(issue);
        recordActivity(issue.getId(), "CREATED", null, null);
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
                throw new ServiceException(400, "工作项类型不存在: " + typeId);
            }
            return requireTypeInCategory(type);
        }
        if (code == null || code.isBlank()) {
            throw new ServiceException(400, "缺少工作项类型：issueTypeId 与 issueTypeCode 至少填一个");
        }
        IssueType byCode = issueTypeMapper().selectOne(Wrappers.<IssueType>lambdaQuery()
                .eq(IssueType::getCode, code).last("LIMIT 1"));
        if (byCode != null) return requireTypeInCategory(byCode);

        ItemCategory category = WorkItemRouter.categoryOfCode(code);
        if (category == null) throw new ServiceException(400, "未知的工作项类型: " + code);
        IssueType alias = issueTypeMapper().selectOne(Wrappers.<IssueType>lambdaQuery()
                .in(IssueType::getCode, WorkItemRouter.codesOf(category))
                .orderByAsc(IssueType::getSortOrder).last("LIMIT 1"));
        if (alias == null) throw new ServiceException(400, "未初始化 IssueType: " + category);
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
            throw new ServiceException(400, "未注册的工作项类型: " + type.getCode());
        }
        if (typeCategory != category()) {
            throw new ServiceException(400, "工作项类型「" + type.getName() + "」属于 " + typeCategory
                    + " 类别，不能用于 " + category() + " 类别的工作项");
        }
        return type.getId();
    }

    // ========================= 更新 =========================

    @Transactional
    public IssueVO update(UUID id, UpdateIssueRequest req) {
        T issue = requireIssue(id);
        requireProjectWritable(issue.getProjectId());
        requireUpdatePermissions(issue, req);
        requireReferencesInProject(issue, req);

        UpdateWrapper<T> uw = Wrappers.update();
        uw.eq("id", id);
        applyScalarFields(uw, issue, req);
        applyClearFields(uw, issue, req);
        StatusOutcome status = applyStatusTransition(uw, issue, req);

        if (uw.getSqlSet() != null && !uw.getSqlSet().isEmpty()) {
            uw.set("updated_at", LocalDateTime.now());
            mapper().update(null, uw);
        }
        recordStatusActivity(id, status);
        recordHandoffIfAny(issue, status, req);
        applyParticipantsAndCc(id, req);
        applyLabels(id, req);
        return toVO(mapper().selectById(id));
    }

    /** 状态变更的结果：用于事后写活动记录与交接落点，避免各段重复解析 */
    private record StatusOutcome(String fromCode, String toCode, HandoffDecision handoff, boolean sprintApplied) {
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
                throw new ServiceException(400, "工作项类型不存在: " + req.getIssueTypeId());
            }
            requireTypeInCategory(type);
        }
        // 细粒度权限：状态变更、负责人变更不只是「编辑」，分别对应 issue:status / issue:assign。
        // 此前这两个权限码定义了却从未校验，管理员在 RBAC 里取消勾选完全不生效（假权限）。
        if (isStatusChanging(issue, req)) {
            requirePermission(issue.getProjectId(), Permission.ISSUE_STATUS, "修改工作项状态");
        }
        boolean assigneeCleared = req.getClearFields() != null
                && req.getClearFields().contains("assigneeId");
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
        if (req.getTitle() != null) uw.set("title", req.getTitle());
        if (req.getDescription() != null) uw.set("description", req.getDescription());
        if (req.getPriority() != null) uw.set("priority", req.getPriority());
        if (req.getIssueTypeId() != null) uw.set("issue_type_id", req.getIssueTypeId());
        if (req.getAssigneeId() != null) uw.set("assignee_id", req.getAssigneeId());
        if (req.getVerifierId() != null) uw.set("verifier_id", req.getVerifierId());
        if (req.getSprintId() != null) uw.set("sprint_id", req.getSprintId());
        if (req.getDueDate() != null) uw.set("due_date", req.getDueDate());
        if (req.getArchived() != null) uw.set("archived", req.getArchived());
        if (req.getModule() != null) {
            if (issue instanceof Requirement || issue instanceof Bug) uw.set("module", req.getModule());
        }
        if (issue instanceof Bug) {
            if (req.getSeverity() != null) uw.set("severity", req.getSeverity());
            if (req.getFixVersion() != null) uw.set("fix_version", req.getFixVersion());
            if (req.getSource() != null) uw.set("source", req.getSource());
            if (req.getIteration() != null) uw.set("iteration", req.getIteration());
            if (req.getPlanStart() != null) uw.set("plan_start", req.getPlanStart());
            if (req.getNoFixReason() != null) uw.set("no_fix_reason", req.getNoFixReason());
        }
    }

    /** 显式清空字段（updateById 会跳过 null，必须在这里写 set null） */
    private void applyClearFields(UpdateWrapper<T> uw, T issue, UpdateIssueRequest req) {
        if (req.getClearFields() == null) {
            return;
        }
        for (String field : req.getClearFields()) {
            switch (field) {
                case "assigneeId" -> uw.set("assignee_id", null);
                case "verifierId" -> uw.set("verifier_id", null);
                case "sprintId" -> uw.set("sprint_id", null);
                case "dueDate" -> uw.set("due_date", null);
                case "planStart" -> { if (issue instanceof Bug) uw.set("plan_start", null); }
                case "iteration" -> { if (issue instanceof Bug) uw.set("iteration", null); }
                case "noFixReason" -> { if (issue instanceof Bug) uw.set("no_fix_reason", null); }
                case "module" -> {
                    if (issue instanceof Requirement || issue instanceof Bug) uw.set("module", null);
                }
                case "fixVersion" -> { if (issue instanceof Bug) uw.set("fix_version", null); }
                case "participants", "ccUsers" -> { }
                default -> throw new ServiceException(400, "不支持清空字段: " + field);
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
        IssueStatus fromStatus = issue.getStatusId() == null ? null : issueStatusMapper().selectById(issue.getStatusId());
        IssueStatus target = issueStatusMapper().selectById(req.getStatusId());
        if (target == null) {
            throw new ServiceException(400, "状态不存在");
        }
        if (!workflowService().canTransition(issue.getProjectId(), category(), issue.getStatusId(), req.getStatusId())) {
            String fromName = fromStatus == null ? "未知状态" : fromStatus.getName();
            throw new ServiceException(400, "不允许从「" + fromName + "」流转到「" + target.getName() + "」");
        }
        uw.set("status_id", req.getStatusId());
        uw.set("sort_key", computeSortKey(issue.getProjectId(), req.getStatusId(), null, null));
        // 解决周期按 done_at 统计：进入 DONE 记时间，离开 DONE 清空
        if (isDoneCategory(target)) {
            uw.set("done_at", LocalDateTime.now());
        } else if (isDoneCategory(fromStatus)) {
            uw.setSql("done_at = NULL");
        }

        HandoffDecision handoff = resolveHandoff(issue, req.getStatusId(), target.getName(), req.getAssigneeId());
        boolean sprintApplied = false;
        if (handoff != null) {
            uw.set("assignee_id", handoff.toUserId());
            // 「真改了迭代」要排除掉「提交的还是当前迭代」——详情页整表单保存会把未变字段一并带上，
            // 若把相同值当成显式指派，落点就永远不会生效（负责人那侧同理）。
            boolean sprintSpecified = req.getSprintId() != null
                    && !req.getSprintId().equals(issue.getSprintId());
            if (handoff.sprintId() != null && !sprintSpecified && !clearsField(req, "sprintId")) {
                uw.set("sprint_id", handoff.sprintId());
                sprintApplied = true;
            }
        }
        String fromCode = fromStatus == null ? null : fromStatus.getCode();
        return new StatusOutcome(fromCode, target.getCode(), handoff, sprintApplied);
    }

    private void recordStatusActivity(UUID id, StatusOutcome status) {
        if (status.toCode() != null) {
            recordActivity(id, "STATUS_CHANGED", jsonStatus(status.fromCode()), jsonStatus(status.toCode()));
        }
    }

    private void recordHandoffIfAny(T issue, StatusOutcome status, UpdateIssueRequest req) {
        if (status.handoff() != null) {
            // labelIds 非空时下方会全量替换标签，交接再插一条等于白做，故只在未显式指定时补标签
            recordHandoff(issue, status.handoff(), status.sprintApplied(), req.getLabelIds() == null);
        }
    }

    private void applyParticipantsAndCc(UUID id, UpdateIssueRequest req) {
        boolean clearParticipants = req.getClearFields() != null && req.getClearFields().contains("participants");
        boolean clearCc = req.getClearFields() != null && req.getClearFields().contains("ccUsers");
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

    @Transactional
    public IssueVO move(UUID id, MoveIssueRequest req) {
        T issue = requireIssue(id);
        requireProjectWritable(issue.getProjectId());
        UUID fromStatus = issue.getStatusId();
        UUID toStatus = req.getStatusId();
        // 状态为空的历史数据无法做流转校验（from 为空时 canTransition 必失败且原因难懂），
        // 直接给可操作的提示，避免 NPE 或误导性的"不允许流转"
        if (fromStatus == null) {
            throw new ServiceException(400, "该工作项缺少当前状态，无法移动，请先在详情中设置状态");
        }
        boolean statusChanging = !Objects.equals(fromStatus, toStatus);
        IssueStatus fromStatusEntity = statusChanging ? issueStatusMapper().selectById(fromStatus) : null;
        boolean wasDone = isDoneCategory(fromStatusEntity);
        String statusFromCode = null;
        String statusToCode = null;
        HandoffDecision handoff = null;
        IssueStatus target = null;
        if (statusChanging) {
            // 跨列拖拽属于「状态变更」，对应 issue:status；同列排序仍只需要 issue:edit（注解已拦）
            requirePermission(issue.getProjectId(), Permission.ISSUE_STATUS, "修改工作项状态");
            target = issueStatusMapper().selectById(toStatus);
            if (target == null) throw new ServiceException(400, "状态不存在");
            if (!workflowService().canTransition(issue.getProjectId(), category(), fromStatus, toStatus)) {
                String fromName = fromStatusEntity == null ? "未知状态" : fromStatusEntity.getName();
                throw new ServiceException(400, "不允许从「" + fromName + "」流转到「" + target.getName() + "」");
            }
            statusFromCode = fromStatusEntity == null ? null : fromStatusEntity.getCode();
            statusToCode = target.getCode();
            handoff = resolveHandoff(issue, toStatus, target.getName(), null);
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
            mapper().update(issue, Wrappers.<T>update().eq("id", id).setSql("done_at = NULL"));
        } else {
            mapper().updateById(issue);
        }
        if (statusToCode != null) {
            recordActivity(id, "STATUS_CHANGED", jsonStatus(statusFromCode), jsonStatus(statusToCode));
        }
        if (handoff != null) {
            // 拖拽（move）请求不带迭代与标签，落点一律由列配置写入
            recordHandoff(issue, handoff, handoff.sprintId() != null, true);
        }
        Project project = projectMapper().selectById(issue.getProjectId());
        return toVO(issue, project == null ? "" : project.getProjectKey());
    }

    // ========================= 删除（树形递归 + 双向关联 + 附件） =========================

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
        String category = categoryName();

        // 先取附件 storage_key：记录删掉后就无从得知磁盘上该删哪些文件了
        List<String> storageKeys = attachmentStorageKeys(ids, category);

        mapper().delete(Wrappers.<T>query().in("id", ids));
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
    @Transactional
    public int deleteAllByProject(UUID projectId) {
        List<UUID> ids = mapper().selectList(Wrappers.<T>query().eq("project_id", projectId))
                .stream().map(AbstractWorkItem::getId).toList();
        if (ids.isEmpty()) {
            return 0;
        }
        String category = categoryName();

        // 同 delete：附件记录删除前先留住 storage_key，否则磁盘文件永远删不掉
        List<String> storageKeys = attachmentStorageKeys(ids, category);

        mapper().delete(Wrappers.<T>query().eq("project_id", projectId));
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
        List<T> children = mapper().selectList(Wrappers.<T>query().eq("parent_id", parentId));
        for (T child : children) collectDescendantIds(child.getId(), out);
    }

    /** 目标状态列内当前最大的 sort_key，用于把工作项追加到列尾；空列返回 null */
    private String tailSortKey(UUID projectId, UUID statusId) {
        T tail = mapper().selectOne(Wrappers.<T>query()
                .eq("project_id", projectId)
                .eq("status_id", statusId)
                .orderByDesc("sort_key")
                .last("LIMIT 1"));
        return tail == null ? null : tail.getSortKey();
    }

    /** 排序键计算与列内重排已抽到 {@link WorkItemSortKeyService}（算法与业务字段无关，可独立测试） */
    private String computeSortKey(UUID projectId, UUID statusId, UUID beforeId, UUID afterId) {
        return sortKeyService.computeSortKey(mapper(), projectId, statusId, beforeId, afterId);
    }

    // ========================= 看板聚合（供 BoardService 调用，已按表隔离） =========================

    @Transactional(readOnly = true)
    public List<IssueVO> selectByProjectForBoard(UUID projectId) {
        List<T> issues = mapper().selectList(Wrappers.<T>query()
                .eq("project_id", projectId).eq("archived", false)
                .orderByAsc("sort_key"));
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

    /** 交接动态：负责人由 A 变为 B */
    private static final String ACTION_ASSIGNEE_CHANGED = "ASSIGNEE_CHANGED";
    /** 撤销交接：负责人由 B 退回 A（与上面区分，避免撤销本身又成为可撤销项） */
    private static final String ACTION_HANDOFF_REVOKED = "HANDOFF_REVOKED";
    /** 交接落点：工作项被放进某个迭代（单列「迭代」是工作项的强属性，换掉要留痕） */
    private static final String ACTION_SPRINT_CHANGED = "SPRINT_CHANGED";
    /** 交接落点：工作项被补上某个标签（只在真正新增时记一条，已经有就静默） */
    private static final String ACTION_LABEL_ADDED = "LABEL_ADDED";


    /**
     * 一次流转的交接计划；仅在确实解析出「换人」时才有值。
     *
     * @param fromSprintId 交接前的迭代，撤销时据此把迭代退回去；null 表示本来没有迭代
     * @param sprintId     落点迭代，null 表示本列不规定落点
     * @param labelId      落点标签，null 表示本列不规定落点
     */
    private record HandoffDecision(UUID fromUserId, UUID toUserId, String toUserName,
                                   String statusName, String note,
                                   UUID sprintId, String sprintName,
                                   UUID labelId, String labelName,
                                   UUID fromSprintId) {
    }

    /**
     * 解析目标列的交接。用户主动改了负责人时跳过——刚选的人不该被列配置覆盖。
     *
     * <p>注意「显式指定」的判定要排除「等于当前负责人」的情形：详情页保存时会把未变的
     * 负责人一并提交，若把这种请求当作显式指派，从详情页改状态就永远不触发交接。
     *
     * @return null 表示本次流转不交接：无配置、类型 NONE、解析不出人、或与当前负责人相同
     */
    private HandoffDecision resolveHandoff(T issue, UUID toStatusId, String toStatusName, UUID explicitAssigneeId) {
        if (toStatusId == null) {
            return null;
        }
        if (explicitAssigneeId != null && !explicitAssigneeId.equals(issue.getAssigneeId())) {
            return null;
        }
        // 只在真正流转时查项目，静态更新（改标题/优先级）不付这次查询
        Project project = projectMapper().selectById(issue.getProjectId());
        HandoffTarget target = workflowService().resolveHandoffTarget(
                issue.getProjectId(), category(), toStatusId, project);
        if (target == null || target.userId().equals(issue.getAssigneeId())) {
            return null;
        }
        return new HandoffDecision(issue.getAssigneeId(), target.userId(), target.userName(),
                toStatusName, target.note(),
                target.sprintId(), target.sprintName(), target.labelId(), target.labelName(),
                issue.getSprintId());
    }

    /**
     * 交接副作用：动态 + 落点 + 站内通知。与负责人改写同处一个事务，
     * 任一失败整体回滚，不会留下「人转了但没通知」的中间态。
     *
     * @param applySprint 迭代落点是否由本次交接写入（用户在请求里显式指定了迭代时为 false，
     *                    与「显式指定负责人不覆盖」同一原则）
     * @param applyLabel  标签落点是否由本次交接写入（请求里带了 labelIds 时为 false：
     *                    那是全量替换，交接再插一次会被下一行代码删掉，属于白做工）
     */
    private void recordHandoff(T issue, HandoffDecision decision, boolean applySprint, boolean applyLabel) {
        String fromName = userNameOf(decision.fromUserId());
        // 首尾两条都带上「当时的迭代」：撤销要靠它们判断交接到底动没动迭代，
        // 以及这一刻迭代是否还停在交接写入的那个值（被别人改过就不能退回去覆盖）
        UUID toSprintId = applySprint && decision.sprintId() != null
                ? decision.sprintId()
                : decision.fromSprintId();
        recordActivity(issue.getId(), ACTION_ASSIGNEE_CHANGED,
                jsonUserWithSprint(fromName, decision.fromUserId(), decision.fromSprintId()),
                jsonUserWithSprint(decision.toUserName(), decision.toUserId(), toSprintId));

        boolean sprintApplied = applySprint && decision.sprintId() != null;
        if (sprintApplied) {
            String fromSprintName = decision.fromSprintId() == null
                    ? null
                    : workflowService().sprintNameOf(decision.fromSprintId());
            recordActivity(issue.getId(), ACTION_SPRINT_CHANGED,
                    jsonField("sprint", fromSprintName), jsonField("sprint", decision.sprintName()));
        }
        boolean labelApplied = false;
        if (applyLabel && decision.labelId() != null) {
            Integer existing = issueTagMapper().countIssueLabel(
                    issue.getId(), decision.labelId(), categoryName());
            // 已经有这个标签就不写：重复 insert 违反唯一约束，白写一条「打上标签」的动态也没意义
            if (existing == null || existing == 0) {
                issueTagMapper().insertIssueLabel(issue.getId(), decision.labelId(), categoryName());
                recordActivity(issue.getId(), ACTION_LABEL_ADDED, null,
                        jsonField("label", decision.labelName()));
                labelApplied = true;
            }
        }

        // 通知里的落点按「实际发生了什么」写：标签本来就有的那种情况不该骗接手人说打上了
        notificationService().notifyHandoff(decision.toUserId(), issue.getProjectId(), issue.getId(),
                category().name(), issue.getIssueNumber(), issue.getTitle(), decision.statusName(),
                decision.note(),
                landingText(sprintApplied ? decision.sprintName() : null,
                        labelApplied ? decision.labelName() : null));
    }

    /** 交接通知里的一句落点说明；两项都没配返回 null，通知正文与之前完全一致 */
    private String landingText(String sprintName, String labelName) {
        List<String> parts = new ArrayList<>(2);
        if (sprintName != null) {
            parts.add("已放进迭代「" + sprintName + "」");
        }
        if (labelName != null) {
            parts.add("已打上标签「" + labelName + "」");
        }
        return parts.isEmpty() ? null : String.join("，", parts);
    }

    /**
     * 撤销最近一次交接：负责人回到交接前的人。
     *
     * <p>Multica 里误交接靠「取消这条 run」兜底；我们没有 run 可取消，
     * 等价能力就是把负责人退回去，并给退回的人补一条通知（否则任务悄悄回到他名下没人知道）。
     *
     * <p>只认最后一条交接动态，且它的接手人必须仍是当前负责人 —— 中间若有人手工改过负责人，
     * 撤销就会覆盖别人的编辑，这里直接拒绝而不是猜。
     *
     * <p>撤销记成 {@code HANDOFF_REVOKED} 而不是再写一条 ASSIGNEE_CHANGED：否则撤销本身
     * 又变成一条「可以撤销的交接」，按钮会永远挂着（撤销的撤销语义上是重新转交，不是撤销）。
     */
    @Transactional
    public IssueVO revokeHandoff(UUID id) {
        T issue = requireIssue(id);
        IssueActivity last = issueActivityMapper().selectOne(Wrappers.<IssueActivity>lambdaQuery()
                .eq(IssueActivity::getIssueId, id)
                .eq(IssueActivity::getCategory, categoryName())
                .in(IssueActivity::getAction, ACTION_ASSIGNEE_CHANGED, ACTION_HANDOFF_REVOKED)
                .orderByDesc(IssueActivity::getCreatedAt)
                .last("LIMIT 1"));
        if (last == null || !ACTION_ASSIGNEE_CHANGED.equals(last.getAction())) {
            throw new ServiceException(400, "没有可撤销的交接");
        }
        UUID handedTo = userIdOf(last.getNewValue());
        UUID handedFrom = userIdOf(last.getOldValue());
        if (handedTo == null || !handedTo.equals(issue.getAssigneeId())) {
            throw new ServiceException(400, "负责人已被再次变更，无法撤销这次交接");
        }

        UpdateWrapper<T> uw = Wrappers.update();
        uw.eq("id", id);
        if (handedFrom == null) {
            // set(column, null) 在 PG 上可能推断不出参数类型，置空直接写 SQL
            uw.setSql("assignee_id = NULL");
        } else {
            uw.set("assignee_id", handedFrom);
        }
        // 迭代落点一起退回。标签不退：标签是可累加的，撤销这一刻它可能已经被别人手工加过，
        // 删掉等于覆盖别人的编辑（ProjectHandoff 上有同样口径的说明）。
        //
        // 迭代则必须先确认「还停在交接写入的那个值」：负责人被改过就拒绝撤销，迭代被改过同理
        // 不能闷头退回去，否则撤销会悄悄抹掉别人的调整。这里的处理是跳过迭代回退、只退负责人，
        // 而不是整体拒绝 —— 用户的诉求是「把人退回去」，不该被一个已经无关的迭代卡住。
        boolean sprintRestored = false;
        UUID restoredSprintId = null;
        String oldSprintId = fieldOf(last.getOldValue(), "sprintId");
        String newSprintId = fieldOf(last.getNewValue(), "sprintId");
        if (oldSprintId != null && newSprintId != null
                && Objects.equals(parseUuidOrNull(newSprintId), issue.getSprintId())) {
            if (oldSprintId.isBlank()) {
                uw.setSql("sprint_id = NULL");
            } else {
                restoredSprintId = parseUuidOrNull(oldSprintId);
                if (restoredSprintId != null) {
                    uw.set("sprint_id", restoredSprintId);
                }
            }
            // 旧值损坏（解析不出 id）时不写这一项，避免把迭代误清空
            sprintRestored = restoredSprintId != null || oldSprintId.isBlank();
        }
        LocalDateTime now = LocalDateTime.now();
        uw.set("updated_at", now);
        mapper().update(null, uw);

        recordActivity(id, ACTION_HANDOFF_REVOKED,
                jsonUser(userNameOf(handedTo), handedTo), jsonUser(userNameOf(handedFrom), handedFrom));
        if (sprintRestored) {
            // 时间线要能看到迭代也退回去了，否则用户只看到「负责人被退回」而迭代悄悄变了
            recordActivity(id, ACTION_SPRINT_CHANGED,
                    jsonField("sprint", workflowService().sprintNameOf(issue.getSprintId())),
                    jsonField("sprint", restoredSprintId == null ? null : workflowService().sprintNameOf(restoredSprintId)));
        }
        if (handedFrom != null) {
            notificationService().notifyHandoffRevoked(handedFrom, issue.getProjectId(), issue.getId(),
                    category().name(), issue.getIssueNumber(), issue.getTitle(), userNameOf(UserContext.getUserId()));
        }

        issue.setAssigneeId(handedFrom);
        if (sprintRestored) {
            issue.setSprintId(restoredSprintId);
        }
        issue.setUpdatedAt(now);
        return toVO(issue, resolveProjectKey(issue.getProjectId()));
    }

    private String userNameOf(UUID userId) {
        if (userId == null) {
            return null;
        }
        SysUser user = sysUserMapper().selectById(userId);
        if (user == null) {
            return null;
        }
        String display = user.getDisplayName();
        return display == null || display.isBlank() ? user.getUsername() : display;
    }

    /**
     * 动态中的用户值：{@code {"user":"张三","userId":"..."}}。
     *
     * <p>userId 是给「撤销交接」定位原负责人用的；展示侧（ActivityTextResolver）只读 user 字段，
     * 所以这是对旧数据和旧前端都无感的字段扩展。历史记录没有 userId，
     * 解析不出来时按「不可撤销」处理，不做猜人。
     */
    private String jsonUser(String name, UUID userId) {
        return ActivityValue.user(name, userId);
    }

    /**
     * 交接动态里的「原负责人」值，额外带上交接前的迭代 id：
     * {@code {"user":"张三","userId":"...","sprintId":"..."}}。
     *
     * <p>sprintId 无论有没有都会写（没有就是空串）：撤销时要能区分「本来没有迭代」和
     * 「历史记录没记这个字段」，前者要把 sprint_id 置空，后者应保持原样什么都不做。
     */
    private String jsonUserWithSprint(String name, UUID userId, UUID sprintId) {
        return ActivityValue.userWithSprint(name, userId, sprintId);
    }

    /** 动态中的通用单字段值：{@code {"sprint":"S1"}} / {@code {"label":"bug"}} */
    private String jsonField(String field, String value) {
        return ActivityValue.field(field, value);
    }

    /** 解析动态里的 UUID；空串或格式不对一律 null（历史脏数据不该让撤销/流转整体失败） */
    private UUID parseUuidOrNull(String raw) {
        return Uuids.parseOrNull(raw);
    }

    /** 取动态值里某个字符串字段；字段不存在返回 null（注意空串与 null 语义不同） */
    private String fieldOf(String raw, String field) {
        return ActivityValue.text(raw, field);
    }

    /** clearFields 里是否点名要清空该字段 */
    private boolean clearsField(UpdateIssueRequest req, String field) {
        Set<String> clearFields = req.getClearFields();
        return clearFields != null && clearFields.contains(field);
    }

    /** 从动态的 user 值里取 userId；缺字段或格式不对返回 null */
    private UUID userIdOf(String raw) {
        return ActivityValue.userId(raw);
    }

    private String resolveProjectKey(UUID projectId) {
        Project p = projectMapper().selectById(projectId);
        return p == null ? "" : p.getProjectKey();
    }

    private void applySort(QueryWrapper<T> q, String sortField, String sortOrder) {
        // 排序规则（SQL 片段）与跨表合并用的内存 Comparator 共用 WorkItemSort 这一份定义
        WorkItemSort.applySql(q, sortField, sortOrder, category());
    }

    private T requireIssue(UUID id) {
        T issue = mapper().selectById(id);
        if (issue == null) throw new ServiceException(404, category() + " 不存在");
        return issue;
    }
}

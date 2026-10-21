package com.seven7m.kanban.service.workitem;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.seven7m.kanban.common.domain.PageResult;
import com.seven7m.kanban.common.enums.ItemCategory;
import com.seven7m.kanban.common.exception.ServiceException;
import com.seven7m.kanban.common.security.IssueProjectCache;
import com.seven7m.kanban.common.security.UserContext;
import com.seven7m.kanban.common.util.SortKeyUtils;
import com.seven7m.kanban.dto.issue.CreateIssueRequest;
import com.seven7m.kanban.dto.issue.IssueFilterRequest;
import com.seven7m.kanban.dto.issue.IssueVO;
import com.seven7m.kanban.dto.issue.LabelVO;
import com.seven7m.kanban.dto.issue.MoveIssueRequest;
import com.seven7m.kanban.dto.issue.UpdateIssueRequest;
import com.seven7m.kanban.entity.issue.IssueActivity;
import com.seven7m.kanban.entity.issue.IssueCc;
import com.seven7m.kanban.entity.issue.IssueParticipant;
import com.seven7m.kanban.entity.issue.IssueType;
import com.seven7m.kanban.entity.issue.attachment.IssueAttachment;
import com.seven7m.kanban.entity.issue.comment.IssueComment;
import com.seven7m.kanban.entity.issue.link.IssueLink;
import com.seven7m.kanban.entity.issue.tag.IssueTag;
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
import com.seven7m.kanban.service.workflow.WorkflowService;
import org.springframework.beans.BeanUtils;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * 工作项共用 Service（任务/需求/缺陷三类复用），方法签名与前端现有 IssueService 完全一致，
 * VO 结构与 IssueVO 完全一致。所有子表读写都强制携带 category 隔离维度，从查询层杜绝跨表串数据。
 *
 * @param <T> 具体工作项实体（Task / Requirement / Bug）
 */
public abstract class BaseWorkItemService<T extends AbstractWorkItem> {

    // ---- 子类必须提供 ----
    protected abstract BaseMapper<T> mapper();
    protected abstract ItemCategory category();
    protected abstract IssueTypeMapper issueTypeMapper();
    protected abstract IssueStatusMapper issueStatusMapper();
    protected abstract IssueParticipantMapper issueParticipantMapper();
    protected abstract IssueCcMapper issueCcMapper();
    protected abstract IssueActivityMapper issueActivityMapper();
    protected abstract IssueTagMapper issueTagMapper();
    protected abstract IssueCommentMapper issueCommentMapper();
    protected abstract IssueAttachmentMapper issueAttachmentMapper();
    protected abstract IssueLinkMapper issueLinkMapper();
    protected abstract SysUserMapper sysUserMapper();
    protected abstract ProjectMapper projectMapper();
    protected abstract WorkflowService workflowService();
    protected abstract String nextIssueNumber();
    protected abstract T newEntityInstance();

    private String categoryName() { return category().name(); }


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
            q.like("title", req.getKeyword());

        if ("ASSIGNED".equals(req.getScope())) q.eq("assignee_id", me);
        else if ("REPORTED".equals(req.getScope())) q.eq("reporter_id", me);
        else if ("VERIFIED".equals(req.getScope())) q.eq("verifier_id", me);
        else if ("SHARED".equals(req.getScope()))
            q.apply("id IN (SELECT issue_id FROM issue_participant WHERE user_id = {0} AND category = {1})", me, categoryName());
        else if ("ARCHIVED".equals(req.getScope())) q.eq("archived", true);
        else if ("ACTIVE".equals(req.getScope())) q.eq("archived", false);

        if (req.getScope() == null) q.eq("archived", false);

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
        IssueStatus status = issueStatusMapper().selectById(req.getStatusId());
        if (status == null) throw new ServiceException(400, "状态不存在");
        // 状态字典是全局的，但任务/需求/缺陷各有自己的工作流节点（V31 起）：
        // 只允许用本类别的节点建单，否则会建出「任务停在已修复」这种跨流程脏数据
        UUID targetStatusId = status.getId();
        boolean inWorkflow = workflowService().statusesOf(req.getProjectId(), category()).stream()
                .anyMatch(st -> st.getId().equals(targetStatusId));
        if (!inWorkflow) throw new ServiceException(400, "状态不属于该工作项类型的工作流");

        UUID issueTypeId = resolveIssueTypeId(req);

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
        // 追加到目标状态列末尾：必须取列内现有最大键，否则所有新卡片 sort_key 相同，
        // 排序出现并列（Postgres 返回顺序不确定）且后续拖拽无中间值可用
        issue.setSortKey(SortKeyUtils.after(
                tailSortKey(req.getProjectId(), req.getStatusId())));
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
     */
    private UUID resolveIssueTypeId(CreateIssueRequest req) {
        UUID typeId = req.getIssueTypeId();
        String code = req.getIssueTypeCode() == null ? null : req.getIssueTypeCode().trim();
        if (typeId != null) {
            if (issueTypeMapper().selectById(typeId) == null) {
                throw new ServiceException(400, "工作项类型不存在: " + typeId);
            }
            return typeId;
        }
        if (code == null || code.isBlank()) {
            throw new ServiceException(400, "缺少工作项类型：issueTypeId 与 issueTypeCode 至少填一个");
        }
        IssueType byCode = issueTypeMapper().selectOne(Wrappers.<IssueType>lambdaQuery()
                .eq(IssueType::getCode, code).last("LIMIT 1"));
        if (byCode != null) return byCode.getId();

        ItemCategory category = WorkItemRouter.categoryOfCode(code);
        if (category == null) throw new ServiceException(400, "未知的工作项类型: " + code);
        IssueType alias = issueTypeMapper().selectOne(Wrappers.<IssueType>lambdaQuery()
                .in(IssueType::getCode, WorkItemRouter.codesOf(category))
                .orderByAsc(IssueType::getSortOrder).last("LIMIT 1"));
        if (alias == null) throw new ServiceException(400, "未初始化 IssueType: " + category);
        return alias.getId();
    }

    // ========================= 更新 =========================

    @Transactional
    public IssueVO update(UUID id, UpdateIssueRequest req) {
        T issue = requireIssue(id);
        UpdateWrapper<T> uw = Wrappers.update();
        uw.eq("id", id);
        if (req.getIssueTypeId() != null && issueTypeMapper().selectById(req.getIssueTypeId()) == null) {
            throw new ServiceException(400, "工作项类型不存在: " + req.getIssueTypeId());
        }

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
            if (issue instanceof Requirement r) uw.set("module", req.getModule());
            else if (issue instanceof Bug b) uw.set("module", req.getModule());
        }

        if (issue instanceof Bug bug) {
            if (req.getSeverity() != null) uw.set("severity", req.getSeverity());
            if (req.getFixVersion() != null) uw.set("fix_version", req.getFixVersion());
            if (req.getSource() != null) uw.set("source", req.getSource());
            if (req.getIteration() != null) uw.set("iteration", req.getIteration());
            if (req.getPlanStart() != null) uw.set("plan_start", req.getPlanStart());
            if (req.getNoFixReason() != null) uw.set("no_fix_reason", req.getNoFixReason());
        }

        if (req.getClearFields() != null) {
            for (String field : req.getClearFields()) {
                switch (field) {
                    case "assigneeId" -> uw.set("assignee_id", null);
                    case "verifierId" -> uw.set("verifier_id", null);
                    case "sprintId" -> uw.set("sprint_id", null);
                    case "dueDate" -> uw.set("due_date", null);
                    case "planStart" -> { if (issue instanceof Bug b) uw.set("plan_start", null); }
                    case "iteration" -> { if (issue instanceof Bug b) uw.set("iteration", null); }
                    case "noFixReason" -> { if (issue instanceof Bug b) uw.set("no_fix_reason", null); }
                    case "module" -> {
                        if (issue instanceof Requirement r) uw.set("module", null);
                        else if (issue instanceof Bug b) uw.set("module", null);
                    }
                    case "fixVersion" -> { if (issue instanceof Bug b) uw.set("fix_version", null); }
                    case "participants", "ccUsers" -> { }
                    default -> throw new ServiceException(400, "不支持清空字段: " + field);
                }
            }
        }

        String statusFromCode = null;
        String statusToCode = null;
        if (req.getStatusId() != null && !req.getStatusId().equals(issue.getStatusId())) {
            IssueStatus target = issueStatusMapper().selectById(req.getStatusId());
            if (target == null) throw new ServiceException(400, "状态不存在");
            if (!workflowService().canTransition(issue.getProjectId(), category(), issue.getStatusId(), req.getStatusId())) {
                String fromName = Optional.ofNullable(issueStatusMapper().selectById(issue.getStatusId()))
                        .map(IssueStatus::getName).orElse("未知状态");
                throw new ServiceException(400, "不允许从「" + fromName + "」流转到「" + target.getName() + "」");
            }
            statusFromCode = Optional.ofNullable(issueStatusMapper().selectById(issue.getStatusId()))
                    .map(IssueStatus::getCode).orElse(null);
            statusToCode = target.getCode();
            uw.set("status_id", req.getStatusId());
            uw.set("sort_key", computeSortKey(issue.getProjectId(), req.getStatusId(), null, null));
        }

        if (uw.getSqlSet() != null && !uw.getSqlSet().isEmpty()) {
            uw.set("updated_at", LocalDateTime.now());
            mapper().update(null, uw);
        }
        if (statusToCode != null) {
            recordActivity(id, "STATUS_CHANGED", jsonStatus(statusFromCode), jsonStatus(statusToCode));
        }

        boolean clearParticipants = req.getClearFields() != null && req.getClearFields().contains("participants");
        boolean clearCc = req.getClearFields() != null && req.getClearFields().contains("ccUsers");
        if (req.getParticipants() != null || clearParticipants)
            replaceParticipants(id, clearParticipants ? List.of() : req.getParticipants());
        if (req.getCcUsers() != null || clearCc)
            replaceCcUsers(id, clearCc ? List.of() : req.getCcUsers());

        if (req.getLabelIds() != null) {
            issueTagMapper().deleteIssueLabelByIssueIdsAndCategory(List.of(id), categoryName());
            for (UUID lid : req.getLabelIds()) issueTagMapper().insertIssueLabel(id, lid, categoryName());
        }
        T updated = mapper().selectById(id);
        return toVO(updated);
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
        try {
            return UUID.fromString(uid.trim());
        } catch (IllegalArgumentException e) {
            throw new ServiceException(400, "用户 ID 格式非法: " + uid);
        }
    }

    // ========================= 拖拽移动 =========================

    @Transactional
    public IssueVO move(UUID id, MoveIssueRequest req) {
        T issue = requireIssue(id);
        UUID fromStatus = issue.getStatusId();
        UUID toStatus = req.getStatusId();
        String statusFromCode = null;
        String statusToCode = null;
        if (!fromStatus.equals(toStatus)) {
            IssueStatus target = issueStatusMapper().selectById(toStatus);
            if (target == null) throw new ServiceException(400, "状态不存在");
            if (!workflowService().canTransition(issue.getProjectId(), category(), fromStatus, toStatus)) {
                String fromName = Optional.ofNullable(issueStatusMapper().selectById(fromStatus))
                        .map(IssueStatus::getName).orElse("未知状态");
                throw new ServiceException(400, "不允许从「" + fromName + "」流转到「" + target.getName() + "」");
            }
            statusFromCode = Optional.ofNullable(issueStatusMapper().selectById(fromStatus))
                    .map(IssueStatus::getCode).orElse(null);
            statusToCode = target.getCode();
        }
        issue.setStatusId(toStatus);
        String sortKey = computeSortKey(issue.getProjectId(), toStatus, req.getBeforeId(), req.getAfterId());
        issue.setSortKey(sortKey);
        issue.setUpdatedAt(LocalDateTime.now());
        mapper().updateById(issue);
        if (statusToCode != null) {
            recordActivity(id, "STATUS_CHANGED", jsonStatus(statusFromCode), jsonStatus(statusToCode));
        }
        Project project = projectMapper().selectById(issue.getProjectId());
        return toVO(issue, project == null ? "" : project.getProjectKey());
    }

    // ========================= 删除（树形递归 + 双向关联 + 附件） =========================

    @Transactional
    public void delete(UUID id) {
        List<UUID> ids = new ArrayList<>();
        collectDescendantIds(id, ids);
        String category = categoryName();

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
        // 失效 issue -> project 解析缓存（静态调用，不引入 Bean 依赖），
        // 否则权限拦截器会拿到已删除工作项的陈旧归属
        ids.forEach(IssueProjectCache::invalidate);
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

    private String computeSortKey(UUID projectId, UUID statusId, UUID beforeId, UUID afterId) {
        if (beforeId != null && afterId != null) {
            T b = mapper().selectById(beforeId);
            T a = mapper().selectById(afterId);
            if (b == null || a == null) throw new ServiceException(400, "相邻卡片不存在");
            ensureSameStatusColumn(statusId, b, a);
            return SortKeyUtils.between(b.getSortKey(), a.getSortKey());
        }
        if (beforeId != null) {
            T b = mapper().selectById(beforeId);
            if (b == null) throw new ServiceException(400, "相邻卡片不存在");
            ensureSameStatusColumn(statusId, b);
            return SortKeyUtils.between(b.getSortKey(), null);
        }
        if (afterId != null) {
            T a = mapper().selectById(afterId);
            if (a == null) throw new ServiceException(400, "相邻卡片不存在");
            ensureSameStatusColumn(statusId, a);
            return SortKeyUtils.between(null, a.getSortKey());
        }
        // 未指定相邻卡片：追加到目标列末尾（不能返回常量，否则与列内其它卡片并列）
        return SortKeyUtils.after(tailSortKey(projectId, statusId));
    }

    private void ensureSameStatusColumn(UUID statusId, T... neighbors) {
        for (T n : neighbors) {
            if (!Objects.equals(statusId, n.getStatusId())) {
                throw new ServiceException(400, "相邻卡片不属于目标状态列");
            }
        }
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

    public List<IssueVO> toVOList(List<T> issues, String projectKey) {
        if (issues == null || issues.isEmpty()) return List.of();
        List<UUID> ids = issues.stream().map(AbstractWorkItem::getId).toList();

        // 批量装配：类型/状态/用户/标签/评论数/参与者/抄送 各一次查询，替代逐条 toVO 的 9×N 次查询
        Set<UUID> typeIds = issues.stream().map(AbstractWorkItem::getIssueTypeId)
                .filter(Objects::nonNull).collect(Collectors.toSet());
        Map<UUID, IssueType> typeMap = typeIds.isEmpty() ? Map.of()
                : issueTypeMapper().selectBatchIds(typeIds).stream()
                        .collect(Collectors.toMap(IssueType::getId, t -> t));
        Set<UUID> statusIds = issues.stream().map(AbstractWorkItem::getStatusId)
                .filter(Objects::nonNull).collect(Collectors.toSet());
        Map<UUID, IssueStatus> statusMap = statusIds.isEmpty() ? Map.of()
                : issueStatusMapper().selectBatchIds(statusIds).stream()
                        .collect(Collectors.toMap(IssueStatus::getId, s -> s));

        Set<UUID> userIds = new HashSet<>();
        for (AbstractWorkItem i : issues) {
            if (i.getAssigneeId() != null) userIds.add(i.getAssigneeId());
            if (i.getReporterId() != null) userIds.add(i.getReporterId());
            if (i.getVerifierId() != null) userIds.add(i.getVerifierId());
        }
        Map<UUID, SysUser> userMap = userIds.isEmpty() ? Map.of()
                : sysUserMapper().selectBatchIds(userIds).stream()
                        .collect(Collectors.toMap(SysUser::getId, u -> u));

        Map<UUID, List<IssueTag>> tagMap = issueTagMapper().selectByIssueIds(ids, categoryName())
                .stream().collect(Collectors.groupingBy(IssueTag::getIssueId));
        Map<UUID, Long> commentCountMap = new HashMap<>();
        for (Map<String, Object> row : issueCommentMapper().countByIssueIds(ids, categoryName())) {
            Object rawId = row.get("issue_id");
            UUID issueId = rawId instanceof UUID u ? u : UUID.fromString(String.valueOf(rawId));
            Object cnt = row.get("cnt");
            commentCountMap.put(issueId, cnt instanceof Number n ? n.longValue() : 0L);
        }
        Map<UUID, List<UUID>> participantMap = issueParticipantMapper().selectList(
                        Wrappers.<IssueParticipant>lambdaQuery()
                                .in(IssueParticipant::getIssueId, ids)
                                .eq(IssueParticipant::getCategory, categoryName()))
                .stream().collect(Collectors.groupingBy(IssueParticipant::getIssueId,
                        Collectors.mapping(IssueParticipant::getUserId, Collectors.toList())));
        Map<UUID, List<UUID>> ccMap = issueCcMapper().selectList(
                        Wrappers.<IssueCc>lambdaQuery()
                                .in(IssueCc::getIssueId, ids)
                                .eq(IssueCc::getCategory, categoryName()))
                .stream().collect(Collectors.groupingBy(IssueCc::getIssueId,
                        Collectors.mapping(IssueCc::getUserId, Collectors.toList())));

        return issues.stream()
                .map(i -> assembleVO(i, projectKey, typeMap, statusMap, userMap, tagMap, commentCountMap, participantMap, ccMap))
                .collect(Collectors.toList());
    }

    private IssueVO assembleVO(T issue, String projectKey,
                               Map<UUID, IssueType> typeMap,
                               Map<UUID, IssueStatus> statusMap,
                               Map<UUID, SysUser> userMap,
                               Map<UUID, List<IssueTag>> tagMap,
                               Map<UUID, Long> commentCountMap,
                               Map<UUID, List<UUID>> participantMap,
                               Map<UUID, List<UUID>> ccMap) {
        IssueVO vo = new IssueVO();
        BeanUtils.copyProperties(issue, vo);
        vo.setIssueKey(projectKey + "-" + issue.getIssueNumber());

        IssueType type = issue.getIssueTypeId() == null ? null : typeMap.get(issue.getIssueTypeId());
        if (type != null) {
            vo.setIssueTypeCode(type.getCode());
            vo.setIssueTypeIcon(type.getIcon());
            vo.setIssueTypeName(type.getName());
        }
        IssueStatus st = issue.getStatusId() == null ? null : statusMap.get(issue.getStatusId());
        if (st != null) vo.setStatusCode(st.getCode());

        if (issue.getAssigneeId() != null) {
            SysUser a = userMap.get(issue.getAssigneeId());
            vo.setAssigneeName(a == null ? null : a.getDisplayName());
            vo.setAssigneeAvatarUrl(a == null ? null : a.getAvatarUrl());
        }
        if (issue.getReporterId() != null) {
            SysUser r = userMap.get(issue.getReporterId());
            vo.setReporterName(r == null ? null : r.getDisplayName());
            vo.setReporterAvatarUrl(r == null ? null : r.getAvatarUrl());
        }
        if (issue.getVerifierId() != null) {
            SysUser v = userMap.get(issue.getVerifierId());
            vo.setVerifierName(v == null ? null : v.getDisplayName());
            vo.setVerifierAvatarUrl(v == null ? null : v.getAvatarUrl());
        }
        vo.setLabels(tagMap.getOrDefault(issue.getId(), List.of()).stream()
                .map(this::toLabelVO).collect(Collectors.toList()));
        vo.setCommentCount(commentCountMap.getOrDefault(issue.getId(), 0L));
        vo.setParticipants(participantMap.getOrDefault(issue.getId(), List.of()).stream()
                .map(UUID::toString).collect(Collectors.toList()));
        vo.setCcUsers(ccMap.getOrDefault(issue.getId(), List.of()).stream()
                .map(UUID::toString).collect(Collectors.toList()));
        return vo;
    }

    public IssueVO toVO(T issue) {
        return toVO(issue, resolveProjectKey(issue.getProjectId()));
    }

    /**
     * 单条装配统一委托给 {@link #toVOList} 的批量预取实现。
     * 此前这里是另一套逐字段查询的装配逻辑（约 9 次查询），
     * 与 assembleVO 双轨并存，新增字段时极易漏改其一导致两者展示不一致。
     */
    public IssueVO toVO(T issue, String projectKey) {
        List<IssueVO> list = toVOList(List.of(issue), projectKey);
        return list.isEmpty() ? null : list.get(0);
    }

    private LabelVO toLabelVO(IssueTag tag) {
        LabelVO l = new LabelVO();
        l.setId(tag.getId());
        l.setName(tag.getName());
        l.setColor(tag.getColor());
        return l;
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

    /** 动态中的状态值统一用 {"status": "CODE"} 形式，与存量数据及前端展示保持一致 */
    private String jsonStatus(String code) {
        if (code == null) {
            return null;
        }
        return "{\"status\": \"" + code.replace("\\", "\\\\").replace("\"", "\\\"") + "\"}";
    }

    private String resolveProjectKey(UUID projectId) {
        Project p = projectMapper().selectById(projectId);
        return p == null ? "" : p.getProjectKey();
    }

    private void applySort(QueryWrapper<T> q, String sortField, String sortOrder) {
        if (sortField == null || sortField.isBlank()) {
            q.orderByAsc("status_id").orderByAsc("sort_key");
            return;
        }
        boolean asc = !"desc".equalsIgnoreCase(sortOrder);
        switch (sortField) {
            case "issueKey" -> {
                // issue_number 为 varchar：先比长度再比字典序，等价于数字自然序（避免 "1000" 排在 "999" 之前）
                if (asc) q.orderByAsc("LENGTH(issue_number)").orderByAsc("issue_number");
                else q.orderByDesc("LENGTH(issue_number)").orderByDesc("issue_number");
            }
            case "updatedAt" -> { if (asc) q.orderByAsc("updated_at"); else q.orderByDesc("updated_at"); }
            case "createdAt" -> { if (asc) q.orderByAsc("created_at"); else q.orderByDesc("created_at"); }
            case "priority" -> { if (asc) q.orderByAsc("priority"); else q.orderByDesc("priority"); }
            case "severity" -> {
                if (category() == ItemCategory.BUG) {
                    if (asc) q.orderByAsc("severity"); else q.orderByDesc("severity");
                } else {
                    q.orderByAsc("status_id").orderByAsc("sort_key");
                }
            }
            case "statusId" -> { if (asc) q.orderByAsc("status_id"); else q.orderByDesc("status_id"); }
            case "sortKey" -> { if (asc) q.orderByAsc("sort_key"); else q.orderByDesc("sort_key"); }
            default -> q.orderByAsc("status_id").orderByAsc("sort_key");
        }
    }

    private T requireIssue(UUID id) {
        T issue = mapper().selectById(id);
        if (issue == null) throw new ServiceException(404, category() + " 不存在");
        return issue;
    }
}

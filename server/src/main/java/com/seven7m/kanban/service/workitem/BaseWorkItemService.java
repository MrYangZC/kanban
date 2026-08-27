package com.seven7m.kanban.service.workitem;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.seven7m.kanban.common.domain.PageResult;
import com.seven7m.kanban.common.enums.ItemCategory;
import com.seven7m.kanban.common.exception.ServiceException;
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
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
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

    private String cat() { return category().name(); }

    // ========================= 列表/详情 =========================

    @Transactional(readOnly = true)
    public PageResult<IssueVO> listByFilter(IssueFilterRequest req) {
        UUID me = UserContext.getUserId();
        LambdaQueryWrapper<T> q = Wrappers.lambdaQuery();
        q.eq(AbstractWorkItem::getProjectId, req.getProjectId());

        if (req.getStatusId() != null) q.eq(AbstractWorkItem::getStatusId, req.getStatusId());
        if (req.getPriority() != null) q.eq(AbstractWorkItem::getPriority, req.getPriority());
        if (req.getSeverity() != null && this instanceof com.seven7m.kanban.service.workitem.BugService)
            q.eq(((com.seven7m.kanban.entity.workitem.Bug) newEntityInstance())::getSeverity, req.getSeverity());
        if (req.getKeyword() != null && !req.getKeyword().isBlank())
            q.like(AbstractWorkItem::getTitle, req.getKeyword());

        if ("ASSIGNED".equals(req.getScope())) q.eq(AbstractWorkItem::getAssigneeId, me);
        else if ("REPORTED".equals(req.getScope())) q.eq(AbstractWorkItem::getReporterId, me);
        else if ("VERIFIED".equals(req.getScope())) q.eq(AbstractWorkItem::getVerifierId, me);
        else if ("SHARED".equals(req.getScope()))
            q.apply("id IN (SELECT issue_id FROM issue_participant WHERE user_id = {0} AND category = {1})", me, cat());
        else if ("ARCHIVED".equals(req.getScope())) q.eq(AbstractWorkItem::getArchived, true);
        else if ("ACTIVE".equals(req.getScope())) q.eq(AbstractWorkItem::getArchived, false);

        if (req.getScope() == null) q.eq(AbstractWorkItem::getArchived, false);

        if (req.getLabelId() != null) {
            q.apply("id IN (SELECT issue_id FROM issue_label WHERE label_id = {0} AND category = {1})", req.getLabelId(), cat());
        }

        // 工作项类型编码过滤（需求/任务/缺陷分类，避免列表互相混入）
        if (req.getIssueTypeCodes() != null && !req.getIssueTypeCodes().isEmpty()) {
            List<IssueType> types = issueTypeMapper().selectList(Wrappers.<IssueType>lambdaQuery()
                    .in(IssueType::getCode, req.getIssueTypeCodes()));
            List<UUID> typeIds = types.stream().map(IssueType::getId).toList();
            if (typeIds.isEmpty()) q.apply("1 = 0");
            else q.in(AbstractWorkItem::getIssueTypeId, typeIds);
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

    /** 轻量查询：仅返回主表 id/projectId/issueNumber/title，不加载子资源，供关联展示/权限解析等高频场景使用 */
    @Transactional(readOnly = true)
    public IssueVO getBriefById(UUID id) {
        T issue = mapper().selectById(id);
        if (issue == null) return null;
        IssueVO vo = new IssueVO();
        vo.setId(issue.getId());
        vo.setProjectId(issue.getProjectId());
        vo.setIssueNumber(issue.getIssueNumber());
        vo.setTitle(issue.getTitle());
        return vo;
    }

    public UUID getProjectId(UUID issueId) {
        T i = mapper().selectOne(Wrappers.<T>lambdaQuery().eq(AbstractWorkItem::getId, issueId).last("LIMIT 1"));
        return i == null ? null : i.getProjectId();
    }

    public List<IssueVO> listChildren(UUID issueId) {
        T parent = mapper().selectById(issueId);
        if (parent == null) return List.of();
        String key = resolveProjectKey(parent.getProjectId());
        List<T> children = mapper().selectList(Wrappers.<T>lambdaQuery()
                .eq(AbstractWorkItem::getParentId, issueId).orderByAsc(AbstractWorkItem::getSortKey));
        return children.stream().map(c -> toVO(c, key)).collect(Collectors.toList());
    }

    // ========================= 创建 =========================

    @Transactional
    public IssueVO create(CreateIssueRequest req) {
        Project project = projectMapper().selectById(req.getProjectId());
        if (project == null) throw new ServiceException(404, "项目不存在");
        IssueStatus status = issueStatusMapper().selectById(req.getStatusId());
        if (status == null) throw new ServiceException(400, "状态不存在");

        UUID issueTypeId = req.getIssueTypeId();
        if (issueTypeId == null && req.getIssueTypeCode() != null && !req.getIssueTypeCode().isBlank()) {
            IssueType byCode = issueTypeMapper().selectOne(Wrappers.<IssueType>lambdaQuery()
                    .eq(IssueType::getCode, req.getIssueTypeCode().trim()).last("LIMIT 1"));
            if (byCode != null) issueTypeId = byCode.getId();
        }
        if (issueTypeId == null) {
            IssueType first = issueTypeMapper().selectOne(Wrappers.<IssueType>lambdaQuery()
                    .orderByAsc(IssueType::getSortOrder).last("LIMIT 1"));
            if (first == null) throw new ServiceException(400, "未初始化 IssueType");
            issueTypeId = first.getId();
        }

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
        issue.setSortKey(SortKeyUtils.last());
        issue.setCreatedBy(UserContext.getUserId());
        issue.setCreatedAt(LocalDateTime.now());
        issue.setUpdatedAt(LocalDateTime.now());
        mapper().insert(issue);
        return toVO(issue, project.getProjectKey());
    }

    // ========================= 更新 =========================

    @Transactional
    public IssueVO update(UUID id, UpdateIssueRequest req) {
        T issue = requireIssue(id);
        LambdaUpdateWrapper<T> uw = Wrappers.lambdaUpdate();
        uw.eq(AbstractWorkItem::getId, id);

        if (req.getTitle() != null) uw.set(AbstractWorkItem::getTitle, req.getTitle());
        if (req.getDescription() != null) uw.set(AbstractWorkItem::getDescription, req.getDescription());
        if (req.getPriority() != null) uw.set(AbstractWorkItem::getPriority, req.getPriority());
        if (req.getIssueTypeId() != null) uw.set(AbstractWorkItem::getIssueTypeId, req.getIssueTypeId());
        if (req.getAssigneeId() != null) uw.set(AbstractWorkItem::getAssigneeId, req.getAssigneeId());
        if (req.getVerifierId() != null) uw.set(AbstractWorkItem::getVerifierId, req.getVerifierId());
        if (req.getSprintId() != null) uw.set(AbstractWorkItem::getSprintId, req.getSprintId());
        if (req.getDueDate() != null) uw.set(AbstractWorkItem::getDueDate, req.getDueDate());
        if (req.getModule() != null) {
            if (issue instanceof com.seven7m.kanban.entity.workitem.Requirement r) uw.set(r::getModule, req.getModule());
            else if (issue instanceof com.seven7m.kanban.entity.workitem.Bug b) uw.set(b::getModule, req.getModule());
        }

        if (issue instanceof com.seven7m.kanban.entity.workitem.Bug bug) {
            if (req.getSeverity() != null) uw.set(bug::getSeverity, req.getSeverity());
            if (req.getFixVersion() != null) uw.set(bug::getFixVersion, req.getFixVersion());
            if (req.getSource() != null) uw.set(bug::getSource, req.getSource());
            if (req.getIteration() != null) uw.set(bug::getIteration, req.getIteration());
            if (req.getPlanStart() != null) uw.set(bug::getPlanStart, req.getPlanStart());
            if (req.getNoFixReason() != null) uw.set(bug::getNoFixReason, req.getNoFixReason());
        }

        if (req.getClearFields() != null) {
            for (String field : req.getClearFields()) {
                switch (field) {
                    case "assigneeId" -> uw.set(AbstractWorkItem::getAssigneeId, null);
                    case "verifierId" -> uw.set(AbstractWorkItem::getVerifierId, null);
                    case "sprintId" -> uw.set(AbstractWorkItem::getSprintId, null);
                    case "dueDate" -> uw.set(AbstractWorkItem::getDueDate, null);
                    case "planStart" -> { if (issue instanceof com.seven7m.kanban.entity.workitem.Bug b) uw.set(b::getPlanStart, null); }
                    case "iteration" -> { if (issue instanceof com.seven7m.kanban.entity.workitem.Bug b) uw.set(b::getIteration, null); }
                    case "noFixReason" -> { if (issue instanceof com.seven7m.kanban.entity.workitem.Bug b) uw.set(b::getNoFixReason, null); }
                    case "module" -> {
                        if (issue instanceof com.seven7m.kanban.entity.workitem.Requirement r) uw.set(r::getModule, null);
                        else if (issue instanceof com.seven7m.kanban.entity.workitem.Bug b) uw.set(b::getModule, null);
                    }
                    case "fixVersion" -> { if (issue instanceof com.seven7m.kanban.entity.workitem.Bug b) uw.set(b::getFixVersion, null); }
                    case "participants", "ccUsers" -> { }
                    default -> throw new ServiceException(400, "不支持清空字段: " + field);
                }
            }
        }

        if (req.getStatusId() != null && !req.getStatusId().equals(issue.getStatusId())) {
            IssueStatus target = issueStatusMapper().selectById(req.getStatusId());
            if (target == null) throw new ServiceException(400, "状态不存在");
            if (!workflowService().canTransition(issue.getProjectId(), issue.getStatusId(), req.getStatusId())) {
                String fromName = Optional.ofNullable(issueStatusMapper().selectById(issue.getStatusId()))
                        .map(IssueStatus::getName).orElse("未知状态");
                throw new ServiceException(400, "不允许从「" + fromName + "」流转到「" + target.getName() + "」");
            }
            uw.set(AbstractWorkItem::getStatusId, req.getStatusId());
            uw.set(AbstractWorkItem::getSortKey, computeSortKey(req.getStatusId(), null, null));
        }

        if (uw.getSqlSet() != null && !uw.getSqlSet().isEmpty()) {
            uw.set(AbstractWorkItem::getUpdatedAt, LocalDateTime.now());
            mapper().update(null, uw);
        }

        boolean clearParticipants = req.getClearFields() != null && req.getClearFields().contains("participants");
        boolean clearCc = req.getClearFields() != null && req.getClearFields().contains("ccUsers");
        if (req.getParticipants() != null || clearParticipants)
            replaceParticipants(id, clearParticipants ? List.of() : req.getParticipants());
        if (req.getCcUsers() != null || clearCc)
            replaceCcUsers(id, clearCc ? List.of() : req.getCcUsers());

        if (req.getLabelIds() != null) {
            issueTagMapper().deleteIssueLabelByIssueIdsAndCategory(List.of(id), cat());
            for (UUID lid : req.getLabelIds()) issueTagMapper().insertIssueLabel(id, lid, cat());
        }
        T updated = mapper().selectById(id);
        return toVO(updated);
    }

    private void replaceParticipants(UUID issueId, List<String> userIds) {
        issueParticipantMapper().delete(Wrappers.<IssueParticipant>lambdaQuery()
                .eq(IssueParticipant::getIssueId, issueId).eq(IssueParticipant::getCategory, cat()));
        for (String uid : userIds) {
            if (uid == null || uid.isBlank()) continue;
            IssueParticipant p = new IssueParticipant();
            p.setIssueId(issueId); p.setCategory(cat());
            p.setUserId(UUID.fromString(uid)); p.setCreatedAt(LocalDateTime.now());
            issueParticipantMapper().insert(p);
        }
    }

    private void replaceCcUsers(UUID issueId, List<String> userIds) {
        issueCcMapper().delete(Wrappers.<IssueCc>lambdaQuery()
                .eq(IssueCc::getIssueId, issueId).eq(IssueCc::getCategory, cat()));
        for (String uid : userIds) {
            if (uid == null || uid.isBlank()) continue;
            IssueCc c = new IssueCc();
            c.setIssueId(issueId); c.setCategory(cat());
            c.setUserId(UUID.fromString(uid)); c.setCreatedAt(LocalDateTime.now());
            issueCcMapper().insert(c);
        }
    }

    // ========================= 拖拽移动 =========================

    @Transactional
    public IssueVO move(UUID id, MoveIssueRequest req) {
        T issue = requireIssue(id);
        UUID fromStatus = issue.getStatusId();
        UUID toStatus = req.getStatusId();
        if (!fromStatus.equals(toStatus)) {
            IssueStatus target = issueStatusMapper().selectById(toStatus);
            if (target == null) throw new ServiceException(400, "状态不存在");
            if (!workflowService().canTransition(issue.getProjectId(), fromStatus, toStatus)) {
                String fromName = Optional.ofNullable(issueStatusMapper().selectById(fromStatus))
                        .map(IssueStatus::getName).orElse("未知状态");
                throw new ServiceException(400, "不允许从「" + fromName + "」流转到「" + target.getName() + "」");
            }
        }
        issue.setStatusId(toStatus);
        String sortKey = computeSortKey(toStatus, req.getBeforeId(), req.getAfterId());
        issue.setSortKey(sortKey);
        issue.setUpdatedAt(LocalDateTime.now());
        mapper().updateById(issue);
        Project project = projectMapper().selectById(issue.getProjectId());
        return toVO(issue, project == null ? "" : project.getProjectKey());
    }

    // ========================= 删除（树形递归 + 双向关联 + 附件） =========================

    @Transactional
    public void delete(UUID id) {
        List<UUID> ids = new ArrayList<>();
        collectDescendantIds(id, ids);
        String category = cat();

        mapper().delete(Wrappers.<T>lambdaQuery().in(AbstractWorkItem::getId, ids));
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
    }

    @Transactional
    public int deleteByIds(List<UUID> ids) {
        if (ids == null || ids.isEmpty()) return 0;
        for (UUID id : ids) delete(id);
        return ids.size();
    }

    private void collectDescendantIds(UUID parentId, List<UUID> out) {
        out.add(parentId);
        List<T> children = mapper().selectList(Wrappers.<T>lambdaQuery().eq(AbstractWorkItem::getParentId, parentId));
        for (T child : children) collectDescendantIds(child.getId(), out);
    }

    private String computeSortKey(UUID statusId, UUID beforeId, UUID afterId) {
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
        return SortKeyUtils.last();
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
        List<T> issues = mapper().selectList(Wrappers.<T>lambdaQuery()
                .eq(AbstractWorkItem::getProjectId, projectId).eq(AbstractWorkItem::getArchived, false)
                .orderByAsc(AbstractWorkItem::getSortKey));
        return toVOList(issues, resolveProjectKey(projectId));
    }

    // ========================= VO 组装（与前端 IssueVO 完全一致） =========================

    public List<IssueVO> toVOList(List<T> issues, String projectKey) {
        if (issues == null || issues.isEmpty()) return List.of();
        return issues.stream().map(i -> toVO(i, projectKey)).collect(Collectors.toList());
    }

    public IssueVO toVO(T issue) {
        return toVO(issue, resolveProjectKey(issue.getProjectId()));
    }

    public IssueVO toVO(T issue, String projectKey) {
        IssueVO vo = new IssueVO();
        BeanUtils.copyProperties(issue, vo);
        vo.setIssueKey(projectKey + "-" + issue.getIssueNumber());

        IssueType type = issueTypeMapper().selectById(issue.getIssueTypeId());
        if (type != null) {
            vo.setIssueTypeCode(type.getCode());
            vo.setIssueTypeIcon(type.getIcon());
            vo.setIssueTypeName(type.getName());
        }
        IssueStatus st = issueStatusMapper().selectById(issue.getStatusId());
        if (st != null) vo.setStatusCode(st.getCode());

        if (issue.getAssigneeId() != null) {
            SysUser a = sysUserMapper().selectById(issue.getAssigneeId());
            vo.setAssigneeName(a == null ? null : a.getDisplayName());
        }
        if (issue.getReporterId() != null) {
            SysUser r = sysUserMapper().selectById(issue.getReporterId());
            vo.setReporterName(r == null ? null : r.getDisplayName());
        }
        if (issue.getVerifierId() != null) {
            SysUser v = sysUserMapper().selectById(issue.getVerifierId());
            vo.setVerifierName(v == null ? null : v.getDisplayName());
        }
        vo.setLabels(issueTagMapper().selectByIssue(issue.getId(), cat()).stream()
                .map(this::toLabelVO).collect(Collectors.toList()));
        vo.setCommentCount(issueCommentMapper().countByIssue(issue.getId(), cat()));
        vo.setParticipants(issueParticipantMapper().selectList(
                        Wrappers.<IssueParticipant>lambdaQuery().eq(IssueParticipant::getIssueId, issue.getId())
                                .eq(IssueParticipant::getCategory, cat()))
                .stream().map(p -> p.getUserId().toString()).collect(Collectors.toList()));
        vo.setCcUsers(issueCcMapper().selectList(
                        Wrappers.<IssueCc>lambdaQuery().eq(IssueCc::getIssueId, issue.getId())
                                .eq(IssueCc::getCategory, cat()))
                .stream().map(c -> c.getUserId().toString()).collect(Collectors.toList()));
        return vo;
    }

    private LabelVO toLabelVO(IssueTag tag) {
        LabelVO l = new LabelVO();
        l.setId(tag.getId());
        l.setName(tag.getName());
        l.setColor(tag.getColor());
        return l;
    }

    // ========================= 内部工具 =========================

    private String resolveProjectKey(UUID projectId) {
        Project p = projectMapper().selectById(projectId);
        return p == null ? "" : p.getProjectKey();
    }

    private void applySort(LambdaQueryWrapper<T> q, String sortField, String sortOrder) {
        if (sortField == null || sortField.isBlank()) {
            q.orderByAsc(AbstractWorkItem::getStatusId).orderByAsc(AbstractWorkItem::getSortKey);
            return;
        }
        boolean asc = !"desc".equalsIgnoreCase(sortOrder);
        switch (sortField) {
            case "updatedAt" -> { if (asc) q.orderByAsc(AbstractWorkItem::getUpdatedAt); else q.orderByDesc(AbstractWorkItem::getUpdatedAt); }
            case "createdAt" -> { if (asc) q.orderByAsc(AbstractWorkItem::getCreatedAt); else q.orderByDesc(AbstractWorkItem::getCreatedAt); }
            case "priority" -> { if (asc) q.orderByAsc(AbstractWorkItem::getPriority); else q.orderByDesc(AbstractWorkItem::getPriority); }
            case "severity" -> {
                if (category() == ItemCategory.BUG) {
                    if (asc) q.orderByAsc("severity"); else q.orderByDesc("severity");
                } else {
                    q.orderByAsc(AbstractWorkItem::getStatusId).orderByAsc(AbstractWorkItem::getSortKey);
                }
            }
            case "statusId" -> { if (asc) q.orderByAsc(AbstractWorkItem::getStatusId); else q.orderByDesc(AbstractWorkItem::getStatusId); }
            case "sortKey" -> { if (asc) q.orderByAsc(AbstractWorkItem::getSortKey); else q.orderByDesc(AbstractWorkItem::getSortKey); }
            default -> q.orderByAsc(AbstractWorkItem::getStatusId).orderByAsc(AbstractWorkItem::getSortKey);
        }
    }

    private T requireIssue(UUID id) {
        T issue = mapper().selectById(id);
        if (issue == null) throw new ServiceException(404, category() + " 不存在");
        return issue;
    }
}

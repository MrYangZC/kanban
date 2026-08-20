package com.seven7m.kanban.service.issue;

import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.seven7m.kanban.common.domain.PageResult;
import com.seven7m.kanban.common.exception.ServiceException;
import com.seven7m.kanban.common.security.UserContext;
import com.seven7m.kanban.common.util.SortKeyUtils;
import com.seven7m.kanban.entity.issue.attachment.IssueAttachment;
import com.seven7m.kanban.mapper.issue.attachment.IssueAttachmentMapper;
import com.seven7m.kanban.entity.issue.comment.IssueComment;
import com.seven7m.kanban.mapper.issue.comment.IssueCommentMapper;
import com.seven7m.kanban.dto.issue.CreateIssueRequest;
import com.seven7m.kanban.dto.issue.IssueFilterRequest;
import com.seven7m.kanban.dto.issue.IssueVO;
import com.seven7m.kanban.dto.issue.LabelVO;
import com.seven7m.kanban.dto.issue.MoveIssueRequest;
import com.seven7m.kanban.dto.issue.UpdateIssueRequest;
import com.seven7m.kanban.entity.issue.Issue;
import com.seven7m.kanban.entity.issue.IssueActivity;
import com.seven7m.kanban.entity.issue.IssueType;
import com.seven7m.kanban.entity.issue.link.IssueLink;
import com.seven7m.kanban.mapper.issue.link.IssueLinkMapper;
import com.seven7m.kanban.mapper.issue.IssueActivityMapper;
import com.seven7m.kanban.mapper.issue.IssueMapper;
import com.seven7m.kanban.mapper.issue.IssueTypeMapper;
import com.seven7m.kanban.entity.issue.tag.IssueTag;
import com.seven7m.kanban.mapper.issue.tag.IssueTagMapper;
import com.seven7m.kanban.entity.issuestatus.IssueStatus;
import com.seven7m.kanban.mapper.issuestatus.IssueStatusMapper;
import com.seven7m.kanban.entity.project.Project;
import com.seven7m.kanban.mapper.project.ProjectMapper;
import com.seven7m.kanban.entity.user.SysUser;
import com.seven7m.kanban.mapper.user.SysUserMapper;
import com.seven7m.kanban.service.workflow.WorkflowService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class IssueService {

    private final IssueMapper issueMapper;
    private final IssueStatusMapper issueStatusMapper;
    private final IssueTypeMapper issueTypeMapper;
    private final IssueActivityMapper issueActivityMapper;
    private final IssueTagMapper issueTagMapper;
    private final IssueCommentMapper issueCommentMapper;
    private final IssueAttachmentMapper issueAttachmentMapper;
    private final IssueLinkMapper issueLinkMapper;
    private final SysUserMapper sysUserMapper;
    private final ProjectMapper projectMapper;
    private final WorkflowService workflowService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    /** 通过 issueId 解析所属 projectId，供鉴权拦截器使用 */
    public UUID getProjectId(UUID issueId) {
        Issue i = issueMapper.selectOne(Wrappers.<Issue>lambdaQuery()
                .eq(Issue::getId, issueId).last("LIMIT 1"));
        return i == null ? null : i.getProjectId();
    }

    /** 查询某任务的子任务（parentId = issueId） */
    public List<IssueVO> listChildren(UUID issueId) {
        Issue parent = issueMapper.selectById(issueId);
        if (parent == null) return List.of();
        String key = parent.getProjectId() == null ? "" :
                Optional.ofNullable(projectMapper.selectById(parent.getProjectId())).map(Project::getProjectKey).orElse("");
        List<Issue> children = issueMapper.selectList(Wrappers.<Issue>lambdaQuery()
                .eq(Issue::getParentId, issueId)
                .eq(Issue::getDeleted, 0)
                .orderByAsc(Issue::getSortKey));
        return children.stream().map(i -> toVO(i, key)).collect(Collectors.toList());
    }

    @Transactional
    public IssueVO create(CreateIssueRequest req) {
        Project project = projectMapper.selectById(req.getProjectId());
        if (project == null || project.getDeleted() != null && project.getDeleted() == 1) {
            throw new ServiceException(404, "项目不存在");
        }
        IssueStatus status = issueStatusMapper.selectById(req.getStatusId());
        if (status == null || !status.getProjectId().equals(req.getProjectId())) {
            throw new ServiceException(400, "状态不属于该项目");
        }

        // 未传类型时取项目第一个默认类型（前端新建 Issue 的容错）
        UUID issueTypeId = req.getIssueTypeId();
        if (issueTypeId == null) {
            IssueType first = issueTypeMapper.selectOne(Wrappers.<IssueType>lambdaQuery()
                    .eq(IssueType::getProjectId, req.getProjectId())
                    .orderByAsc(IssueType::getSortOrder).last("LIMIT 1"));
            if (first == null) {
                throw new ServiceException(400, "项目未初始化 IssueType");
            }
            issueTypeId = first.getId();
        }

        Issue issue = new Issue();
        BeanUtils.copyProperties(req, issue);
        issue.setIssueTypeId(issueTypeId);
        issue.setId(UUID.randomUUID());
        issue.setIssueNumber(issueMapper.nextIssueNumber());
        issue.setReporterId(UserContext.getUserId());
        issue.setPriority(req.getPriority() == null ? "P2" : req.getPriority());
        issue.setSeverity(req.getSeverity() == null ? "S3" : req.getSeverity());
        issue.setArchived(req.getArchived() == null ? 0 : req.getArchived());
        issue.setSortKey(SortKeyUtils.last());
        issue.setCreatedBy(UserContext.getUserId());
        issue.setCreatedAt(LocalDateTime.now());
        issue.setUpdatedAt(LocalDateTime.now());
        issueMapper.insert(issue);

        return toVO(issue, project.getProjectKey());
    }

    public IssueVO get(UUID id) {
        Issue issue = issueMapper.selectById(id);
        if (issue == null || issue.getDeleted() != null && issue.getDeleted() == 1) {
            throw new ServiceException(404, "Issue 不存在");
        }
        Project project = projectMapper.selectById(issue.getProjectId());
        return toVO(issue, project == null ? "" : project.getProjectKey());
    }

    /**
     * 更新缺陷：只 set 非 null 字段（保持旧语义），clearFields 中列出的字段显式置空，
     * 解决"负责人未分配/清空日期保存无效"问题。状态变更与看板拖拽统一走工作流校验。
     */
    @Transactional
    public IssueVO update(UUID id, UpdateIssueRequest req) {
        Issue issue = requireIssue(id);
        LambdaUpdateWrapper<Issue> uw = Wrappers.<Issue>lambdaUpdate().eq(Issue::getId, id);

        if (req.getTitle() != null) uw.set(Issue::getTitle, req.getTitle());
        if (req.getDescription() != null) uw.set(Issue::getDescription, req.getDescription());
        if (req.getPriority() != null) uw.set(Issue::getPriority, req.getPriority());
        if (req.getIssueTypeId() != null) uw.set(Issue::getIssueTypeId, req.getIssueTypeId());
        if (req.getAssigneeId() != null) uw.set(Issue::getAssigneeId, req.getAssigneeId());
        if (req.getVerifierId() != null) uw.set(Issue::getVerifierId, req.getVerifierId());
        if (req.getSprintId() != null) uw.set(Issue::getSprintId, req.getSprintId());
        if (req.getDueDate() != null) uw.set(Issue::getDueDate, req.getDueDate());
        if (req.getSeverity() != null) uw.set(Issue::getSeverity, req.getSeverity());
        if (req.getModule() != null) uw.set(Issue::getModule, req.getModule());
        if (req.getFixVersion() != null) uw.set(Issue::getFixVersion, req.getFixVersion());
        if (req.getSource() != null) uw.set(Issue::getSource, req.getSource());
        if (req.getArchived() != null) uw.set(Issue::getArchived, req.getArchived());
        // 缺陷详情页（云效风格）基础字段
        if (req.getIteration() != null) uw.set(Issue::getIteration, req.getIteration());
        if (req.getParticipants() != null) uw.set(Issue::getParticipants, req.getParticipants());
        if (req.getCcUsers() != null) uw.set(Issue::getCcUsers, req.getCcUsers());
        if (req.getPlanStart() != null) uw.set(Issue::getPlanStart, req.getPlanStart());
        if (req.getNoFixReason() != null) uw.set(Issue::getNoFixReason, req.getNoFixReason());

        // 显式清空字段：解决 updateById 忽略 null 导致无法置空的问题
        if (req.getClearFields() != null) {
            for (String field : req.getClearFields()) {
                switch (field) {
                    case "assigneeId" -> uw.set(Issue::getAssigneeId, null);
                    case "verifierId" -> uw.set(Issue::getVerifierId, null);
                    case "sprintId" -> uw.set(Issue::getSprintId, null);
                    case "dueDate" -> uw.set(Issue::getDueDate, null);
                    case "planStart" -> uw.set(Issue::getPlanStart, null);
                    case "iteration" -> uw.set(Issue::getIteration, null);
                    case "participants" -> uw.set(Issue::getParticipants, null);
                    case "ccUsers" -> uw.set(Issue::getCcUsers, null);
                    case "noFixReason" -> uw.set(Issue::getNoFixReason, null);
                    case "module" -> uw.set(Issue::getModule, null);
                    case "fixVersion" -> uw.set(Issue::getFixVersion, null);
                    default -> throw new ServiceException(400, "不支持清空字段: " + field);
                }
            }
        }

        if (req.getStatusId() != null && !req.getStatusId().equals(issue.getStatusId())) {
            // 状态变更：与看板 move 统一走工作流流转校验（不再自由流转）
            IssueStatus target = issueStatusMapper.selectById(req.getStatusId());
            if (target == null || !target.getProjectId().equals(issue.getProjectId())) {
                throw new ServiceException(400, "状态不属于该项目");
            }
            if (!workflowService.canTransition(issue.getProjectId(), issue.getStatusId(), req.getStatusId())) {
                String fromName = Optional.ofNullable(issueStatusMapper.selectById(issue.getStatusId()))
                        .map(IssueStatus::getName).orElse("未知状态");
                throw new ServiceException(400, "不允许从「" + fromName + "」流转到「" + target.getName() + "」");
            }
            writeActivity(issue, "STATUS_CHANGED",
                    Map.of("status", resolveCode(issue.getStatusId())),
                    Map.of("status", resolveCode(req.getStatusId())));
            uw.set(Issue::getStatusId, req.getStatusId());
            // 状态变更等同移动到目标列末尾，重算 sortKey 保持列内排序正确
            uw.set(Issue::getSortKey, computeSortKey(req.getStatusId(), null, null));
        }

        if (uw.getSqlSet() != null && !uw.getSqlSet().isEmpty()) {
            uw.set(Issue::getUpdatedAt, LocalDateTime.now());
            issueMapper.update(null, uw);
        }

        Issue updated = requireIssue(id);
        Project project = projectMapper.selectById(updated.getProjectId());
        return toVO(updated, project == null ? "" : project.getProjectKey());
    }

    /**
     * 列表过滤：左侧过滤器（我负责的/我创建的/我验证的/已归档）+ 顶部筛选（状态/优先级/标签/关键字）。
     * 返回扁平 IssueVO 列表，前端按 statusId 自行分组到看板列。
     */
    public PageResult<IssueVO> listByFilter(IssueFilterRequest req) {
        UUID me = UserContext.getUserId();
        var q = Wrappers.<Issue>lambdaQuery()
                .eq(Issue::getProjectId, req.getProjectId())
                .eq(Issue::getDeleted, 0);

        if (req.getStatusId() != null) q.eq(Issue::getStatusId, req.getStatusId());
        if (req.getPriority() != null) q.eq(Issue::getPriority, req.getPriority());
        if (req.getSeverity() != null) q.eq(Issue::getSeverity, req.getSeverity());
        if (req.getKeyword() != null && !req.getKeyword().isBlank())
            q.like(Issue::getTitle, req.getKeyword());

        // 左侧范围过滤器
        if ("ASSIGNED".equals(req.getScope())) q.eq(Issue::getAssigneeId, me);
        else if ("REPORTED".equals(req.getScope())) q.eq(Issue::getReporterId, me);
        else if ("VERIFIED".equals(req.getScope())) q.eq(Issue::getVerifierId, me);
        else if ("SHARED".equals(req.getScope()))
            q.apply("participants @> {0}::jsonb", "[\"" + me + "\"]");
        else if ("ARCHIVED".equals(req.getScope())) q.eq(Issue::getArchived, 1);
        else if ("ACTIVE".equals(req.getScope())) q.eq(Issue::getArchived, 0);

        // 已归档范围外，默认只展示未归档
        if (req.getScope() == null) q.eq(Issue::getArchived, 0);

        // 标签过滤（子查询，参数化占位符防注入）
        if (req.getLabelId() != null) {
            q.apply("id IN (SELECT issue_id FROM issue_label WHERE label_id = {0})", req.getLabelId());
        }

        int page = req.getPage() == null || req.getPage() < 1 ? 1 : req.getPage();
        // 上限放宽到 1000：看板/缺陷列表需要全量加载分组，默认 20 会导致数据"消失"
        int size = req.getSize() == null || req.getSize() < 1 ? 1000 : Math.min(req.getSize(), 1000);

        // 必须先 count 再排序：MyBatis-Plus 的 selectCount 不会移除 ORDER BY，
        // 若在 count 前加排序，会生成 "COUNT(*) ... ORDER BY status_id"，PostgreSQL 报错
        long total = issueMapper.selectCount(q);

        // 安全排序：白名单字段，防止 SQL 注入
        applySort(q, req.getSortField(), req.getSortOrder());
        // PostgreSQL 分页语法：LIMIT size OFFSET offset（不支持 MySQL 的 "LIMIT offset, size"）
        q.last("LIMIT " + size + " OFFSET " + ((long) (page - 1) * size));

        List<Issue> issues = issueMapper.selectList(q);
        Project project = projectMapper.selectById(req.getProjectId());
        String key = project == null ? "" : project.getProjectKey();
        return new PageResult<>(total, page, size, toVOList(issues, key));
    }

    /** 排序字段白名单：保证 order by 列名安全 */
    private void applySort(com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<Issue> q,
                           String sortField, String sortOrder) {
        if (sortField == null || sortField.isBlank()) {
            q.orderByAsc(Issue::getStatusId).orderByAsc(Issue::getSortKey);
            return;
        }
        boolean asc = !"desc".equalsIgnoreCase(sortOrder);
        switch (sortField) {
            case "updatedAt" -> { if (asc) q.orderByAsc(Issue::getUpdatedAt); else q.orderByDesc(Issue::getUpdatedAt); }
            case "createdAt" -> { if (asc) q.orderByAsc(Issue::getCreatedAt); else q.orderByDesc(Issue::getCreatedAt); }
            case "priority" -> { if (asc) q.orderByAsc(Issue::getPriority); else q.orderByDesc(Issue::getPriority); }
            case "severity" -> { if (asc) q.orderByAsc(Issue::getSeverity); else q.orderByDesc(Issue::getSeverity); }
            case "statusId" -> { if (asc) q.orderByAsc(Issue::getStatusId); else q.orderByDesc(Issue::getStatusId); }
            case "sortKey" -> { if (asc) q.orderByAsc(Issue::getSortKey); else q.orderByDesc(Issue::getSortKey); }
            default -> q.orderByAsc(Issue::getStatusId).orderByAsc(Issue::getSortKey);
        }
    }

    @Transactional
    public IssueVO move(UUID id, MoveIssueRequest req) {
        Issue issue = requireIssue(id);
        UUID fromStatus = issue.getStatusId();
        UUID toStatus = req.getStatusId();

        if (!fromStatus.equals(toStatus)) {
            // 跨状态：目标状态必须属于当前项目，且默认工作流存在 from→to 流转规则
            IssueStatus target = issueStatusMapper.selectById(toStatus);
            if (target == null || !target.getProjectId().equals(issue.getProjectId())) {
                throw new ServiceException(400, "状态不属于该项目");
            }
            if (!workflowService.canTransition(issue.getProjectId(), fromStatus, toStatus)) {
                String fromName = Optional.ofNullable(issueStatusMapper.selectById(fromStatus))
                        .map(IssueStatus::getName).orElse("未知状态");
                throw new ServiceException(400, "不允许从「" + fromName + "」流转到「" + target.getName() + "」");
            }
        }

        issue.setStatusId(toStatus);

        // 统一由后端计算 sortKey（fractional indexing），避免前后端算法不一致
        String sortKey = computeSortKey(toStatus, req.getBeforeId(), req.getAfterId());
        issue.setSortKey(sortKey);
        issue.setUpdatedAt(LocalDateTime.now());
        issueMapper.updateById(issue);

        // 写活动记录（审计）
        if (!fromStatus.equals(toStatus)) {
            writeActivity(issue, "STATUS_CHANGED",
                    Map.of("status", resolveCode(fromStatus)),
                    Map.of("status", resolveCode(toStatus)));
        }

        Project project = projectMapper.selectById(issue.getProjectId());
        return toVO(issue, project == null ? "" : project.getProjectKey());
    }

    /**
     * 删除缺陷并级联清理：子任务（含多级）、评论、附件、标签关联、关联记录。
     * 活动记录作为审计日志保留。
     */
    @Transactional
    public void delete(UUID id) {
        requireIssue(id);
        // 收集自身及所有后代任务 id（树形子任务）
        List<UUID> ids = new ArrayList<>();
        collectDescendantIds(id, ids);

        // 主任务与子任务软删除（@TableLogic 自动补 deleted=0 条件）
        issueMapper.deleteBatchIds(ids);
        // 评论逻辑删除
        issueCommentMapper.delete(Wrappers.<IssueComment>lambdaQuery().in(IssueComment::getIssueId, ids));
        // 附件物理删除
        issueAttachmentMapper.delete(Wrappers.<IssueAttachment>lambdaQuery().in(IssueAttachment::getIssueId, ids));
        // 标签关联物理删除
        issueTagMapper.deleteIssueLabelByIssueIds(ids);
        // 关联记录双向删除，避免树形视图出现孤儿节点
        issueLinkMapper.delete(Wrappers.<IssueLink>lambdaQuery()
                .and(w -> w.in(IssueLink::getIssueId, ids).or().in(IssueLink::getTargetIssueId, ids)));
    }

    private void collectDescendantIds(UUID parentId, List<UUID> out) {
        out.add(parentId);
        List<Issue> children = issueMapper.selectList(Wrappers.<Issue>lambdaQuery()
                .eq(Issue::getParentId, parentId)
                .eq(Issue::getDeleted, 0));
        for (Issue child : children) {
            collectDescendantIds(child.getId(), out);
        }
    }

    private String computeSortKey(UUID statusId, UUID beforeId, UUID afterId) {
        if (beforeId != null && afterId != null) {
            Issue b = issueMapper.selectById(beforeId);
            Issue a = issueMapper.selectById(afterId);
            if (b == null || a == null) {
                throw new ServiceException(400, "相邻卡片不存在");
            }
            ensureSameStatusColumn(statusId, b, a);
            return SortKeyUtils.between(b.getSortKey(), a.getSortKey());
        }
        if (beforeId != null) {
            Issue b = issueMapper.selectById(beforeId);
            if (b == null) {
                throw new ServiceException(400, "相邻卡片不存在");
            }
            ensureSameStatusColumn(statusId, b);
            return SortKeyUtils.between(b.getSortKey(), null);
        }
        if (afterId != null) {
            Issue a = issueMapper.selectById(afterId);
            if (a == null) {
                throw new ServiceException(400, "相邻卡片不存在");
            }
            ensureSameStatusColumn(statusId, a);
            return SortKeyUtils.between(null, a.getSortKey());
        }
        return SortKeyUtils.last();
    }

    /** 拖拽排序的相邻卡片必须属于目标状态列，防止跨列拖拽破坏 sortKey 顺序 */
    private void ensureSameStatusColumn(UUID statusId, Issue... neighbors) {
        for (Issue n : neighbors) {
            if (!statusId.equals(n.getStatusId())) {
                throw new ServiceException(400, "相邻卡片不属于目标状态列");
            }
        }
    }

    private String resolveCode(UUID statusId) {
        IssueStatus s = issueStatusMapper.selectById(statusId);
        return s == null ? statusId.toString() : s.getCode();
    }

    private void writeActivity(Issue issue, String action, Map<String, Object> oldValue, Map<String, Object> newValue) {
        IssueActivity act = new IssueActivity();
        act.setId(UUID.randomUUID());
        act.setIssueId(issue.getId());
        act.setActorId(UserContext.getUserId());
        act.setAction(action);
        act.setOldValue(toJson(oldValue));
        act.setNewValue(toJson(newValue));
        act.setCreatedAt(LocalDateTime.now());
        issueActivityMapper.insert(act);
    }

    /** 统一序列化活动字段，避免手拼 JSON 出现转义/拼接错误 */
    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new ServiceException(500, "序列化活动记录失败");
        }
    }

    private Issue requireIssue(UUID id) {
        Issue issue = issueMapper.selectById(id);
        if (issue == null || issue.getDeleted() != null && issue.getDeleted() == 1) {
            throw new ServiceException(404, "Issue 不存在");
        }
        return issue;
    }

    /**
     * 批量 VO 转换：类型/状态/用户一次 selectBatchIds，标签/评论数一次聚合，
     * 消除列表接口的 N+1 查询（看板 100 卡片从约 700 次 DB 查询降到固定几次）。
     */
    public List<IssueVO> toVOList(List<Issue> issues, String projectKey) {
        if (issues == null || issues.isEmpty()) {
            return List.of();
        }
        Set<UUID> issueIds = new HashSet<>();
        Set<UUID> typeIds = new HashSet<>();
        Set<UUID> statusIds = new HashSet<>();
        Set<UUID> userIds = new HashSet<>();
        for (Issue i : issues) {
            issueIds.add(i.getId());
            if (i.getIssueTypeId() != null) typeIds.add(i.getIssueTypeId());
            if (i.getStatusId() != null) statusIds.add(i.getStatusId());
            if (i.getAssigneeId() != null) userIds.add(i.getAssigneeId());
            if (i.getReporterId() != null) userIds.add(i.getReporterId());
            if (i.getVerifierId() != null) userIds.add(i.getVerifierId());
        }
        Map<UUID, IssueType> typeMap = typeIds.isEmpty() ? new HashMap<>()
                : batchMap(issueTypeMapper.selectBatchIds(typeIds), IssueType::getId);
        Map<UUID, IssueStatus> statusMap = statusIds.isEmpty() ? new HashMap<>()
                : batchMap(issueStatusMapper.selectBatchIds(statusIds), IssueStatus::getId);
        Map<UUID, SysUser> userMap = userIds.isEmpty() ? new HashMap<>()
                : batchMap(sysUserMapper.selectBatchIds(userIds), SysUser::getId);

        // 标签：一次查出所有 issue 的标签，按 issueId 分组
        Map<UUID, List<LabelVO>> labelMap = new HashMap<>();
        for (IssueTag tag : issueTagMapper.selectByIssueIds(issueIds)) {
            labelMap.computeIfAbsent(tag.getIssueId(), k -> new ArrayList<>()).add(toLabelVO(tag));
        }
        // 评论数：一次 GROUP BY 聚合
        Map<UUID, Long> commentMap = new HashMap<>();
        for (Map<String, Object> row : issueCommentMapper.countByIssueIds(issueIds)) {
            Object issueId = row.get("issue_id");
            if (issueId != null) {
                long cnt = row.get("cnt") instanceof Number n ? n.longValue() : 0L;
                commentMap.put(UUID.fromString(issueId.toString()), cnt);
            }
        }

        List<IssueVO> result = new ArrayList<>(issues.size());
        for (Issue i : issues) {
            IssueVO vo = new IssueVO();
            BeanUtils.copyProperties(i, vo);
            vo.setIssueKey(projectKey + "-" + i.getIssueNumber());

            IssueType type = typeMap.get(i.getIssueTypeId());
            if (type != null) {
                vo.setIssueTypeCode(type.getCode());
                vo.setIssueTypeIcon(type.getIcon());
                vo.setIssueTypeName(type.getName());
            }
            IssueStatus st = statusMap.get(i.getStatusId());
            if (st != null) vo.setStatusCode(st.getCode());
            SysUser a = i.getAssigneeId() == null ? null : userMap.get(i.getAssigneeId());
            vo.setAssigneeName(a == null ? null : a.getDisplayName());
            SysUser r = i.getReporterId() == null ? null : userMap.get(i.getReporterId());
            vo.setReporterName(r == null ? null : r.getDisplayName());
            SysUser v = i.getVerifierId() == null ? null : userMap.get(i.getVerifierId());
            vo.setVerifierName(v == null ? null : v.getDisplayName());
            vo.setLabels(labelMap.getOrDefault(i.getId(), List.of()));
            vo.setCommentCount(commentMap.getOrDefault(i.getId(), 0L));
            result.add(vo);
        }
        return result;
    }

    /** 按 id 建索引，重复 key 后者覆盖前者（理论上不会出现） */
    private <T> Map<UUID, T> batchMap(Collection<T> list, Function<T, UUID> idGetter) {
        Map<UUID, T> map = new HashMap<>();
        if (list == null) return map;
        for (T item : list) {
            map.put(idGetter.apply(item), item);
        }
        return map;
    }

    private LabelVO toLabelVO(IssueTag tag) {
        LabelVO l = new LabelVO();
        l.setId(tag.getId());
        l.setName(tag.getName());
        l.setColor(tag.getColor());
        return l;
    }

    public IssueVO toVO(Issue issue, String projectKey) {
        IssueVO vo = new IssueVO();
        BeanUtils.copyProperties(issue, vo);
        vo.setIssueKey(projectKey + "-" + issue.getIssueNumber());

        // 类型信息
        IssueType type = issueTypeMapper.selectById(issue.getIssueTypeId());
        if (type != null) {
            vo.setIssueTypeCode(type.getCode());
            vo.setIssueTypeIcon(type.getIcon());
            vo.setIssueTypeName(type.getName());
        }
        // 状态 code
        IssueStatus st = issueStatusMapper.selectById(issue.getStatusId());
        if (st != null) vo.setStatusCode(st.getCode());
        // 人员名
        if (issue.getAssigneeId() != null) {
            SysUser a = sysUserMapper.selectById(issue.getAssigneeId());
            vo.setAssigneeName(a == null ? null : a.getDisplayName());
        }
        if (issue.getReporterId() != null) {
            SysUser r = sysUserMapper.selectById(issue.getReporterId());
            vo.setReporterName(r == null ? null : r.getDisplayName());
        }
        if (issue.getVerifierId() != null) {
            SysUser v = sysUserMapper.selectById(issue.getVerifierId());
            vo.setVerifierName(v == null ? null : v.getDisplayName());
        }
        // 标签
        vo.setLabels(issueTagMapper.selectByIssue(issue.getId()).stream()
                .map(this::toLabelVO).collect(Collectors.toList()));
        // 评论数
        vo.setCommentCount(issueCommentMapper.countByIssue(issue.getId()));
        return vo;
    }
}

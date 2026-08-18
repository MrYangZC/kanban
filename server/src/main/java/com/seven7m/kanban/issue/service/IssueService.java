package com.seven7m.kanban.issue.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.seven7m.kanban.common.domain.PageResult;
import com.seven7m.kanban.common.exception.ServiceException;
import com.seven7m.kanban.common.security.UserContext;
import com.seven7m.kanban.common.util.SortKeyUtils;
import com.seven7m.kanban.issue.dto.CreateIssueRequest;
import com.seven7m.kanban.issue.dto.IssueFilterRequest;
import com.seven7m.kanban.issue.dto.IssueVO;
import com.seven7m.kanban.issue.dto.LabelVO;
import com.seven7m.kanban.issue.dto.MoveIssueRequest;
import com.seven7m.kanban.issue.dto.UpdateIssueRequest;
import com.seven7m.kanban.issue.entity.Issue;
import com.seven7m.kanban.issue.entity.IssueActivity;
import com.seven7m.kanban.issue.entity.IssueType;
import com.seven7m.kanban.issue.mapper.IssueActivityMapper;
import com.seven7m.kanban.issue.mapper.IssueMapper;
import com.seven7m.kanban.issue.mapper.IssueTypeMapper;
import com.seven7m.kanban.issue.tag.IssueTagMapper;
import com.seven7m.kanban.issue.comment.IssueCommentMapper;
import com.seven7m.kanban.issuestatus.entity.IssueStatus;
import com.seven7m.kanban.issuestatus.mapper.IssueStatusMapper;
import com.seven7m.kanban.project.entity.Project;
import com.seven7m.kanban.project.mapper.ProjectMapper;
import com.seven7m.kanban.user.entity.SysUser;
import com.seven7m.kanban.user.mapper.SysUserMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
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
    private final SysUserMapper sysUserMapper;
    private final ProjectMapper projectMapper;
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
        return toVO(issue, project.getProjectKey());
    }

    @Transactional
    public IssueVO update(UUID id, UpdateIssueRequest req) {
        Issue issue = requireIssue(id);
        if (req.getTitle() != null) issue.setTitle(req.getTitle());
        if (req.getDescription() != null) issue.setDescription(req.getDescription());
        if (req.getPriority() != null) issue.setPriority(req.getPriority());
        if (req.getIssueTypeId() != null) issue.setIssueTypeId(req.getIssueTypeId());
        if (req.getAssigneeId() != null) issue.setAssigneeId(req.getAssigneeId());
        if (req.getVerifierId() != null) issue.setVerifierId(req.getVerifierId());
        if (req.getDueDate() != null) issue.setDueDate(req.getDueDate());
        if (req.getSeverity() != null) issue.setSeverity(req.getSeverity());
        if (req.getModule() != null) issue.setModule(req.getModule());
        if (req.getFixVersion() != null) issue.setFixVersion(req.getFixVersion());
        if (req.getSource() != null) issue.setSource(req.getSource());
        if (req.getArchived() != null) issue.setArchived(req.getArchived());
        if (req.getStatusId() != null && !req.getStatusId().equals(issue.getStatusId())) {
            // 状态变更：仅校验目标状态属于当前项目（详情页编辑为自由流转），并写审计记录
            IssueStatus target = issueStatusMapper.selectById(req.getStatusId());
            if (target == null || !target.getProjectId().equals(issue.getProjectId())) {
                throw new ServiceException(400, "状态不属于该项目");
            }
            writeActivity(issue, "STATUS_CHANGED",
                    "{\"status\":\"" + resolveCode(issue.getStatusId()) + "\"}",
                    "{\"status\":\"" + resolveCode(req.getStatusId()) + "\"}");
            issue.setStatusId(req.getStatusId());
            // 状态变更等同移动到目标列末尾，重算 sortKey 保持列内排序正确
            issue.setSortKey(computeSortKey(req.getStatusId(), null, null));
        }
        issue.setUpdatedAt(LocalDateTime.now());
        issueMapper.updateById(issue);
        Project project = projectMapper.selectById(issue.getProjectId());
        return toVO(issue, project.getProjectKey());
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
        else if ("ARCHIVED".equals(req.getScope())) q.eq(Issue::getArchived, 1);
        else if ("ACTIVE".equals(req.getScope())) q.eq(Issue::getArchived, 0);

        // 已归档范围外，默认只展示未归档
        if (req.getScope() == null) q.eq(Issue::getArchived, 0);

        // 标签过滤（子查询）
        if (req.getLabelId() != null) {
            q.inSql(Issue::getId, "SELECT issue_id FROM issue_label WHERE label_id = '" + req.getLabelId() + "'");
        }

        int page = req.getPage() == null || req.getPage() < 1 ? 1 : req.getPage();
        int size = req.getSize() == null || req.getSize() < 1 ? 20 : Math.min(req.getSize(), 200);

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
        List<IssueVO> voList = issues.stream().map(i -> toVO(i, key)).collect(Collectors.toList());
        return new PageResult<>(total, page, size, voList);
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
            // 跨状态：仅校验目标状态属于当前项目（看板拖拽为自由流转，不做工作流流转校验）
            IssueStatus target = issueStatusMapper.selectById(toStatus);
            if (target == null || !target.getProjectId().equals(issue.getProjectId())) {
                throw new ServiceException(400, "状态不属于该项目");
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
                    "{\"status\":\"" + resolveCode(fromStatus) + "\"}",
                    "{\"status\":\"" + resolveCode(toStatus) + "\"}");
        }

        Project project = projectMapper.selectById(issue.getProjectId());
        return toVO(issue, project.getProjectKey());
    }

    @Transactional
    public void delete(UUID id) {
        Issue issue = requireIssue(id);
        // @TableLogic 会在 deleteById 时自动执行 UPDATE ... SET deleted=1 WHERE deleted=0
        issueMapper.deleteById(issue.getId());
    }

    private String computeSortKey(UUID statusId, UUID beforeId, UUID afterId) {
        if (beforeId != null && afterId != null) {
            Issue b = issueMapper.selectById(beforeId);
            Issue a = issueMapper.selectById(afterId);
            if (b == null || a == null) {
                throw new ServiceException(400, "相邻卡片不存在");
            }
            return SortKeyUtils.between(b.getSortKey(), a.getSortKey());
        }
        if (beforeId != null) {
            Issue b = issueMapper.selectById(beforeId);
            if (b == null) {
                throw new ServiceException(400, "相邻卡片不存在");
            }
            return SortKeyUtils.between(b.getSortKey(), null);
        }
        if (afterId != null) {
            Issue a = issueMapper.selectById(afterId);
            if (a == null) {
                throw new ServiceException(400, "相邻卡片不存在");
            }
            return SortKeyUtils.between(null, a.getSortKey());
        }
        return SortKeyUtils.last();
    }

    private String resolveCode(UUID statusId) {
        IssueStatus s = issueStatusMapper.selectById(statusId);
        return s == null ? statusId.toString() : s.getCode();
    }

    private void writeActivity(Issue issue, String action, String oldValue, String newValue) {
        IssueActivity act = new IssueActivity();
        act.setId(UUID.randomUUID());
        act.setIssueId(issue.getId());
        act.setActorId(UserContext.getUserId());
        act.setAction(action);
        act.setOldValue(oldValue);
        act.setNewValue(newValue);
        act.setCreatedAt(LocalDateTime.now());
        issueActivityMapper.insert(act);
    }

    private Issue requireIssue(UUID id) {
        Issue issue = issueMapper.selectById(id);
        if (issue == null || issue.getDeleted() != null && issue.getDeleted() == 1) {
            throw new ServiceException(404, "Issue 不存在");
        }
        return issue;
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
        vo.setLabels(issueTagMapper.selectByIssue(issue.getId()).stream().map(t -> {
            LabelVO l = new LabelVO();
            l.setId(t.getId());
            l.setName(t.getName());
            l.setColor(t.getColor());
            return l;
        }).collect(Collectors.toList()));
        // 评论数
        vo.setCommentCount(issueCommentMapper.countByIssue(issue.getId()));
        return vo;
    }
}

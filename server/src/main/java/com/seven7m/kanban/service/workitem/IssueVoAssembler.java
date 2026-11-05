package com.seven7m.kanban.service.workitem;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.seven7m.kanban.common.enums.ItemCategory;
import com.seven7m.kanban.dto.issue.IssueVO;
import com.seven7m.kanban.dto.issue.LabelVO;
import com.seven7m.kanban.entity.issue.IssueCc;
import com.seven7m.kanban.entity.issue.IssueParticipant;
import com.seven7m.kanban.entity.issue.IssueType;
import com.seven7m.kanban.entity.issue.tag.IssueTag;
import com.seven7m.kanban.mapper.issue.comment.CommentCountRow;
import com.seven7m.kanban.entity.issuestatus.IssueStatus;
import com.seven7m.kanban.entity.user.SysUser;
import com.seven7m.kanban.entity.workitem.AbstractWorkItem;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * 工作项 VO 装配（工作项实体 → 前端 {@link IssueVO}）。
 *
 * <p>从 1200 行的 {@code BaseWorkItemService} 里移出来的原因：这段逻辑只做"批量预取 + 字段映射"，
 * 不涉及权限、状态机或事务，却占了基类里很大一块。抽出来后可以单独验证
 * "类型/状态/人员/标签/评论数/参与人/抄送"是否都装配到位。
 *
 * <p>批量预取是这里的核心约束：列表页一次 20~100 条，逐条装配会产生 9×N 次查询。
 * 因此所有字典与关联数据都按 id 集合各查一次，再在内存里分发。
 */
@Component
@RequiredArgsConstructor
public class IssueVoAssembler {

    private final WorkItemSharedDeps shared;

    /** 批量装配：每类关联数据最多一次查询 */
    public <T extends AbstractWorkItem> List<IssueVO> toVOList(List<T> issues, String projectKey,
                                                              ItemCategory category) {
        if (issues == null || issues.isEmpty()) {
            return List.of();
        }
        String categoryName = category.name();
        List<UUID> ids = issues.stream().map(AbstractWorkItem::getId).toList();

        Set<UUID> typeIds = issues.stream().map(AbstractWorkItem::getIssueTypeId)
                .filter(Objects::nonNull).collect(Collectors.toSet());
        Map<UUID, IssueType> typeMap = typeIds.isEmpty() ? Map.of()
                : shared.issueTypeMapper().selectBatchIds(typeIds).stream()
                        .collect(Collectors.toMap(IssueType::getId, t -> t));

        Set<UUID> statusIds = issues.stream().map(AbstractWorkItem::getStatusId)
                .filter(Objects::nonNull).collect(Collectors.toSet());
        Map<UUID, IssueStatus> statusMap = statusIds.isEmpty() ? Map.of()
                : shared.issueStatusMapper().selectBatchIds(statusIds).stream()
                        .collect(Collectors.toMap(IssueStatus::getId, s -> s));

        Set<UUID> userIds = new HashSet<>();
        for (AbstractWorkItem i : issues) {
            if (i.getAssigneeId() != null) userIds.add(i.getAssigneeId());
            if (i.getReporterId() != null) userIds.add(i.getReporterId());
            if (i.getVerifierId() != null) userIds.add(i.getVerifierId());
        }
        Map<UUID, SysUser> userMap = userIds.isEmpty() ? Map.of()
                : shared.sysUserMapper().selectBatchIds(userIds).stream()
                        .collect(Collectors.toMap(SysUser::getId, u -> u));

        Map<UUID, List<IssueTag>> tagMap = shared.issueTagMapper().selectByIssueIds(ids, categoryName)
                .stream().collect(Collectors.groupingBy(IssueTag::getIssueId));

        Map<UUID, Long> commentCountMap = new HashMap<>();
        for (CommentCountRow row : shared.issueCommentMapper().countByIssueIds(ids, categoryName)) {
            commentCountMap.put(row.getIssueId(), row.getCnt() == null ? 0L : row.getCnt());
        }

        Map<UUID, List<UUID>> participantMap = shared.issueParticipantMapper().selectList(
                        Wrappers.<IssueParticipant>lambdaQuery()
                                .in(IssueParticipant::getIssueId, ids)
                                .eq(IssueParticipant::getCategory, categoryName))
                .stream().collect(Collectors.groupingBy(IssueParticipant::getIssueId,
                        Collectors.mapping(IssueParticipant::getUserId, Collectors.toList())));

        Map<UUID, List<UUID>> ccMap = shared.issueCcMapper().selectList(
                        Wrappers.<IssueCc>lambdaQuery()
                                .in(IssueCc::getIssueId, ids)
                                .eq(IssueCc::getCategory, categoryName))
                .stream().collect(Collectors.groupingBy(IssueCc::getIssueId,
                        Collectors.mapping(IssueCc::getUserId, Collectors.toList())));

        return issues.stream()
                .map(i -> assemble(i, projectKey, new Lookups(typeMap, statusMap, userMap, tagMap,
                        commentCountMap, participantMap, ccMap)))
                .collect(Collectors.toList());
    }

    /**
     * 装配所需的批量查询结果。
     *
     * <p>此前 {@code assemble} 有 9 个参数（1 个实体 + 1 个 key + 7 个 Map），
     * 调用处只能靠位置区分谁是谁；按手册"参数过多宜封装"收敛为一个值对象。
     */
    private record Lookups(
            Map<UUID, IssueType> typeMap,
            Map<UUID, IssueStatus> statusMap,
            Map<UUID, SysUser> userMap,
            Map<UUID, List<IssueTag>> tagMap,
            Map<UUID, Long> commentCountMap,
            Map<UUID, List<UUID>> participantMap,
            Map<UUID, List<UUID>> ccMap) {
    }

    /** 单条装配：委托给批量实现，保证与列表页字段口径完全一致 */
    public <T extends AbstractWorkItem> IssueVO toVO(T issue, String projectKey, ItemCategory category) {
        List<IssueVO> list = toVOList(List.of(issue), projectKey, category);
        return list.isEmpty() ? null : list.get(0);
    }

    private IssueVO assemble(AbstractWorkItem issue, String projectKey, Lookups lookups) {
        IssueVO vo = new IssueVO();
        BeanUtils.copyProperties(issue, vo);
        vo.setIssueKey(projectKey + "-" + issue.getIssueNumber());

        IssueType type = issue.getIssueTypeId() == null ? null : lookups.typeMap().get(issue.getIssueTypeId());
        if (type != null) {
            vo.setIssueTypeCode(type.getCode());
            vo.setIssueTypeIcon(type.getIcon());
            vo.setIssueTypeName(type.getName());
        }
        IssueStatus st = issue.getStatusId() == null ? null : lookups.statusMap().get(issue.getStatusId());
        if (st != null) {
            vo.setStatusCode(st.getCode());
        }

        if (issue.getAssigneeId() != null) {
            SysUser a = lookups.userMap().get(issue.getAssigneeId());
            vo.setAssigneeName(a == null ? null : a.getDisplayName());
            vo.setAssigneeAvatarUrl(a == null ? null : a.getAvatarUrl());
        }
        if (issue.getReporterId() != null) {
            SysUser r = lookups.userMap().get(issue.getReporterId());
            vo.setReporterName(r == null ? null : r.getDisplayName());
            vo.setReporterAvatarUrl(r == null ? null : r.getAvatarUrl());
        }
        if (issue.getVerifierId() != null) {
            SysUser v = lookups.userMap().get(issue.getVerifierId());
            vo.setVerifierName(v == null ? null : v.getDisplayName());
            vo.setVerifierAvatarUrl(v == null ? null : v.getAvatarUrl());
        }

        vo.setLabels(lookups.tagMap().getOrDefault(issue.getId(), List.of()).stream()
                .map(IssueVoAssembler::toLabelVO).collect(Collectors.toList()));
        vo.setCommentCount(lookups.commentCountMap().getOrDefault(issue.getId(), 0L));
        vo.setParticipants(lookups.participantMap().getOrDefault(issue.getId(), List.of()).stream()
                .map(UUID::toString).collect(Collectors.toList()));
        vo.setCcUsers(lookups.ccMap().getOrDefault(issue.getId(), List.of()).stream()
                .map(UUID::toString).collect(Collectors.toList()));
        return vo;
    }

    private static LabelVO toLabelVO(IssueTag tag) {
        LabelVO l = new LabelVO();
        l.setId(tag.getId());
        l.setName(tag.getName());
        l.setColor(tag.getColor());
        return l;
    }
}

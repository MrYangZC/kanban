package com.seven7m.kanban.service.issue.link.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.seven7m.kanban.common.exception.ErrorCode;
import com.seven7m.kanban.common.exception.ServiceException;
import com.seven7m.kanban.dto.issue.IssueVO;
import com.seven7m.kanban.dto.issue.LinkVO;
import com.seven7m.kanban.entity.issue.link.IssueLink;
import com.seven7m.kanban.mapper.issue.link.IssueLinkMapper;
import com.seven7m.kanban.service.issue.link.IssueLinkService;
import com.seven7m.kanban.service.workitem.WorkItemRouter;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * 工作项关联服务实现。
 *
 * <p>单表能力来自 {@link ServiceImpl}；跨表的"关联项编号/标题"由 {@link WorkItemRouter}
 * 反查（关联可以指向任意类别的工作项，物理表不同，必须由 router 定位）。
 */
@Service
@RequiredArgsConstructor
public class IssueLinkServiceImpl extends ServiceImpl<IssueLinkMapper, IssueLink>
        implements IssueLinkService {

    /** 关联类型白名单，与前端下拉选项一致；库里存的就是这四个中文值，改一处要同步改前端 */
    private static final Set<String> VALID_RELATION_TYPES = Set.of("关联", "阻塞", "重复", "修复");

    /** 关联项编号缺失时的占位（编号由工作项真实数据生成，不自拼前缀） */
    private static final String EMPTY_ISSUE_KEY = "";

    private final WorkItemRouter workItemRouter;

    /** 类别名（TASK/REQUIREMENT/BUG）；工作项不存在时由 router 统一抛 404 */
    private String categoryOf(UUID issueId) {
        return workItemRouter.categoryNameOrThrow(issueId);
    }

    @Override
    public List<LinkVO> list(UUID issueId) {
        return list(Wrappers.<IssueLink>lambdaQuery()
                        .eq(IssueLink::getIssueId, issueId)
                        .orderByDesc(IssueLink::getCreatedAt))
                .stream().map(l -> {
                    LinkVO vo = new LinkVO();
                    BeanUtils.copyProperties(l, vo);
                    IssueVO target = workItemRouter.getBriefById(l.getTargetIssueId());
                    if (target != null) {
                        fillTarget(vo, target);
                    }
                    return vo;
                }).collect(Collectors.toList());
    }

    @Override
    @Transactional
    public LinkVO add(UUID issueId, UUID targetIssueId, String relationType) {
        if (issueId.equals(targetIssueId)) {
            throw new ServiceException(ErrorCode.LINK_SELF);
        }
        if (relationType == null || !VALID_RELATION_TYPES.contains(relationType)) {
            throw new ServiceException(ErrorCode.LINK_TYPE_INVALID);
        }
        IssueVO source = workItemRouter.getBriefById(issueId);
        if (source == null) {
            throw new ServiceException(ErrorCode.ISSUE_NOT_FOUND);
        }
        IssueVO target = workItemRouter.getBriefById(targetIssueId);
        if (target == null) {
            throw new ServiceException(ErrorCode.LINK_TARGET_NOT_FOUND);
        }
        if (!source.getProjectId().equals(target.getProjectId())) {
            throw new ServiceException(ErrorCode.LINK_CROSS_PROJECT);
        }
        long dup = count(Wrappers.<IssueLink>lambdaQuery()
                .eq(IssueLink::getIssueId, issueId)
                .eq(IssueLink::getTargetIssueId, targetIssueId)
                .eq(IssueLink::getRelationType, relationType));
        if (dup > 0) {
            throw new ServiceException(ErrorCode.LINK_ALREADY_EXISTS);
        }
        IssueLink l = new IssueLink();
        l.setId(UUID.randomUUID());
        l.setIssueId(issueId);
        // 显式落类别，避免取列默认值 TASK 导致需求/缺陷的关联查询与清理失效
        l.setCategory(categoryOf(issueId));
        l.setTargetIssueId(targetIssueId);
        l.setRelationType(relationType);
        l.setCreatedAt(LocalDateTime.now());
        save(l);

        LinkVO vo = new LinkVO();
        BeanUtils.copyProperties(l, vo);
        fillTarget(vo, target);
        return vo;
    }

    /** 关联项的展示字段统一在此填充：编号必须取工作项真实编号，不允许自拼前缀 */
    private void fillTarget(LinkVO vo, IssueVO target) {
        vo.setTargetKey(target.getIssueKey() == null ? EMPTY_ISSUE_KEY : target.getIssueKey());
        vo.setTargetTitle(target.getTitle());
    }

    @Override
    @Transactional
    public void delete(UUID issueId, UUID linkId) {
        IssueLink l = getById(linkId);
        if (l == null || !l.getIssueId().equals(issueId)) {
            return;
        }
        removeById(linkId);
    }
}

package com.seven7m.kanban.service.issue.link;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.seven7m.kanban.common.enums.ItemCategory;
import com.seven7m.kanban.common.exception.ServiceException;
import com.seven7m.kanban.dto.issue.IssueVO;
import com.seven7m.kanban.dto.issue.LinkVO;
import com.seven7m.kanban.entity.issue.link.IssueLink;
import com.seven7m.kanban.mapper.issue.link.IssueLinkMapper;
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

@Service
@RequiredArgsConstructor
public class IssueLinkService {

    private final IssueLinkMapper linkMapper;
    private final WorkItemRouter workItemRouter;

    /** 关联类型枚举（与前端下拉选项一致） */
    private static final Set<String> VALID_RELATION_TYPES = Set.of("关联", "阻塞", "重复", "修复");

    /** 根据工作项 id 判定所属类别（TASK/REQUIREMENT/BUG），不存在抛 404 */
    private String categoryOf(UUID issueId) {
        ItemCategory c = workItemRouter.categoryOf(issueId);
        if (c == null) throw new ServiceException(404, "工作项不存在");
        return c.name();
    }

    public List<LinkVO> list(UUID issueId) {
        return linkMapper.selectList(Wrappers.<IssueLink>lambdaQuery()
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

    @Transactional
    public LinkVO add(UUID issueId, UUID targetIssueId, String relationType) {
        if (issueId.equals(targetIssueId)) {
            throw new ServiceException(400, "不能关联自身");
        }
        if (relationType == null || !VALID_RELATION_TYPES.contains(relationType)) {
            throw new ServiceException(400, "关联类型不合法");
        }
        IssueVO source = workItemRouter.getBriefById(issueId);
        if (source == null) {
            throw new ServiceException(404, "工作项不存在");
        }
        IssueVO target = workItemRouter.getBriefById(targetIssueId);
        if (target == null) {
            throw new ServiceException(404, "关联工作项不存在");
        }
        if (!source.getProjectId().equals(target.getProjectId())) {
            throw new ServiceException(400, "不能跨项目关联");
        }
        long dup = linkMapper.selectCount(Wrappers.<IssueLink>lambdaQuery()
                .eq(IssueLink::getIssueId, issueId)
                .eq(IssueLink::getTargetIssueId, targetIssueId)
                .eq(IssueLink::getRelationType, relationType));
        if (dup > 0) {
            throw new ServiceException(400, "关联已存在");
        }
        IssueLink l = new IssueLink();
        l.setId(UUID.randomUUID());
        l.setIssueId(issueId);
        // 显式落类别，避免取列默认值 TASK 导致需求/缺陷的关联查询与清理失效
        l.setCategory(categoryOf(issueId));
        l.setTargetIssueId(targetIssueId);
        l.setRelationType(relationType);
        l.setCreatedAt(LocalDateTime.now());
        linkMapper.insert(l);

        LinkVO vo = new LinkVO();
        BeanUtils.copyProperties(l, vo);
        fillTarget(vo, target);
        return vo;
    }

    /** 关联项的展示字段统一在此填充：编号必须取工作项真实编号，不允许自拼前缀 */
    private void fillTarget(LinkVO vo, IssueVO target) {
        vo.setTargetKey(target.getIssueKey() == null ? "" : target.getIssueKey());
        vo.setTargetTitle(target.getTitle());
    }

    @Transactional
    public void delete(UUID issueId, UUID linkId) {
        IssueLink l = linkMapper.selectById(linkId);
        if (l == null || !l.getIssueId().equals(issueId)) {
            return;
        }
        linkMapper.deleteById(linkId);
    }
}

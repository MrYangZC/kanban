package com.seven7m.kanban.issue.link;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.seven7m.kanban.common.exception.ServiceException;
import com.seven7m.kanban.issue.dto.LinkVO;
import com.seven7m.kanban.issue.entity.Issue;
import com.seven7m.kanban.issue.mapper.IssueMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class IssueLinkService {

    private final IssueLinkMapper linkMapper;
    private final IssueMapper issueMapper;

    public List<LinkVO> list(UUID issueId) {
        return linkMapper.selectList(Wrappers.<IssueLink>lambdaQuery()
                        .eq(IssueLink::getIssueId, issueId)
                        .orderByDesc(IssueLink::getCreatedAt))
                .stream().map(l -> {
                    LinkVO vo = new LinkVO();
                    BeanUtils.copyProperties(l, vo);
                    Issue target = issueMapper.selectById(l.getTargetIssueId());
                    if (target != null) {
                        vo.setTargetKey(target.getIssueNumber() == null ? "" : "ISSUE-" + target.getIssueNumber());
                        vo.setTargetTitle(target.getTitle());
                    }
                    return vo;
                }).collect(Collectors.toList());
    }

    @Transactional
    public LinkVO add(UUID issueId, UUID targetIssueId, String relationType) {
        if (issueId.equals(targetIssueId)) {
            throw new ServiceException(400, "不能关联自身");
        }
        Issue target = issueMapper.selectById(targetIssueId);
        if (target == null || target.getDeleted() != null && target.getDeleted() == 1) {
            throw new ServiceException(404, "关联缺陷不存在");
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
        l.setTargetIssueId(targetIssueId);
        l.setRelationType(relationType);
        l.setCreatedAt(LocalDateTime.now());
        linkMapper.insert(l);

        LinkVO vo = new LinkVO();
        BeanUtils.copyProperties(l, vo);
        vo.setTargetKey(target.getIssueNumber() == null ? "" : "ISSUE-" + target.getIssueNumber());
        vo.setTargetTitle(target.getTitle());
        return vo;
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

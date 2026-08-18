package com.seven7m.kanban.issue.tag;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.seven7m.kanban.common.security.UserContext;
import com.seven7m.kanban.issue.dto.LabelVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class IssueTagService {

    private final IssueTagMapper tagMapper;

    /** 项目下所有标签（用于给 issue 打标时选择） */
    public List<LabelVO> listForProject(UUID projectId) {
        return tagMapper.selectByProject(projectId).stream().map(t -> {
            LabelVO vo = new LabelVO();
            vo.setId(t.getId());
            vo.setName(t.getName());
            vo.setColor(t.getColor());
            return vo;
        }).collect(Collectors.toList());
    }

    /** 某个 issue 已关联的标签 */
    public List<LabelVO> listForIssue(UUID issueId) {
        return tagMapper.selectByIssue(issueId).stream().map(t -> {
            LabelVO vo = new LabelVO();
            vo.setId(t.getId());
            vo.setName(t.getName());
            vo.setColor(t.getColor());
            return vo;
        }).collect(Collectors.toList());
    }

    @Transactional
    public void addToIssue(UUID projectId, UUID issueId, String name, String color) {
        IssueTag tag = tagMapper.selectOne(Wrappers.<IssueTag>lambdaQuery()
                .eq(IssueTag::getProjectId, projectId)
                .eq(IssueTag::getName, name));
        if (tag == null) {
            tag = new IssueTag();
            tag.setId(UUID.randomUUID());
            tag.setProjectId(projectId);
            tag.setName(name);
            tag.setColor(color == null ? "#1677FF" : color);
            tag.setCreatedAt(LocalDateTime.now());
            tagMapper.insert(tag);
        }
        if (tagMapper.countIssueLabel(issueId, tag.getId()) > 0) {
            return;
        }
        tagMapper.insertIssueLabel(issueId, tag.getId());
    }

    @Transactional
    public void removeFromIssue(UUID issueId, UUID labelId) {
        tagMapper.deleteIssueLabel(issueId, labelId);
    }
}

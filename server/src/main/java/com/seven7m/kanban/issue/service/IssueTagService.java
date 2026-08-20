package com.seven7m.kanban.issue.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.seven7m.kanban.common.exception.ServiceException;
import com.seven7m.kanban.issue.dto.LabelVO;
import com.seven7m.kanban.issue.entity.IssueTag;
import com.seven7m.kanban.issue.mapper.IssueTagMapper;
import com.seven7m.kanban.user.entity.User;
import com.seven7m.kanban.user.mapper.UserMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class IssueTagService {

    private final IssueTagMapper tagMapper;
    private final UserMapper userMapper;

    public List<LabelVO> list(UUID issueId) {
        return tagMapper.selectByIssueId(issueId).stream().map(t -> {
            LabelVO vo = new LabelVO();
            BeanUtils.copyProperties(t, vo);
            User u = userMapper.selectById(t.getProjectId());
            if (u != null) vo.setCreatorName(u.getName());
            return vo;
        }).collect(Collectors.toList());
    }

    public LabelVO add(UUID issueId, UUID labelId) {
        IssueTag t = tagMapper.selectById(labelId);
        if (t == null) throw new ServiceException(404, "标签不存在");
        int exists = tagMapper.selectCount(Wrappers.<IssueTag>lambdaQuery()
                .eq(IssueTag::getProjectId, t.getProjectId())
                .eq(IssueTag::getIssueId, issueId));
        if (exists > 0) throw new ServiceException(400, "标签已存在");
        t.setIssueId(issueId);
        t.setCreatedAt(LocalDateTime.now());
        tagMapper.insert(t);
        LabelVO vo = new LabelVO();
        BeanUtils.copyProperties(t, vo);
        return vo;
    }

    public void delete(UUID issueId, UUID labelId) {
        tagMapper.delete(Wrappers.<IssueTag>lambdaQuery()
                .eq(IssueTag::getProjectId, issueId)
                .eq(IssueTag::getId, labelId));
    }
}

package com.seven7m.kanban.service.issue.tag;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.seven7m.kanban.common.exception.ServiceException;
import com.seven7m.kanban.dto.issue.LabelVO;
import com.seven7m.kanban.entity.issue.tag.IssueTag;
import com.seven7m.kanban.entity.issue.tag.ProjectLabel;
import com.seven7m.kanban.mapper.issue.tag.IssueTagMapper;
import com.seven7m.kanban.mapper.issue.tag.ProjectLabelMapper;
import com.seven7m.kanban.service.workitem.WorkItemRouter;
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
    private final ProjectLabelMapper projectLabelMapper;
    private final WorkItemRouter workItemRouter;

    /** 类别名（TASK/REQUIREMENT/BUG）；工作项不存在时由 router 统一抛 404 */
    private String categoryOf(UUID issueId) {
        return workItemRouter.categoryNameOrThrow(issueId);
    }

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
        return tagMapper.selectByIssue(issueId, categoryOf(issueId)).stream().map(t -> {
            LabelVO vo = new LabelVO();
            vo.setId(t.getId());
            vo.setName(t.getName());
            vo.setColor(t.getColor());
            return vo;
        }).collect(Collectors.toList());
    }

    @Transactional
    public void addToIssue(UUID projectId, UUID issueId, String name, String color) {
        String category = categoryOf(issueId);
        // 请求体里的 projectId 不能直接信：它决定往哪个项目的标签库写选用关系。
        // 之前不校验会让 A 项目的成员把标签塞进 B 项目（跨项目污染），这里强制工作项属于该项目。
        UUID actualProjectId = workItemRouter.getProjectId(issueId);
        if (projectId == null || !projectId.equals(actualProjectId)) {
            throw new ServiceException(400, "工作项不属于该项目");
        }
        String labelName = name == null ? "" : name.trim();
        if (labelName.isEmpty()) {
            throw new ServiceException(400, "标签名称不能为空");
        }
        if (labelName.length() > 50) {
            throw new ServiceException(400, "标签名称不能超过 50 个字符");
        }
        // 标签全局化：按 name 查全局标签库，不存在则创建
        IssueTag tag = tagMapper.selectOne(Wrappers.<IssueTag>lambdaQuery()
                .eq(IssueTag::getName, labelName));
        if (tag == null) {
            tag = new IssueTag();
            tag.setId(UUID.randomUUID());
            tag.setName(labelName);
            tag.setColor(color == null ? "#1677FF" : color);
            tag.setCreatedAt(LocalDateTime.now());
            tagMapper.insert(tag);
        }
        // 建立项目选用关系，保证 listForProject 可见
        ensureProjectLabel(projectId, tag.getId());
        if (tagMapper.countIssueLabel(issueId, tag.getId(), category) > 0) {
            return;
        }
        tagMapper.insertIssueLabel(issueId, tag.getId(), category);
    }

    private void ensureProjectLabel(UUID projectId, UUID labelId) {
        Long exists = projectLabelMapper.selectCount(Wrappers.<ProjectLabel>lambdaQuery()
                .eq(ProjectLabel::getProjectId, projectId)
                .eq(ProjectLabel::getLabelId, labelId));
        if (exists != null && exists > 0) {
            return;
        }
        ProjectLabel pl = new ProjectLabel();
        pl.setId(UUID.randomUUID());
        pl.setProjectId(projectId);
        pl.setLabelId(labelId);
        pl.setCreatedAt(LocalDateTime.now());
        projectLabelMapper.insert(pl);
    }

    @Transactional
    public void removeFromIssue(UUID issueId, UUID labelId) {
        tagMapper.deleteIssueLabel(issueId, labelId, categoryOf(issueId));
    }

    /** 全局标签库（供标签管理界面选择） */
    public List<LabelVO> listGlobal() {
        return tagMapper.selectList(Wrappers.<IssueTag>lambdaQuery()
                .orderByAsc(IssueTag::getName)).stream().map(t -> {
            LabelVO vo = new LabelVO();
            vo.setId(t.getId());
            vo.setName(t.getName());
            vo.setColor(t.getColor());
            return vo;
        }).collect(Collectors.toList());
    }

    /** 项目选用全局标签（建立 project_label 关系） */
    @Transactional
    public void addProjectLabel(UUID projectId, UUID labelId) {
        ensureProjectLabel(projectId, labelId);
    }

    /** 项目取消选用标签（仅解除选用关系，不删除全局标签） */
    @Transactional
    public void removeProjectLabel(UUID projectId, UUID labelId) {
        projectLabelMapper.delete(Wrappers.<ProjectLabel>lambdaQuery()
                .eq(ProjectLabel::getProjectId, projectId)
                .eq(ProjectLabel::getLabelId, labelId));
    }
}

package com.seven7m.kanban.service.issue.tag.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.seven7m.kanban.common.exception.ErrorCode;
import com.seven7m.kanban.common.exception.ServiceException;
import com.seven7m.kanban.dto.issue.LabelVO;
import com.seven7m.kanban.entity.issue.tag.IssueTag;
import com.seven7m.kanban.entity.issue.tag.ProjectLabel;
import com.seven7m.kanban.mapper.issue.tag.IssueTagMapper;
import com.seven7m.kanban.mapper.issue.tag.ProjectLabelMapper;
import com.seven7m.kanban.service.issue.tag.IssueTagService;
import com.seven7m.kanban.service.workitem.WorkItemRouter;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * 标签服务实现。
 *
 * <p>两张表各走各的 Mapper：{@code tagMapper} 除通用能力外还带 issue_label 关联表的定制 SQL
 * （{@code selectByIssue} / {@code countIssueLabel} / {@code insertIssueLabel}），
 * {@code projectLabelMapper} 只做 project_label 的计数与删除，因此都按构造注入保留。
 */
@Service
@RequiredArgsConstructor
public class IssueTagServiceImpl implements IssueTagService {

    /**
     * 标签名长度上限。
     *
     * <p>与 {@link ErrorCode#LABEL_NAME_TOO_LONG} 的文案「不能超过 50 个字符」一致；
     * 取 50 是因为看板卡片上的标签区只放得下一行，更长的名字会被前端截断成省略号，
     * 反而看不出标签是什么。
     */
    private static final int LABEL_NAME_MAX_LENGTH = 50;

    /** 新建标签未指定颜色时的默认色，与前端主题色（tag 默认蓝）保持一致 */
    private static final String DEFAULT_LABEL_COLOR = "#1677FF";

    private final IssueTagMapper tagMapper;
    private final ProjectLabelMapper projectLabelMapper;
    private final WorkItemRouter workItemRouter;

    /** 类别名（TASK/REQUIREMENT/BUG）；工作项不存在时由 router 统一抛 404 */
    private String categoryOf(UUID issueId) {
        return workItemRouter.categoryNameOrThrow(issueId);
    }

    @Override
    public List<LabelVO> listForProject(UUID projectId) {
        return tagMapper.selectByProject(projectId).stream().map(this::toVO).collect(Collectors.toList());
    }

    @Override
    public List<LabelVO> listForIssue(UUID issueId) {
        return tagMapper.selectByIssue(issueId, categoryOf(issueId)).stream()
                .map(this::toVO).collect(Collectors.toList());
    }

    @Override
    @Transactional
    public void addToIssue(UUID projectId, UUID issueId, String name, String color) {
        String category = categoryOf(issueId);
        // 请求体里的 projectId 不能直接信：它决定往哪个项目的标签库写选用关系。
        // 之前不校验会让 A 项目的成员把标签塞进 B 项目（跨项目污染），这里强制工作项属于该项目。
        UUID actualProjectId = workItemRouter.getProjectId(issueId);
        if (projectId == null || !projectId.equals(actualProjectId)) {
            throw new ServiceException(ErrorCode.WORK_ITEM_PROJECT_MISMATCH);
        }
        String labelName = name == null ? "" : name.trim();
        if (labelName.isEmpty()) {
            throw new ServiceException(ErrorCode.LABEL_NAME_REQUIRED);
        }
        if (labelName.length() > LABEL_NAME_MAX_LENGTH) {
            throw new ServiceException(ErrorCode.LABEL_NAME_TOO_LONG);
        }
        // 标签全局化：按 name 查全局标签库，不存在则创建（label.name 有唯一约束，selectOne 不会多行）
        IssueTag tag = tagMapper.selectOne(Wrappers.<IssueTag>lambdaQuery()
                .eq(IssueTag::getName, labelName));
        if (tag == null) {
            tag = new IssueTag();
            tag.setId(UUID.randomUUID());
            tag.setName(labelName);
            tag.setColor(color == null ? DEFAULT_LABEL_COLOR : color);
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

    @Override
    @Transactional
    public void removeFromIssue(UUID issueId, UUID labelId) {
        tagMapper.deleteIssueLabel(issueId, labelId, categoryOf(issueId));
    }

    @Override
    public List<LabelVO> listGlobal() {
        return tagMapper.selectList(Wrappers.<IssueTag>lambdaQuery()
                .orderByAsc(IssueTag::getName)).stream().map(this::toVO).collect(Collectors.toList());
    }

    @Override
    @Transactional
    public void addProjectLabel(UUID projectId, UUID labelId) {
        ensureProjectLabel(projectId, labelId);
    }

    @Override
    @Transactional
    public void removeProjectLabel(UUID projectId, UUID labelId) {
        projectLabelMapper.delete(Wrappers.<ProjectLabel>lambdaQuery()
                .eq(ProjectLabel::getProjectId, projectId)
                .eq(ProjectLabel::getLabelId, labelId));
    }

    /** 三处列表（项目/工作项/全局）的字段映射完全相同，收成一个方法避免三份复制粘贴 */
    private LabelVO toVO(IssueTag tag) {
        LabelVO vo = new LabelVO();
        vo.setId(tag.getId());
        vo.setName(tag.getName());
        vo.setColor(tag.getColor());
        return vo;
    }
}

package com.seven7m.kanban.service.workitem;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.seven7m.kanban.common.enums.ItemCategory;
import com.seven7m.kanban.entity.workitem.Bug;
import com.seven7m.kanban.mapper.issue.IssueActivityMapper;
import com.seven7m.kanban.mapper.issue.IssueCcMapper;
import com.seven7m.kanban.mapper.issue.IssueParticipantMapper;
import com.seven7m.kanban.mapper.issue.IssueTypeMapper;
import com.seven7m.kanban.mapper.issue.attachment.IssueAttachmentMapper;
import com.seven7m.kanban.mapper.issue.comment.IssueCommentMapper;
import com.seven7m.kanban.mapper.issue.link.IssueLinkMapper;
import com.seven7m.kanban.mapper.issue.tag.IssueTagMapper;
import com.seven7m.kanban.mapper.issuestatus.IssueStatusMapper;
import com.seven7m.kanban.mapper.project.ProjectMapper;
import com.seven7m.kanban.mapper.user.SysUserMapper;
import com.seven7m.kanban.mapper.workitem.BugMapper;
import com.seven7m.kanban.service.workflow.WorkflowService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class BugService extends BaseWorkItemService<Bug> {

    private final BugMapper bugMapper;
    private final IssueTypeMapper issueTypeMapper;
    private final IssueStatusMapper issueStatusMapper;
    private final IssueParticipantMapper issueParticipantMapper;
    private final IssueCcMapper issueCcMapper;
    private final IssueActivityMapper issueActivityMapper;
    private final IssueTagMapper issueTagMapper;
    private final IssueCommentMapper issueCommentMapper;
    private final IssueAttachmentMapper issueAttachmentMapper;
    private final IssueLinkMapper issueLinkMapper;
    private final SysUserMapper sysUserMapper;
    private final ProjectMapper projectMapper;
    private final WorkflowService workflowService;

    @Override
    protected BaseMapper<Bug> mapper() { return bugMapper; }
    @Override
    protected ItemCategory category() { return ItemCategory.BUG; }
    @Override
    protected IssueTypeMapper issueTypeMapper() { return issueTypeMapper; }
    @Override
    protected IssueStatusMapper issueStatusMapper() { return issueStatusMapper; }
    @Override
    protected IssueParticipantMapper issueParticipantMapper() { return issueParticipantMapper; }
    @Override
    protected IssueCcMapper issueCcMapper() { return issueCcMapper; }
    @Override
    protected IssueActivityMapper issueActivityMapper() { return issueActivityMapper; }
    @Override
    protected IssueTagMapper issueTagMapper() { return issueTagMapper; }
    @Override
    protected IssueCommentMapper issueCommentMapper() { return issueCommentMapper; }
    @Override
    protected IssueAttachmentMapper issueAttachmentMapper() { return issueAttachmentMapper; }
    @Override
    protected IssueLinkMapper issueLinkMapper() { return issueLinkMapper; }
    @Override
    protected SysUserMapper sysUserMapper() { return sysUserMapper; }
    @Override
    protected ProjectMapper projectMapper() { return projectMapper; }
    @Override
    protected WorkflowService workflowService() { return workflowService; }
    @Override
    protected String nextIssueNumber() { return bugMapper.nextIssueNumber(); }
    @Override
    protected Bug newEntityInstance() { return new Bug(); }
}

package com.seven7m.kanban.service.workitem;

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
import com.seven7m.kanban.service.notification.NotificationService;
import com.seven7m.kanban.service.workflow.WorkflowService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 三个工作项 Service 共用的依赖集合（任务 / 需求 / 缺陷各表都需要的 Mapper 与 Service）。
 *
 * <p>此前这些依赖在 `TaskService`、`RequirementService`、`BugService` 里各声明一遍
 * （每个类 14 个 `private final` + 15 个 `@Override` 访问器），新增一个依赖要改三处，
 * 漏改一处会在对应类别上炸出 NPE。现在收敛为一个组件，由三个 Service 共享同一批实例。
 */
@Component
@RequiredArgsConstructor
public class WorkItemSharedDeps {

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
    private final NotificationService notificationService;

    public IssueTypeMapper issueTypeMapper() {
        return issueTypeMapper;
    }

    public IssueStatusMapper issueStatusMapper() {
        return issueStatusMapper;
    }

    public IssueParticipantMapper issueParticipantMapper() {
        return issueParticipantMapper;
    }

    public IssueCcMapper issueCcMapper() {
        return issueCcMapper;
    }

    public IssueActivityMapper issueActivityMapper() {
        return issueActivityMapper;
    }

    public IssueTagMapper issueTagMapper() {
        return issueTagMapper;
    }

    public IssueCommentMapper issueCommentMapper() {
        return issueCommentMapper;
    }

    public IssueAttachmentMapper issueAttachmentMapper() {
        return issueAttachmentMapper;
    }

    public IssueLinkMapper issueLinkMapper() {
        return issueLinkMapper;
    }

    public SysUserMapper sysUserMapper() {
        return sysUserMapper;
    }

    public ProjectMapper projectMapper() {
        return projectMapper;
    }

    public WorkflowService workflowService() {
        return workflowService;
    }

    public NotificationService notificationService() {
        return notificationService;
    }
}

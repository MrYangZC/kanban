package com.seven7m.kanban.entity.project;

import com.seven7m.kanban.entity.board.Board;
import com.seven7m.kanban.entity.board.BoardColumn;
import com.seven7m.kanban.mapper.board.BoardColumnMapper;
import com.seven7m.kanban.mapper.board.BoardMapper;
import com.seven7m.kanban.entity.issue.IssueType;
import com.seven7m.kanban.mapper.issue.IssueTypeMapper;
import com.seven7m.kanban.entity.issuestatus.IssueStatus;
import com.seven7m.kanban.mapper.issuestatus.IssueStatusMapper;
import com.seven7m.kanban.entity.workflow.Workflow;
import com.seven7m.kanban.entity.workflow.WorkflowStatus;
import com.seven7m.kanban.entity.workflow.WorkflowTransition;
import com.seven7m.kanban.mapper.workflow.WorkflowMapper;
import com.seven7m.kanban.mapper.workflow.WorkflowStatusMapper;
import com.seven7m.kanban.mapper.workflow.WorkflowTransitionMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * 项目创建即初始化（文档第28节）。
 * 在 ProjectService 的事务内调用，生成：
 *   - 默认 IssueType（Task/Bug/Story/Feature/Improvement）
 *   - 默认 issue_status（TODO/IN_PROGRESS/TESTING/DONE）
 *   - 默认 Workflow + workflow_status 映射 + workflow_transition 流转规则
 *   - 默认 Board + board_column
 * 注意：原文档第28节只给了状态，缺 workflow_transition 数据，此处补全流转规则。
 */
@Component
@RequiredArgsConstructor
public class ProjectInitializer {

    private final IssueTypeMapper issueTypeMapper;
    private final IssueStatusMapper issueStatusMapper;
    private final WorkflowMapper workflowMapper;
    private final WorkflowStatusMapper workflowStatusMapper;
    private final WorkflowTransitionMapper workflowTransitionMapper;
    private final BoardMapper boardMapper;
    private final BoardColumnMapper boardColumnMapper;

    @Transactional
    public void initDefaults(UUID projectId, UUID operator) {
        List<IssueType> types = defaultTypes(projectId);
        for (IssueType t : types) {
            issueTypeMapper.insert(t);
        }

        // 缺陷工作流：6 列缺陷流（code / 中文名 / category / 颜色）
        String[][] statusDefs = {
                {"TO_CONFIRM", "待确认", "TODO", "#8F959E"},
                {"REOPENED", "再次打开", "IN_PROGRESS", "#FA8C16"},
                {"IN_PROGRESS", "处理中", "IN_PROGRESS", "#1677FF"},
                {"FIXED", "已修复", "DONE", "#52C41A"},
                {"WONT_FIX", "暂不修复", "DONE", "#BFBFBF"},
                {"CLOSED", "已关闭", "DONE", "#8C8C8C"},
        };
        List<IssueStatus> statuses = new ArrayList<>();
        for (int i = 0; i < statusDefs.length; i++) {
            String[] d = statusDefs[i];
            IssueStatus s = new IssueStatus();
            s.setId(UUID.randomUUID());
            s.setProjectId(projectId);
            s.setCode(d[0]);
            s.setName(d[1]);
            s.setCategory(d[2]);
            s.setColor(d[3]);
            s.setSortOrder(i);
            s.setCreatedAt(LocalDateTime.now());
            s.setUpdatedAt(LocalDateTime.now());
            statuses.add(s);
        }
        for (IssueStatus s : statuses) {
            issueStatusMapper.insert(s);
        }

        // 默认工作流
        Workflow wf = new Workflow();
        wf.setId(UUID.randomUUID());
        wf.setProjectId(projectId);
        wf.setName("缺陷工作流");
        wf.setDescription("待确认 → 处理中 → 已修复 → 已关闭");
        wf.setIsDefault(true);
        wf.setCreatedAt(LocalDateTime.now());
        wf.setUpdatedAt(LocalDateTime.now());
        workflowMapper.insert(wf);

        // 工作流与状态映射（决定列顺序）
        for (int i = 0; i < statuses.size(); i++) {
            WorkflowStatus ws = new WorkflowStatus();
            ws.setId(UUID.randomUUID());
            ws.setWorkflowId(wf.getId());
            ws.setStatusId(statuses.get(i).getId());
            ws.setSortOrder(i);
            workflowStatusMapper.insert(ws);
        }

        // 流转规则：缺陷流线性 + 回退 + 再次打开/暂不修复分支
        addTransition(wf.getId(), statuses.get(0).getId(), statuses.get(2).getId(), "开始处理");
        addTransition(wf.getId(), statuses.get(2).getId(), statuses.get(0).getId(), "退回待确认");
        addTransition(wf.getId(), statuses.get(2).getId(), statuses.get(3).getId(), "提交修复");
        addTransition(wf.getId(), statuses.get(3).getId(), statuses.get(2).getId(), "修复不通过");
        addTransition(wf.getId(), statuses.get(3).getId(), statuses.get(5).getId(), "验证通过");
        addTransition(wf.getId(), statuses.get(2).getId(), statuses.get(4).getId(), "暂不修复");
        addTransition(wf.getId(), statuses.get(4).getId(), statuses.get(2).getId(), "重新处理");
        addTransition(wf.getId(), statuses.get(1).getId(), statuses.get(2).getId(), "重新处理");
        addTransition(wf.getId(), statuses.get(0).getId(), statuses.get(1).getId(), "再次打开");
        addTransition(wf.getId(), statuses.get(5).getId(), statuses.get(1).getId(), "重新打开");

        // 默认看板 + 列（status 到列的映射）
        Board board = new Board();
        board.setId(UUID.randomUUID());
        board.setProjectId(projectId);
        board.setName("默认看板");
        board.setBoardType("KANBAN");
        board.setCreatedBy(operator);
        board.setCreatedAt(LocalDateTime.now());
        board.setUpdatedAt(LocalDateTime.now());
        boardMapper.insert(board);

        for (int i = 0; i < statuses.size(); i++) {
            IssueStatus st = statuses.get(i);
            BoardColumn col = new BoardColumn();
            col.setId(UUID.randomUUID());
            col.setBoardId(board.getId());
            col.setStatusId(st.getId());
            col.setName(st.getName());
            col.setSortOrder(i);
            col.setCreatedAt(LocalDateTime.now());
            col.setUpdatedAt(LocalDateTime.now());
            boardColumnMapper.insert(col);
        }
    }

    private void addTransition(UUID workflowId, UUID from, UUID to, String name) {
        WorkflowTransition t = new WorkflowTransition();
        t.setId(UUID.randomUUID());
        t.setWorkflowId(workflowId);
        t.setFromStatusId(from);
        t.setToStatusId(to);
        t.setName(name);
        t.setCreatedAt(LocalDateTime.now());
        workflowTransitionMapper.insert(t);
    }

    private List<IssueType> defaultTypes(UUID projectId) {
        String[][] defs = {
                {"TASK", "任务", "📋", "#1677FF"},
                {"BUG", "缺陷", "🐞", "#FF4D4F"},
                {"STORY", "故事", "📖", "#52C41A"},
                {"FEATURE", "特性", "✨", "#722ED1"},
                {"IMPROVEMENT", "改进", "🔧", "#FAAD14"},
                // 缺陷专属子类型（对齐云效缺陷分类）
                {"FUNC_DEFECT", "功能缺陷", "⚙️", "#FF4D4F"},
                {"PERF_DEFECT", "性能缺陷", "🚀", "#FA8C16"},
                {"SEC_DEFECT", "安全漏洞", "🛡️", "#722ED1"},
                {"COMPAT_DEFECT", "兼容性问题", "🔗", "#13C2C2"},
                {"UI_DEFECT", "界面缺陷", "🎨", "#EB2F96"},
        };
        List<IssueType> list = new ArrayList<>();
        for (int i = 0; i < defs.length; i++) {
            String[] d = defs[i];
            IssueType t = new IssueType();
            t.setId(UUID.randomUUID());
            t.setProjectId(projectId);
            t.setCode(d[0]);
            t.setName(d[1]);
            t.setIcon(d[2]);
            t.setColor(d[3]);
            t.setSortOrder(i);
            t.setCreatedAt(LocalDateTime.now());
            list.add(t);
        }
        return list;
    }
}

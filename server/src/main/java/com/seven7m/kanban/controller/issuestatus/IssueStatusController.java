package com.seven7m.kanban.controller.issuestatus;

import com.seven7m.kanban.common.domain.R;
import com.seven7m.kanban.common.enums.ItemCategory;
import com.seven7m.kanban.common.security.Permission;
import com.seven7m.kanban.common.security.annotation.ProjectRoleAnnotation;
import com.seven7m.kanban.entity.issuestatus.IssueStatus;
import com.seven7m.kanban.service.board.BoardService;
import com.seven7m.kanban.service.issuestatus.IssueStatusService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/**
 * 项目状态节点查询（按工作项类别返回该类别工作流的全部节点）。
 *
 * <p>只读，要求 issue:view。任务/需求/缺陷各有自己的节点，不能混用一套。
 */
@RestController
@RequestMapping("/projects")
@RequiredArgsConstructor
public class IssueStatusController {

    private final IssueStatusService issueStatusService;
    private final BoardService boardService;

    /**
     * 项目指定类别工作流的状态列表（供详情页状态下拉、新建默认状态）。
     * category=TASK/REQUIREMENT/BUG，缺省 BUG（兼容旧调用）。
     */
    @ProjectRoleAnnotation(Permission.ISSUE_VIEW)
    @GetMapping("/{projectId}/statuses")
    public R<List<IssueStatus>> projectStatuses(@PathVariable UUID projectId,
                                                @RequestParam(required = false) String category) {
        ItemCategory cat = boardService.resolveCategory(category, ItemCategory.BUG);
        return R.ok(issueStatusService.listByProject(projectId, cat));
    }
}

package com.seven7m.kanban.service.issue.tag;

import com.seven7m.kanban.dto.issue.LabelVO;

import java.util.List;
import java.util.UUID;

/**
 * 标签服务接口：工作项打/取消标签、全局标签库、项目选用标签。
 *
 * <p>这里**不**继承 {@code IService}：本域同时操作 {@code label}（全局标签库）与
 * {@code project_label}（项目选用关系）两张表，还依赖 {@code issue_label} 关联表的定制 SQL，
 * 不是"单实体 + 单 Mapper"的形态，套用通用 CRUD 反而要把多余能力暴露给调用方。
 *
 * <p>实现见 {@code com.seven7m.kanban.service.issue.tag.impl.IssueTagServiceImpl}。
 */
public interface IssueTagService {

    /** 项目下所有标签（用于给 issue 打标时选择） */
    List<LabelVO> listForProject(UUID projectId);

    /** 某个 issue 已关联的标签 */
    List<LabelVO> listForIssue(UUID issueId);

    /** 给工作项打标签：标签不存在时先入全局库，再建立项目选用关系与 issue_label 关联 */
    void addToIssue(UUID projectId, UUID issueId, String name, String color);

    /** 取消工作项的某个标签（仅解除 issue_label 关联） */
    void removeFromIssue(UUID issueId, UUID labelId);

    /** 全局标签库（供标签管理界面选择） */
    List<LabelVO> listGlobal();

    /**
     * 项目选用全局标签（建立 project_label 关系）。
     *
     * <p>重复调用不会重复建立关系；但并发重复提交可能撞唯一约束而抛 400（未做 ON CONFLICT 吞并）。
     */
    void addProjectLabel(UUID projectId, UUID labelId);

    /** 项目取消选用标签（仅解除选用关系，不删除全局标签） */
    void removeProjectLabel(UUID projectId, UUID labelId);
}

package com.seven7m.kanban.dto.issue;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * 工作项关联的展示模型。
 *
 * <p>关联在库里只存一行（source → target），但列表是**双向**读取的：
 * 别人关联到当前工作项时，这里的 {@code issueId} 仍是当前工作项（调用方视角），
 * {@code targetIssueId/targetKey/targetTitle} 指向**另一端**，
 * {@code outbound=false} 表示"这条关联是对方发起的"。
 * 这样前端沿用原有字段就能正确展示与跳转，需要区分方向时再看 {@code outbound}。
 */
@Data
public class LinkVO {

    /** 关联记录 id（删除时用） */
    private UUID id;

    /** 当前工作项 id（调用方视角，恒等于请求里的 issueId） */
    private UUID issueId;

    /** 另一端的工作项 id */
    private UUID targetIssueId;

    /** 关联类型（库里存的中文值：关联 / 阻塞 / 重复 / 修复） */
    private String relationType;

    /** 另一端的工作项编号 */
    private String targetKey;

    /** 另一端的工作项标题 */
    private String targetTitle;

    /**
     * 是否由当前工作项发起。
     *
     * <p>{@code true} = 当前工作项 → 另一端；{@code false} = 另一端 → 当前工作项。
     * 单向类型（阻塞/修复）可以据此把文案渲染成"阻塞/被阻塞"。
     */
    private Boolean outbound;

    private LocalDateTime createdAt;
}

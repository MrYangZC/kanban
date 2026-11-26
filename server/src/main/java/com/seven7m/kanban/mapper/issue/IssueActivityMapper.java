package com.seven7m.kanban.mapper.issue;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.seven7m.kanban.entity.issue.IssueActivity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;
import java.util.UUID;

@Mapper
public interface IssueActivityMapper extends BaseMapper<IssueActivity> {

    /**
     * 取该工作项最近一条「交接 / 撤销交接」动态（撤销交接的可用性判定用）。
     *
     * <p>为什么要下沉成 Mapper：服务层此前用 {@code selectOne(...).last("LIMIT 1")} 把 SQL 片段写进了
     * 条件构造器；去掉片段后又变成「把该工作项全部动态取回内存再取第一条」。
     * 「取最新一条」本来就该由 SQL 的 {@code ORDER BY ... LIMIT 1} 表达。
     *
     * <p>{@code ORDER BY created_at DESC, id DESC}：同一毫秒内写入的两条动态靠 id 兜底，
     * 与 {@code IssueTimelineMapper#latestHandoff}（决定"撤销按钮是否可见"）保持同一取值规则，
     * 否则会出现"按钮亮着、点下去取到另一条"的割裂。
     *
     * @param handedAction  交接动作码（{@code ActivityActions.ASSIGNEE_CHANGED}）
     * @param revokedAction 撤销动作码（{@code ActivityActions.HANDOFF_REVOKED}）
     */
    @Select("""
            SELECT * FROM issue_activity
            WHERE issue_id = #{issueId}
              AND category = #{category}
              AND action IN (#{handedAction}, #{revokedAction})
            ORDER BY created_at DESC, id DESC
            LIMIT 1
            """)
    IssueActivity selectLatestHandoffActivity(@Param("issueId") UUID issueId,
                                              @Param("category") String category,
                                              @Param("handedAction") String handedAction,
                                              @Param("revokedAction") String revokedAction);
}

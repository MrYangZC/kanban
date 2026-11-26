package com.seven7m.kanban.mapper.issue.link;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.seven7m.kanban.entity.issue.link.IssueLink;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;
import java.util.UUID;

@Mapper
public interface IssueLinkMapper extends BaseMapper<IssueLink> {

    /**
     * 双向列出某工作项的关联：既包含它发起的（issue_id = 它），也包含别人关联到它的
     * （target_issue_id = 它）。
     *
     * <p>关联只存一行（{@code issue_id → target_issue_id}），不写反向副本：
     * 反向副本要么需要"类型取反"（阻塞 ↔ 被阻塞）的产品定义，要么会出现两侧类型不一致的脏数据；
     * 而"B 看不到 A 的关联"纯粹是查询少了一半条件。所以双向语义放在读侧解决，
     * 写侧保持一行、删除工作项时两个方向一起清（见 {@code AbstractWorkItemService#deleteRows}）。
     *
     * <p>排序：创建时间倒序；同一时刻的两行用 id 兜底，避免分页/断言抖动。
     */
    @Select("""
            SELECT * FROM issue_relation
            WHERE issue_id = #{issueId} OR target_issue_id = #{issueId}
            ORDER BY created_at DESC, id DESC
            """)
    List<IssueLink> selectByIssueBothDirections(@Param("issueId") UUID issueId);

    /**
     * 两个工作项之间是否已存在某类关联（**任一方向**都算）。
     *
     * <p>写侧只存一行，所以重复判定必须双向查：A→B 已存在时，再从 B 加 B→A 属于同一关系，
     * 不能因为方向不同就放行（否则详情页会出现两条互为镜像的关联）。
     */
    @Select("""
            SELECT COUNT(*) FROM issue_relation
            WHERE relation_type = #{relationType}
              AND (
                    (issue_id = #{sourceId} AND target_issue_id = #{targetId})
                 OR (issue_id = #{targetId} AND target_issue_id = #{sourceId})
                  )
            """)
    long countBetween(@Param("sourceId") UUID sourceId,
                      @Param("targetId") UUID targetId,
                      @Param("relationType") String relationType);
}

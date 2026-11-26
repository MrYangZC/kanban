package com.seven7m.kanban.mapper.issue;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.seven7m.kanban.entity.issue.IssueParticipant;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;
import java.util.UUID;

/**
 * 问题参与者关联 Mapper。批量查询/删除直接使用 BaseMapper + LambdaQueryWrapper。
 */
@Mapper
public interface IssueParticipantMapper extends BaseMapper<IssueParticipant> {

    /**
     * 「共享给我」筛选用：某用户在某类别下参与的全部工作项 id。
     *
     * <p>此前工作项列表在条件构造器里手写
     * {@code id IN (SELECT issue_id FROM issue_participant WHERE user_id = ... AND category = ...)}，
     * 把子查询留在了 Service 层。这里收回 mapper，Service 只负责把结果交给
     * {@code wrapper.in(id, ids)}。
     */
    @Select("""
            SELECT issue_id FROM issue_participant
            WHERE user_id = #{userId} AND category = #{category}
            """)
    List<UUID> selectIssueIdsByUserAndCategory(@Param("userId") UUID userId,
                                               @Param("category") String category);
}

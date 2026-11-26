package com.seven7m.kanban.mapper.issuestatus;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.seven7m.kanban.entity.issuestatus.IssueStatus;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;
import java.util.UUID;

@Mapper
public interface IssueStatusMapper extends BaseMapper<IssueStatus> {

    /**
     * 某阶段（TODO / IN_PROGRESS / DONE）下的全部状态 id。
     *
     * <p>工作项列表按阶段筛选时，此前是在条件构造器里手写
     * {@code status_id IN (SELECT id FROM issue_status WHERE category = ...)}；
     * 子查询属于 SQL，按分层约定放回 mapper，Service 只做
     * {@code wrapper.in(STATUS_ID, ids)}。
     *
     * <p>状态字典是全局的（十几个节点），先把 id 取回来再拼 IN 不会带来额外的往返开销。
     */
    @Select("SELECT id FROM issue_status WHERE category = #{category}")
    List<UUID> selectIdsByCategory(@Param("category") String category);
}

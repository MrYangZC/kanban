package com.seven7m.kanban.service.issue.link;

import com.baomidou.mybatisplus.extension.service.IService;
import com.seven7m.kanban.dto.issue.LinkVO;
import com.seven7m.kanban.entity.issue.link.IssueLink;

import java.util.List;
import java.util.UUID;

/**
 * 工作项关联（issue_relation）服务接口。
 *
 * <p>本质是「单实体 + 单主 Mapper」的增删查：继承 {@link IService} 后实现里
 * 直接复用 {@code getById} / {@code save} / {@code count} / {@code list} / {@code removeById}，
 * 不再重复 {@code linkMapper.xxx} 的样板。关联类型白名单等业务校验仍留在实现里。
 *
 * <p>实现见 {@code com.seven7m.kanban.service.issue.link.impl.IssueLinkServiceImpl}。
 */
public interface IssueLinkService extends IService<IssueLink> {

    /** 某工作项的关联列表（按创建时间倒序，关联项的编号/标题由 router 反查填充） */
    List<LinkVO> list(UUID issueId);

    /** 新增关联：校验自关联、类型白名单、跨项目与重复关联 */
    LinkVO add(UUID issueId, UUID targetIssueId, String relationType);

    /** 删除关联（不属于该工作项的记录直接忽略） */
    void delete(UUID issueId, UUID linkId);
}

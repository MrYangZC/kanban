package com.seven7m.kanban.dto.project;

import lombok.Data;

import java.util.UUID;

/**
 * 可加入项目的用户（尚未成为该项目成员），供「添加成员」按关键字检索。
 * 只暴露展示所需的最小字段，不返回邮箱、启用状态等无关信息。
 */
@Data
public class MemberCandidateVO {
    private UUID id;
    private String username;
    private String displayName;
    private String avatarUrl;
}

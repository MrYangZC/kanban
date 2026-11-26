package com.seven7m.kanban.mapper.project;

import java.util.List;
import java.util.UUID;

/**
 * 项目列表可见性查询的参数对象（{@link ProjectMapper#selectVisible} 用）。
 *
 * <p>为什么用一个 record 而不是一堆散参数：可见性规则由「分类 + 身份 + 关键词 + 排序」四个维度共同决定，
 * 散参数在 Provider 里拼 SQL 时很容易错位；收敛成值对象后，口径由
 * {@code ProjectQueryScope#visibility(...)} 一处产出，Provider 只负责翻译成 SQL。
 *
 * <p>为什么把「分类」拆成 {@code managedOnly}/{@code favoritesOnly} 两个布尔：SQL 里的
 * {@code <if test="...">} 用布尔判断最直白，避免在 XML 属性里做字符串比较（OGNL 的引号转义很容易写错）。
 */
public record ProjectVisibilityQuery(

        /** 项目状态：ACTIVE / ARCHIVED（分类为「已归档」时取 ARCHIVED，其余取 ACTIVE） */
        String status,

        /** 当前用户；超管在 ALL/MANAGED 分支可豁免成员过滤，但收藏分支仍需它 */
        UUID userId,

        /** 是否超管（豁免成员/角色过滤，收藏不豁免——收藏是私人数据，不是权限） */
        boolean superAdmin,

        /** 分类是否为「我管理的」：只看角色含 OWNER/ADMIN/PM 的项目 */
        boolean managedOnly,

        /** 分类是否为「我收藏的」：只看当前用户收藏的项目 */
        boolean favoritesOnly,

        /** 「我管理的」认可的角色码（由 ProjectRole 常量组装，SQL 里用 foreach 绑定） */
        List<String> managedRoleCodes,

        /** 关键词 LIKE 模式（已转义、已带两侧 %）；null 表示不过滤 */
        String keywordPattern,

        /** 排序字段（白名单由 Provider 决定，未知值回落创建时间） */
        String sortField,

        /** 排序方向："asc" 才是升序，其余（含 null）沿用"最新在前"的降序 */
        String sortOrder) {

    /** 排序字段：创建时间（缺省） */
    public static final String SORT_CREATED_AT = "createdAt";
    /** 排序字段：更新时间 */
    public static final String SORT_UPDATED_AT = "updatedAt";
    /** 排序字段：项目名称 */
    public static final String SORT_NAME = "name";
    /** 排序字段：项目 Key */
    public static final String SORT_PROJECT_KEY = "projectKey";
    /** 升序标记 */
    public static final String ORDER_ASC = "asc";
}

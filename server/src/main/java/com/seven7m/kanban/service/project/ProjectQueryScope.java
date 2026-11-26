package com.seven7m.kanban.service.project;

import com.seven7m.kanban.common.util.LikeEscaper;
import com.seven7m.kanban.entity.project.ProjectCategoryKey;
import com.seven7m.kanban.entity.project.ProjectRole;
import com.seven7m.kanban.entity.project.ProjectStatus;
import com.seven7m.kanban.mapper.project.ProjectVisibilityQuery;

import java.util.List;
import java.util.UUID;

/**
 * 项目列表的可见性口径（唯一事实源）：把「分类 + 身份 + 关键词 + 排序」翻译成
 * {@link ProjectVisibilityQuery}，真正的 SQL 在
 * {@code ProjectMapper} / {@code ProjectVisibilitySqlProvider} 里。
 *
 * <p>为什么要有这么一层：可见性规则此前在两个方法里各写一遍（{@code apply("id IN (SELECT ...)")}），
 * 改一次规则要改两处，而两处的疏漏只会在"某个分类看不到项目"这种边缘场景暴露。
 * 现在"哪些角色算管理员、哪个分类看什么状态、超管是否豁免、收藏是否豁免"全部收在这一个类里，
 * 服务层与 SQL 都不再各自持有口径。
 *
 * <p>可见性语义：
 * <ul>
 *   <li>ALL / PARTICIPATED：当前用户是项目成员（超管豁免，直接看全部）；</li>
 *   <li>MANAGED：角色含 OWNER/ADMIN/PM（超管同样豁免）；</li>
 *   <li>FAVORITED：当前用户收藏的项目（<b>超管不豁免</b>：收藏是私人数据，不是权限）；</li>
 *   <li>ARCHIVED：状态为 ARCHIVED，其余分类为 ACTIVE。</li>
 * </ul>
 */
public final class ProjectQueryScope {

    /**
     * "我管理的"分类认可的角色码：与 {@code role_permission} 里持有 {@code project:member} 的角色一致。
     *
     * <p>交给 SQL 做参数绑定，而不是把角色码写死在 Provider 的注解里——口径属于业务定义，
     * 改口径时应该改这里，而不是去 SQL 字符串里翻。
     */
    private static final List<String> MANAGED_ROLE_CODES =
            List.of(ProjectRole.OWNER, ProjectRole.ADMIN, ProjectRole.PM);

    private ProjectQueryScope() {
    }

    /**
     * 构造一次可见性查询的参数。
     *
     * @param category   分类键，null 视为 ALL
     * @param userId     当前用户
     * @param superAdmin 是否超管（豁免成员/角色过滤，收藏不豁免）
     * @param keyword    关键词，null/空白表示不过滤
     * @param sortField  排序字段，null/未知值回落创建时间
     * @param sortOrder  排序方向，仅 {@code asc} 为升序
     */
    public static ProjectVisibilityQuery visibility(ProjectCategoryKey category, UUID userId,
                                                    boolean superAdmin, String keyword,
                                                    String sortField, String sortOrder) {
        ProjectCategoryKey cat = category == null ? ProjectCategoryKey.ALL : category;
        String status = ProjectCategoryKey.ARCHIVED == cat
                ? ProjectStatus.ARCHIVED.name()
                : ProjectStatus.ACTIVE.name();
        return new ProjectVisibilityQuery(
                status,
                userId,
                superAdmin,
                ProjectCategoryKey.MANAGED == cat,
                ProjectCategoryKey.FAVORITED == cat,
                MANAGED_ROLE_CODES,
                keywordPattern(keyword),
                sortField,
                sortOrder);
    }

    /**
     * 名称/项目 Key 模糊匹配用的模式串；关键词为空返回 null（表示不加条件）。
     *
     * <p>必须转义 LIKE 通配符（否则 keyword=_ 会命中全部）。PostgreSQL 的 LIKE 默认转义符就是反斜杠，
     * 因此 {@link LikeEscaper#contains} 转义过的 {@code \%} / {@code \_} 不需要再写 {@code ESCAPE} 子句；
     * 若将来换方言（如关掉反斜杠转义的 MySQL 模式），要回来改这里的约定。
     */
    public static String keywordPattern(String keyword) {
        if (keyword == null || keyword.isBlank()) {
            return null;
        }
        return LikeEscaper.contains(keyword);
    }
}

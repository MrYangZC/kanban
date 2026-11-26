package com.seven7m.kanban.service.project;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.seven7m.kanban.common.util.LikeEscaper;
import com.seven7m.kanban.entity.project.Project;
import com.seven7m.kanban.entity.project.ProjectCategoryKey;
import com.seven7m.kanban.entity.project.ProjectRole;
import com.seven7m.kanban.entity.project.ProjectStatus;
import com.seven7m.kanban.mapper.project.ProjectMapper;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * 项目列表的查询条件构造：可见性（分类 + 成员/角色/收藏）与关键词过滤。
 *
 * <p>抽出来的原因：这段条件此前在 {@code ProjectService.listAll} 与 {@code listByFilter}
 * 各写一遍（超管豁免、MANAGED 的角色子查询、FAVORITED 的收藏子查询、LIKE 转义）。
 * 改一次可见性规则要改两处，而两处的疏漏只会在"某个分类看不到项目"这种边缘场景暴露。
 *
 * <p><b>本类不含 SQL 文本</b>：原先三处 {@code apply("id IN (SELECT ... )")} 已下沉到
 * {@link ProjectMapper}（{@link ProjectMapper#selectMemberProjectIds}、
 * {@link ProjectMapper#selectManagedProjectIds}、{@link ProjectMapper#selectFavoritedProjectIds}），
 * 关键词匹配也改成纯 lambda 的 {@code like}。这里只负责"拼 eq / in / 关键词条件"，
 * 具体的子查询长什么样由 Mapper 决定。
 *
 * <p>可见性从 {@code final class} 改为 {@code public}：实现类搬到了
 * {@code com.seven7m.kanban.service.project.impl}，跨包需要访问；类仍是纯静态工具、
 * 不持有状态，放出来不会扩大可变状态面。
 */
public final class ProjectQueryScope {

    /**
     * "我管理的"分类认可的角色码：与 {@code role_permission} 里持有 project:member 的角色一致。
     *
     * <p>做成常量清单交给 Mapper 的参数绑定，而不是把角色码写死在 SQL 注解里——
     * 口径属于业务定义，改口径时应该在这里改，而不是去 Mapper 的字符串里翻。
     */
    private static final List<String> MANAGED_ROLE_CODES =
            List.of(ProjectRole.OWNER, ProjectRole.ADMIN, ProjectRole.PM);

    private ProjectQueryScope() {
    }

    /**
     * 构造"当前用户在某分类下可见项目"的查询。
     *
     * <ul>
     *   <li>ALL / PARTICIPATED：项目成员（超管豁免，直接看全部）</li>
     *   <li>MANAGED：角色含 OWNER/ADMIN/PM</li>
     *   <li>FAVORITED：当前用户收藏（超管同样按收藏过滤：收藏是私人数据，不是权限）</li>
     *   <li>ARCHIVED：状态为 ARCHIVED，其余分类为 ACTIVE</li>
     * </ul>
     *
     * <p><b>返回 {@link Optional#empty()} 表示"该分类下必然没有任何项目"</b>：成员/管理/收藏的
     * id 集合为空时，条件只剩 {@code id IN ()}，而 PostgreSQL 的 {@code IN ()} 是语法错误。
     * 调用方拿到 empty 必须直接短路返回空结果（空列表 / 空分页），既不要拼空 IN，
     * 也不要用 {@code apply("1 = 0")} 之类绕过去——那等于又把 SQL 文本引回服务层。
     *
     * @param mapper     项目 Mapper，用于取成员/管理/收藏的项目 id 集合
     * @param category   分类键，null 视为 ALL
     * @param userId     当前用户
     * @param superAdmin 是否超管（超管对 ALL/PARTICIPATED/ARCHIVED/MANAGED 不做成员过滤）
     */
    public static Optional<LambdaQueryWrapper<Project>> visibleQuery(
            ProjectMapper mapper, ProjectCategoryKey category, UUID userId, boolean superAdmin) {
        ProjectCategoryKey cat = category == null ? ProjectCategoryKey.ALL : category;
        String status = ProjectCategoryKey.ARCHIVED == cat
                ? ProjectStatus.ARCHIVED.name()
                : ProjectStatus.ACTIVE.name();

        LambdaQueryWrapper<Project> query = Wrappers.<Project>lambdaQuery()
                .eq(Project::getStatus, status);

        switch (cat) {
            case MANAGED -> {
                if (!superAdmin) {
                    List<UUID> ids = managedProjectIds(mapper, userId);
                    if (ids.isEmpty()) {
                        return Optional.empty();
                    }
                    query.in(Project::getId, ids);
                }
            }
            case FAVORITED -> {
                List<UUID> ids = mapper.selectFavoritedProjectIds(userId);
                if (ids.isEmpty()) {
                    return Optional.empty();
                }
                query.in(Project::getId, ids);
            }
            default -> {
                if (!superAdmin) {
                    List<UUID> ids = mapper.selectMemberProjectIds(userId);
                    if (ids.isEmpty()) {
                        return Optional.empty();
                    }
                    query.in(Project::getId, ids);
                }
            }
        }
        return Optional.of(query);
    }

    /**
     * "我管理的"项目 id 集合（角色含 OWNER/ADMIN/PM），供分类计数复用同一份角色口径。
     *
     * <p>返回值可能为空集合，调用方需自行短路（分类计数里空集直接记 0）。
     */
    public static List<UUID> managedProjectIds(ProjectMapper mapper, UUID userId) {
        return mapper.selectManagedProjectIds(userId, MANAGED_ROLE_CODES);
    }

    /**
     * 名称/项目 Key 模糊匹配，必须转义 LIKE 通配符（否则 keyword=_ 会命中全部）。
     *
     * <p>PostgreSQL 的 LIKE 默认转义符就是反斜杠，所以 {@link LikeEscaper#contains} 转义过的
     * {@code \%} / {@code \_} 无需再写 {@code ESCAPE} 子句。
     * {@code like} 还会在参数两侧再补一对 {@code %}，与这里已补的 {@code %} 叠加后
     * 仍是"两侧任意字符"，语义不变（连续的 {@code %} 等价于一个）。
     */
    public static void applyKeyword(LambdaQueryWrapper<Project> query, String keyword) {
        if (keyword == null || keyword.isBlank()) {
            return;
        }
        String pattern = LikeEscaper.contains(keyword);
        query.and(w -> w.like(Project::getName, pattern).or().like(Project::getProjectKey, pattern));
    }
}

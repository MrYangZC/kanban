package com.seven7m.kanban.service.project;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.seven7m.kanban.common.util.LikeEscaper;
import com.seven7m.kanban.entity.project.Project;
import com.seven7m.kanban.entity.project.ProjectCategoryKey;
import com.seven7m.kanban.entity.project.ProjectRole;
import com.seven7m.kanban.entity.project.ProjectStatus;

import java.util.UUID;

/**
 * 项目列表的查询条件构造：可见性（分类 + 成员/角色/收藏）与关键词过滤。
 *
 * <p>抽出来的原因：这段 SQL 条件此前在 {@code ProjectService.listAll} 与 {@code listByFilter}
 * 各写一遍（超管豁免、MANAGED 的角色子查询、FAVORITED 的收藏子查询、LIKE 转义）。
 * 改一次可见性规则要改两处，而两处的疏漏只会在"某个分类看不到项目"这种边缘场景暴露。
 *
 * <p>放在同包内、包可见：它依赖项目实体与枚举，不属于通用工具，但被同包两个方法复用。
 */
final class ProjectQueryScope {

    private ProjectQueryScope() {
    }

    /**
     * 构造"当前用户在某分类下可见项目"的查询。
     *
     * <ul>
     *   <li>ALL / PARTICIPATED：项目成员（超管豁免，直接看全部）</li>
     *   <li>MANAGED：角色含 OWNER/ADMIN/PM</li>
     *   <li>FAVORITED：当前用户收藏</li>
     *   <li>ARCHIVED：状态为 ARCHIVED，其余分类为 ACTIVE</li>
     * </ul>
     */
    static LambdaQueryWrapper<Project> visibleQuery(ProjectCategoryKey category, UUID userId, boolean superAdmin) {
        ProjectCategoryKey cat = category == null ? ProjectCategoryKey.ALL : category;
        String status = ProjectCategoryKey.ARCHIVED == cat
                ? ProjectStatus.ARCHIVED.name()
                : ProjectStatus.ACTIVE.name();

        LambdaQueryWrapper<Project> query = Wrappers.<Project>lambdaQuery()
                .eq(Project::getStatus, status);

        switch (cat) {
            case MANAGED -> {
                if (!superAdmin) {
                    query.apply("id IN (SELECT ur.project_id FROM user_role ur " +
                                    "JOIN role r ON r.id = ur.role_id " +
                                    "WHERE ur.user_id = {0} " +
                                    "AND r.code IN ({1},{2},{3}))",
                            userId, ProjectRole.OWNER, ProjectRole.ADMIN, ProjectRole.PM);
                }
            }
            case FAVORITED -> query.apply(
                    "id IN (SELECT project_id FROM project_favorite WHERE user_id = {0})", userId);
            default -> {
                if (!superAdmin) {
                    query.apply("id IN (SELECT project_id FROM project_member WHERE user_id = {0})", userId);
                }
            }
        }
        return query;
    }

    /** 名称/项目 Key 模糊匹配，必须转义 LIKE 通配符（否则 keyword=_ 会命中全部） */
    static void applyKeyword(LambdaQueryWrapper<Project> query, String keyword) {
        if (keyword == null || keyword.isBlank()) {
            return;
        }
        String pattern = LikeEscaper.contains(keyword);
        query.and(w -> w.apply("(name LIKE {0} ESCAPE '\\' OR project_key LIKE {1} ESCAPE '\\')",
                pattern, pattern));
    }
}

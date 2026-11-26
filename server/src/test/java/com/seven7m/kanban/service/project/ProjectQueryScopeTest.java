package com.seven7m.kanban.service.project;

import com.seven7m.kanban.common.util.LikeEscaper;
import com.seven7m.kanban.entity.project.ProjectCategoryKey;
import com.seven7m.kanban.entity.project.ProjectRole;
import com.seven7m.kanban.entity.project.ProjectStatus;
import com.seven7m.kanban.mapper.project.ProjectVisibilityQuery;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 项目可见性口径的守卫测试。
 *
 * <p>这段逻辑决定「谁能看到哪些项目」，是 project 域唯一有越权风险的查询。重构后
 * 条件是 SQL 里的 EXISTS（{@code ProjectVisibilitySqlProvider}），服务层只负责把
 * 「分类 + 身份 + 关键词 + 排序」翻译成 {@link ProjectVisibilityQuery}——
 * 翻译错一个布尔，就会出现"我的项目看不到"或"看到别人的项目"，但编译与启动都不会报错。
 * 所以这里盯着翻译结果，而不是盯着 SQL 文本。
 */
class ProjectQueryScopeTest {

    private final UUID userId = UUID.randomUUID();

    @Test
    @DisplayName("普通成员的 ALL：状态 ACTIVE、按成员过滤、不带管理/收藏条件")
    void allCategoryFiltersByMembership() {
        ProjectVisibilityQuery query = ProjectQueryScope.visibility(
                ProjectCategoryKey.ALL, userId, false, null, null, null);

        assertThat(query.status()).isEqualTo(ProjectStatus.ACTIVE.name());
        assertThat(query.managedOnly()).isFalse();
        assertThat(query.favoritesOnly()).isFalse();
        assertThat(query.superAdmin()).isFalse();
        assertThat(query.userId()).isEqualTo(userId);
        assertThat(query.keywordPattern()).isNull();
    }

    @Test
    @DisplayName("超管 ALL：带 superAdmin 标记（SQL 据此跳过成员 EXISTS）+ 仍按 ACTIVE 过滤")
    void superAdminFlagsExemptMembership() {
        ProjectVisibilityQuery query = ProjectQueryScope.visibility(
                ProjectCategoryKey.ALL, userId, true, "  ", null, null);

        assertThat(query.superAdmin()).isTrue();
        assertThat(query.status()).isEqualTo(ProjectStatus.ACTIVE.name());
        assertThat(query.managedOnly()).isFalse();
        // 空白关键词等价于不过滤，不能拼出 "%  %" 这种模式
        assertThat(query.keywordPattern()).isNull();
    }

    @Test
    @DisplayName("已归档分类：状态换成 ARCHIVED 且仍按成员过滤")
    void archivedCategoryUsesArchivedStatus() {
        ProjectVisibilityQuery query = ProjectQueryScope.visibility(
                ProjectCategoryKey.ARCHIVED, userId, false, null, null, null);

        assertThat(query.status()).isEqualTo(ProjectStatus.ARCHIVED.name());
        assertThat(query.managedOnly()).isFalse();
        assertThat(query.favoritesOnly()).isFalse();
    }

    @Test
    @DisplayName("我管理的：managedOnly=true，角色码固定 OWNER/ADMIN/PM")
    void managedCategoryUsesRoleCodes() {
        ProjectVisibilityQuery query = ProjectQueryScope.visibility(
                ProjectCategoryKey.MANAGED, userId, false, null, null, null);

        assertThat(query.managedOnly()).isTrue();
        assertThat(query.managedRoleCodes())
                .containsExactly(ProjectRole.OWNER, ProjectRole.ADMIN, ProjectRole.PM);
    }

    @Test
    @DisplayName("我收藏的：favoritesOnly=true（收藏是私人数据，超管也不豁免）")
    void favoritedCategoryIgnoresSuperAdmin() {
        ProjectVisibilityQuery query = ProjectQueryScope.visibility(
                ProjectCategoryKey.FAVORITED, userId, true, null, null, null);

        assertThat(query.favoritesOnly()).isTrue();
        // SQL 里 favoritesOnly 分支不看 superAdmin；这里断言标记位不被分类逻辑覆盖
        assertThat(query.managedOnly()).isFalse();
    }

    @Test
    @DisplayName("分类为 null 时按 ALL 处理（不传 category 的旧客户端行为不变）")
    void nullCategoryFallsBackToAll() {
        ProjectVisibilityQuery query = ProjectQueryScope.visibility(null, userId, false, null, null, null);

        assertThat(query.status()).isEqualTo(ProjectStatus.ACTIVE.name());
        assertThat(query.managedOnly()).isFalse();
        assertThat(query.favoritesOnly()).isFalse();
    }

    @Test
    @DisplayName("关键词里的 LIKE 通配符必须被转义（搜 _ / % 不能退化成匹配全部）")
    void keywordEscapesLikeWildcards() {
        String pattern = ProjectQueryScope.keywordPattern("50%_a");

        assertThat(pattern).isEqualTo("%50\\%\\_a%");
        assertThat(LikeEscaper.contains("100%")).doesNotContain("%100%%");
    }
}

package com.seven7m.kanban.mapper.project;

import java.util.Map;

/**
 * 项目列表可见性 SQL 的 Provider。
 *
 * <p>为什么要用 Provider 而不是把可见性条件拼在条件构造器上：可见性本质是「按成员/角色/收藏做
 * EXISTS 半连接」，而条件构造器要么写 {@code apply("id IN (SELECT ...)")}（SQL 落回服务层），
 * 要么先取 id 集合再拼 {@code IN}（行数多时参数爆炸，且与项目规模无关的全库回捞）。
 * 用一条带 EXISTS 的 SQL 表达，既把 SQL 收在 mapper 层，也让数据库拿到最优执行计划。
 *
 * <p>口径与重构前逐条一致（见 {@code ProjectQueryScope} 的 javadoc）：
 * <ul>
 *   <li>ALL / PARTICIPATED / ARCHIVED：非超管要求是项目成员；</li>
 *   <li>MANAGED：非超管要求角色含 OWNER/ADMIN/PM；</li>
 *   <li>FAVORITED：按当前用户收藏过滤，<b>超管同样过滤</b>（收藏是私人数据，不是权限）；</li>
 *   <li>关键词：名称或 Key 模糊匹配（模式串已在服务层用 LikeEscaper 转义）。</li>
 * </ul>
 */
public class ProjectVisibilitySqlProvider {

    /** 可见性 where 片段；{@code <if>} 由 MyBatis 的 <script> 解析 */
    private static final String FROM_WHERE = """
            FROM project p
            WHERE p.status = #{q.status}
            <if test="q.managedOnly and !q.superAdmin">
              AND EXISTS (
                SELECT 1 FROM user_role ur
                JOIN role r ON r.id = ur.role_id
                WHERE ur.project_id = p.id AND ur.user_id = #{q.userId}
                  AND r.code IN
                  <foreach collection="q.managedRoleCodes" item="roleCode" open="(" separator="," close=")">
                    #{roleCode}
                  </foreach>
              )
            </if>
            <if test="q.favoritesOnly">
              AND EXISTS (
                SELECT 1 FROM project_favorite pf
                WHERE pf.project_id = p.id AND pf.user_id = #{q.userId}
              )
            </if>
            <if test="!q.managedOnly and !q.favoritesOnly and !q.superAdmin">
              AND EXISTS (
                SELECT 1 FROM project_member pm
                WHERE pm.project_id = p.id AND pm.user_id = #{q.userId}
              )
            </if>
            <if test="q.keywordPattern != null">
              AND (p.name LIKE #{q.keywordPattern} OR p.project_key LIKE #{q.keywordPattern})
            </if>
            """;

    /** 分页/非分页共用同一段 SQL：MyBatis-Plus 见到 IPage 参数才会追加 LIMIT/OFFSET */
    public String selectVisible(Map<String, Object> params) {
        ProjectVisibilityQuery query = query(params);
        return "<script>SELECT p.* " + FROM_WHERE + orderBy(query) + "</script>";
    }

    /** 计数：不带 ORDER BY，避免无谓排序 */
    public String countVisible(Map<String, Object> params) {
        return "<script>SELECT COUNT(*) " + FROM_WHERE + "</script>";
    }

    private static ProjectVisibilityQuery query(Map<String, Object> params) {
        Object value = params.get("q");
        if (value instanceof ProjectVisibilityQuery query) {
            return query;
        }
        throw new IllegalArgumentException("缺少查询参数 q（ProjectVisibilityQuery）");
    }

    /**
     * 排序白名单：只有这四个字段能被拼进 SQL，其余一律回落创建时间。
     *
     * <p>列名来自常量、方向只有 ASC/DESC 两种取值，拼接是安全的；
     * 末尾统一补 {@code p.id ASC} 作为稳定兜底，避免同键值行在翻页时抖动。
     */
    private static String orderBy(ProjectVisibilityQuery query) {
        String field = query.sortField() == null ? "" : query.sortField();
        String column = switch (field) {
            case ProjectVisibilityQuery.SORT_UPDATED_AT -> "p.updated_at";
            case ProjectVisibilityQuery.SORT_NAME -> "p.name";
            case ProjectVisibilityQuery.SORT_PROJECT_KEY -> "p.project_key";
            case ProjectVisibilityQuery.SORT_CREATED_AT -> "p.created_at";
            // 未指定或无法识别：沿用"最新在前"
            default -> "p.created_at";
        };
        boolean asc = !field.isBlank()
                && ProjectVisibilityQuery.ORDER_ASC.equalsIgnoreCase(query.sortOrder());
        return " ORDER BY " + column + (asc ? " ASC" : " DESC") + ", p.id ASC";
    }
}

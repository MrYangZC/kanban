package com.seven7m.kanban.mapper.issue;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * 时间线（评论 + 动态 UNION ALL）的一行结果，作为查询投影的 POJO 由 MyBatis 直接映射。
 *
 * <p>为什么是实体类而不是 {@code Map<String, Object>}：
 * 用 Map 承载数据库行时，字段名只是字符串键，列别名写错、类型不符都只能在运行时暴露，
 * 而且业务代码里到处是 {@code row.get("new_value")} 这种魔法值。《阿里巴巴 Java 开发手册》
 * 对此的要求是明确的——POJO 用来承载数据、映射关系显式声明（resultMap/@Results）、
 * 避免魔法值与弱类型容器在业务层流转。
 *
 * <p>列与属性的对应关系写在 {@code IssueTimelineMapper} 的 {@code @Results} 里，
 * 并由 {@code TimelineMapperMappingTest} 静态校验"SQL 里的每个 AS 别名都被映射、
 * 每个映射属性都在本类上存在"，因此别名漂移会在跑测试时暴露，而不是等线上看到空值。
 *
 * <p>类型说明：PG 的 {@code uuid} 列由 MyBatis 的 {@code UUIDTypeHandler} 处理（UUID/字符串都能吃下），
 * {@code timestamptz} 由 {@code LocalDateTimeTypeHandler} 处理，无需在本类里手写转换。
 */
@Data
public class TimelineRow {

    /** 评论或动态的 id */
    private UUID id;

    /** 行类型：评论固定为 COMMENT，动态取 activity.action（STATUS_CHANGED / ASSIGNEE_CHANGED …） */
    private String type;

    /** 操作人（评论作者 / 动态触发者） */
    private UUID actorId;

    /** 评论正文；动态行为 null */
    private String content;

    /** 父评论 id（嵌套回复），动态行为 null */
    private UUID parentId;

    /** 动态旧值，JSON 文本（见 {@code ActivityValue}）；评论行为 null */
    private String oldValue;

    /** 动态新值，JSON 文本；评论行为 null */
    private String newValue;

    private LocalDateTime createdAt;
}

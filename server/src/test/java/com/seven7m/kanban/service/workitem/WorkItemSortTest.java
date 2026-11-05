package com.seven7m.kanban.service.workitem;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.seven7m.kanban.common.enums.ItemCategory;
import com.seven7m.kanban.dto.issue.IssueVO;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 排序单一事实源的守护测试。
 *
 * <p>这组用例存在的原因：排序规则同时用于「单表 SQL」和「跨表内存合并」两条路径，
 * 只要两边不一致，用户在"全部工作项"视图看到的顺序就会和单类型视图不同，
 * 而这种问题只会被"看起来没报错"掩盖。这里固定住内存侧的行为与 SQL 片段的形状。
 */
class WorkItemSortTest {

    private static IssueVO issue(String number, String statusId, String sortKey) {
        IssueVO vo = new IssueVO();
        vo.setId(UUID.randomUUID());
        vo.setIssueNumber(number);
        vo.setStatusId(statusId == null ? null : UUID.nameUUIDFromBytes(statusId.getBytes()));
        vo.setSortKey(sortKey);
        return vo;
    }

    @Test
    @DisplayName("issueKey 按数字自然序（长度优先），1000 不会排在 999 前面")
    void issueKeyUsesNumericOrder() {
        IssueVO a = issue("999", "s1", "a");
        IssueVO b = issue("1000", "s1", "b");
        IssueVO c = issue("12", "s1", "c");

        List<IssueVO> sorted = List.of(a, b, c).stream()
                .sorted(WorkItemSort.comparator("issueKey")).toList();

        assertThat(sorted).extracting(IssueVO::getIssueNumber).containsExactly("12", "999", "1000");
    }

    @Test
    @DisplayName("默认排序 = 状态 + sort_key，且 id 兜底保证分页稳定")
    void defaultOrderIsStatusThenSortKeyThenId() {
        IssueVO first = issue("1", "s1", "a");
        IssueVO second = issue("2", "s1", "b");
        IssueVO third = issue("3", "s2", "a");

        List<IssueVO> sorted = List.of(third, second, first).stream()
                .sorted(WorkItemSort.comparator(null)).toList();

        assertThat(sorted).containsExactly(first, second, third);
    }

    @Test
    @DisplayName("severity 排序：有严重度的在前，其余回落默认排序（与 SQL 的非缺陷表回落一致）")
    void severityFallsBackToDefaultForRowsWithoutSeverity() {
        IssueVO bugHigh = issue("1", "s1", "a");
        bugHigh.setSeverity("HIGH");
        IssueVO bugLow = issue("2", "s1", "b");
        bugLow.setSeverity("LOW");
        IssueVO task = issue("3", "s1", "c");   // 任务没有 severity

        List<IssueVO> sorted = List.of(bugHigh, task, bugLow).stream()
                .sorted(WorkItemSort.comparator("severity")).toList();

        // 字典序 HIGH < LOW，任务因 severity 为空排在最后
        assertThat(sorted).containsExactly(bugHigh, bugLow, task);
    }

    @Test
    @DisplayName("未知排序字段回落到默认排序，不抛异常、不拼进 SQL")
    void unknownFieldFallsBackToDefault() {
        IssueVO a = issue("1", "s1", "b");
        IssueVO b = issue("2", "s1", "a");

        assertThat(List.of(a, b).stream().sorted(WorkItemSort.comparator("drop table x")).toList())
                .containsExactly(b, a);

        QueryWrapper<Object> q = new QueryWrapper<>();
        WorkItemSort.applySql(q, "drop table x", "asc", ItemCategory.TASK);
        assertThat(q.getSqlSegment()).contains("status_id").doesNotContain("drop");
    }

    @Test
    @DisplayName("SQL 片段与内存规则成对：issueKey 用 LENGTH + issue_number，severity 仅缺陷表取该列")
    void sqlFragmentsMatchDeclaredRules() {
        QueryWrapper<Object> byKey = new QueryWrapper<>();
        WorkItemSort.applySql(byKey, "issueKey", "asc", ItemCategory.TASK);
        assertThat(byKey.getSqlSegment()).contains("LENGTH(issue_number)");

        QueryWrapper<Object> bugSeverity = new QueryWrapper<>();
        WorkItemSort.applySql(bugSeverity, "severity", "desc", ItemCategory.BUG);
        assertThat(bugSeverity.getSqlSegment()).contains("severity");

        QueryWrapper<Object> taskSeverity = new QueryWrapper<>();
        WorkItemSort.applySql(taskSeverity, "severity", "desc", ItemCategory.TASK);
        assertThat(taskSeverity.getSqlSegment()).doesNotContain("severity").contains("status_id");

        // 所有分支都要有稳定兜底列
        assertThat(byKey.getSqlSegment()).contains("id");
    }

    @Test
    @DisplayName("降序通过 reversed() 实现：与 Postgres 的 NULLS FIRST/DESC 语义一致")
    void descendingReversesNullPlacement() {
        IssueVO nullValue = issue("1", "s1", "a");
        IssueVO nonNull = issue("2", "s1", "b");
        nonNull.setUpdatedAt(LocalDateTime.now());

        List<IssueVO> asc = List.of(nullValue, nonNull).stream()
                .sorted(WorkItemSort.comparator("updatedAt")).toList();
        assertThat(asc).containsExactly(nonNull, nullValue);

        List<IssueVO> desc = List.of(nullValue, nonNull).stream()
                .sorted(WorkItemSort.comparator("updatedAt").reversed()).toList();
        assertThat(desc).containsExactly(nullValue, nonNull);
    }
}

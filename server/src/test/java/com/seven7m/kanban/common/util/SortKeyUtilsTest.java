package com.seven7m.kanban.common.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * sort_key 生成算法的不变量测试。
 * 这些性质直接决定看板拖拽排序是否稳定，一旦破坏很难从界面上定位。
 */
class SortKeyUtilsTest {

    @Test
    @DisplayName("first 取中间字符，两侧都留有插入空间")
    void firstKeepsRoomOnBothSides() {
        String first = SortKeyUtils.first();
        assertThat(first).isEqualTo("V");

        // 首键之前仍能插入更小的键（拖拽到列首）
        String before = SortKeyUtils.between(null, first);
        assertThat(before).isLessThan(first);

        // 首键之后能继续追加
        assertThat(SortKeyUtils.after(first)).isGreaterThan(first);
    }

    @Test
    @DisplayName("after：空键退化为首位置，非空键严格递增")
    void afterIsStrictlyGreater() {
        assertThat(SortKeyUtils.after(null)).isEqualTo(SortKeyUtils.first());
        assertThat(SortKeyUtils.after("")).isEqualTo(SortKeyUtils.first());

        String key = SortKeyUtils.first();
        for (int i = 0; i < 100; i++) {
            String next = SortKeyUtils.after(key);
            assertThat(next).isGreaterThan(key);
            key = next;
        }
    }

    @Test
    @DisplayName("between：严格大于左侧，前缀相邻时落在两者之间")
    void betweenRespectsBounds() {
        assertThat(SortKeyUtils.between(null, null)).isEqualTo(SortKeyUtils.first());
        assertThat(SortKeyUtils.between("0", null)).isGreaterThan("0");
        assertThat(SortKeyUtils.between("a", "b")).isGreaterThan("a");
        assertThat(SortKeyUtils.between("a", "aV")).isGreaterThan("a").isLessThan("aV");
    }

    @Test
    @DisplayName("反复插到列首不会产生与首位相同的键")
    void repeatedInsertBeforeHeadStaysUnique() {
        String head = SortKeyUtils.first();
        for (int i = 0; i < 20; i++) {
            String newKey = SortKeyUtils.between(null, head);
            assertThat(newKey).as("第 %d 次插入列首", i + 1).isNotEqualTo(head);
            head = newKey;
        }
    }

    @Test
    @DisplayName("退化场景仍返回与左侧不同的键，避免排序并列")
    void degenerateCaseNeverReusesKey() {
        // '0' 是存量数据首键，字典序上没有比它更小的非空串
        assertThat(SortKeyUtils.between(null, "0")).isNotEqualTo("0");
        // a 是 b 的前缀，且 b 的下一位已是最小字符
        assertThat(SortKeyUtils.between("a", "a0")).isNotEqualTo("a");
    }

    @Test
    @DisplayName("拖拽落到相邻卡片之间时列内仍严格有序")
    void insertBetweenNeighboursKeepsOrder() {
        String left = SortKeyUtils.first();
        String right = SortKeyUtils.after(left);
        String inserted = SortKeyUtils.between(left, right);
        assertThat(inserted).isGreaterThan(left).isLessThan(right);
    }

    @Test
    @DisplayName("空键与非法区间不抛异常")
    void handlesEmptyAndReversedRange() {
        assertThat(SortKeyUtils.between(null, "")).isEqualTo(SortKeyUtils.first());
        assertThat(SortKeyUtils.between("", null)).isEqualTo(SortKeyUtils.first());
        // left >= right 属调用方异常：退化为 after(left)，保证键唯一即可
        assertThat(SortKeyUtils.between("b", "a")).isGreaterThan("b");
    }
}

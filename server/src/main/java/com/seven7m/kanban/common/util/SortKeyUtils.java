package com.seven7m.kanban.common.util;

/**
 * Fractional Indexing 工具：用字符串 sort_key 表示有序位置，
 * 在 a、b 之间插入时返回中间值，无需重排整列。
 * 采用 base-62 字典序编码（字符集顺序与 ASCII 一致，可直接用字符串比较排序）。
 *
 * 实现要点：
 * 1) 在某键之后追加时，使用"中间字符"而非最小字符。若追加最小字符 '0'，
 *    会形成 "z" 与 "z0" 这种相邻键，二者之间在编码上已无可用中间值，
 *    导致后续插入只能返回与邻居相同的键（拖拽排序失效）。
 * 2) mid(a, b) 保证返回值严格满足 a < result < b（相邻键等退化场景除外，
 *    此时退化为 after(a) 并至少保证严格大于 a）。
 */
public final class SortKeyUtils {

    private static final String CHARS = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz";
    private static final int BASE = CHARS.length();

    /** 追加时使用的中间字符：为左右两侧都留出继续插入的空间 */
    private static final char MID_CHAR = CHARS.charAt(BASE / 2);

    private SortKeyUtils() {
    }

    /**
     * 列内首位置。
     * 取中间字符而非最小字符 '0'：字典序上不存在比 '0' 更小的非空串，若首键为 '0'，
     * 则 {@link #between}(null, 首键) 无法产生更小的键，"拖拽到列首"会退化为与首位并列。
     */
    public static String first() {
        return String.valueOf(MID_CHAR);
    }

    /**
     * 返回严格大于 key 的下一个键；key 为空时返回首位置。
     * 用于把工作项追加到某一列末尾。
     */
    public static String after(String key) {
        if (key == null || key.isEmpty()) {
            return first();
        }
        return key + MID_CHAR;
    }

    /**
     * 在 left 与 right 之间生成一个中间位置。
     * 若 left 为 null，返回 right 之前的位置；若 right 为 null，返回 left 之后的位置。
     */
    public static String between(String left, String right) {
        if (left == null) {
            return right == null ? first() : decrement(right);
        }
        if (right == null) {
            return after(left);
        }
        return mid(left, right);
    }

    /**
     * 返回严格小于 key 的前一个位置。
     * 多字符时直接截断末位（前缀必然小于原串）；单字符时取更小的字符。
     */
    private static String decrement(String key) {
        if (key == null || key.isEmpty()) {
            return first();
        }
        if (key.length() > 1) {
            return key.substring(0, key.length() - 1);
        }
        int idx = CHARS.indexOf(key.charAt(0));
        if (idx > 0) {
            return String.valueOf(CHARS.charAt(idx - 1));
        }
        // key 已是最小字符（存量数据首键 '0'）：字典序上不存在更小的非空串，
        // 退化为 after(key)。宁可位置不精确，也不能返回与 key 相同的值——
        // 后者会让两张卡片 sort_key 并列，Postgres 返回顺序不确定。
        return after(key);
    }

    /**
     * 求 a 与 b 之间的字符串，保证 a &lt; result &lt; b。
     *
     * 设 i 为 a、b 首个不同字符的下标（若 a 是 b 的前缀则 i = a.length()）：
     * - i &lt; a.length()：a[i] &lt; b[i]，直接返回 a 的"后继" a+MID_CHAR。
     *   它与 a 在 [0, i] 完全一致且更长，故严格大于 a；而在下标 i 处
     *   result[i] = a[i] &lt; b[i]，故严格小于 b。
     * - i == a.length()（a 是 b 的前缀）：取 a + (b[i] 的前一个字符)，
     *   同样满足严格大于 a 且严格小于 b；若 b[i] 已是最小字符则无中间值可取，
     *   退化为 after(a)。
     */
    private static String mid(String a, String b) {
        if (a == null || a.isEmpty()) {
            return b == null || b.isEmpty() ? first() : decrement(b);
        }
        if (b == null || b.isEmpty() || a.compareTo(b) >= 0) {
            return after(a);
        }

        int i = 0;
        while (i < a.length() && i < b.length() && a.charAt(i) == b.charAt(i)) {
            i++;
        }

        if (i < a.length()) {
            // 首个不同字符落在 a 内部：a[i] < b[i]，a 的后继必然落在两者之间
            return after(a);
        }

        // a 是 b 的前缀：在 a 后补一个小于 b[i] 的字符
        int idx = CHARS.indexOf(b.charAt(i));
        if (idx > 0) {
            return a + CHARS.charAt(idx - 1);
        }
        // b[i] 已是最小字符，a 与 b 相邻，无中间值可取
        return after(a);
    }

    // ======================== 列内重平衡 ========================

    /**
     * 为 count 条记录选择重排后的键宽（等宽，保证字典序 == 数值序）。
     *
     * <p>为什么需要重平衡：{@link #after} 是"在原键后追加一个字符"，反复拖到列尾会让键
     * 每次长 1 个字符——既能撑破 varchar(255)，也会在列首反复插入时把 a、b 挤成相邻键，
     * 此时 {@link #between} 只能退化，出现"拖到 A 左边却落到 A 右边"的错序。
     * 定期把整列改写成等宽键即可恢复大量可用间隔。
     */
    public static int widthForCount(int count) {
        int width = 1;
        long capacity = capacityOf(width);
        while (count > capacity) {
            width++;
            capacity = capacityOf(width);
        }
        return width;
    }

    /** 宽度为 width 时可容纳的键数量：首字符从 '1' 开始，为"插到最前"预留 '0' 前缀 */
    private static long capacityOf(int width) {
        long total = pow(BASE, width);
        return total - pow(BASE, width - 1);
    }

    private static long pow(int base, int exp) {
        long result = 1;
        for (int i = 0; i < exp; i++) {
            result *= base;
        }
        return result;
    }

    /** 第 index 个等宽键（0 起）；同一 width 下字典序严格递增 */
    public static String keyForIndex(int index, int width) {
        if (index < 0) {
            throw new IllegalArgumentException("index must be >= 0");
        }
        if (width < 1) {
            throw new IllegalArgumentException("width must be >= 1");
        }
        // 偏移一个"首位"的量级，使所有键的首字符 >= '1'，从而 before(first) 仍有空间
        long value = pow(BASE, width - 1) + index;
        char[] buf = new char[width];
        for (int i = width - 1; i >= 0; i--) {
            buf[i] = CHARS.charAt((int) (value % BASE));
            value /= BASE;
        }
        if (value > 0) {
            throw new IllegalArgumentException("index out of range for width " + width);
        }
        return new String(buf);
    }

    /** 重排整列用的等宽键序列（count 个，字典序递增） */
    public static java.util.List<String> rebalancedKeys(int count) {
        int width = widthForCount(count);
        java.util.List<String> keys = new java.util.ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            keys.add(keyForIndex(i, width));
        }
        return keys;
    }
}

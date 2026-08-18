package com.seven7m.kanban.common.util;

/**
 * Fractional Indexing 工具：用字符串 sort_key 表示有序位置，
 * 在 a、b 之间插入时返回中间值，无需重排整列。
 * 采用 base-64 字典序编码（短字符串，避免大数精度问题）。
 */
public final class SortKeyUtils {

    private static final String CHARS = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz";
    private static final int BASE = CHARS.length();

    private SortKeyUtils() {
    }

    /** 首位置 */
    public static String first() {
        return "0";
    }

    /** 末尾位置 */
    public static String last() {
        return "z";
    }

    /**
     * 在 left 与 right 之间生成一个中间位置。
     * 若 left 为 null，返回首位置；若 right 为 null，返回末尾位置。
     */
    public static String between(String left, String right) {
        if (left == null) {
            return right == null ? first() : decrement(right);
        }
        if (right == null) {
            return increment(left);
        }
        return mid(left, right);
    }

    private static String increment(String key) {
        return key + "0";
    }

    private static String decrement(String key) {
        if (key.isEmpty()) {
            return "0";
        }
        return key.substring(0, key.length() - 1);
    }

    private static String mid(String a, String b) {
        int maxLen = Math.max(a.length(), b.length());
        StringBuilder result = new StringBuilder();
        int carry = 0;
        for (int i = 0; i < maxLen; i++) {
            int va = i < a.length() ? CHARS.indexOf(a.charAt(i)) : 0;
            int vb = i < b.length() ? CHARS.indexOf(b.charAt(i)) : BASE - 1;
            int sum = va + vb + carry;
            int digit = sum / 2;
            carry = sum % 2;
            result.append(CHARS.charAt(digit));
            if (digit > va) {
                carry = 0;
                break;
            }
        }
        if (result.length() == 0) {
            result.append("0");
        }
        return result.toString();
    }
}

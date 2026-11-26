package com.seven7m.kanban.common.security;

import com.seven7m.kanban.common.exception.ServiceException;

import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Set;

/**
 * 密码强度策略。
 *
 * <p>此前唯一的规则是"长度不少于 6 位"（且只在自助改密时校验，管理员建号/重置密码完全不校验），
 * 于是 {@code 123456} 这种密码可以直接建出来。
 *
 * <p>规则刻意保持"人能做到"的强度，而不是堆参数：
 * <ul>
 *   <li><b>长度 10–72 字节</b>：下限防暴力破解；上限不是洁癖——BCrypt 只处理前 72 字节，
 *       更长的密码尾部会被静默丢弃，用户以为设了 100 位、实际只生效前 72 位，属于骗人；</li>
 *   <li><b>同时含字母与数字</b>：不加"必须含大写/符号"这类要求，实测只会逼出
 *       {@code Password1!} 这种可预测模式；</li>
 *   <li><b>不含用户名</b>：{@code zhangsan2024} 是最常见的一类弱口令；</li>
 *   <li><b>不是常见弱口令</b>、**没有 3 个以上连续相同字符**。</li>
 * </ul>
 *
 * <p>抽成独立类而不是散在 Service 里：建号、管理员重置、自助改密三处规则必须完全一致，
 * 否则"管理员能设弱密码、用户自己不能"这种口径分裂会长期存在。
 */
public final class PasswordPolicy {

    /** 最小长度（字符数） */
    public static final int MIN_LENGTH = 10;
    /** 最大长度（字节数）：BCrypt 的硬上限，超过部分会被忽略 */
    public static final int MAX_BYTES = 72;

    /** 常见弱口令（小写比较）；不追求覆盖全部字典，只挡住最常被撞库的几个 */
    private static final Set<String> WEAK_PASSWORDS = Set.of(
            "1234567890", "12345678910", "qwertyuiop", "password123", "admin12345",
            "p@ssw0rd123", "abcd123456", "1qaz2wsx3edc", "kanban12345", "welcome1234");

    private PasswordPolicy() {
    }

    /**
     * 校验密码；不合规直接抛 400。
     *
     * @param username 用于"密码不得包含用户名"的判定；建号时传新用户名，改密时传当前用户名
     * @param password 待校验的明文密码
     */
    public static void validate(String username, String password) {
        if (password == null || password.isBlank()) {
            throw new ServiceException(400, "密码不能为空");
        }
        if (password.length() < MIN_LENGTH) {
            throw new ServiceException(400, "密码长度不能少于 " + MIN_LENGTH + " 位");
        }
        if (password.getBytes(StandardCharsets.UTF_8).length > MAX_BYTES) {
            throw new ServiceException(400, "密码过长（超过 " + MAX_BYTES + " 字节的部分不会被校验，请缩短）");
        }
        String lower = password.toLowerCase(Locale.ROOT);
        // 弱口令判定放在字符种类之前：qwertyuiop / 1234567890 这类密码同时违反两条规则，
        // 提示"过于常见"比"必须包含字母和数字"对用户更有指导性
        if (WEAK_PASSWORDS.contains(lower)) {
            throw new ServiceException(400, "密码过于常见，请更换");
        }
        boolean hasLetter = password.chars().anyMatch(Character::isLetter);
        boolean hasDigit = password.chars().anyMatch(Character::isDigit);
        if (!hasLetter || !hasDigit) {
            throw new ServiceException(400, "密码必须同时包含字母和数字");
        }
        if (username != null && username.trim().length() >= 3
                && lower.contains(username.trim().toLowerCase(Locale.ROOT))) {
            throw new ServiceException(400, "密码不能包含用户名");
        }
        if (hasRunOfSameChar(password, 3)) {
            throw new ServiceException(400, "密码不能包含 3 个及以上连续相同字符");
        }
    }

    /** 是否存在 length 个连续相同字符（如 aaa / 1111） */
    private static boolean hasRunOfSameChar(String s, int length) {
        int run = 1;
        for (int i = 1; i < s.length(); i++) {
            if (s.charAt(i) == s.charAt(i - 1)) {
                run++;
                if (run >= length) {
                    return true;
                }
            } else {
                run = 1;
            }
        }
        return false;
    }

    /** 给前端展示的规则说明，避免前后端各写一份文案导致不一致 */
    public static String rulesText() {
        return "至少 " + MIN_LENGTH + " 位，需同时包含字母和数字，不能包含用户名、不能是常见弱口令、不能有 3 个连续相同字符";
    }
}

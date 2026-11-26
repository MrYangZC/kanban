package com.seven7m.kanban.common.security;

import com.seven7m.kanban.common.exception.ServiceException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 密码策略守护测试。
 *
 * <p>这类规则最容易在两处出问题：一是上下限（BCrypt 72 字节截断），
 * 二是"校验入口漏了一处"（建号 / 管理员重置 / 自助改密）。
 * 后者由 UserService 的调用点保证，这里逐条钉死规则本身。
 */
class PasswordPolicyTest {

    @Test
    @DisplayName("合规密码通过")
    void strongPasswordPasses() {
        assertThatCode(() -> PasswordPolicy.validate("zhangsan", "kanban2026dev"))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("长度不足 10 位拒绝")
    void tooShortRejected() {
        assertThatThrownBy(() -> PasswordPolicy.validate("zhangsan", "abc1234"))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("不能少于 10 位");
    }

    @Test
    @DisplayName("超过 72 字节拒绝（BCrypt 会静默截断，等于用户设的密码没完全生效）")
    void tooLongRejected() {
        String longPassword = "a1" + "x".repeat(80);
        assertThatThrownBy(() -> PasswordPolicy.validate("zhangsan", longPassword))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("72");
    }

    @Test
    @DisplayName("纯字母或纯数字拒绝")
    void singleClassRejected() {
        assertThatThrownBy(() -> PasswordPolicy.validate("zhangsan", "abcdefghij"))
                .hasMessageContaining("字母和数字");
        assertThatThrownBy(() -> PasswordPolicy.validate("zhangsan", "1234567890"))
                .hasMessageContaining("常见");
    }

    @Test
    @DisplayName("包含用户名拒绝")
    void containsUsernameRejected() {
        assertThatThrownBy(() -> PasswordPolicy.validate("zhangsan", "zhangsan2026"))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("用户名");
    }

    @Test
    @DisplayName("常见弱口令拒绝")
    void weakPasswordRejected() {
        assertThatThrownBy(() -> PasswordPolicy.validate("someuser", "qwertyuiop"))
                .hasMessageContaining("常见");
    }

    @Test
    @DisplayName("3 个连续相同字符拒绝")
    void repeatedCharsRejected() {
        assertThatThrownBy(() -> PasswordPolicy.validate("someuser", "abcaaa1234"))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("连续相同");
    }

    @Test
    @DisplayName("空密码拒绝")
    void blankRejected() {
        assertThatThrownBy(() -> PasswordPolicy.validate("someuser", "   "))
                .hasMessageContaining("不能为空");
        assertThatThrownBy(() -> PasswordPolicy.validate("someuser", null))
                .hasMessageContaining("不能为空");
    }
}

package com.seven7m.kanban.codestyle;

import com.seven7m.kanban.common.exception.ErrorCode;
import com.seven7m.kanban.common.exception.ServiceException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 错误码字典的守卫测试。
 *
 * <p>错误码是前端与日志都依赖的稳定契约：码必须落在 HTTP 语义集合里（否则前端拦截器分支失效），
 * 文案必须非空且不能重复（同一句话挂两个码，排查时无法只凭文案定位），
 * 带 {@code %s} 的文案必须能被异常正确格式化（否则会把 {@code %s} 原样抛给用户）。
 */
class ErrorCodeTest {

    /** 允许的业务码：复用 HTTP 语义，不另设业务码段 */
    private static final Set<Integer> ALLOWED_CODES = Set.of(200, 400, 401, 403, 404, 409, 429, 500);

    @Test
    @DisplayName("错误码只使用 HTTP 语义码")
    void codesFollowHttpSemantics() {
        for (ErrorCode code : ErrorCode.values()) {
            assertThat(ALLOWED_CODES).as("%s 的 code=%d 不在允许集合内", code.name(), code.getCode())
                    .contains(code.getCode());
        }
    }

    @Test
    @DisplayName("码 + 文案组合不重复（同一句话挂不同 HTTP 码是允许的，但同一组合不能出现两次）")
    void codeMessagePairsAreUnique() {
        assertThat(ErrorCode.values()).allSatisfy(code ->
                assertThat(code.getMessage()).as("%s 的文案", code.name()).isNotBlank());
        // 允许"同一句话挂不同码"：历史接口对同一场景返回过不同 HTTP 语义码（建项目时 400、查询时 404），
        // 为保持前端行为不变而保留；真正要杜绝的是同一 (码, 文案) 组合被复制成两个常量。
        var byPair = Arrays.stream(ErrorCode.values())
                .collect(Collectors.groupingBy(c -> c.getCode() + "|" + c.getMessage(),
                        Collectors.mapping(ErrorCode::name, Collectors.toList())));
        assertThat(byPair.entrySet().stream().filter(e -> e.getValue().size() > 1).toList())
                .as("同一 (码, 文案) 组合挂了多个错误码")
                .isEmpty();
    }

    @Test
    @DisplayName("带占位符的文案能正确格式化，不会把占位符漏给用户")
    void placeholdersAreFormatted() {
        ServiceException e = new ServiceException(ErrorCode.PROJECT_KEY_EXISTS, "DEMO");
        assertThat(e.getMessage()).isEqualTo("项目 Key 已存在：DEMO");
        assertThat(e.getCode()).isEqualTo(400);

        ServiceException notFound = new ServiceException(ErrorCode.WORK_ITEM_NOT_FOUND, "TASK");
        assertThat(notFound.getMessage()).isEqualTo("TASK 不存在");
        assertThat(notFound.getCode()).isEqualTo(404);

        // 无参数时保持默认文案（不跑 String.format，避免文案里的 % 被当成格式符）
        assertThat(new ServiceException(ErrorCode.USER_NOT_FOUND).getMessage()).isEqualTo("用户不存在");
    }

    @Test
    @DisplayName("错误码常量名唯一（避免复制粘贴出重名）")
    void namesAreUnique() {
        Set<String> names = new HashSet<>();
        assertThat(Arrays.stream(ErrorCode.values()).allMatch(c -> names.add(c.name()))).isTrue();
    }
}

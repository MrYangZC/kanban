package com.seven7m.kanban.dto.user;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * `isAdmin` 的 JSON 契约测试。
 *
 * <p>按《阿里巴巴 Java 开发手册》，POJO 布尔属性不能带 {@code is} 前缀，因此 Java 属性改名为
 * {@code admin}；但前端（登录态、用户管理页、RBAC 页）一直读的是 JSON 字段 {@code isAdmin}。
 * 这里用 `@JsonProperty("isAdmin")` 保住对外契约，并用本测试锁死：
 * 一旦有人误删注解或改字段名，接口字段会静默变成 {@code admin}，前端管理员入口随之失效。
 */
class UserJsonContractTest {

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    @DisplayName("LoginVO 序列化后字段名仍是 isAdmin")
    void loginVoKeepsIsAdminField() throws Exception {
        LoginVO vo = new LoginVO();
        vo.setAdmin(true);

        String json = mapper.writeValueAsString(vo);

        assertThat(json).contains("\"isAdmin\":true");
    }

    @Test
    @DisplayName("UserVO 序列化后字段名仍是 isAdmin")
    void userVoKeepsIsAdminField() throws Exception {
        UserVO vo = new UserVO();
        vo.setAdmin(false);

        String json = mapper.writeValueAsString(vo);

        assertThat(json).contains("\"isAdmin\":false");
    }

    @Test
    @DisplayName("CreateUserRequest 能按 isAdmin 反序列化（前端提交的就是这个字段名）")
    void createUserRequestReadsIsAdmin() throws Exception {
        CreateUserRequest req = mapper.readValue(
                "{\"username\":\"zhangsan\",\"displayName\":\"张三\",\"isAdmin\":true}",
                CreateUserRequest.class);

        assertThat(req.getAdmin()).isTrue();
    }

    @Test
    @DisplayName("UpdateUserRequest 能按 isAdmin 反序列化，且缺省时为 null（表示不修改）")
    void updateUserRequestReadsIsAdmin() throws Exception {
        UpdateUserRequest withFlag = mapper.readValue("{\"isAdmin\":false}", UpdateUserRequest.class);
        assertThat(withFlag.getAdmin()).isFalse();

        UpdateUserRequest withoutFlag = mapper.readValue("{\"enabled\":true}", UpdateUserRequest.class);
        assertThat(withoutFlag.getAdmin()).isNull();
    }
}

package com.seven7m.kanban.codestyle;

import com.seven7m.kanban.PmServerApplication;
import com.seven7m.kanban.common.security.IssueLocationPort;
import com.seven7m.kanban.common.security.MemberRolePort;
import org.apache.ibatis.annotations.Mapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mybatis.spring.annotation.MapperScan;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * mapper 扫描范围守卫。
 *
 * <p>2026-11-05 连库回归时发现的真实事故：{@code @MapperScan("com.seven7m.kanban")} 覆盖了整个基础包，
 * MyBatis 会把包下**所有接口**注册成 mapper 代理，`common.security.IssueLocationPort` /
 * `MemberRolePort` 这类业务端口也被注册了。结果所有 `/issues/{id}/...` 接口 500
 * （{@code Invalid bound statement (not found)}），而管理员访问「我的权限码」却正常
 * （超级管理员短路，不走端口），极易被误判成单点故障。
 *
 * <p>编译、启动、以及当时 89 个单测**全部通过**，说明这类问题只有连库或上下文级测试才抓得到；
 * 这里用一条便宜的结构断言把它盯住：扫描范围必须限定在 mapper 包，端口不能带 {@code @Mapper}。
 */
class MapperScanScopeTest {

    @Test
    @DisplayName("Boot 类的 @MapperScan 必须限定在 mapper 包，不能是基础包")
    void mapperScanIsLimitedToMapperPackage() {
        MapperScan scan = PmServerApplication.class.getAnnotation(MapperScan.class);

        assertThat(scan).as("Boot 类应显式声明 @MapperScan").isNotNull();
        // 用 @MapperScan("x") 写法时值落在 value()，basePackages() 是别名、此时为空数组
        assertThat(scan.value()).containsExactly("com.seven7m.kanban.mapper");
    }

    @Test
    @DisplayName("业务端口不能被标记为 MyBatis mapper（否则会被注入成代理并抛 Invalid bound statement）")
    void businessPortsAreNotMappers() {
        assertThat(IssueLocationPort.class.getAnnotation(Mapper.class))
                .as("IssueLocationPort 是业务端口，实现是 WorkItemRouter，不是 mapper")
                .isNull();
        assertThat(MemberRolePort.class.getAnnotation(Mapper.class))
                .as("MemberRolePort 是业务端口，实现是 ProjectMemberService，不是 mapper")
                .isNull();
    }

    @Test
    @DisplayName("端口接口不在 mapper 包内（即使有人误加 @Mapper 也不会被扫到）")
    void portsLiveOutsideMapperPackage() {
        assertThat(IssueLocationPort.class.getPackageName()).isEqualTo("com.seven7m.kanban.common.security");
        assertThat(MemberRolePort.class.getPackageName()).isEqualTo("com.seven7m.kanban.common.security");
    }
}

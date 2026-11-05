package com.seven7m.kanban;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * 应用入口。
 *
 * <p>{@code @MapperScan} 必须限定在 {@code com.seven7m.kanban.mapper} 包内，不能写成整个基础包：
 * 扫描整个基础包时，MyBatis 会把包下**所有接口**都注册成 mapper 代理，包括
 * {@code common.security.IssueLocationPort} / {@code MemberRolePort} 这类业务端口。
 * 一旦这些端口被注入成 mapper 代理，调用时就会抛
 * {@code Invalid bound statement (not found)} —— 2026-11-05 连库回归时就是这样炸的：
 * 所有 {@code /issues/{id}/...} 接口 500，而管理员访问「我的权限码」却正常
 * （超级管理员短路，不走 {@code memberRolePort}），很容易被误判成单点故障。
 *
 * <p>所有 mapper 接口都在 {@code mapper} 包下（并带 {@code @Mapper}），因此限定包名不会漏扫。
 */
@SpringBootApplication
@MapperScan("com.seven7m.kanban.mapper")
public class PmServerApplication {

    public static void main(String[] args) {
        SpringApplication.run(PmServerApplication.class, args);
    }
}

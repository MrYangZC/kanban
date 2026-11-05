package com.seven7m.kanban;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * 应用入口。
 *
 * <p>{@code @MapperScan} 覆盖整个基础包而不是只扫 {@code mapper} 子包：MyBatis-Plus 的
 * BaseMapper 子接口分布在不同业务域的 mapper 目录下，逐个列举容易漏；
 * 扫描范围收敛在 {@code com.seven7m.kanban} 内，不会波及第三方包。
 */
@SpringBootApplication
@MapperScan("com.seven7m.kanban")
public class PmServerApplication {

    public static void main(String[] args) {
        SpringApplication.run(PmServerApplication.class, args);
    }
}

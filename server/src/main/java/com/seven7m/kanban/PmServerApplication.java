package com.seven7m.kanban;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
@MapperScan("com.seven7m.kanban")
public class PmServerApplication {

    public static void main(String[] args) {
        SpringApplication.run(PmServerApplication.class, args);
    }
}

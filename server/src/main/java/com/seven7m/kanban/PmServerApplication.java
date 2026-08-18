package com.seven7m.kanban;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
@MapperScan({
        "com.seven7m.kanban.issue.mapper",
        "com.seven7m.kanban.issue.tag",
        "com.seven7m.kanban.issue.comment",
        "com.seven7m.kanban.issue.attachment",
        "com.seven7m.kanban.issue.link",
        "com.seven7m.kanban.workflow.mapper",
        "com.seven7m.kanban.board.mapper",
        "com.seven7m.kanban.user.mapper",
        "com.seven7m.kanban.issuestatus.mapper",
        "com.seven7m.kanban.project.mapper",
        "com.seven7m.kanban.workspace.mapper",
        "com.seven7m.kanban.dashboard.mapper"
})
public class PmServerApplication {

    public static void main(String[] args) {
        SpringApplication.run(PmServerApplication.class, args);
    }
}

package com.seven7m.kanban.codestyle;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 源码风格守卫（对应《阿里巴巴 Java 开发手册》的编码规约）。
 *
 * <p>这几条规则 p3c 也能查，但 p3c 只在 CI 的 quality profile 里跑、而且当前处于"报告不阻断"阶段；
 * 把它们放进普通单测，本地 `mvn -o test` 就能挡住回归，不依赖网络与额外插件。
 *
 * <p>约定：检查范围只有 {@code src/main/java}（测试代码不受排版约束），
 * 且规则只挑"零误报"的几条，避免为了风格噪音反复调测试。
 */
class SourceStyleTest {

    /** 手册：单行字符数不得超过 120 */
    private static final int MAX_LINE_LENGTH = 120;

    private static final Path MAIN_SOURCES = Paths.get("src", "main", "java");

    private static List<Path> javaFiles() throws IOException {
        assertThat(MAIN_SOURCES).as("测试需在 server 模块目录下运行（Maven surefire 的默认工作目录）").exists();
        try (Stream<Path> stream = Files.walk(MAIN_SOURCES)) {
            return stream.filter(p -> p.toString().endsWith(".java")).toList();
        }
    }

    @Test
    @DisplayName("单行不超过 120 字符（超长行在 review 与 diff 里都极难读）")
    void noLineExceedsMaxLength() throws IOException {
        List<String> offenders = new ArrayList<>();
        for (Path file : javaFiles()) {
            List<String> lines = Files.readAllLines(file, StandardCharsets.UTF_8);
            for (int i = 0; i < lines.size(); i++) {
                if (lines.get(i).length() > MAX_LINE_LENGTH) {
                    offenders.add("%s:%d (%d 字符)".formatted(file, i + 1, lines.get(i).length()));
                }
            }
        }
        assertThat(offenders).as("超过 %d 字符的行", MAX_LINE_LENGTH).isEmpty();
    }

    @Test
    @DisplayName("生产代码不得使用 System.out / System.err 打印（手册：日志应用日志框架）")
    void noSystemOutInProductionCode() throws IOException {
        List<String> offenders = new ArrayList<>();
        for (Path file : javaFiles()) {
            List<String> lines = Files.readAllLines(file, StandardCharsets.UTF_8);
            for (int i = 0; i < lines.size(); i++) {
                String line = lines.get(i);
                if (line.contains("System.out.print") || line.contains("System.err.print")) {
                    offenders.add("%s:%d".formatted(file, i + 1));
                }
            }
        }
        assertThat(offenders).as("生产代码里的 System.out/System.err").isEmpty();
    }

    @Test
    @DisplayName("源文件使用 UTF-8 且无 BOM（BOM 会让 javac 报错）")
    void sourcesAreUtf8WithoutBom() throws IOException {
        List<String> offenders = new ArrayList<>();
        for (Path file : javaFiles()) {
            byte[] head = Files.readAllBytes(file);
            if (head.length >= 3 && (head[0] & 0xFF) == 0xEF && (head[1] & 0xFF) == 0xBB && (head[2] & 0xFF) == 0xBF) {
                offenders.add(file.toString());
            }
        }
        assertThat(offenders).as("带 UTF-8 BOM 的源文件").isEmpty();
    }
}

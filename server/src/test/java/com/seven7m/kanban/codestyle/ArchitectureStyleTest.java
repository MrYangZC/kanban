package com.seven7m.kanban.codestyle;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 架构与分层守卫（对应本次"代码习惯整改"的六条约定）。
 *
 * <p>为什么用源码静态断言而不是只靠 review：这些规则一旦被下一个人"顺手照抄旧写法"就会退化，
 * 而退化后编译、启动、单测通常都不会报错（SQL 能跑、Map 能用、int 码能返），
 * 等到 review 才发现成本已经很高。这里挑的都是**零误报**的规则，本地 `mvn -o test` 就能拦住。
 *
 * <p>检查范围只有 {@code src/main/java}（测试代码不受约束），并且会跳过注释行——
 * 注释里为了解释历史问题经常会引用旧写法（例如 {@code .last("LIMIT 1")}），不能算违规。
 */
class ArchitectureStyleTest {

    private static final Path MAIN_SOURCES = Paths.get("src", "main", "java");
    private static final Path SERVICE_ROOT =
            MAIN_SOURCES.resolve(Paths.get("com", "seven7m", "kanban", "service"));

    /** 条件构造器上的原生 SQL 片段：子查询/函数/分页片段都必须下沉到 Mapper */
    private static final Pattern RAW_SQL_IN_WRAPPER = Pattern.compile(
            "\\.(apply|setSql|last|inSql|having)\\(\\s*\"");

    /** 业务异常必须走 ErrorCode（新的 ServiceException 只保留 ErrorCode 构造） */
    private static final Pattern INT_SERVICE_EXCEPTION = Pattern.compile("new ServiceException\\(\\s*\\d");

    /** 方法签名里出现 Map 返回值（手册：不允许把 Map 当查询结果返回） */
    private static final Pattern MAP_RETURN = Pattern.compile(
            "^\\s*(?:public|protected|private|static|final|\\s)*[\\w<>,\\[\\]\\s]*\\bMap<[^>]*>\\s+\\w+\\s*\\(");

    private static List<Path> javaFiles(Path root) throws IOException {
        assertThat(root).as("测试需在 server 模块目录下运行（Maven surefire 的默认工作目录）").exists();
        try (Stream<Path> stream = Files.walk(root)) {
            return stream.filter(p -> p.toString().endsWith(".java")).toList();
        }
    }

    /** 是否是注释行（块注释体内、以 * 开头、以 // 开头），这些行里的示例代码不算违规 */
    private static boolean isCommentLine(String line) {
        String trimmed = line.trim();
        return trimmed.startsWith("*") || trimmed.startsWith("/*") || trimmed.startsWith("//");
    }

    /** 返回"文件名:行号: 行内容"形式的违规清单 */
    private static List<String> findViolations(Path root, Pattern pattern) throws IOException {
        List<String> offenders = new ArrayList<>();
        for (Path file : javaFiles(root)) {
            List<String> lines = Files.readAllLines(file, StandardCharsets.UTF_8);
            for (int i = 0; i < lines.size(); i++) {
                String line = lines.get(i);
                if (isCommentLine(line)) {
                    continue;
                }
                Matcher m = pattern.matcher(line);
                if (m.find()) {
                    offenders.add("%s:%d (%s)".formatted(file, i + 1, line.trim()));
                }
            }
        }
        return offenders;
    }

    @Test
    @DisplayName("服务层不得把原生 SQL 写进条件构造器（apply/setSql/last/inSql 一律下沉 Mapper）")
    void noRawSqlInServiceLayer() {
        List<String> offenders;
        try {
            offenders = findViolations(SERVICE_ROOT, RAW_SQL_IN_WRAPPER);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        assertThat(offenders)
                .as("条件构造器上的 SQL 片段：请改成 Mapper 的 @Select 方法（子查询）、"
                        + "selectList().stream().findFirst()（取一条）或 UpdateWrappers.clearToNull（置空）")
                .isEmpty();
    }

    @Test
    @DisplayName("业务异常必须使用 ErrorCode 枚举（不允许 new ServiceException(400, \"...\")）")
    void exceptionsUseErrorCodeEnum() {
        List<String> offenders;
        try {
            offenders = findViolations(MAIN_SOURCES, INT_SERVICE_EXCEPTION);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        assertThat(offenders)
                .as("业务异常请改成 new ServiceException(ErrorCode.XXX) / (ErrorCode.XXX, args)，"
                        + "码与文案统一在 ErrorCode 里维护")
                .isEmpty();
    }

    @Test
    @DisplayName("Service 层方法签名不得返回 Map（应返回 VO / record / NameIndex）")
    void serviceMethodsDoNotReturnMap() {
        List<String> offenders;
        try {
            offenders = findViolations(SERVICE_ROOT, MAP_RETURN);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        assertThat(offenders)
                .as("Map 作为返回值时键值语义只能靠猜：id→名称改 NameIndex，其余请定义 VO 或 record")
                .isEmpty();
    }

    @Test
    @DisplayName("每个 Service 接口都必须有 impl 实现（服务层接口与实现分离）")
    void everyServiceHasAnImplementation() throws IOException {
        List<String> offenders = new ArrayList<>();
        for (Path file : javaFiles(SERVICE_ROOT)) {
            if (file.toString().contains(java.io.File.separator + "impl" + java.io.File.separator)) {
                continue;
            }
            String source = Files.readString(file, StandardCharsets.UTF_8);
            String simpleName = file.getFileName().toString().replace(".java", "");
            if (!source.contains("public interface " + simpleName)) {
                continue;  // 协作类（Guard/Assembler/Resolver/枚举）不要求接口
            }
            Path implDir = file.getParent().resolve("impl");
            boolean implemented = false;
            if (Files.isDirectory(implDir)) {
                for (Path impl : javaFiles(implDir)) {
                    String implSource = Files.readString(impl, StandardCharsets.UTF_8);
                    if (implSource.contains("implements " + simpleName)
                            || implSource.contains("implements " + simpleName + " ")) {
                        implemented = true;
                        break;
                    }
                }
            }
            if (!implemented) {
                offenders.add(file + "（接口没有 impl 实现，或实现类没有 implements " + simpleName + "）");
            }
        }
        assertThat(offenders).isEmpty();
    }
}

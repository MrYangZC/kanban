package com.seven7m.kanban.common.security;

import org.springframework.security.access.prepost.PreAuthorize;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.type.filter.AnnotationTypeFilter;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 权限码一致性测试：把"代码里的常量""控制器上的注解""数据库迁移里的种子/删除"三方对齐。
 *
 * <p>这类不一致不会编译报错、也不会在启动时报错，只会在用户点下去时表现为
 * 「永远 403」或者「这个权限勾了也没用」（假权限）。三种典型事故：
 * <ol>
 *   <li>注解里写错一个字母（{@code issue:commnet}）→ 该接口对所有人 403；</li>
 *   <li>常量定义了但迁移里没有种子/被清理脚本删掉 → 接口永远无法被授予；</li>
 *   <li>种子里有、代码里没人引用 → 死权限码，RBAC 页面上勾了也不生效。</li>
 * </ol>
 * 本测试直接读 {@code db/migration} 下的 SQL 做比对，因此新增迁移时必须同步更新权益数据。
 */
class PermissionConsistencyTest {

    /** 项目级权限表达式里的权限码：@projectAuth.has(#projectId, 'issue:view') */
    private static final Pattern PROJECT_AUTH_CODE = Pattern.compile(
            "@projectAuth\\.(?:has|hasIssue|hasId)\\([^,]+,\\s*'([^']+)'\\)");


    /** V1.0.0 里的权限码种子：INSERT INTO ... ("code", ...) VALUES ('issue:view', ...) */
    private static final Pattern SEED_PERMISSION = Pattern.compile(
            "INSERT INTO \"public\"\\.\"permission\"[^;]*?VALUES \\('([^']+)'", Pattern.DOTALL);

    /** 角色授权：INSERT INTO ... "role_permission" (...) VALUES ('id', 'roleId', 'code', ...) */
    private static final Pattern SEED_ROLE_PERMISSION = Pattern.compile(
            "INSERT INTO \"public\"\\.\"role_permission\"[^;]*?VALUES \\('[^']+', '[^']+', '([^']+)'", Pattern.DOTALL);

    /** V1.0.5 的清理脚本里被删除的码 */
    private static final Pattern DELETED_PERMISSION = Pattern.compile(
            "DELETE FROM \"public\"\\.\"permission\"\\s+WHERE \"code\" IN \\(([^)]*)\\)", Pattern.DOTALL);

    private static String readMigration(String name) {
        String path = "db/migration/" + name;
        try (InputStream in = PermissionConsistencyTest.class.getClassLoader().getResourceAsStream(path)) {
            assertThat(in).as("迁移脚本不在 classpath 上: %s", path).isNotNull();
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException("读取迁移脚本失败: " + path, e);
        }
    }

    private static Set<String> seededCodes() {
        Set<String> codes = new LinkedHashSet<>();
        Matcher m = SEED_PERMISSION.matcher(readMigration("V1.0.0__baseline.sql"));
        while (m.find()) {
            codes.add(m.group(1));
        }
        return codes;
    }

    private static Set<String> deletedCodes() {
        Set<String> codes = new LinkedHashSet<>();
        Matcher m = DELETED_PERMISSION.matcher(readMigration("V1.0.5__cleanup_unused_permissions.sql"));
        while (m.find()) {
            Matcher code = Pattern.compile("'([^']+)'").matcher(m.group(1));
            while (code.find()) {
                codes.add(code.group(1));
            }
        }
        return codes;
    }

    private static Set<String> grantedCodes() {
        Set<String> codes = new LinkedHashSet<>();
        Matcher m = SEED_ROLE_PERMISSION.matcher(readMigration("V1.0.0__baseline.sql"));
        while (m.find()) {
            codes.add(m.group(1));
        }
        return codes;
    }

    /** 代码里的权限码常量（与 allCodes() 同源，但这里排除掉非权限码的 String 常量风险） */
    private static Set<String> declaredCodes() {
        Set<String> codes = new TreeSet<>();
        for (Field f : Permission.class.getDeclaredFields()) {
            if (Modifier.isStatic(f.getModifiers()) && f.getType() == String.class) {
                try {
                    codes.add((String) f.get(null));
                } catch (IllegalAccessException e) {
                    throw new IllegalStateException(e);
                }
            }
        }
        return codes;
    }

    /** 扫描所有 @RestController，收集注解上声明的权限码 */
    private static Set<String> annotationCodes() throws Exception {
        Set<String> codes = new TreeSet<>();
        ClassPathScanningCandidateComponentProvider scanner = new ClassPathScanningCandidateComponentProvider(false);
        scanner.addIncludeFilter(new AnnotationTypeFilter(RestController.class));
        for (BeanDefinition bd : scanner.findCandidateComponents("com.seven7m.kanban")) {
            Class<?> type = Class.forName(bd.getBeanClassName());
            // 权限码现在写在 @PreAuthorize 的 SpEL 里：@projectAuth.has(#projectId, 'issue:view')
            collectPreAuthorizeCodes(type.getAnnotation(PreAuthorize.class), codes);
            for (Method m : type.getDeclaredMethods()) {
                collectPreAuthorizeCodes(m.getAnnotation(PreAuthorize.class), codes);
            }
        }
        return codes;
    }

    @Test
    @DisplayName("代码里的每个权限码常量都在种子里，且没有被清理脚本删掉")
    void everyConstantIsSeededAndAlive() {
        Set<String> seeded = seededCodes();
        Set<String> deleted = deletedCodes();

        assertThat(seeded).as("baseline 里应能解析出权限码种子").isNotEmpty();
        assertThat(declaredCodes())
                .as("代码里定义了、但数据库种子里没有的权限码（接口会永远 403）")
                .isSubsetOf(seeded);
        assertThat(declaredCodes())
                .as("代码里还在用、但清理脚本已删除的权限码（假权限）")
                .doesNotContainAnyElementsOf(deleted);
    }

    @Test
    @DisplayName("种子里每个权限码在代码里都有常量，且至少被授予一个角色")
    void everySeedCodeIsDeclaredAndGranted() {
        Set<String> alive = new TreeSet<>(seededCodes());
        alive.removeAll(deletedCodes());
        Set<String> granted = grantedCodes();

        assertThat(alive)
                .as("种子里有、代码里没有常量的权限码（属于死数据）")
                .isSubsetOf(declaredCodes());
        assertThat(alive)
                .as("种子权限码必须至少授予一个角色，否则任何人都无法获得")
                .isSubsetOf(granted);
    }

    @Test
    @DisplayName("控制器注解上的权限码都必须是已声明的常量（防手写拼写错误）")
    void annotationCodesAreDeclaredConstants() throws Exception {
        Set<String> annotated = annotationCodes();

        assertThat(annotated).as("应至少扫描到若干受保护的控制器方法").isNotEmpty();
        assertThat(annotated)
                .as("注解里出现了 Permission 中不存在的权限码（该接口会永远 403）")
                .isSubsetOf(declaredCodes());
    }

    @Test
    @DisplayName("权限码格式统一：小写模块 + 冒号 + 小写动作，无重复")
    void codesFollowNamingPattern() {
        List<String> codes = Permission.allCodes();

        assertThat(codes).doesNotHaveDuplicates();
        assertThat(codes).allSatisfy(code ->
                assertThat(code).as("权限码命名应为 module:action").matches("^[a-z]+:[a-z_]+$"));
    }

    /**
     * 从 @PreAuthorize 表达式里取出权限码。
     *
     * <p>只认 {@code @projectAuth.has/hasIssue/hasId(..., 'code')} 这一种形态：
     * 迁移后项目级权限必须经 ProjectAuth 判定，其它写法（如直接 hasAuthority('xxx')）
     * 意味着有人绕过了项目作用域，直接报错比静默忽略更安全。
     */
    private static void collectPreAuthorizeCodes(PreAuthorize annotation, Set<String> codes) {
        if (annotation == null) {
            return;
        }
        Matcher m = PROJECT_AUTH_CODE.matcher(annotation.value());
        while (m.find()) {
            codes.add(m.group(1));
        }
    }
}

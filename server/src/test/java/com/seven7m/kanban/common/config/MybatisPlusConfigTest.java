package com.seven7m.kanban.common.config;

import com.baomidou.mybatisplus.annotation.DbType;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.InnerInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 分页插件注册守卫。
 *
 * <p>分页从「手拼 {@code LIMIT} 字符串」改为 {@code selectPage} 之后，能否真正分页**完全取决于这个插件**：
 * 插件缺失时 MyBatis-Plus 不报错，而是忽略 LIMIT 返回全表——接口照样 200，只是把整个表吐出来，
 * 数据一多就是事故，而且在 2 行数据的测试库里根本看不出来。
 *
 * <p>所以这里把注册结果固化：方言必须是 PostgreSQL、单页上限必须存在。
 */
class MybatisPlusConfigTest {

    @Test
    @DisplayName("分页拦截器已注册，方言为 PostgreSQL")
    void paginationInterceptorIsRegistered() {
        MybatisPlusInterceptor interceptor = new MybatisPlusConfig().mybatisPlusInterceptor();
        List<InnerInterceptor> inner = interceptor.getInterceptors();

        assertThat(inner).as("至少要注册一个内部拦截器").isNotEmpty();
        PaginationInnerInterceptor pagination = inner.stream()
                .filter(PaginationInnerInterceptor.class::isInstance)
                .map(PaginationInnerInterceptor.class::cast)
                .findFirst()
                .orElse(null);

        assertThat(pagination).as("缺少 PaginationInnerInterceptor：selectPage 会退化成返回全表").isNotNull();
        assertThat(pagination.getDbType()).isEqualTo(DbType.POSTGRE_SQL);
    }

    @Test
    @DisplayName("单页上限兜底：防止调用方传超大 size 把库打爆")
    void maxLimitIsConfigured() {
        MybatisPlusInterceptor interceptor = new MybatisPlusConfig().mybatisPlusInterceptor();
        PaginationInnerInterceptor pagination = (PaginationInnerInterceptor) interceptor.getInterceptors().get(0);

        assertThat(pagination.getMaxLimit()).isEqualTo(MybatisPlusConfig.MAX_PAGE_SIZE);
        assertThat(MybatisPlusConfig.MAX_PAGE_SIZE).isLessThanOrEqualTo(1000L);
    }
}

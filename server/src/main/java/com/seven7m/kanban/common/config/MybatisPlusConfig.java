package com.seven7m.kanban.common.config;

import com.baomidou.mybatisplus.annotation.DbType;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * MyBatis-Plus 插件：分页。
 *
 * <p><b>为什么必须有它</b>：分页查询此前用 {@code wrapper.last("LIMIT " + size + " OFFSET " + offset)}
 * 拼字符串（手册禁止 SQL 拼接）。改用 {@code selectPage} 之后，能否真正分页取决于本插件是否注册：
 * 插件缺失时 MyBatis-Plus <b>不报错</b>，而是把 LIMIT 整个忽略、返回全表数据——
 * 接口看起来"能用"，但一页会吐出所有行。
 *
 * <p>因此这里显式注册，并用 {@code PaginationInterceptorTest} 盯住，避免被顺手删掉。
 *
 * <p>两个参数：
 * <ul>
 *   <li>{@code DbType.POSTGRE_SQL}：方言决定 LIMIT/OFFSET 的写法，写错会生成非法 SQL；</li>
 *   <li>{@code setMaxLimit}：单页上限兜底，防止调用方传入超大 size 把库打爆
 *       （各 Service 自身也会 clamp，这里是最后一道防线）。</li>
 * </ul>
 */
@Configuration
public class MybatisPlusConfig {

    /** 单页最多返回的行数（各 Service 的业务上限都小于它） */
    static final long MAX_PAGE_SIZE = 1000L;

    @Bean
    public MybatisPlusInterceptor mybatisPlusInterceptor() {
        MybatisPlusInterceptor interceptor = new MybatisPlusInterceptor();
        PaginationInnerInterceptor pagination = new PaginationInnerInterceptor(DbType.POSTGRE_SQL);
        pagination.setMaxLimit(MAX_PAGE_SIZE);
        interceptor.addInnerInterceptor(pagination);
        return interceptor;
    }
}

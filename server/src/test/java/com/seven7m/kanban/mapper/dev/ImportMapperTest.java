package com.seven7m.kanban.mapper.dev;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 导入状态映射的单元测试。
 *
 * <p>这段映射决定「源表里的一行落在看板哪一列」，映射错不会报错，只会把已完成的任务
 * 全部堆到待办列——2026-11-13 导入 238 条钉钉数据时就踩过：Excel 里写进去的是选项 id
 * 而不是中文名，mapStatus 认不出，结果 238 条全进 TODO。所以这里把语义钉死。
 */
class ImportMapperTest {

    @Test
    @DisplayName("已完成 → DONE")
    void doneMapping() {
        assertThat(ImportMapper.mapStatus("已完成")).isEqualTo(ImportMapper.CAT_DONE);
    }

    @Test
    @DisplayName("进行中 / 研发中 / 测试中 → IN_PROGRESS")
    void inProgressMapping() {
        assertThat(ImportMapper.mapStatus("进行中")).isEqualTo(ImportMapper.CAT_IN_PROGRESS);
        assertThat(ImportMapper.mapStatus("研发中")).isEqualTo(ImportMapper.CAT_IN_PROGRESS);
        assertThat(ImportMapper.mapStatus("测试中")).isEqualTo(ImportMapper.CAT_IN_PROGRESS);
    }

    @Test
    @DisplayName("待办、空值、无法识别的文本统一落到 TODO")
    void todoAndFallbackMapping() {
        assertThat(ImportMapper.mapStatus("待办")).isEqualTo(ImportMapper.CAT_TO_DO);
        assertThat(ImportMapper.mapStatus(null)).isEqualTo(ImportMapper.CAT_TO_DO);
        assertThat(ImportMapper.mapStatus("  ")).isEqualTo(ImportMapper.CAT_TO_DO);
        // 选项 id 这类机器值不应被误判成进行中/已完成
        assertThat(ImportMapper.mapStatus("CZsZ7c84RB")).isEqualTo(ImportMapper.CAT_TO_DO);
    }

    @Test
    @DisplayName("纯中文 sheet 名也能生成非空项目 Key（钉钉表名多为中文）")
    void chineseSheetNameProducesKey() {
        String key = ImportMapper.toProjectKeyBase("禹舜");

        assertThat(key).isNotBlank();
        assertThat(key).doesNotContain(" ");
    }
}

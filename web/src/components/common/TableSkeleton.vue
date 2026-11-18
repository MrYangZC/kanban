<script setup lang="ts">
/**
 * 表格加载骨架。
 *
 * <p>此前加载态是一个居中 spinner（v-loading），页面在"空 → 有数据"之间会整块跳动；
 * 骨架屏保持与真实表格相同的行高和列宽节奏，视觉上更稳，也更快让人理解为"正在取数据"。
 *
 * <p>用法：放在 el-table 的 #empty 插槽里，加载时优先命中（此时表格本来就没有行）。
 */
withDefaults(defineProps<{
  /** 骨架行数 */
  rows?: number
  /** 每行渲染几段（对应列数，仅影响观感） */
  cols?: number
}>(), {
  rows: 6,
  cols: 4
})

/** 每段宽度错开，避免看起来像一张规整的网格 */
const widths = ['38%', '14%', '12%', '16%', '10%']
</script>

<template>
  <div class="wi-skeleton" aria-busy="true" aria-label="加载中">
    <div v-for="r in rows" :key="r" class="wi-skeleton-row">
      <span
        v-for="c in Math.min(cols, widths.length)"
        :key="c"
        class="wi-skeleton-bar"
        :style="{ width: widths[(c - 1) % widths.length] }"
      />
    </div>
  </div>
</template>

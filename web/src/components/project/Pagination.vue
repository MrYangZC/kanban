<script setup lang="ts">
/**
 * 分页条（Element Plus 版）。
 *
 * <p>原先这里手写了一套分页：上一页/下一页/首末页四个按钮、页码窗口计算、原生 select 选每页条数。
 * 这些 el-pagination 都已经实现（含键盘操作、无障碍属性、窗口溢出省略），
 * 手写的版本还要自己维护「首页」「末页」按钮、省略号逻辑和样式一致性。
 * 现在只做一层薄封装，把 props / emits 映射到 el-pagination。
 *
 * <p>保留 currentPage / pageSize / total 三个入参，调用方（分类 store）继续用
 * update:page / update:pageSize 两个事件驱动加载，接口不变。
 * 中文文案（共 N 条）由 App.vue 的 el-config-provider zh-cn 提供。
 */
const props = defineProps<{
  currentPage: number
  total: number
  pageSize: number
}>()

const emit = defineEmits<{
  (e: 'update:page', page: number): void
  (e: 'update:pageSize', size: number): void
}>()

const pageSizeOptions = [10, 20, 25, 50]

function onPageChange(page: number) {
  if (page === props.currentPage) return
  emit('update:page', page)
}

function onSizeChange(size: number) {
  if (size === props.pageSize) return
  emit('update:pageSize', size)
}
</script>

<template>
  <el-pagination
    :current-page="currentPage"
    :page-size="pageSize"
    :page-sizes="pageSizeOptions"
    :total="total"
    :pager-count="5"
    layout="total, sizes, prev, pager, next"
    background
    @current-change="onPageChange"
    @size-change="onSizeChange"
  />
</template>

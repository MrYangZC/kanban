import { computed, onBeforeUnmount, onMounted, ref } from 'vue'

/**
 * 视口宽度断点。
 *
 * 表格的次要列在窄屏要从 DOM 里真正移除，而不是用 CSS `display:none`：
 * el-table 按列定义生成 `<colgroup>`，隐藏单元格不会收掉列宽，
 * 表体因此多出一段"看不见但占位"的横向滚动。
 *
 * 断点与 style.css 末尾的响应式段落保持一致：≤1200 收侧栏、≤1100 去日期列、≤860 去负责人。
 */
export function useViewportWidth() {
  const width = ref(typeof window === 'undefined' ? 1440 : window.innerWidth)

  const onResize = () => {
    width.value = window.innerWidth
  }

  onMounted(() => window.addEventListener('resize', onResize))
  onBeforeUnmount(() => window.removeEventListener('resize', onResize))

  /** ≤1100px：隐藏「计划完成 / 更新于」 */
  const hideMdColumns = computed(() => width.value <= 1100)
  /** ≤860px：再隐藏「负责人」 */
  const hideSmColumns = computed(() => width.value <= 860)

  return { width, hideMdColumns, hideSmColumns }
}

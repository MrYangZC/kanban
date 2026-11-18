import { computed, onBeforeUnmount, onMounted, ref } from 'vue'

/**
 * 视口宽度断点。
 *
 * 表格的次要列在窄屏要从 DOM 里真正移除，而不是用 CSS `display:none`：
 * el-table 按列定义生成 `<colgroup>`，隐藏单元格不会收掉列宽，
 * 表体因此多出一段"看不见却占位"的横向滚动。
 *
 * 断点与 style.css 末尾的响应式段落保持一致：≤1200 收侧栏、≤1100 去次要列、≤860 再去一列。
 *
 * 三档的语义按"信息重要度"排：
 * - lg（≤1280）：最先让位的是"创建时间/截止日"这类辅助时间列
 * - md（≤1100）：其次让位的是"描述"这类长文本列
 * - sm（≤860）：最后让位的是"负责人/邮箱"这类辅助标识列
 */
export function useViewportWidth() {
  const width = ref(typeof window === 'undefined' ? 1440 : window.innerWidth)

  const onResize = () => {
    width.value = window.innerWidth
  }

  onMounted(() => window.addEventListener('resize', onResize))
  onBeforeUnmount(() => window.removeEventListener('resize', onResize))

  /** ≤1280px：隐藏辅助时间列（创建人 / 创建时间 / 截止日） */
  const hideLgColumns = computed(() => width.value <= 1280)
  /** ≤1100px：隐藏长文本列（描述 / 目标）与工作项的日期列 */
  const hideMdColumns = computed(() => width.value <= 1100)
  /** ≤860px：隐藏辅助标识列（负责人 / 邮箱） */
  const hideSmColumns = computed(() => width.value <= 860)

  return { width, hideLgColumns, hideMdColumns, hideSmColumns }
}

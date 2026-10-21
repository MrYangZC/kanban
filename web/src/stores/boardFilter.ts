import { reactive } from 'vue'

/** 看板/表格视图共享的筛选条件结构 */
export interface FilterPayload {
  scope: string
  keyword: string
  priority: string
  severity: string
  statusId: string
  labelId: string
}

/**
 * 看板/表格视图共享的筛选状态。
 *
 * 必须是模块级单例：若各视图各自 reactive，则"看板 ↔ 表格"切换时筛选条件会丢失，
 * 与组件内"切换视图保持一致"的预期不符。
 */
const boardFilter = reactive<FilterPayload>({
  scope: '',
  keyword: '',
  priority: '',
  severity: '',
  statusId: '',
  labelId: ''
})

/** 记录当前筛选条件所属项目，用于跨项目时自动重置 */
let ownerProjectId: string | null = null

/** 重置筛选条件 */
export function resetBoardFilter(): void {
  boardFilter.scope = ''
  boardFilter.keyword = ''
  boardFilter.priority = ''
  boardFilter.severity = ''
  boardFilter.statusId = ''
  boardFilter.labelId = ''
}

/**
 * 获取（单例的）筛选状态。
 * 传入当前项目 ID 时，若已切换到其它项目会自动清空，避免沿用上一个项目的筛选条件。
 */
export function useBoardFilter(projectId: string): FilterPayload {
  if (ownerProjectId !== projectId) {
    resetBoardFilter()
    ownerProjectId = projectId
  }
  return boardFilter
}

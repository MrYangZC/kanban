import { reactive } from 'vue'
import type { FilterPayload } from '@/components/BoardFilterBar.vue'

/** 看板/表格视图共享的筛选状态，切换视图时保持一致 */
export function createBoardFilter(): FilterPayload {
  return reactive<FilterPayload>({
    scope: '',
    keyword: '',
    priority: '',
    severity: '',
    statusId: '',
    labelId: ''
  })
}

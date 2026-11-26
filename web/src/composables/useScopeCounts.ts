import { ref } from 'vue'
import { filterIssues } from '@/api'
import type { IssueFilterPayload } from '@/api/types'
import { useIssueFilterParams } from '@/composables/useIssueFilterParams'

/**
 * 工作项列表左侧范围导航的 scope 取值，与后端 BaseWorkItemService 的 scope 语义一一对应：
 * ''（全部未归档）/ REPORTED / ASSIGNED / VERIFIED / SHARED / ARCHIVED。
 */
export const SCOPE_KEYS = ['', 'REPORTED', 'ASSIGNED', 'VERIFIED', 'SHARED', 'ARCHIVED'] as const

export type ScopeKey = (typeof SCOPE_KEYS)[number]

/** 校验持久化的 scope 是否属于合法取值，避免脏值把导航“钉”在某个空范围上 */
export function isScopeKey(value: string): value is ScopeKey {
  return (SCOPE_KEYS as readonly string[]).includes(value)
}

/**
 * 范围导航计数的唯一来源：逐个 scope 取 filter 接口的 total（size=1 只取计数）。
 * 缺陷/任务/需求页共用，避免出现「硬编码 0」和「看板视图不更新计数」两类偏差——
 * 计数与当前视图无关，切到看板/树形后依然准确。
 *
 * @param extra 除 scope 之外的当前筛选条件（关键词/状态/优先级…）。
 *   带上它，左侧计数就与列表口径一致：否则用户筛到 137 条，左栏还写着 252，
 *   看起来像筛选没生效。计数仍然逐 scope 各自统计，只是都套用同一组条件。
 */
export function useScopeCounts(
  projectId: () => string,
  typeCodes: () => string[],
  extra: () => Partial<IssueFilterPayload> = () => ({})
) {
  const counts = ref<Record<string, number>>({})
  // 与列表页共用同一套筛选字段，避免"计数口径"和"列表口径"各写一份
  const buildFilterParams = useIssueFilterParams({ projectId, typeCodes })
  // 并发刷新时只认最后一次结果，避免旧请求覆盖新数据（如切换项目后回包乱序）
  let seq = 0

  async function refresh() {
    const my = ++seq
    const pairs = await Promise.all(
      SCOPE_KEYS.map(async (key) => {
        try {
          const res = await filterIssues(
            buildFilterParams({ scope: key || undefined, page: 1, size: 1, ...extra() })
          )
          return [key, res.total] as const
        } catch {
          // 单个范围失败不阻塞其它范围（错误提示由接口层统一弹出）
          return [key, 0] as const
        }
      })
    )
    if (my !== seq) return
    const next: Record<string, number> = {}
    for (const [key, total] of pairs) next[key] = total
    counts.value = next
  }

  return { counts, refresh }
}

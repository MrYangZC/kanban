import type { IssueFilterPayload } from '@/api/types'

/**
 * 各来源字段都是 getter：调用方传 `ref` 的 `.value` 包装，保证每次请求都读到当前筛选状态。
 * 缺省字段不传，构造出来的对象里对应 key 为 `undefined`。
 */
export interface IssueFilterSource {
  projectId: () => string
  typeCodes?: () => string[] | undefined
  scope?: () => string | undefined
  keyword?: () => string | undefined
  statusCategory?: () => string | undefined
  overdue?: () => boolean | undefined
  sprintId?: () => string | undefined
}

/**
 * 工作项筛选参数构造器（唯一来源）。
 *
 * <p>此前 `WorkItemListPage`、`DefectManageView`、`useScopeCounts`、`BoardView`、`TableView`
 * 各自手拼一份 `{ projectId, scope, keyword, issueTypeCodes, statusCategory, overdue }`，
 * 字段增减要改五处，漏改一处表现为"某个视图筛选不生效"（很难在测试里发现）。
 *
 * <p>约定：所有"空值"统一转成 `undefined`，避免把 `''`/`false` 传给后端造成
 * "scope= 空字符串" 之类的假筛选。
 */
export function useIssueFilterParams(source: IssueFilterSource) {
  return function buildFilterParams(extra: Partial<IssueFilterPayload> = {}): IssueFilterPayload {
    const scope = source.scope?.()
    const keyword = source.keyword?.()
    const statusCategory = source.statusCategory?.()
    const overdue = source.overdue?.()
    const sprintId = source.sprintId?.()

    return {
      projectId: source.projectId(),
      scope: scope || undefined,
      keyword: keyword || undefined,
      issueTypeCodes: source.typeCodes?.(),
      statusCategory: statusCategory || undefined,
      overdue: overdue || undefined,
      sprintId: sprintId || undefined,
      ...extra
    }
  }
}

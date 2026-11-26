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
  statusId?: () => string | undefined
  statusCategory?: () => string | undefined
  priority?: () => string | undefined
  severity?: () => string | undefined
  labelId?: () => string | undefined
  sprintId?: () => string | undefined
  assigneeId?: () => string | undefined
  reporterId?: () => string | undefined
  createdFrom?: () => string | undefined
  createdTo?: () => string | undefined
  updatedFrom?: () => string | undefined
  updatedTo?: () => string | undefined
  dueFrom?: () => string | undefined
  dueTo?: () => string | undefined
  overdue?: () => boolean | undefined
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
    const statusId = source.statusId?.()
    const statusCategory = source.statusCategory?.()
    const priority = source.priority?.()
    const severity = source.severity?.()
    const labelId = source.labelId?.()
    const sprintId = source.sprintId?.()
    const assigneeId = source.assigneeId?.()
    const reporterId = source.reporterId?.()
    const createdFrom = source.createdFrom?.()
    const createdTo = source.createdTo?.()
    const updatedFrom = source.updatedFrom?.()
    const updatedTo = source.updatedTo?.()
    const dueFrom = source.dueFrom?.()
    const dueTo = source.dueTo?.()
    const overdue = source.overdue?.()

    return {
      projectId: source.projectId(),
      scope: scope || undefined,
      keyword: keyword || undefined,
      statusId: statusId || undefined,
      issueTypeCodes: source.typeCodes?.(),
      statusCategory: statusCategory || undefined,
      priority: priority || undefined,
      severity: severity || undefined,
      labelId: labelId || undefined,
      sprintId: sprintId || undefined,
      assigneeId: assigneeId || undefined,
      reporterId: reporterId || undefined,
      createdFrom: createdFrom || undefined,
      createdTo: createdTo || undefined,
      updatedFrom: updatedFrom || undefined,
      updatedTo: updatedTo || undefined,
      dueFrom: dueFrom || undefined,
      dueTo: dueTo || undefined,
      overdue: overdue || undefined,
      ...extra
    }
  }
}

import type { Issue } from '@/api/types'

/**
 * 列表视图的「按…分组」。
 *
 * <p>后端 `groupBy` 只服务于看板（`getBoardGrouped` 按状态分列），列表页的分组放在前端做：
 * 列表页在分组模式下本来就是全量取（≤1000 条），本地归组才能保证「组计数」和表格行同源，
 * 不会出现组头写 20、展开只有 25 条里的 8 条这种错位。
 */

/** 分组行：插在表格数据里的伪行，用 span-method 合并成一整行组标题 */
export interface IssueGroupRow {
  id: string
  __group: true
  label: string
  count: number
}

export interface IssueGroup {
  key: string
  label: string
  /** 组间排序位：状态按工作流顺序、优先级按 P0→P3，其余按名称 */
  order: number
  rows: Issue[]
}

export const GROUP_OPTIONS = [
  { value: '', label: '不分组' },
  { value: 'STATUS', label: '按状态' },
  { value: 'PRIORITY', label: '按优先级' },
  { value: 'ASSIGNEE', label: '按负责人' },
  { value: 'SPRINT', label: '按迭代' }
]

/**
 * 把一个工作项映射成「属于哪一组」。
 *
 * @param key   组标识（同名即同组）
 * @param label 组标题
 * @param order 组间排序位
 */
export type GroupResolver = (issue: Issue) => { key: string; label: string; order: number }

export function groupIssues(rows: Issue[], resolve: GroupResolver): IssueGroup[] {
  const map = new Map<string, IssueGroup>()
  for (const row of rows) {
    const { key, label, order } = resolve(row)
    const hit = map.get(key)
    if (hit) hit.rows.push(row)
    else map.set(key, { key, label, order, rows: [row] })
  }
  return [...map.values()].sort((a, b) => a.order - b.order || a.label.localeCompare(b.label, 'zh-CN'))
}

/** 任务/需求/缺陷三个页面共用的字典查询能力：由页面注入自己那份 meta */
export interface GroupResolverDeps {
  statusName: (statusId: string) => string
  /** 状态在工作流里的顺序（用于组间排序） */
  statusOrder: (statusId: string) => number
  priorityLabel: (priority: string) => string
  memberName: (userId: string | null) => string
  sprintName: (sprintId: string | null) => string
  sprintOrder: (sprintId: string | null) => number
}

/**
 * 按维度生成组归属解析器。
 *
 * <p>放在这里而不是各页面各写一份：三个页面的分组维度完全一样，
 * 只有"字典从哪来"不同，那部分由 GroupResolverDeps 注入。
 */
export function createGroupResolver(groupBy: string, deps: GroupResolverDeps): GroupResolver {
  if (groupBy === 'STATUS') {
    return (i) => ({ key: i.statusId || 'none', label: deps.statusName(i.statusId), order: deps.statusOrder(i.statusId) })
  }
  if (groupBy === 'PRIORITY') {
    return (i) => ({ key: i.priority || 'none', label: deps.priorityLabel(i.priority) || '未设置', order: PRIORITY_ORDER[i.priority] ?? 9 })
  }
  if (groupBy === 'ASSIGNEE') {
    return (i) => ({ key: i.assigneeId || 'none', label: i.assigneeId ? deps.memberName(i.assigneeId) : '未分配', order: 0 })
  }
  return (i) => ({
    key: i.sprintId || 'none',
    label: deps.sprintName(i.sprintId),
    order: deps.sprintOrder(i.sprintId)
  })
}

/** 优先级组间顺序：P0 最高，未知值沉到最后 */
const PRIORITY_ORDER: Record<string, number> = { P0: 0, P1: 1, P2: 2, P3: 3 }

/**
 * 组 → 表格数据行：组标题行 + 该组的成员行。
 *
 * <p>el-table 不支持真正的分组行，所以用「伪行 + span-method 合并」的方式渲染组标题，
 * 这样表头、列宽、行内交互（抽屉、状态下拉）全部沿用同一张表，不需要为分组再复制一遍列定义。
 */
export function flattenGroups(groups: IssueGroup[]): Array<Issue | IssueGroupRow> {
  const rows: Array<Issue | IssueGroupRow> = []
  for (const g of groups) {
    rows.push({ id: `group:${g.key}`, __group: true, label: g.label, count: g.rows.length })
    rows.push(...g.rows)
  }
  return rows
}

export function isGroupRow(row: unknown): row is IssueGroupRow {
  return Boolean(row && (row as IssueGroupRow).__group)
}

/**
 * 组标题行的合并规则。
 *
 * @param columnCount    当前表格实际列数（不含表头之外的隐藏列）
 * @param leadingColumns 组标题左侧保留的独立单元格数（勾选列要单独留着，否则复选框会跟着被合并掉）
 */
export function groupRowSpan(columnCount: number, leadingColumns = 0) {
  return ({ row, columnIndex }: { row: unknown; columnIndex: number }): [number, number] | undefined => {
    if (!isGroupRow(row)) return undefined
    if (columnIndex < leadingColumns) return [1, 1]
    if (columnIndex === leadingColumns) return [1, Math.max(1, columnCount - leadingColumns)]
    return [0, 0]
  }
}

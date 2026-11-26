import { reactive, watch } from 'vue'

/**
 * 工作项列表的可下发筛选条件。
 *
 * <p>只放后端 `IssueFilterRequest` 已经支持的字段——前端不能凭空造条件，
 * 否则会出现「选了但列表不变」的假筛选。关键词归工具栏搜索框、左侧范围归 ScopeNav，
 * 两者各有独立状态，不进这里。
 */
export interface IssueFilterState {
  /** 具体状态节点（issue_status.id）；'' 表示不限 */
  statusId: string
  /** 优先级 P0–P3 */
  priority: string
  /** 严重度 S1–S4：只有缺陷页暴露这个条件（后端仅对 BUG 生效） */
  severity: string
  /** 标签 id */
  labelId: string
  /** 迭代 id */
  sprintId: string
  /** 负责人（成员 userId）；与左侧「我负责的」并存 */
  assigneeId: string
  /** 创建者（用户 id） */
  reporterId: string
  /**
   * 时间区间：按天，两端都含当天，'yyyy-MM-dd'；空串表示该端不限。
   * 创建时间 / 更新时间对应时间戳列，计划完成对应 due_date（纯日期列）。
   */
  createdFrom: string
  createdTo: string
  updatedFrom: string
  updatedTo: string
  dueFrom: string
  dueTo: string
  /** 只看逾期（未完成且已过截止日） */
  overdue: boolean
  /** 列表视图分组维度：'' | STATUS | PRIORITY | ASSIGNEE | SPRINT（前端本地分组） */
  groupBy: string
}

export function emptyIssueFilter(): IssueFilterState {
  return {
    statusId: '',
    priority: '',
    severity: '',
    labelId: '',
    sprintId: '',
    assigneeId: '',
    reporterId: '',
    createdFrom: '',
    createdTo: '',
    updatedFrom: '',
    updatedTo: '',
    dueFrom: '',
    dueTo: '',
    overdue: false,
    groupBy: ''
  }
}

/**
 * 页面级的筛选状态。
 *
 * <p>每个工作项页面（任务/需求/缺陷，以及历史 /board、/table）各持一份：
 * 各页口径不同（缺陷多一个严重度），共用一个对象会让「在缺陷页选了严重度，
 * 切到任务页仍生效」——对用户是纯噪音。页面内列表/树形/看板三视图自然共享同一份
 * （它们本来就在同一个组件里切视图）。
 *
 * <p>项目切换时重置：路由参数变化不会重建组件，不重置的话新项目会沿用上一个项目的
 * 状态 id / 标签 id，表现为「新项目一进来列表就是空的」。
 *
 * @param persistKey 传了就记住「分组维度」（刷新后仍在），其余条件不持久化：
 *   筛选条件带过期语义（如"只看逾期"），刷新后自动生效会让人困惑。
 */
export function useIssueFilterState(projectId: () => string, persistKey?: string) {
  const state = reactive<IssueFilterState>(emptyIssueFilter())
  const storageKey = persistKey ? `kanban:issueGroup:${persistKey}` : ''

  if (storageKey) {
    const saved = localStorage.getItem(storageKey)
    if (saved && GROUP_KEYS.includes(saved)) state.groupBy = saved
  }

  watch(projectId, () => {
    const groupBy = state.groupBy
    Object.assign(state, emptyIssueFilter(), { groupBy })
  })

  if (storageKey) {
    watch(() => state.groupBy, (v) => {
      if (v) localStorage.setItem(storageKey, v)
      else localStorage.removeItem(storageKey)
    })
  }

  return state
}

/** 分组维度的合法取值，与 utils/listGrouping 的 GROUP_OPTIONS 保持一致 */
const GROUP_KEYS = ['STATUS', 'PRIORITY', 'ASSIGNEE', 'SPRINT']

/** 「筛选」角标数字：条件生效时提示用户「当前列表是被筛过的」；一个时间区间算一条 */
export function countActiveConditions(state: IssueFilterState): number {
  let n = 0
  if (state.statusId) n++
  if (state.priority) n++
  if (state.severity) n++
  if (state.labelId) n++
  if (state.sprintId) n++
  if (state.assigneeId) n++
  if (state.reporterId) n++
  if (state.createdFrom || state.createdTo) n++
  if (state.updatedFrom || state.updatedTo) n++
  if (state.dueFrom || state.dueTo) n++
  if (state.overdue) n++
  return n
}

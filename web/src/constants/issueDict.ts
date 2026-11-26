export interface DictOption {
  value: string
  label: string
  color: string
}

export const PRIORITY_OPTIONS: DictOption[] = [
  { value: 'P0', label: '紧急', color: '#F56C6C' },
  { value: 'P1', label: '高', color: '#E6A23C' },
  { value: 'P2', label: '中', color: '#409EFF' },
  { value: 'P3', label: '低', color: '#909399' }
]

export const SEVERITY_OPTIONS: DictOption[] = [
  { value: 'S1', label: '致命', color: '#F56C6C' },
  { value: 'S2', label: '严重', color: '#FA8C16' },
  { value: 'S3', label: '一般', color: '#409EFF' },
  { value: 'S4', label: '轻微', color: '#909399' }
]

export const PRIORITY_LABEL: Record<string, string> = {
  P0: '紧急', P1: '高', P2: '中', P3: '低'
}

export const SEVERITY_LABEL: Record<string, string> = {
  S1: '致命', S2: '严重', S3: '一般', S4: '轻微'
}

export const PRIORITY_COLOR: Record<string, string> = {
  P0: '#F56C6C', P1: '#E6A23C', P2: '#409EFF', P3: '#909399'
}

export const SEVERITY_COLOR: Record<string, string> = {
  S1: '#F56C6C', S2: '#FA8C16', S3: '#409EFF', S4: '#909399'
}

export function priorityLabel(v?: string): string {
  return (v && PRIORITY_LABEL[v]) || v || ''
}

export function severityLabel(v?: string): string {
  return (v && SEVERITY_LABEL[v]) || v || ''
}

export function priorityColor(v?: string): string {
  return (v && PRIORITY_COLOR[v]) || '#909399'
}

export function severityColor(v?: string): string {
  return (v && SEVERITY_COLOR[v]) || '#909399'
}

// ---- 迭代 / 里程碑 / 风险 状态词典 ----
// 与上方优先级/严重程度同构：统一以「实色文字 + 10% 透明度底」胶囊呈现，
// 避免各页面内联 statusType/levelType 三元表达式造成配色不一致。

export const SPRINT_STATUS: Record<string, { label: string; color: string }> = {
  PLANNED: { label: '规划中', color: '#E6A23C' },
  ACTIVE: { label: '进行中', color: '#52c41a' },
  COMPLETED: { label: '已完成', color: '#909399' }
}

export const MILESTONE_STATUS: Record<string, { label: string; color: string }> = {
  ACTIVE: { label: '进行中', color: '#E6A23C' },
  COMPLETED: { label: '已完成', color: '#52c41a' }
}

export const RISK_LEVEL: Record<string, { label: string; color: string }> = {
  LOW: { label: '低', color: '#909399' },
  MEDIUM: { label: '中', color: '#409EFF' },
  HIGH: { label: '高', color: '#E6A23C' },
  CRITICAL: { label: '严重', color: '#F56C6C' }
}

export const RISK_STATUS: Record<string, { label: string; color: string }> = {
  OPEN: { label: '待处理', color: '#F56C6C' },
  MITIGATING: { label: '缓解中', color: '#E6A23C' },
  CLOSED: { label: '已关闭', color: '#909399' }
}

/** 将任意词典项渲染为胶囊样式：实色文字 + 10% 透明度底。
 *  使用 color-mix 以支持 var() 令牌（如缺省色 var(--ink-secondary)）与十六进制色 alike。 */
export function badgeStyle(entry?: { label: string; color: string }): Record<string, string> {
  const color = entry?.color || 'var(--ink-secondary)'
  return { color, background: `color-mix(in srgb, ${color} 10%, transparent)` }
}

// ---- 项目状态词典 ----
// Tailwind 类名版本（用于表格/卡片内联徽标），与上方色值词典并存：
// 类名受主题令牌约束，色值用于内联 style，两者不可互换。

export interface ProjectStatusBadge {
  label: string
  /** 徽标底色 + 文字色 */
  cls: string
  /** 左侧圆点色 */
  dot: string
}

/** 同义状态码归一化：后端历史数据存在多种等价写法 */
export const PROJECT_STATUS_ALIAS: Record<string, string> = {
  DONE: 'COMPLETED',
  ACTIVE: 'IN_PROGRESS',
  ONGOING: 'IN_PROGRESS',
  PENDING: 'NOT_STARTED',
  TODO: 'NOT_STARTED'
}

export const PROJECT_STATUS_BADGE: Record<string, ProjectStatusBadge> = {
  ARCHIVED: { label: '已归档', cls: 'bg-surface-light text-ink-secondary', dot: 'bg-ink-secondary' },
  // 配色按状态递进：未开始(琥珀) → 进行中(绿) → 已完成(蓝) → 已归档(灰) → 已关闭(红)，
  // 五个状态五色，扫一眼就能区分；此前「进行中」用的是主色蓝，和链接/按钮同色，不够醒目。
  COMPLETED: { label: '已完成', cls: 'bg-primary-50 text-primary-600', dot: 'bg-primary-500' },
  IN_PROGRESS: { label: '进行中', cls: 'bg-emerald-50 text-emerald-700', dot: 'bg-emerald-500' },
  NOT_STARTED: { label: '未开始', cls: 'bg-warning-light text-warning', dot: 'bg-warning' },
  CLOSED: { label: '已关闭', cls: 'bg-danger-light text-danger', dot: 'bg-danger' }
}

/** 项目状态 → 徽标样式；未知状态回退为"进行中"，与列表既有展示保持一致 */
export function projectStatusBadge(status?: string): ProjectStatusBadge {
  const raw = (status || '').toUpperCase()
  const key = PROJECT_STATUS_ALIAS[raw] || raw
  return PROJECT_STATUS_BADGE[key] || PROJECT_STATUS_BADGE.IN_PROGRESS
}
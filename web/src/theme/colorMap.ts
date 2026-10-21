/**
 * 数据色单一来源（与 web/src/style.css 的 :root 令牌对齐）
 *
 * 设计分层：
 *   style.css CSS 变量  —— 最底层令牌事实
 *   colorMap.ts          —— JS 侧（:style 动态绑定 / echarts）可引用的令牌原语与图表配色；
 *                           业务数据映射（优先级/严重度/迭代/里程碑/风险）复用 @/constants/issueDict，
 *                           在此统一再导出，使全站数据色仅此一处来源。
 *
 * 约定：
 *   - 有对应 CSS 变量的令牌，用 var(--xxx) 引用，保证运行时与 :root 同源；
 *   - 接口返回的真实状态色（status.color）属数据，组件仅透传，不改写语义；
 *   - 本文件为编译期常量，不引入运行时开销。
 */

/* ---------- 令牌原语（var()，与 style.css :root 同源） ---------- */
export const PRIMARY = 'var(--primary)'            // #409EFF
export const PRIMARY_HEX = '#409EFF'                 // 主色十六进制：用于需追加透明度 alpha 的场景（如 color + '1A'）
export const PRIMARY_DARK = 'var(--primary-dark)'  // #1890ff
export const PRIMARY_LIGHT = 'var(--primary-light)' // #e6f7ff
export const INK = 'var(--ink)'                    // #303133
export const INK_REGULAR = 'var(--ink-regular)'    // #606266
export const INK_SECONDARY = 'var(--ink-secondary)' // #909399
export const INK_PLACEHOLDER = 'var(--ink-placeholder)' // #c0c4cc
export const LINE = 'var(--line)'                  // #dcdfe6
export const LINE_LIGHT = 'var(--line-light)'      // #e4e7ed
export const SURFACE_PAGE = 'var(--surface-page)'  // #f5f7fa
export const SUCCESS = 'var(--success)'            // #52c41a
export const DANGER = 'var(--danger)'              // #f56c6c

/* ---------- 语义数据色（无对应 CSS 变量，取 Element Plus 规范 hex） ---------- */
export const WARNING = '#E6A23C'

/* ---------- 兜底色（状态/优先级未命中映射时的默认） ---------- */
export const FALLBACK_COLOR = INK_SECONDARY
export const FALLBACK_BG = 'rgba(144,149,158,0.1)'

/* ---------- echarts 图表配色（集中管理，避免散落硬编码） ---------- */
export const CHART_PALETTE: readonly string[] = [
  '#1677FF', '#4096FF', '#69B1FF', '#95DEFF',
  '#0958D9', '#8F959E', '#52C41A', '#FAAD14', '#FF4D4F',
]
export const CHART_AXIS_LINE = '#E5E6EB'
export const CHART_SPLIT_LINE = '#F0F2F5'

/* ---------- 报表语义色（按 variant 区分 ring / border，area 为燃尽图填充底色） ---------- */
export const METRIC_DONE = { ring: '#52C41A', border: '#28A745' }
export const METRIC_OVERDUE = { ring: '#FF4D4F', border: '#DC3545', area: 'rgba(255,77,79,0.08)' }

/* ---------- 复用既有业务数据映射（单一来源，避免重复定义） ---------- */
export {
  PRIORITY_COLOR,
  PRIORITY_LABEL,
  SEVERITY_COLOR,
  SEVERITY_LABEL,
  SPRINT_STATUS,
  MILESTONE_STATUS,
  RISK_LEVEL,
  RISK_STATUS,
  badgeStyle,
} from '@/constants/issueDict'

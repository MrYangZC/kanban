import type { HandoffType } from '@/api/types'
import { confirmAction } from './confirm'

/**
 * 交接相关的展示与确认逻辑。
 *
 * 看板列（GroupColumnView）与 workflow-columns 接口（WorkflowColumnVO）携带同一组交接字段，
 * 这里只声明公共部分，两种来源都能直接传进来，避免两处各写一套判空与文案。
 */
export interface HandoffLike {
  name?: string
  handoffType?: HandoffType
  handoffUserId?: string | null
  handoffUserName?: string | null
  handoffNote?: string | null
  /** 落点迭代 / 标签：交接时顺手把工作项放进去、打上标签 */
  targetSprintName?: string | null
  targetLabelName?: string | null
}

/** 落点文案，如「放进迭代 S1、打标签 bug」；没配落点返回空串 */
export function handoffLanding(col: HandoffLike | null | undefined): string {
  const parts: string[] = []
  if (col?.targetSprintName) parts.push(`放进迭代 ${col.targetSprintName}`)
  if (col?.targetLabelName) parts.push(`打标签 ${col.targetLabelName}`)
  return parts.join('、')
}

/** 列头文案；不交接返回空串，由调用方决定是否占位 */
export function handoffLabel(col: HandoffLike | null | undefined): string {
  if (!col || !col.handoffType || col.handoffType === 'NONE') return ''
  const name = col.handoffUserName || ''
  const base =
    col.handoffType === 'PROJECT_LEAD'
      ? name
        ? `交给项目负责人 ${name}`
        : '交给项目负责人'
      : name
        ? `交给 ${name}`
        : '交给指定成员'
  const landing = handoffLanding(col)
  return landing ? `${base} · ${landing}` : base
}

/** 这次流转是否真的会换人（决定要不要弹确认框） */
export function willHandoff(col: HandoffLike | null | undefined, currentAssigneeId?: string | null): boolean {
  if (!col || !col.handoffType || col.handoffType === 'NONE') return false
  if (!col.handoffUserId) return false
  return col.handoffUserId !== (currentAssigneeId || null)
}

/**
 * 交接确认。返回 false 表示用户取消，调用方应放弃这次流转（并把卡片位置还原）。
 * 不会换人时直接返回 true，不打扰用户。
 */
export function confirmHandoff(
  col: HandoffLike | null | undefined,
  currentAssigneeId?: string | null
): Promise<boolean> {
  if (!willHandoff(col, currentAssigneeId)) return Promise.resolve(true)
  const name = col?.handoffUserName || '目标成员'
  const where = col?.name ? `进入「${col.name}」后` : '进入该列后'
  const note = col?.handoffNote?.trim()
  const landing = handoffLanding(col)
  // 落点也要说清楚：用户点确认前得知道这条工作项会被移进哪个迭代、打上什么标签
  const landingLine = landing ? `，并${landing}` : ''
  const message = note
    ? `${where}会转交给「${name}」${landingLine}。\n交接说明：${note}`
    : `${where}会转交给「${name}」${landingLine}。`
  return confirmAction(message, '转交确认', '确认转交')
}

/** 详情页状态下拉里的短标注，如「测试中 · 交给 张三」 */
export function statusOptionLabel(col: HandoffLike | null | undefined, fallbackName: string): string {
  const label = handoffLabel(col)
  return label ? `${fallbackName} · ${label}` : fallbackName
}

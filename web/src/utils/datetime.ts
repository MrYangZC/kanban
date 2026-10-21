export function fmtDate(t?: string | null): string {
  if (!t) return ''
  return t.slice(0, 10)
}

export function fmtTime(t?: string | null): string {
  if (!t) return ''
  return t.slice(0, 19).replace('T', ' ')
}

export function fmtDateTime(t?: string | null): string {
  if (!t) return ''
  return t.slice(0, 16).replace('T', ' ')
}

export function fmtHM(t?: string | null): string {
  if (!t) return ''
  return t.slice(11, 16)
}

/**
 * 表格里展示日期时统一用「—」占位。
 * 之前 WorkItemListPage / DefectManageView 各写了一份 `t ? t.slice(0,10) : '—'`。
 */
export function fmtDateOrDash(t?: string | null, dash = '—'): string {
  return t ? t.slice(0, 10) : dash
}

/** 距截止日还有几天：正数是剩余、0 是今天、负数已逾期；无值时返回 null */
export function daysUntil(t?: string | null): number | null {
  if (!t) return null
  const target = new Date(t)
  if (Number.isNaN(target.getTime())) return null
  return Math.ceil((target.getTime() - Date.now()) / 86_400_000)
}

/** 卡片上的截止时间文案（已逾期 / 今天 / N 天后） */
export function dueText(t?: string | null): string {
  const diff = daysUntil(t)
  if (diff === null) return ''
  if (diff < 0) return '已逾期'
  if (diff === 0) return '今天'
  return `${diff}天后`
}

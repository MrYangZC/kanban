/**
 * 工作项「动态」文案。
 *
 * 后端把 `issue_activity.old_value / new_value` 里的状态 code 翻成中文名后返回，
 * 这里只负责拼成一句人话：`将 状态 从 已取消 置为 待处理`。
 */

interface ActivityLike {
  type?: string | null
  content?: string | null
  oldValue?: string | null
  newValue?: string | null
}

/** 动作类型 → 中文动词（未列出的兜底为「更新了字段」） */
const ACTION_TEXT: Record<string, string> = {
  CREATED: '创建了工作项',
  STATUS_CHANGED: '变更了状态',
  UPDATED: '更新了字段',
  ASSIGNEE_CHANGED: '变更了负责人',
  HANDOFF_REVOKED: '撤销了交接',
  COMMENT: '发表了评论',
  DELETED: '删除了工作项'
}

/** 单条动态的展示文案（评论走 content，由调用方决定是否渲染 HTML） */
export function activityText(a: ActivityLike): string {
  const type = a.type || ''
  if (type === 'STATUS_CHANGED') {
    const from = a.oldValue || '—'
    const to = a.newValue || '—'
    return `将 状态 从 ${from} 置为 ${to}`
  }
  // 交接要写清「从谁到谁」：只说「变更了负责人」的话，时间线看不出这次转给了谁
  if (type === 'ASSIGNEE_CHANGED') {
    return `将 负责人 从 ${a.oldValue || '—'} 置为 ${a.newValue || '—'}`
  }
  if (type === 'HANDOFF_REVOKED') {
    return a.newValue ? `撤销了交接，负责人退回 ${a.newValue}` : '撤销了交接，负责人已置空'
  }
  // 交接落点：旧值可能为空（原来不在任何迭代里），这时说「移入」比「从 — 置为」自然
  if (type === 'SPRINT_CHANGED') {
    if (!a.newValue) {
      return a.oldValue ? `移出迭代 ${a.oldValue}` : '调整了迭代'
    }
    return a.oldValue ? `将 迭代 从 ${a.oldValue} 置为 ${a.newValue}` : `移入迭代 ${a.newValue}`
  }
  if (type === 'LABEL_ADDED') {
    return a.newValue ? `打上标签 ${a.newValue}` : '添加了标签'
  }
  if (type === 'CREATED') {
    return ACTION_TEXT.CREATED
  }
  return ACTION_TEXT[type] || '更新了字段'
}

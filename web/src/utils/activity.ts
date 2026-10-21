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
  if (type === 'CREATED') {
    return ACTION_TEXT.CREATED
  }
  return ACTION_TEXT[type] || '更新了字段'
}

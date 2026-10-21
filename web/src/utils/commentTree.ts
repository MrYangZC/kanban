import type { IssueActivity } from '@/api/types'

/** 评论楼层节点：在动态记录上挂载子回复 */
export type CommentNode = IssueActivity & { children?: CommentNode[] }

/** 列表按时间倒序：最新的在最上面（评论、动态都一样） */
const byCreatedAtDesc = (x: IssueActivity, y: IssueActivity) =>
  (y.createdAt || '').localeCompare(x.createdAt || '')

/** 同一楼层内的回复仍按对话先后升序，先说的话在上面 */
const byCreatedAtAsc = (x: IssueActivity, y: IssueActivity) =>
  (x.createdAt || '').localeCompare(y.createdAt || '')

/**
 * 把扁平的动态列表组装为展示序列：
 * - `COMMENT` 按 `parentId` 组装为嵌套楼层（顶层 parentId 为空）；
 * - 父评论缺失（已被删除）的回复降级为顶层展示，避免内容整条消失；
 * - 非 `COMMENT`（状态/字段变更）保持平铺；
 * - 顶层（楼层根 + 动态）按创建时间**倒序**，最新的内容贴在最上面；
 * - 楼层内的子回复按创建时间**升序**，保持一问一答的阅读顺序。
 *
 * 纯函数，便于详情页/抽屉面板共用同一套楼层规则。
 */
export function buildCommentTree(activities: IssueActivity[]): (IssueActivity | CommentNode)[] {
  const byId = new Map<string, CommentNode>()
  for (const a of activities) {
    if (a.type !== 'COMMENT') continue
    byId.set(a.id, { ...a, children: [] })
  }
  // 楼层根 = 无父评论，或父评论已不在列表中（被删除）→ 降级为顶层，保证回复仍可见
  const isFloorRoot = (a: IssueActivity) => !a.parentId || !byId.has(a.parentId)
  for (const a of activities) {
    if (a.type !== 'COMMENT') continue
    const parent = a.parentId ? byId.get(a.parentId) : undefined
    if (parent) parent.children!.push(byId.get(a.id)!)
  }
  const roots = activities.filter((a) => a.type === 'COMMENT' && isFloorRoot(a)).map((a) => byId.get(a.id)!)
  roots.sort(byCreatedAtDesc)
  const sortChildren = (n: CommentNode) => {
    n.children!.sort(byCreatedAtAsc)
    n.children!.forEach(sortChildren)
  }
  roots.forEach(sortChildren)

  // 顶层评论作为楼层根，与状态活动一起按时间倒序平铺
  const result: (IssueActivity | CommentNode)[] = []
  for (const a of activities) {
    if (a.type !== 'COMMENT') result.push(a)
    else if (isFloorRoot(a)) result.push(byId.get(a.id)!)
  }
  result.sort(byCreatedAtDesc)
  return result
}

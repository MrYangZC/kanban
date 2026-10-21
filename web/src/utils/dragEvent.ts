import type { Issue } from '@/api/types'

/**
 * vuedraggable（vue.draggable.next）拖拽事件的结构化描述。
 *
 * vuedraggable 会把被拖拽元素的数据对象挂到 DOM 节点的 `__draggable_context.element` 上，
 * 这些是库内部字段，没有公开类型声明。此前 BoardView / DefectManageView 各自用 `any`
 * 并在模板/脚本里做深层断言，这里统一收敛为具名类型与读取函数，避免重复实现。
 */
type DraggableElement = Element & { __draggable_context?: { element?: unknown } }

/** 拖拽容器（列的根元素） */
type DraggableColumn = HTMLElement

export interface WorkItemDragEvent {
  /** 拖拽起点列 */
  from?: DraggableColumn
  /** 拖拽落点列 */
  to?: DraggableColumn
  /** 被拖拽的元素 */
  item?: DraggableElement
}

/** 被拖拽的工作项数据对象 */
export function draggedIssue(evt: WorkItemDragEvent): Issue | undefined {
  return evt.item?.__draggable_context?.element as Issue | undefined
}

/** 拖拽起点列的状态 ID（由列的 data-status-id 透传） */
export function dragFromStatusId(evt: WorkItemDragEvent): string | undefined {
  return evt.from?.dataset?.statusId
}

/** 拖拽落点列的状态 ID（由列的 data-status-id 透传） */
export function dropStatusId(evt: WorkItemDragEvent): string | undefined {
  return evt.to?.dataset?.statusId
}

interface IssueColumn {
  statusId: string
  issues: Issue[]
}

/** 按状态 ID 取出看板某一列当前绑定的工作项数组（即 draggable 的 :list） */
export function findColumnIssues(
  groups: Array<{ columns: IssueColumn[] }> | undefined,
  statusId: string | undefined
): Issue[] | undefined {
  if (!groups || !statusId) return undefined
  for (const group of groups) {
    const column = group.columns.find((c) => c.statusId === statusId)
    if (column) return column.issues
  }
  return undefined
}

/**
 * 落点前后的兄弟工作项，用于计算排序位置。
 *
 * 必须按「绑定数组」取邻居，不能直接读被拖 DOM 的兄弟节点：
 * 看板开启了 `force-fallback`，Sortable 用 ghost 克隆体表示落点，`evt.item` 仍是原节点，
 * 于是 `evt.item.previousElementSibling` 拿到的是**原位置**的邻居 —— 同列拖拽因此恒等于
 * 「原地不动」，跨列拖拽则把源列卡片当成落点邻居提交，被后端以「相邻卡片不属于目标状态列」拒绝。
 *
 * vuedraggable 在触发 `@end` 之前（onDragAdd / onDragRemove / onDragUpdate 走 nextTick 前一步）
 * 已把 `:list` 就地改成拖拽后的顺序，故按数组下标取邻居即可得到真实落点；
 * 数组里找不到（调用方未传列表）时退回 DOM 扫描，并跳过没有上下文的 ghost 克隆体。
 */
export function dragNeighbors(
  evt: WorkItemDragEvent,
  list?: Issue[]
): { before?: Issue; after?: Issue } {
  const movedId = draggedIssue(evt)?.id
  if (list && movedId) {
    const index = list.findIndex((i) => i.id === movedId)
    if (index !== -1) {
      return { before: list[index - 1], after: list[index + 1] }
    }
  }
  return neighborsFromDom(evt)
}

function neighborsFromDom(evt: WorkItemDragEvent): { before?: Issue; after?: Issue } {
  const container = evt.to
  if (!container) return {}
  const nodes = Array.from(container.children) as DraggableElement[]
  const idOf = (el: DraggableElement) => (el.__draggable_context?.element as Issue | undefined)?.id
  const movedId = draggedIssue(evt)?.id
  // 落点由 ghost 克隆体标示（它没有 __draggable_context）
  let anchor = movedId ? nodes.findIndex((el) => idOf(el) === movedId) : -1
  if (anchor === -1) anchor = nodes.findIndex((el) => !el.__draggable_context)
  if (anchor === -1) return {}
  const before = nodes.slice(0, anchor).reverse().find((el) => !!el.__draggable_context)
  const after = nodes.slice(anchor + 1).find((el) => !!el.__draggable_context)
  return {
    before: before?.__draggable_context?.element as Issue | undefined,
    after: after?.__draggable_context?.element as Issue | undefined
  }
}

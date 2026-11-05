import { ref, type Ref } from 'vue'
import { moveIssue } from '@/api'
import type { Issue } from '@/api/types'
import {
  draggedIssue,
  dragFromStatusId,
  dropStatusId,
  dragNeighbors,
  findColumnIssues,
  type WorkItemDragEvent
} from '@/utils/dragEvent'

/** 页面的分组结构：只需要能按 statusId 找到列与列内的绑定数组 */
type ColumnCarrier = { columns: Array<{ statusId: string; issues: Issue[] }> }

export interface BoardDragOptions {
  /** 状态流转白名单：fromStatusId → 允许到达的 toStatusId 集合（来自 useWorkflowTransition） */
  transitionMap: Ref<Record<string, Set<string>>>
  /** 当前渲染的分组；用于按"绑定数组"计算落点前后邻接卡片 */
  groups: () => ColumnCarrier[] | undefined
  /** 拖拽结束后重新拉取看板：成功与失败都要，用于把乐观移动的卡片回滚到服务端顺序 */
  reload: () => Promise<unknown> | unknown
}

/**
 * 看板拖拽编排（唯一实现）。
 *
 * <p>这套逻辑此前在 `WorkItemListPage`、`DefectManageView`、`BoardView` 里各写一遍
 * （`dragOverCol` 高亮、同列/跨列白名单预判、落点邻接计算、失败回滚重载）。
 * 三份实现只有注释措辞不同，却意味着改一次流转预判要改三处，且很容易漏掉某一页。
 *
 * <p>页面只负责：把 `transitionMap` / `groups` / `reload` 传进来，然后把 `onMove`、`onEnd`
 * 接到 `vuedraggable` 上，并用 `dragOverCol` 渲染允许/拒绝的高亮样式。
 */
export function useBoardDrag(options: BoardDragOptions) {
  /** 正在拖拽经过的列 + 是否允许落下（用于列高亮，null 表示没有拖拽进行中） */
  const dragOverCol = ref<{ statusId: string; allowed: boolean } | null>(null)

  function isAllowed(fromStatusId: string, toStatusId: string): boolean {
    // 同列排序恒允许；跨列必须存在本类别工作流的流转规则
    return fromStatusId === toStatusId || options.transitionMap.value[fromStatusId]?.has(toStatusId) === true
  }

  /** 拖拽过程中的实时校验：不合法返回 false，vuedraggable 会立即回弹，同时列高亮为拒绝色 */
  function onMove(evt: WorkItemDragEvent): boolean {
    const fromId = dragFromStatusId(evt)
    const toId = dropStatusId(evt)
    if (!fromId || !toId) return true
    const allowed = isAllowed(fromId, toId)
    if (dragOverCol.value?.statusId !== toId || dragOverCol.value?.allowed !== allowed) {
      dragOverCol.value = { statusId: toId, allowed }
    }
    return allowed
  }

  async function onEnd(event: WorkItemDragEvent) {
    dragOverCol.value = null
    const moved = draggedIssue(event)
    if (!moved) return
    const toStatusId = dropStatusId(event)
    if (!toStatusId) return

    if (!isAllowed(moved.statusId, toStatusId)) {
      // 服务端同样会拒绝，这里先按规则回弹，避免一次必然失败的请求
      await options.reload()
      return
    }

    const { before, after } = dragNeighbors(event, findColumnIssues(options.groups(), toStatusId))
    try {
      await moveIssue(moved.id, { statusId: toStatusId, beforeId: before?.id, afterId: after?.id })
    } catch {
      // 失败提示由接口层统一弹出；finally 重新拉取看板，把乐观移动的卡片回滚到服务端真实顺序
    } finally {
      await options.reload()
    }
  }

  return { dragOverCol, onMove, onEnd }
}

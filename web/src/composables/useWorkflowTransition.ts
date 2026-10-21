import { ref, type Ref } from 'vue'
import { listTransitions } from '@/api'
import type { WorkflowTransitionVO, IssueStatusVO, WorkItemCategory } from '@/api/types'

export interface WorkflowTransition {
  transitionMap: Ref<Record<string, Set<string>>>
  build: (transitions: WorkflowTransitionVO[]) => Record<string, Set<string>>
  load: (projectId: string, category?: WorkItemCategory) => Promise<void>
  statusOptionsFor: (fromStatusId: string, statuses: IssueStatusVO[]) => IssueStatusVO[]
}

export function useWorkflowTransition(): WorkflowTransition {
  const transitionMap = ref<Record<string, Set<string>>>({})

  function build(transitions: WorkflowTransitionVO[]): Record<string, Set<string>> {
    const map: Record<string, Set<string>> = {}
    for (const t of transitions) {
      if (!map[t.fromStatusId]) map[t.fromStatusId] = new Set()
      map[t.fromStatusId].add(t.toStatusId)
    }
    return map
  }

  /** category 决定取哪套工作流规则（任务/需求/缺陷各一套），缺省 BUG 仅作兼容 */
  async function load(projectId: string, category: WorkItemCategory = 'BUG') {
    try {
      const transitions = await listTransitions(projectId, category)
      transitionMap.value = build(transitions)
    } catch {
      transitionMap.value = {}
    }
  }

  /**
   * 可达状态选项。字典缺失时只返回当前状态，绝不退化成全部状态，
   * 避免把非法流转目标暴露给用户（选了之后才被后端 400 拒绝）。
   */
  function statusOptionsFor(fromStatusId: string, statuses: IssueStatusVO[]): IssueStatusVO[] {
    const reachable = transitionMap.value[fromStatusId]
    if (!reachable || !reachable.size) return statuses.filter((s) => s.id === fromStatusId)
    return statuses.filter((s) => s.id === fromStatusId || reachable.has(s.id))
  }

  return { transitionMap, build, load, statusOptionsFor }
}
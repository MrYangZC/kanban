import { ref, type Ref } from 'vue'
import { listTransitions } from '@/api'
import type { WorkflowTransitionVO, IssueStatusVO } from '@/api/types'

export interface WorkflowTransition {
  transitionMap: Ref<Record<string, Set<string>>>
  build: (transitions: WorkflowTransitionVO[]) => Record<string, Set<string>>
  load: (projectId: string) => Promise<void>
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

  async function load(projectId: string) {
    try {
      const transitions = await listTransitions(projectId)
      transitionMap.value = build(transitions)
    } catch {
      transitionMap.value = {}
    }
  }

  function statusOptionsFor(fromStatusId: string, statuses: IssueStatusVO[]): IssueStatusVO[] {
    const reachable = transitionMap.value[fromStatusId]
    if (!reachable || !reachable.size) return statuses
    return statuses.filter((s) => s.id === fromStatusId || reachable.has(s.id))
  }

  return { transitionMap, build, load, statusOptionsFor }
}
import http, { req } from './request'
import { C } from './cacheKeys'
import { cached } from '@/utils/cache'
import type {
  Issue,
  IssueStatusVO,
  SaveHandoffRequest,
  WorkflowColumnVO,
  WorkflowTransitionVO,
  WorkItemCategory
} from './types'

// ---- 状态节点 ----
/**
 * 指定类别工作流的状态节点。任务/需求/缺陷各有自己的节点，
 * 缺省 BUG 仅为兼容旧的单工作流调用点。
 */
export function listProjectStatuses(projectId: string, category: WorkItemCategory = 'BUG') {
  return cached<IssueStatusVO[]>(C.statuses(projectId, category), () =>
    req<IssueStatusVO[]>(http.get(`/v1/projects/${projectId}/statuses`, { params: { category } }))
  )
}

// ---- 流转规则 ----
/** 指定类别工作流的流转规则（看板拖拽蓝/红提示、详情状态下拉预校验） */
export function listTransitions(projectId: string, category: WorkItemCategory = 'BUG') {
  return req<WorkflowTransitionVO[]>(http.get(`/v1/projects/${projectId}/transitions`, { params: { category } }))
}

// ---- 看板列 + 交接配置 ----
/** 指定类别工作流的全部列 + 每列交接配置（看板列头、详情状态下拉共用） */
export function listWorkflowColumns(projectId: string, category: WorkItemCategory = 'TASK') {
  return req<WorkflowColumnVO[]>(http.get(`/v1/projects/${projectId}/workflow-columns`, { params: { category } }))
}

/** 保存某一列的交接配置；handoffType=NONE 即清除 */
export function saveWorkflowHandoff(
  projectId: string,
  statusId: string,
  payload: SaveHandoffRequest,
  category: WorkItemCategory = 'TASK'
) {
  return req<WorkflowColumnVO>(
    http.put(`/v1/projects/${projectId}/workflow-columns/${statusId}/handoff`, payload, { params: { category } })
  )
}

/** 撤销最近一次工作流交接：负责人退回交接前的人 */
export function revokeHandoff(issueId: string) {
  return req<Issue>(http.post(`/v1/issues/${issueId}/handoff/revoke`))
}

import http, { req } from './request'
import { C } from './cacheKeys'
import { cached, invalidate } from '@/utils/cache'
import type {
  AttachmentVO,
  BoardGroupView,
  DashboardVO,
  Issue,
  IssueActivity,
  IssueFilterPayload,
  IssueTimeline,
  LabelVO,
  LinkVO,
  PageResult,
  UploadImageVO
} from './types'

// ---- 工作项主体 ----
export function createIssue(payload: {
  projectId: string
  issueTypeId?: string
  issueTypeCode?: string
  statusId: string
  sprintId?: string
  title: string
  description?: string
  priority?: string
  severity?: string
  module?: string
  assigneeId?: string
  verifierId?: string
  fixVersion?: string
  source?: string
  iteration?: string
  participants?: string[]
  ccUsers?: string[]
  planStart?: string
  noFixReason?: string
}) {
  return req<Issue>(http.post('/v1/issues', payload))
}

export function moveIssue(id: string, payload: { statusId: string; beforeId?: string; afterId?: string }) {
  return req<Issue>(http.patch(`/v1/issues/${id}/position`, payload))
}

export function listIssueChildren(issueId: string) {
  return req<Issue[]>(http.get(`/v1/issues/${issueId}/children`))
}

export interface UpdateIssuePayload {
  title?: string
  description?: string
  priority?: string
  issueTypeId?: string
  statusId?: string
  sprintId?: string
  assigneeId?: string
  verifierId?: string
  dueDate?: string
  severity?: string
  module?: string
  fixVersion?: string
  source?: string
  archived?: number
  iteration?: string
  participants?: string[]
  ccUsers?: string[]
  planStart?: string
  noFixReason?: string
  /** 需要显式置空的字段名集合（配合 assigneeId: null 等使用） */
  clearFields?: string[]
}

export function updateIssue(id: string, payload: UpdateIssuePayload) {
  return req<Issue>(http.put(`/v1/issues/${id}`, payload))
}

// ---- 列表筛选 / 导出 ----
export function filterIssues(payload: IssueFilterPayload) {
  return req<PageResult<Issue>>(http.post('/v1/issues/filter', payload))
}

/**
 * 工作项导出地址（CSV）。
 * 直接用 window.open 触发浏览器下载：会话是 Cookie，无需额外请求头。
 * 注意必须带上 baseURL 的 /api 前缀（window.open 不走 axios）。
 */
export function exportIssuesUrl(projectId: string, params: {
  types?: string[]
  scope?: string
  keyword?: string
} = {}) {
  const query = new URLSearchParams()
  if (params.types?.length) query.set('types', params.types.join(','))
  if (params.scope) query.set('scope', params.scope)
  if (params.keyword) query.set('keyword', params.keyword)
  const suffix = query.toString() ? `?${query.toString()}` : ''
  return `/api/v1/projects/${projectId}/issues/export${suffix}`
}

// ---- 看板 / 度量 ----
/**
 * 分组看板。**必须显式传 category**：
 * 后端 `/board` 不带类别时按 TASK 工作流返回列（4 列），带 REQUIREMENT/BUG 才是对应类别的列（6 列），
 * 少传会得到「列数对不上、卡片全空」的假象。此前还有一个不带 category 的 getBoard()，已删除。
 */
export function getBoardGrouped(projectId: string, groupBy?: string, category?: string) {
  return req<BoardGroupView>(http.get(`/v1/projects/${projectId}/board/grouped`, { params: { groupBy, category } }))
}

export function getDashboard(projectId: string) {
  return req<DashboardVO>(http.get(`/v1/projects/${projectId}/dashboard`))
}

// ---- 时间线 / 评论 ----
/** 时间线分页：page=1 是最新一页；"加载更早"取 page+1 */
export function listActivities(issueId: string, page = 1, size = 50) {
  return req<IssueTimeline>(http.get(`/v1/issues/${issueId}/activities`, { params: { page, size } }))
}

export function addComment(issueId: string, content: string, parentId?: string | null) {
  return req<IssueActivity>(http.post(`/v1/issues/${issueId}/comments`, { content, parentId: parentId ?? null }))
}

export function deleteComment(issueId: string, commentId: string) {
  return req<void>(http.delete(`/v1/issues/${issueId}/comments/${commentId}`))
}

/**
 * 删除工作项。
 *
 * <p>后端 {@code DELETE /v1/issues/{id}} 一直存在（会级联删除子任务、评论、附件与标签），
 * 但前端此前既没有封装也没有入口，导致页面上"找不到删除按钮"。
 * 权限校验由后端 {@code Permission.ISSUE_DELETE} 负责，前端按同一权限码控制按钮可见性。
 */
export function deleteIssue(id: string) {
  return req<void>(http.delete(`/v1/issues/${id}`))
}

/** 批量删除结果：删除/上提/跳过/失败四类计数 */
export interface BatchDeleteResult {
  deleted: number
  /** 因父节点被删而保留、并上提到上一级的子任务数 */
  promoted: number
  skipped: number
  failed: number
  errors: string[]
}

/**
 * 批量删除工作项（一次请求，避免逐条调用）。
 *
 * <p>必须带 projectId：后端权限拦截器从请求体解析项目来校验 issue:delete，
 * 并据此拒绝删除其它项目下的工作项。
 */
export function batchDeleteIssues(projectId: string, ids: string[]) {
  return req<BatchDeleteResult>(http.post('/v1/issues/batch-delete', { projectId, ids }))
}

// ---- 附件 ----
export function listAttachments(issueId: string) {
  return req<AttachmentVO[]>(http.get(`/v1/issues/${issueId}/attachments`))
}

/** 真实文件上传（multipart），返回含 url 的附件记录 */
export function uploadAttachment(issueId: string, file: File) {
  const form = new FormData()
  form.append('file', file)
  return req<AttachmentVO>(http.post(`/v1/issues/${issueId}/attachments/upload`, form))
}

export function addAttachment(issueId: string, fileName: string, contentType?: string, fileSize?: number) {
  return req<AttachmentVO>(http.post(`/v1/issues/${issueId}/attachments`, { fileName, contentType, fileSize }))
}

export function deleteAttachment(issueId: string, attachmentId: string) {
  return req<void>(http.delete(`/v1/issues/${issueId}/attachments/${attachmentId}`))
}

// ---- 通用图片上传 ----
/**
 * 通用图片上传（评论/描述内嵌图、头像）。
 * 传 projectId 时服务端登记项目归属，仅该项目成员可读取；不传则作为全站共享图片。
 */
export function uploadImage(file: File, projectId?: string) {
  const form = new FormData()
  form.append('file', file)
  if (projectId) form.append('projectId', projectId)
  return req<UploadImageVO>(http.post('/v1/upload/image', form))
}

// ---- 关联 ----
export function listLinks(issueId: string) {
  return req<LinkVO[]>(http.get(`/v1/issues/${issueId}/links`))
}

export function addLink(issueId: string, targetIssueId: string, relationType: string) {
  return req<LinkVO>(http.post(`/v1/issues/${issueId}/links`, { targetIssueId, relationType }))
}

export function deleteLink(issueId: string, linkId: string) {
  return req<void>(http.delete(`/v1/issues/${issueId}/links/${linkId}`))
}

// ---- 工作项标签 ----
export function listIssueTags(issueId: string) {
  return req<LabelVO[]>(http.get(`/v1/issues/${issueId}/tags`))
}

export function addIssueTag(issueId: string, projectId: string, name: string, color?: string) {
  return req<void>(http.post(`/v1/issues/${issueId}/tags`, { projectId, name, color }))
}

export function removeIssueTag(issueId: string, labelId: string) {
  return req<void>(http.delete(`/v1/issues/${issueId}/tags/${labelId}`))
}

// ---- 项目标签（选用全局标签） ----
export function listProjectTags(projectId: string) {
  return cached<LabelVO[]>(C.tags(projectId), () => req<LabelVO[]>(http.get(`/v1/projects/${projectId}/tags`)))
}

/** 全局标签库 */
export function listGlobalLabels() {
  return req<LabelVO[]>(http.get(`/v1/labels`))
}

export function addProjectLabel(projectId: string, labelId: string) {
  return req<void>(http.post(`/v1/projects/${projectId}/labels/${labelId}`)).then((r) => {
    invalidate(C.tags(projectId))
    return r
  })
}

export function removeProjectLabel(projectId: string, labelId: string) {
  return req<void>(http.delete(`/v1/projects/${projectId}/labels/${labelId}`)).then((r) => {
    invalidate(C.tags(projectId))
    return r
  })
}

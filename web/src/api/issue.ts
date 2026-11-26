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
 * 导出当前筛选结果为 CSV，并直接触发浏览器下载。
 *
 * <p>不再用 `window.open(url)`：认证走 `Authorization: Bearer`，而 window.open 是浏览器原生请求，
 * 带不上拦截器注入的请求头（过去靠 Cookie 兜底，改造后必然 401）。这里改用 axios 取 blob，
 * 顺带能把整份筛选条件作为 query 参数下发，保证「列表看到的」与「导出的」是同一套条件。
 *
 * @param payload 列表页当前的筛选条件（与 filterIssues 的入参同源）
 * @param types   工作项类型码；为空表示不按类型过滤
 * @returns 总命中数、本次实际行数、是否因单次上限被截断
 */
export async function exportIssuesCsv(projectId: string, payload: IssueFilterPayload, types: string[] = []) {
  // 只搬运后端 IssueFilterRequest 认的筛选字段；分页/排序/类型码在这里单独处理
  const filterKeys = [
    'scope', 'keyword', 'statusId', 'statusCategory', 'priority', 'severity',
    'labelId', 'sprintId', 'assigneeId', 'reporterId',
    'createdFrom', 'createdTo', 'updatedFrom', 'updatedTo', 'dueFrom', 'dueTo'
  ] as const
  const params: Record<string, string> = {}
  for (const key of filterKeys) {
    const value = payload[key]
    if (typeof value === 'string' && value) params[key] = value
  }
  if (payload.overdue) params.overdue = 'true'
  if (types.length) params.types = types.join(',')

  const resp = await http.get<Blob>(`/v1/projects/${projectId}/issues/export`, {
    params,
    responseType: 'blob'
  })
  const headers = resp.headers as Record<string, string>
  const filename = filenameFromDisposition(headers['content-disposition']) || `issues-${Date.now()}.csv`
  saveBlob(resp.data, filename)
  return {
    filename,
    total: Number(headers['x-export-total'] ?? 0),
    rows: Number(headers['x-export-rows'] ?? 0),
    truncated: String(headers['x-export-truncated'] ?? 'false') === 'true'
  }
}

/** 后端用 Content-Disposition 下发文件名（非 ASCII 走 filename*=UTF-8''），两种写法都兼容 */
function filenameFromDisposition(disposition?: string): string | null {
  if (!disposition) return null
  const utf8 = /filename\*=UTF-8''([^;]+)/i.exec(disposition)
  if (utf8) {
    try {
      return decodeURIComponent(utf8[1])
    } catch {
      return utf8[1]
    }
  }
  const plain = /filename="?([^";]+)"?/i.exec(disposition)
  return plain ? plain[1] : null
}

/** 用临时 a 标签触发下载；objectURL 在点击后再释放，避免部分浏览器取消下载 */
function saveBlob(blob: Blob, filename: string) {
  const url = URL.createObjectURL(blob)
  const a = document.createElement('a')
  a.href = url
  a.download = filename
  document.body.appendChild(a)
  a.click()
  a.remove()
  setTimeout(() => URL.revokeObjectURL(url), 0)
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

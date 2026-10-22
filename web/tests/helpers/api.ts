import { expect, type APIRequestContext } from '@playwright/test'

/** 后端接口根（直连后端，绕过前端代理） */
export const API = 'http://localhost:9085/api/v1'
export const ORIGIN = 'http://localhost:9085'

export const ADMIN = { username: 'admin', password: 'admin123' }

export interface Envelope<T> {
  code: number
  message?: string
  data: T
}

export interface IssueStatusVO { id: string; name: string; code: string; category: string }
export interface IssueVO { id: string; issueKey: string; title: string; issueTypeCode: string; statusId: string }
/** 工作流流转规则（from → to） */
export interface TransitionVO { fromStatusId: string; toStatusId: string; name: string }
export interface IssueActivityVO {
  id: string
  type: string
  actorName: string
  parentId?: string | null
  deleted?: number | null
  content: string | null
  oldValue: string | null
  newValue: string | null
  createdAt: string
}
export interface AttachmentVO {
  id: string
  issueId: string
  fileName: string
  contentType: string
  fileSize: number
  url?: string
  uploaderName: string
  createdAt: string
}
export interface LinkVO {
  id: string
  issueId: string
  targetIssueId: string
  relationType: string
  targetKey: string
  targetTitle: string
  createdAt: string
}
export interface LabelVO { id: string; name: string; color?: string }

// ---------------------------------------------------------------------------
// 认证：Playwright 的 request 夹具在 beforeAll 与各用例之间是不同实例，
// cookie 不共享，因此这里按上下文懒登录，并在遇到 401 时重登一次再重试。
// ---------------------------------------------------------------------------
const authed = new WeakSet<APIRequestContext>()

export async function loginApi(
  request: APIRequestContext,
  username = ADMIN.username,
  password = ADMIN.password
) {
  const resp = await request.post(`${API}/users/login`, { data: { username, password } })
  const body = await resp.json()
  expect(body.code, `API 登录 ${username} 应成功`).toBe(200)
  authed.add(request)
  return body.data as { id: string; username: string; displayName?: string; token: string }
}

async function ensureLogin(request: APIRequestContext) {
  if (authed.has(request)) return
  await loginApi(request)
}

type Method = 'get' | 'post' | 'put' | 'delete'

interface CallOptions {
  label: string
  data?: unknown
  multipart?: Record<string, unknown>
  params?: Record<string, string>
}

function send(request: APIRequestContext, method: Method, url: string, opts: CallOptions) {
  const init = { data: opts.data, multipart: opts.multipart, params: opts.params } as never
  switch (method) {
    case 'get': return request.get(url, { params: opts.params })
    case 'post': return request.post(url, init)
    case 'put': return request.put(url, init)
    case 'delete': return request.delete(url, init)
  }
}

/** 统一解包 {code,data,message}；断言 code=200，失败信息带上后端 message 便于定位 */
async function call<T>(
  request: APIRequestContext,
  method: Method,
  url: string,
  opts: CallOptions
): Promise<T> {
  await ensureLogin(request)
  for (let attempt = 0; attempt < 2; attempt++) {
    const body = (await (await send(request, method, url, opts)).json()) as Envelope<T>
    if (body.code === 401 && attempt === 0) {
      authed.delete(request)
      await ensureLogin(request)
      continue
    }
    expect(body.code, `${opts.label} 应成功（后端返回：${body.message ?? '无 message'}）`).toBe(200)
    return body.data
  }
  throw new Error(`${opts.label}：认证重试后仍失败`)
}

export const apiGet = <T>(request: APIRequestContext, url: string, label: string, params?: Record<string, string>) =>
  call<T>(request, 'get', url, { label, params })
export const apiPost = <T>(request: APIRequestContext, url: string, label: string, data?: unknown) =>
  call<T>(request, 'post', url, { label, data })
export const apiPut = <T>(request: APIRequestContext, url: string, label: string, data?: unknown) =>
  call<T>(request, 'put', url, { label, data })
export const apiDelete = <T>(request: APIRequestContext, url: string, label: string) =>
  call<T>(request, 'delete', url, { label })

// ---------------------------------------------------------------------------
// 基础数据
// ---------------------------------------------------------------------------

/**
 * 幂等准备测试账号：先按用户名清理残留。
 * 用例里的 finally 清理一旦没生效（例如断言先失败），下一轮 POST /users 会返回
 * 「用户名已存在」，用例被迫 skip，看起来像环境问题，实际是数据残留。
 */
export async function deleteUsersByName(request: APIRequestContext, username: string) {
  const list = await apiGet<{ id: string; username: string }[]>(request, `${API}/users`, '查询用户列表')
  const rows = Array.isArray(list)
    ? list
    : ((list as unknown as { records?: { id: string; username: string }[] })?.records ?? [])
  for (const u of rows) {
    if (u.username === username) {
      await apiDelete<void>(request, `${API}/users/${u.id}`, `清理残留用户 ${username}`)
    }
  }
}

/** 建项目：已存在同 key 时先删再建，保证每轮从干净数据开始 */
export async function createProject(request: APIRequestContext, key: string, name: string) {
  const payload = { projectKey: key, name, description: '' }
  try {
    return (await apiPost<{ id: string }>(request, `${API}/projects`, `创建项目 ${name}`, payload)).id
  } catch {
    const list = await apiGet<{ id: string; projectKey: string }[]>(request, `${API}/projects`, '查询项目列表')
    const stale = (list || []).find((p) => p.projectKey === key)
    if (stale) await apiDelete<void>(request, `${API}/projects/${stale.id}`, `清理旧项目 ${key}`)
    return (await apiPost<{ id: string }>(request, `${API}/projects`, `重建项目 ${name}`, payload)).id
  }
}

/**
 * 删除测试项目。
 *
 * 必须走 apiDelete 而不是裸 `request.delete`：`request` 是 test 级 fixture，
 * `test.afterAll` 里拿到的是新实例、没有登录 cookie，裸删除会被 401 静默吞掉，
 * 项目及其工作项就长期堆在库里（历史上 TCOL / TPLN / TBDG / TUIW 都是这么残留的）。
 */
export const deleteProject = (request: APIRequestContext, projectId: string) =>
  apiDelete<void>(request, `${API}/projects/${projectId}`, '删除测试项目')

export async function listProjectStatuses(request: APIRequestContext, projectId: string) {
  const statuses = await apiGet<IssueStatusVO[]>(
    request, `${API}/projects/${projectId}/statuses`, '查询项目状态'
  )
  expect(statuses.length, '项目应至少有一个工作流状态').toBeGreaterThan(0)
  return statuses
}

export async function firstStatusId(request: APIRequestContext, projectId: string) {
  return (await listProjectStatuses(request, projectId))[0].id
}

/** 项目默认工作流的流转规则（后端 WorkflowController：/projects/{id}/transitions） */
export const listTransitions = (request: APIRequestContext, projectId: string) =>
  apiGet<TransitionVO[]>(request, `${API}/projects/${projectId}/transitions`, '查询工作流流转规则')

// ---------------------------------------------------------------------------
// 项目成员（RBAC：一人可多角色）
// ---------------------------------------------------------------------------

export interface ProjectMemberVO {
  id: string
  userId: string
  username: string
  displayName: string
  roles: string[]
}

export const listProjectMembers = (request: APIRequestContext, projectId: string) =>
  apiGet<ProjectMemberVO[]>(request, `${API}/projects/${projectId}/members`, '查询项目成员')

export const addProjectMember = (
  request: APIRequestContext, projectId: string, userId: string, roles: string[] = ['MEMBER']
) =>
  apiPost<ProjectMemberVO>(request, `${API}/projects/${projectId}/members`, `添加成员 ${userId}`, { userId, roles })

/** 覆盖式设置成员角色（后端 PUT /members/{userId}/roles，请求体是纯数组） */
export const updateProjectMemberRoles = (
  request: APIRequestContext, projectId: string, userId: string, roles: string[]
) =>
  apiPut<ProjectMemberVO>(
    request, `${API}/projects/${projectId}/members/${userId}/roles`, `更新成员角色 ${userId}`, roles
  )

export const removeProjectMember = (request: APIRequestContext, projectId: string, userId: string) =>
  apiDelete<void>(request, `${API}/projects/${projectId}/members/${userId}`, `移除成员 ${userId}`)

/** 幂等创建测试账号（先清同名残留），返回 userId */
export async function createUser(
  request: APIRequestContext,
  username: string,
  displayName: string,
  password = 'uitest123'
) {
  await deleteUsersByName(request, username)
  const created = await apiPost<{ id: string }>(request, `${API}/users`, `创建用户 ${username}`, {
    username, displayName, password
  })
  return created.id
}

/** 按用户名查账号（残留账号无法删除时可直接复用，保证用例可重复执行） */
export async function findUserByName(request: APIRequestContext, username: string) {
  const list = await apiGet<{ id: string; username: string }[]>(request, `${API}/users`, '查询用户列表')
  const rows = Array.isArray(list)
    ? list
    : ((list as unknown as { records?: { id: string; username: string }[] })?.records ?? [])
  return rows.find((u) => u.username === username)
}

export async function createIssue(
  request: APIRequestContext,
  init: {
    projectId: string
    statusId: string
    issueTypeCode: string
    title: string
    description?: string
    priority?: string
    severity?: string
  }
) {
  return apiPost<IssueVO>(request, `${API}/issues`, `创建 ${init.title}`, { priority: 'P2', ...init })
}

export async function updateIssue(request: APIRequestContext, issueId: string, patch: Record<string, unknown>) {
  return apiPut<IssueVO>(request, `${API}/issues/${issueId}`, `更新工作项 ${issueId}`, patch)
}

// ---------------------------------------------------------------------------
// 协作域：评论 / 动态 / 附件 / 关联 / 标签
// ---------------------------------------------------------------------------

export const listActivities = (request: APIRequestContext, issueId: string) =>
  apiGet<IssueActivityVO[]>(request, `${API}/issues/${issueId}/activities`, '查询动态流')

export const addCommentViaApi = (request: APIRequestContext, issueId: string, content: string) =>
  apiPost<IssueActivityVO>(request, `${API}/issues/${issueId}/comments`, '接口发评论', { content })

export const deleteCommentViaApi = (request: APIRequestContext, issueId: string, commentId: string) =>
  apiDelete<void>(request, `${API}/issues/${issueId}/comments/${commentId}`, '接口删评论')

export const listAttachments = (request: APIRequestContext, issueId: string) =>
  apiGet<AttachmentVO[]>(request, `${API}/issues/${issueId}/attachments`, '查询附件')

/** 直接 multipart 上传（用于构造用例前置数据，不替代 UI 上传用例） */
export const uploadAttachmentViaApi = (
  request: APIRequestContext,
  issueId: string,
  file: { name: string; mimeType: string; buffer: Buffer }
) => call<AttachmentVO>(request, 'post', `${API}/issues/${issueId}/attachments/upload`, {
  label: `接口上传 ${file.name}`,
  multipart: { file: { name: file.name, mimeType: file.mimeType, buffer: file.buffer } }
})

export const listLinks = (request: APIRequestContext, issueId: string) =>
  apiGet<LinkVO[]>(request, `${API}/issues/${issueId}/links`, '查询关联工作项')

export const listIssueTags = (request: APIRequestContext, issueId: string) =>
  apiGet<LabelVO[]>(request, `${API}/issues/${issueId}/tags`, '查询工作项标签')

/** 校验附件下载地址真实可访问 */
export async function probeDownload(request: APIRequestContext, url: string) {
  const resp = await request.get(`${ORIGIN}${url}`)
  return { status: resp.status(), contentType: resp.headers()['content-type'] ?? '' }
}

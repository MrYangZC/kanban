import http, { req, type R } from './request'
import { cached, invalidate, readMyPermissions, writeMyPermissions, clearMyPermissions } from '@/utils/cache'
import type { Project, Issue, BoardView, LoginVO, LabelVO, IssueActivity, AttachmentVO, LinkVO, UserVO, BoardGroupView, DashboardVO, ProjectMember, MemberCandidate, PageResult, IssueStatusVO, UploadImageVO, WorkflowTransitionVO, MilestoneVO, AnnouncementVO, ProjectActivityVO, ProjectCategoryKey, ProjectCategoryCounts, ProjectFilterRequest, ProfileVO, UpdateProfileRequest, ChangePasswordRequest, SprintVO, RiskVO, RbacRole, RbacMenu, PermissionDef, WorkItemCategory } from './types'

// 内存缓存 key（项目类数据按 projectId 隔离，会话内仅请求一次）
// 状态字典按类别分开缓存：任务/需求/缺陷是三套节点，混用一个 key 会串数据
const C = {
  users: 'users',
  project: (id: string) => `project:${id}`,
  members: (id: string) => `members:${id}`,
  statuses: (id: string, category: WorkItemCategory) => `statuses:${id}:${category}`,
  tags: (id: string) => `tags:${id}`
}

// ---- user ----
export function login(username: string, password: string) {
  return req<LoginVO>(http.post('/v1/users/login', { username, password }))
}
/** 人员下拉用的全量简表（仅管理员可读，不参与分页） */
export function listUsers() {
  return cached<UserVO[]>(C.users, () => req<UserVO[]>(http.get('/v1/users/options')))
}
/** 用户管理列表：分页 + 关键字检索 */
export function listUsersPage(params?: { page?: number; size?: number; keyword?: string }) {
  return req<PageResult<UserVO>>(http.get('/v1/users', { params }))
}
export function createUser(payload: {
  username: string
  displayName: string
  email?: string
  avatarUrl?: string
  password?: string
}) {
  return req<UserVO>(http.post('/v1/users', payload)).then((r) => {
    invalidate(C.users)
    return r
  })
}
export function updateUser(id: string, payload: {
  displayName?: string
  email?: string
  avatarUrl?: string
  enabled?: boolean
  password?: string
}) {
  return req<UserVO>(http.put(`/v1/users/${id}`, payload)).then((r) => {
    invalidate(C.users)
    return r
  })
}
export function deleteUser(id: string) {
  return req<void>(http.delete(`/v1/users/${id}`)).then((r) => {
    invalidate(C.users)
    return r
  })
}

// ---- profile（当前登录用户个人中心） ----
export function getMyProfile() {
  return req<ProfileVO>(http.get('/v1/users/me'))
}
export function updateMyProfile(payload: UpdateProfileRequest) {
  return req<ProfileVO>(http.put('/v1/users/me', payload))
}
export function changeMyPassword(payload: ChangePasswordRequest) {
  return req<void>(http.put('/v1/users/me/password', payload))
}

/**
 * 登出：销毁服务端 HttpSession。
 * 仅清本地凭证会导致服务端会话仍然有效（会话复用风险）。
 */
export function logout() {
  return req<void>(http.post('/v1/users/logout'))
}

// ---- project ----
export function listProjects(category?: ProjectCategoryKey) {
  return req<Project[]>(http.get('/v1/projects', { params: { category } }))
}
export function filterProjects(params: ProjectFilterRequest) {
  return req<PageResult<Project>>(http.post('/v1/projects/filter', params))
}
export function getProjectCategoryCounts() {
  return req<ProjectCategoryCounts>(http.get('/v1/projects/categories'))
}
export function createProject(projectKey: string, name: string, description = '') {
  return req<Project>(http.post('/v1/projects', { projectKey, name, description }))
}
export function getProject(id: string) {
  return cached<Project>(C.project(id), () => req<Project>(http.get(`/v1/projects/${id}`)))
}
export function updateProject(id: string, payload: {
  name?: string
  description?: string
  avatarUrl?: string
  status?: string
  leadUserId?: string
}) {
  return req<Project>(http.put(`/v1/projects/${id}`, payload)).then((r) => {
    invalidate(C.project(id))
    return r
  })
}
export function favoriteProject(id: string) {
  return req<void>(http.post(`/v1/projects/${id}/favorite`)).then((r) => {
    // getProject 走了内存缓存，收藏后 favorited 字段会陈旧
    invalidate(C.project(id))
    return r
  })
}
export function unfavoriteProject(id: string) {
  return req<void>(http.delete(`/v1/projects/${id}/favorite`)).then((r) => {
    invalidate(C.project(id))
    return r
  })
}
export function archiveProject(id: string) {
  return req<void>(http.put(`/v1/projects/${id}/archive`)).then((r) => {
    // 归档改变 status，缓存不失效会导致详情页继续显示"进行中"
    invalidate(C.project(id))
    return r
  })
}
export function unarchiveProject(id: string) {
  return req<void>(http.put(`/v1/projects/${id}/unarchive`)).then((r) => {
    invalidate(C.project(id))
    return r
  })
}
export function deleteProject(id: string) {
  return req<void>(http.delete(`/v1/projects/${id}`)).then((r) => {
    // 项目已删除，连带清掉详情与成员缓存，避免残留数据被复用
    invalidate(C.project(id))
    invalidate(C.members(id))
    ;(['TASK', 'REQUIREMENT', 'BUG'] as WorkItemCategory[]).forEach((cat) => invalidate(C.statuses(id, cat)))
    invalidate(C.tags(id))
    clearMyPermissions(id)
    return r
  })
}

// ---- project members ----
export function listProjectMembers(projectId: string) {
  return cached<ProjectMember[]>(C.members(projectId), () => req<ProjectMember[]>(http.get(`/v1/projects/${projectId}/members`)))
}
export function addProjectMember(projectId: string, userId: string, roles: string[] = ['MEMBER']) {
  return req<ProjectMember>(http.post(`/v1/projects/${projectId}/members`, { userId, roles })).then((r) => {
    invalidate(C.members(projectId))
    clearMyPermissions(projectId)
    return r
  })
}
/** 「添加成员」检索候选用户（未加入本项目），按账号/显示名模糊匹配；结果随关键字变化，不做缓存 */
export function getMemberCandidates(projectId: string, keyword?: string) {
  return req<MemberCandidate[]>(
    http.get(`/v1/projects/${projectId}/members/candidates`, { params: keyword ? { keyword } : {} })
  )
}
export function removeProjectMember(projectId: string, userId: string) {
  return req<void>(http.delete(`/v1/projects/${projectId}/members/${userId}`)).then((r) => {
    invalidate(C.members(projectId))
    clearMyPermissions(projectId)
    return r
  })
}
export function updateProjectMemberRoles(projectId: string, userId: string, roles: string[]) {
  return req<ProjectMember>(http.put(`/v1/projects/${projectId}/members/${userId}/roles`, roles)).then((r) => {
    invalidate(C.members(projectId))
    clearMyPermissions(projectId)
    return r
  })
}

// ---- board ----
export function getBoard(projectId: string) {
  return req<BoardView>(http.get(`/v1/projects/${projectId}/board`))
}
export function getBoardGrouped(projectId: string, groupBy?: string, category?: string) {
  return req<BoardGroupView>(http.get(`/v1/projects/${projectId}/board/grouped`, { params: { groupBy, category } }))
}
export function getDashboard(projectId: string) {
  return req<DashboardVO>(http.get(`/v1/projects/${projectId}/dashboard`))
}

// ---- issue ----
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

// ---- issue filter ----
export function filterIssues(payload: {
  projectId: string
  scope?: string
  statusId?: string
  priority?: string
  severity?: string
  labelId?: string
  keyword?: string
  /** 状态类别过滤：TODO / IN_PROGRESS / DONE（度量页点统计数字跳转时用） */
  statusCategory?: string
  /** 只看逾期（未完成且已过截止日） */
  overdue?: boolean
  issueTypeCodes?: string[]
  /** 迭代过滤：为空表示不过滤，后端会跨 task/requirement/bug 三表查询 */
  sprintId?: string
  page?: number
  size?: number
  sortField?: string
  sortOrder?: string
}) {
  return req<PageResult<Issue>>(http.post('/v1/issues/filter', payload))
}

// ---- upload ----
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

// ---- activities / comments ----
export function listActivities(issueId: string) {
  return req<IssueActivity[]>(http.get(`/v1/issues/${issueId}/activities`))
}
export function addComment(issueId: string, content: string, parentId?: string | null) {
  return req<IssueActivity>(http.post(`/v1/issues/${issueId}/comments`, { content, parentId: parentId ?? null }))
}
export function deleteComment(issueId: string, commentId: string) {
  return req<void>(http.delete(`/v1/issues/${issueId}/comments/${commentId}`))
}

// ---- attachments ----
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

// ---- links ----
export function listLinks(issueId: string) {
  return req<LinkVO[]>(http.get(`/v1/issues/${issueId}/links`))
}
export function addLink(issueId: string, targetIssueId: string, relationType: string) {
  return req<LinkVO>(http.post(`/v1/issues/${issueId}/links`, { targetIssueId, relationType }))
}
export function deleteLink(issueId: string, linkId: string) {
  return req<void>(http.delete(`/v1/issues/${issueId}/links/${linkId}`))
}

// ---- status ----
/**
 * 指定类别工作流的状态节点。任务/需求/缺陷各有自己的节点，
 * 缺省 BUG 仅为兼容旧的单工作流调用点。
 */
export function listProjectStatuses(projectId: string, category: WorkItemCategory = 'BUG') {
  return cached<IssueStatusVO[]>(C.statuses(projectId, category), () =>
    req<IssueStatusVO[]>(http.get(`/v1/projects/${projectId}/statuses`, { params: { category } }))
  )
}

// ---- workflow ----
/** 指定类别工作流的流转规则（看板拖拽蓝/红提示、详情状态下拉预校验） */
export function listTransitions(projectId: string, category: WorkItemCategory = 'BUG') {
  return req<WorkflowTransitionVO[]>(http.get(`/v1/projects/${projectId}/transitions`, { params: { category } }))
}

// ---- tags ----
export function listProjectTags(projectId: string) {
  return cached<LabelVO[]>(C.tags(projectId), () => req<LabelVO[]>(http.get(`/v1/projects/${projectId}/tags`)))
}
export function listIssueTags(issueId: string) {
  return req<LabelVO[]>(http.get(`/v1/issues/${issueId}/tags`))
}
export function addIssueTag(issueId: string, projectId: string, name: string, color?: string) {
  return req<void>(http.post(`/v1/issues/${issueId}/tags`, { projectId, name, color }))
}
export function removeIssueTag(issueId: string, labelId: string) {
  return req<void>(http.delete(`/v1/issues/${issueId}/tags/${labelId}`))
}
// 全局标签库
export function listGlobalLabels() {
  return req<LabelVO[]>(http.get(`/v1/labels`))
}
// 项目选用全局标签
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

// ---- milestones ----
export function listMilestones(projectId: string) {
  return req<MilestoneVO[]>(http.get(`/v1/projects/${projectId}/milestones`))
}
export function createMilestone(projectId: string, payload: { name: string; description?: string; dueDate?: string }) {
  return req<MilestoneVO>(http.post(`/v1/projects/${projectId}/milestones`, payload))
}
export function updateMilestone(projectId: string, id: string, payload: { name?: string; description?: string; dueDate?: string; status?: string; progress?: number }) {
  return req<MilestoneVO>(http.put(`/v1/projects/${projectId}/milestones/${id}`, payload))
}
export function deleteMilestone(projectId: string, id: string) {
  return req<void>(http.delete(`/v1/projects/${projectId}/milestones/${id}`))
}

// ---- announcements ----
export function listAnnouncements(projectId: string) {
  return req<AnnouncementVO[]>(http.get(`/v1/projects/${projectId}/announcements`))
}
export function createAnnouncement(projectId: string, payload: { title: string; content?: string; pinned?: number }) {
  return req<AnnouncementVO>(http.post(`/v1/projects/${projectId}/announcements`, payload))
}
export function updateAnnouncement(projectId: string, id: string, payload: { title?: string; content?: string; pinned?: number }) {
  return req<AnnouncementVO>(http.put(`/v1/projects/${projectId}/announcements/${id}`, payload))
}
export function deleteAnnouncement(projectId: string, id: string) {
  return req<void>(http.delete(`/v1/projects/${projectId}/announcements/${id}`))
}

// ---- project activities ----
export function listProjectActivities(projectId: string, limit = 20) {
  return req<ProjectActivityVO[]>(http.get(`/v1/projects/${projectId}/activities`, { params: { limit } }))
}

// ---- sprints (迭代) ----
export function listSprints(projectId: string) {
  return req<SprintVO[]>(http.get(`/v1/projects/${projectId}/sprints`))
}
export function getSprint(projectId: string, id: string) {
  return req<SprintVO>(http.get(`/v1/projects/${projectId}/sprints/${id}`))
}
export function createSprint(projectId: string, payload: { name: string; goal?: string; startDate?: string; endDate?: string }) {
  return req<SprintVO>(http.post(`/v1/projects/${projectId}/sprints`, payload))
}
export function updateSprint(projectId: string, id: string, payload: { name?: string; goal?: string; startDate?: string; endDate?: string }) {
  return req<SprintVO>(http.put(`/v1/projects/${projectId}/sprints/${id}`, payload))
}
export function deleteSprint(projectId: string, id: string) {
  return req<void>(http.delete(`/v1/projects/${projectId}/sprints/${id}`))
}
export function startSprint(projectId: string, id: string) {
  return req<SprintVO>(http.post(`/v1/projects/${projectId}/sprints/${id}/start`))
}
export function completeSprint(projectId: string, id: string) {
  return req<SprintVO>(http.post(`/v1/projects/${projectId}/sprints/${id}/complete`))
}

// ---- risks (风险) ----
export function listRisks(projectId: string) {
  return req<RiskVO[]>(http.get(`/v1/projects/${projectId}/risks`))
}
export function getRisk(projectId: string, id: string) {
  return req<RiskVO>(http.get(`/v1/projects/${projectId}/risks/${id}`))
}
export function createRisk(projectId: string, payload: { title: string; description?: string; level?: string; status?: string; ownerId?: string; dueDate?: string }) {
  return req<RiskVO>(http.post(`/v1/projects/${projectId}/risks`, payload))
}
export function updateRisk(projectId: string, id: string, payload: {
  title?: string
  description?: string
  level?: string
  status?: string
  ownerId?: string
  dueDate?: string
  /** 置空字段（ownerId / dueDate）：后端把 null 当作「不修改」 */
  clearFields?: string[]
}) {
  return req<RiskVO>(http.put(`/v1/projects/${projectId}/risks/${id}`, payload))
}
export function deleteRisk(projectId: string, id: string) {
  return req<void>(http.delete(`/v1/projects/${projectId}/risks/${id}`))
}

// ---- RBAC 全局管理 (系统管理员) ----
// 当前用户在某项目下的权限码，持久化到 localStorage：登录后仅首次请求，之后（含刷新页面）均从缓存读取
export function getMyPermissions(projectId: string, force = false) {
  if (!force) {
    const cachedPerms = readMyPermissions(projectId)
    if (cachedPerms) return Promise.resolve(cachedPerms)
  }
  return req<string[]>(http.get(`/v1/projects/${projectId}/my-permissions`)).then((perms) => {
    writeMyPermissions(projectId, perms)
    return perms
  })
}
export function listRoles() {
  return req<RbacRole[]>(http.get(`/v1/admin/roles`))
}
export function createRole(payload: { code: string; name: string; description?: string; sortOrder?: number }) {
  return req<RbacRole>(http.post(`/v1/admin/roles`, payload))
}
export function updateRole(id: string, payload: { code?: string; name?: string; description?: string; sortOrder?: number }) {
  return req<RbacRole>(http.put(`/v1/admin/roles/${id}`, payload))
}
export function deleteRole(id: string) {
  return req<void>(http.delete(`/v1/admin/roles/${id}`))
}
export function listMenus() {
  return req<RbacMenu[]>(http.get(`/v1/admin/menus`))
}
export function createMenu(payload: { code: string; name: string; parentId?: string; type?: string; sortOrder?: number }) {
  return req<RbacMenu>(http.post(`/v1/admin/menus`, payload))
}
export function updateMenu(id: string, payload: { code?: string; name?: string; parentId?: string; type?: string; sortOrder?: number }) {
  return req<RbacMenu>(http.put(`/v1/admin/menus/${id}`, payload))
}
export function deleteMenu(id: string) {
  return req<void>(http.delete(`/v1/admin/menus/${id}`))
}
export function listRoleMenus(roleId: string) {
  return req<string[]>(http.get(`/v1/admin/roles/${roleId}/menus`))
}
export function assignRoleMenus(roleId: string, menuIds: string[]) {
  return req<void>(http.post(`/v1/admin/roles/${roleId}/menus`, menuIds))
}
export function listUserRoles(userId: string, projectId: string) {
  return req<string[]>(http.get(`/v1/admin/users/${userId}/roles`, { params: { projectId } }))
}
export function assignUserRoles(userId: string, projectId: string, roleIds: string[]) {
  return req<void>(http.post(`/v1/admin/users/${userId}/roles?projectId=${projectId}`, roleIds))
}

// ---- 权限定义 + 角色-权限码 ----
export function listPermissions() {
  return req<PermissionDef[]>(http.get(`/v1/admin/permissions`))
}
export function listRolePermissions(roleId: string) {
  return req<string[]>(http.get(`/v1/admin/roles/${roleId}/permissions`))
}
export function assignRolePermissions(roleId: string, permissionCodes: string[]) {
  return req<void>(http.post(`/v1/admin/roles/${roleId}/permissions`, permissionCodes))
}

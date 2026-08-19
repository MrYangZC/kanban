import http, { req, type R } from './request'
import type { Project, Issue, BoardView, LoginVO, LabelVO, IssueActivity, AttachmentVO, LinkVO, UserVO, BoardGroupView, DashboardVO, ProjectMember, PageResult, IssueStatusVO, UploadImageVO, WorkflowTransitionVO, MilestoneVO, AnnouncementVO, ProjectActivityVO, ProjectCategoryKey, ProjectCategoryCounts, ProjectFilterRequest, ProfileVO, UpdateProfileRequest, ChangePasswordRequest } from './types'

// ---- user ----
export function login(username: string, password: string) {
  return req<LoginVO>(http.post('/v1/users/login', { username, password }))
}
export function listUsers() {
  return req<UserVO[]>(http.get('/v1/users'))
}
export function createUser(payload: {
  username: string
  displayName: string
  email?: string
  avatarUrl?: string
  password?: string
}) {
  return req<UserVO>(http.post('/v1/users', payload))
}
export function updateUser(id: string, payload: {
  displayName?: string
  email?: string
  avatarUrl?: string
  enabled?: boolean
  password?: string
}) {
  return req<UserVO>(http.put(`/v1/users/${id}`, payload))
}
export function deleteUser(id: string) {
  return req<void>(http.delete(`/v1/users/${id}`))
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
  return req<Project>(http.get(`/v1/projects/${id}`))
}
export function updateProject(id: string, payload: {
  name?: string
  description?: string
  avatarUrl?: string
  status?: string
  leadUserId?: string
}) {
  return req<Project>(http.put(`/v1/projects/${id}`, payload))
}
export function favoriteProject(id: string) {
  return req<void>(http.post(`/v1/projects/${id}/favorite`))
}
export function unfavoriteProject(id: string) {
  return req<void>(http.delete(`/v1/projects/${id}/favorite`))
}
export function archiveProject(id: string) {
  return req<void>(http.put(`/v1/projects/${id}/archive`))
}
export function unarchiveProject(id: string) {
  return req<void>(http.put(`/v1/projects/${id}/unarchive`))
}

// ---- project members ----
export function listProjectMembers(projectId: string) {
  return req<ProjectMember[]>(http.get(`/v1/projects/${projectId}/members`))
}
export function addProjectMember(projectId: string, userId: string, roles: string[] = ['MEMBER']) {
  return req<ProjectMember>(http.post(`/v1/projects/${projectId}/members`, { userId, roles }))
}
export function removeProjectMember(projectId: string, userId: string) {
  return req<void>(http.delete(`/v1/projects/${projectId}/members/${userId}`))
}
export function updateProjectMemberRoles(projectId: string, userId: string, roles: string[]) {
  return req<ProjectMember>(http.put(`/v1/projects/${projectId}/members/${userId}/roles`, roles))
}

// ---- board ----
export function getBoard(projectId: string) {
  return req<BoardView>(http.get(`/v1/projects/${projectId}/board`))
}
export function getBoardGrouped(projectId: string, groupBy?: string) {
  return req<BoardGroupView>(http.get(`/v1/projects/${projectId}/board/grouped`, { params: { groupBy } }))
}
export function getDashboard(projectId: string) {
  return req<DashboardVO>(http.get(`/v1/projects/${projectId}/dashboard`))
}

// ---- issue ----
export function createIssue(payload: {
  projectId: string
  issueTypeId?: string
  statusId: string
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
  participants?: string
  ccUsers?: string
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
  assigneeId?: string
  verifierId?: string
  dueDate?: string
  severity?: string
  module?: string
  fixVersion?: string
  source?: string
  archived?: number
  iteration?: string
  participants?: string
  ccUsers?: string
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
  page?: number
  size?: number
  sortField?: string
  sortOrder?: string
}) {
  return req<PageResult<Issue>>(http.post('/v1/issues/filter', payload))
}

// ---- upload ----
export function uploadImage(file: File) {
  const form = new FormData()
  form.append('file', file)
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
export function listProjectStatuses(projectId: string) {
  return req<IssueStatusVO[]>(http.get(`/v1/projects/${projectId}/statuses`))
}

// ---- workflow ----
export function listTransitions(projectId: string) {
  return req<WorkflowTransitionVO[]>(http.get(`/v1/projects/${projectId}/transitions`))
}

// ---- tags ----
export function listProjectTags(projectId: string) {
  return req<LabelVO[]>(http.get(`/v1/projects/${projectId}/tags`))
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

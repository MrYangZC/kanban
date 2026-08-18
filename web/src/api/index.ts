import http, { req, type R } from './request'
import type { Workspace, Project, Issue, BoardView, LoginVO, LabelVO, IssueActivity, AttachmentVO, LinkVO, UserVO, BoardGroupView, DashboardVO, ProjectMember, PageResult, IssueStatusVO } from './types'

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

// ---- workspace ----
export function listWorkspaces() {
  return req<Workspace[]>(http.get('/v1/workspaces'))
}
export function createWorkspace(name: string, key: string, description = '') {
  return req<Workspace>(http.post('/v1/workspaces', { name, workspaceKey: key, description }))
}

// ---- project ----
export function listProjects(workspaceId: string) {
  return req<Project[]>(http.get(`/v1/projects/workspace/${workspaceId}`))
}
export function createProject(workspaceId: string, projectKey: string, name: string, description = '') {
  return req<Project>(http.post('/v1/projects', { workspaceId, projectKey, name, description }))
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

// ---- project members ----
export function listProjectMembers(projectId: string) {
  return req<ProjectMember[]>(http.get(`/v1/projects/${projectId}/members`))
}
export function addProjectMember(projectId: string, userId: string, role = 'MEMBER') {
  return req<ProjectMember>(http.post(`/v1/projects/${projectId}/members`, { userId, role }))
}
export function removeProjectMember(projectId: string, userId: string) {
  return req<void>(http.delete(`/v1/projects/${projectId}/members/${userId}`))
}
export function updateProjectMemberRole(projectId: string, userId: string, role: string) {
  return req<ProjectMember>(http.put(`/v1/projects/${projectId}/members/${userId}/role`, null, { params: { role } }))
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

// ---- activities / comments ----
export function listActivities(issueId: string) {
  return req<IssueActivity[]>(http.get(`/v1/issues/${issueId}/activities`))
}
export function addComment(issueId: string, content: string) {
  return req<IssueActivity>(http.post(`/v1/issues/${issueId}/comments`, { content }))
}
export function deleteComment(issueId: string, commentId: string) {
  return req<void>(http.delete(`/v1/issues/${issueId}/comments/${commentId}`))
}

// ---- attachments ----
export function listAttachments(issueId: string) {
  return req<AttachmentVO[]>(http.get(`/v1/issues/${issueId}/attachments`))
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

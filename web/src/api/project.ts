import http, { req } from './request'
import { C } from './cacheKeys'
import { cached, clearMyPermissions, invalidate } from '@/utils/cache'
import type {
  MemberCandidate,
  PageResult,
  Project,
  ProjectActivityVO,
  ProjectCategoryCounts,
  ProjectCategoryKey,
  ProjectFilterRequest,
  ProjectMember,
  WorkItemCategory
} from './types'

// ---- 项目列表 / 详情 ----
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

// ---- 收藏 ----
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

// ---- 归档 / 删除 ----
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

// ---- 成员 ----
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

// ---- 项目动态 ----
export function listProjectActivities(projectId: string, limit = 20) {
  return req<ProjectActivityVO[]>(http.get(`/v1/projects/${projectId}/activities`, { params: { limit } }))
}

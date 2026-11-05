import http, { req } from './request'
import type { AnnouncementVO, MilestoneVO, RiskVO, SprintVO } from './types'

// ---- 里程碑 ----
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

// ---- 公告 ----
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

// ---- 迭代 ----
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

// ---- 风险 ----
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

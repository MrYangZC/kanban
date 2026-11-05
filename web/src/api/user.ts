import http, { req } from './request'
import { C } from './cacheKeys'
import {
  cached,
  invalidate,
  readMyPermissions,
  writeMyPermissions
} from '@/utils/cache'
import type {
  ChangePasswordRequest,
  LoginVO,
  NotificationVO,
  PageResult,
  ProfileVO,
  UpdateProfileRequest,
  UserOption,
  UserVO
} from './types'

// ---- 认证 ----
export function login(username: string, password: string) {
  return req<LoginVO>(http.post('/v1/users/login', { username, password }))
}

/**
 * 登出：销毁服务端 HttpSession。
 * 仅清本地凭证会导致服务端会话仍然有效（会话复用风险）。
 */
export function logout() {
  return req<void>(http.post('/v1/users/logout'))
}

// ---- 人员下拉 ----
/**
 * 人员下拉用的精简列表（id/账号/显示名/头像），任何登录用户可读。
 * 注意：不返回 email/enabled；用户管理列表走 listUsersPage（仅管理员）。
 */
export function listUsers() {
  return cached<UserOption[]>(C.users, () => req<UserOption[]>(http.get('/v1/users/options')))
}

// ---- 用户管理（系统管理员） ----
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
  isAdmin?: boolean
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
  isAdmin?: boolean
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

// ---- 个人中心（当前登录用户） ----
export function getMyProfile() {
  return req<ProfileVO>(http.get('/v1/users/me'))
}

export function updateMyProfile(payload: UpdateProfileRequest) {
  // 改头像/改名后人员下拉等缓存的简表要失效，否则别处仍显示旧头像
  return req<ProfileVO>(http.put('/v1/users/me', payload)).then((r) => {
    invalidate(C.users)
    return r
  })
}

export function changeMyPassword(payload: ChangePasswordRequest) {
  return req<void>(http.put('/v1/users/me/password', payload))
}

// ---- 通知收件箱 ----
export function listNotifications(params?: { page?: number; size?: number }) {
  return req<PageResult<NotificationVO>>(http.get('/v1/users/notifications', { params }))
}

export function getUnreadNotificationCount() {
  return req<number>(http.get('/v1/users/notifications/unread-count'))
}

export function markNotificationRead(id: string) {
  return req<void>(http.put(`/v1/users/notifications/${id}/read`))
}

export function markAllNotificationsRead() {
  return req<number>(http.put('/v1/users/notifications/read-all'))
}

// ---- 当前用户在某项目下的权限码 ----
// 持久化到 localStorage：登录后仅首次请求，之后（含刷新页面）均从缓存读取
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

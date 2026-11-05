import http, { req } from './request'
import type { PermissionDef, RbacMenu, RbacRole } from './types'

// ---- 角色 ----
export function listRoles() {
  return req<RbacRole[]>(http.get('/v1/admin/roles'))
}

export function createRole(payload: { code: string; name: string; description?: string; sortOrder?: number }) {
  return req<RbacRole>(http.post('/v1/admin/roles', payload))
}

export function updateRole(id: string, payload: { code?: string; name?: string; description?: string; sortOrder?: number }) {
  return req<RbacRole>(http.put(`/v1/admin/roles/${id}`, payload))
}

export function deleteRole(id: string) {
  return req<void>(http.delete(`/v1/admin/roles/${id}`))
}

// ---- 菜单 ----
export function listMenus() {
  return req<RbacMenu[]>(http.get('/v1/admin/menus'))
}

export function createMenu(payload: { code: string; name: string; parentId?: string; type?: string; sortOrder?: number }) {
  return req<RbacMenu>(http.post('/v1/admin/menus', payload))
}

export function updateMenu(id: string, payload: { code?: string; name?: string; parentId?: string; type?: string; sortOrder?: number }) {
  return req<RbacMenu>(http.put(`/v1/admin/menus/${id}`, payload))
}

export function deleteMenu(id: string) {
  return req<void>(http.delete(`/v1/admin/menus/${id}`))
}

// ---- 角色 ↔ 菜单 ----
export function listRoleMenus(roleId: string) {
  return req<string[]>(http.get(`/v1/admin/roles/${roleId}/menus`))
}

export function assignRoleMenus(roleId: string, menuIds: string[]) {
  return req<void>(http.post(`/v1/admin/roles/${roleId}/menus`, menuIds))
}

// ---- 用户 ↔ 角色（项目维度） ----
export function listUserRoles(userId: string, projectId: string) {
  return req<string[]>(http.get(`/v1/admin/users/${userId}/roles`, { params: { projectId } }))
}

export function assignUserRoles(userId: string, projectId: string, roleIds: string[]) {
  return req<void>(
    http.post(`/v1/admin/users/${userId}/roles`, roleIds, { params: { projectId } })
  )
}

// ---- 权限定义 + 角色 ↔ 权限码 ----
export function listPermissions() {
  return req<PermissionDef[]>(http.get('/v1/admin/permissions'))
}

export function listRolePermissions(roleId: string) {
  return req<string[]>(http.get(`/v1/admin/roles/${roleId}/permissions`))
}

export function assignRolePermissions(roleId: string, permissionCodes: string[]) {
  return req<void>(http.post(`/v1/admin/roles/${roleId}/permissions`, permissionCodes))
}

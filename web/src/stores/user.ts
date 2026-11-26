import { defineStore } from 'pinia'
import { computed, ref } from 'vue'
import type { LoginVO } from '@/api/types'
import { logout as apiLogout } from '@/api'
import { AUTH_TOKEN_KEY, AUTH_USER_KEY, clearAllCaches } from '@/utils/cache'

export const useUserStore = defineStore('user', () => {
  const user = ref<LoginVO | null>(null)

  /**
   * 登录态完全由服务端 HttpSession（JSESSIONID Cookie）承载，本地只缓存"上次登录的用户信息"用于首屏渲染。
   * isLoggedIn 仅作为路由守卫的体验控制：真正的鉴权在服务端，未登录时后端返回 401 由 request.ts 统一跳登录页。
   * 因此该值被本地篡改不会获得任何真实权限。
   */
  const isLoggedIn = computed(() => user.value !== null)

  /** 是否系统管理员，口径与后端 PermissionService#isSuperAdmin 一致 */
  const isAdmin = computed(() => user.value?.isAdmin === true)

  function setLogin(vo: LoginVO) {
    user.value = vo
    // token 单独存一个键：它是请求头要用的凭证，和"用户展示信息"的生命周期不同
    if (vo.token) localStorage.setItem(AUTH_TOKEN_KEY, vo.token)
    localStorage.setItem(AUTH_USER_KEY, JSON.stringify(vo))
  }

  /** token 是否已过期（本地粗判：真正的校验在后端，这里只为避免带着必死 token 发请求） */
  function isTokenExpired(): boolean {
    const token = localStorage.getItem(AUTH_TOKEN_KEY)
    if (!token) return true
    const expiresAt = user.value?.expiresAt
    // 没有 expiresAt（老缓存）时不做本地判断，交给后端 401
    if (!expiresAt) return false
    return Date.now() >= expiresAt
  }

  /** 局部更新当前登录用户（改名/换头像后让全局头像位立即刷新） */
  function patchProfile(patch: Partial<Pick<LoginVO, 'displayName' | 'avatarUrl'>>) {
    if (!user.value) return
    const next = { ...user.value, ...patch }
    user.value = next
    localStorage.setItem(AUTH_USER_KEY, JSON.stringify(next))
  }

  function restore() {
    const raw = localStorage.getItem(AUTH_USER_KEY)
    if (!raw) return
    try {
      user.value = JSON.parse(raw)
      // 本地已判定过期：直接按未登录处理，省掉一轮必然 401 的请求
      if (isTokenExpired()) {
        user.value = null
        localStorage.removeItem(AUTH_USER_KEY)
        localStorage.removeItem(AUTH_TOKEN_KEY)
      }
    } catch {
      // 本地缓存损坏（手工编辑/旧版本数据结构）：丢弃，回到未登录态
      localStorage.removeItem(AUTH_USER_KEY)
      user.value = null
    }
  }

  /**
   * 登出：JWT 无状态，后端没有可销毁的会话，实际上就是清本地凭证。
   * 仍调用一次接口：一是清掉附件用的 HttpOnly Cookie，二是保留将来加黑名单的挂点。
   */
  async function logout() {
    try {
      await apiLogout()
    } catch {
      // 会话可能已过期，忽略错误继续完成本地清理
    }
    user.value = null
    localStorage.removeItem(AUTH_USER_KEY)
    localStorage.removeItem(AUTH_TOKEN_KEY)
    clearAllCaches()
  }

  return { user, isLoggedIn, isAdmin, setLogin, patchProfile, restore, logout, isTokenExpired }
})

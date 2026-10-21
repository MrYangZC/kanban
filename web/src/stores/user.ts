import { defineStore } from 'pinia'
import { computed, ref } from 'vue'
import type { LoginVO } from '@/api/types'
import { logout as apiLogout } from '@/api'
import { AUTH_USER_KEY, clearAllCaches } from '@/utils/cache'

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
    localStorage.setItem(AUTH_USER_KEY, JSON.stringify(vo))
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
    } catch {
      // 本地缓存损坏（手工编辑/旧版本数据结构）：丢弃，回到未登录态
      localStorage.removeItem(AUTH_USER_KEY)
      user.value = null
    }
  }

  /**
   * 登出：先通知后端销毁 HttpSession，再清理本地状态。
   * 服务端异常（会话已失效/网络故障）不应阻止本地退出，否则用户会卡在已登录界面。
   */
  async function logout() {
    try {
      await apiLogout()
    } catch {
      // 会话可能已过期，忽略错误继续完成本地清理
    }
    user.value = null
    localStorage.removeItem(AUTH_USER_KEY)
    clearAllCaches()
  }

  return { user, isLoggedIn, isAdmin, setLogin, patchProfile, restore, logout }
})

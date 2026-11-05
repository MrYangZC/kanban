import { defineStore } from 'pinia'
import { ref } from 'vue'
import {
  getUnreadNotificationCount,
  listNotifications,
  markAllNotificationsRead,
  markNotificationRead
} from '@/api'
import type { NotificationVO } from '@/api/types'

/** 顶栏下拉只展示最近若干条，完整列表留给后续收件箱页 */
const RECENT_SIZE = 10
const POLL_INTERVAL = 60_000

/**
 * 站内通知。
 *
 * 采用轮询而非 WebSocket/SSE：项目整体没有长连接基础设施，为通知单独引入不划算。
 * 页面不可见时跳过这次轮询，避免后台标签页持续打接口。
 */
export const useNotificationStore = defineStore('notification', () => {
  const unreadCount = ref(0)
  const items = ref<NotificationVO[]>([])
  const loading = ref(false)
  const loaded = ref(false)

  let timer: ReturnType<typeof setInterval> | undefined

  async function refreshUnread() {
    try {
      unreadCount.value = await getUnreadNotificationCount()
    } catch {
      // 角标属增强信息，拉取失败保持上一次数值，不打扰用户
    }
  }

  async function loadRecent() {
    loading.value = true
    try {
      const page = await listNotifications({ page: 1, size: RECENT_SIZE })
      items.value = page.list || []
      loaded.value = true
    } catch {
      // 下拉降级为空列表，错误提示由请求层统一给出
    } finally {
      loading.value = false
    }
  }

  async function markRead(id: string) {
    const hit = items.value.find((n) => n.id === id)
    if (hit?.readAt) return
    try {
      await markNotificationRead(id)
      if (hit) hit.readAt = new Date().toISOString()
      unreadCount.value = Math.max(0, unreadCount.value - 1)
    } catch {
      // 可能已在其他标签页标记，忽略
    }
  }

  async function markAllRead() {
    try {
      await markAllNotificationsRead()
      const now = new Date().toISOString()
      items.value.forEach((n) => {
        if (!n.readAt) n.readAt = now
      })
      unreadCount.value = 0
    } catch {
      // 失败时保留角标，下次轮询会纠正
    }
  }

  function startPolling() {
    stopPolling()
    timer = setInterval(() => {
      if (document.visibilityState === 'visible') refreshUnread()
    }, POLL_INTERVAL)
  }

  function stopPolling() {
    if (timer) {
      clearInterval(timer)
      timer = undefined
    }
  }

  /** 组件卸载时复位，避免换账号后残留上一个人的通知 */
  function reset() {
    stopPolling()
    items.value = []
    unreadCount.value = 0
    loaded.value = false
  }

  return {
    unreadCount,
    items,
    loading,
    loaded,
    refreshUnread,
    loadRecent,
    markRead,
    markAllRead,
    startPolling,
    stopPolling,
    reset
  }
})

/**
 * 登录用户信息的 localStorage 键。
 * 由 stores/user、api/request 共用，避免同一字符串在两处硬编码后失配。
 */
export const AUTH_USER_KEY = 'kanban_user'

interface Entry<T> {
  data?: T
  promise?: Promise<T>
}

const mem = new Map<string, Entry<unknown>>()

/**
 * 内存缓存 + 并发去重：同一 key 在一个会话内只发起一次请求，
 * 后续调用直接复用已缓存数据；进行中的请求会共享同一个 Promise。
 */
export function cached<T>(key: string, loader: () => Promise<T>): Promise<T> {
  let entry = mem.get(key) as Entry<T> | undefined
  if (!entry) {
    entry = {}
    mem.set(key, entry)
  }
  if (entry.data !== undefined) return Promise.resolve(entry.data)
  if (entry.promise) return entry.promise
  const p = loader()
    .then((data) => {
      const e = mem.get(key) as Entry<T> | undefined
      if (e) e.data = data
      return data
    })
    .finally(() => {
      const e = mem.get(key) as Entry<T> | undefined
      if (e) e.promise = undefined
    })
  entry.promise = p
  return p
}

/** 使某个 key 的内存缓存失效（下次调用会重新请求） */
export function invalidate(key: string): void {
  mem.delete(key)
}

// ---- 当前登录用户权限码持久化（sessionStorage） ----
// 使用 sessionStorage 而非 localStorage：权限码属于"当前会话"的派生数据，
// 长期驻留会让角色被回收后前端仍按旧权限渲染按钮（后端虽会拦截，但交互表现为点击报错）。
// session 级存储让关闭标签页即失效，把陈旧窗口收敛到单次会话内。

const MY_PERMS_PREFIX = 'kanban:myPermissions:'

const myPermKey = (projectId: string) => `${MY_PERMS_PREFIX}${projectId}`

export function readMyPermissions(projectId: string): string[] | null {
  const raw = sessionStorage.getItem(myPermKey(projectId))
  if (!raw) return null
  try {
    return JSON.parse(raw) as string[]
  } catch {
    sessionStorage.removeItem(myPermKey(projectId))
    return null
  }
}

export function writeMyPermissions(projectId: string, perms: string[]): void {
  sessionStorage.setItem(myPermKey(projectId), JSON.stringify(perms))
}

export function clearMyPermissions(projectId?: string): void {
  if (projectId) {
    sessionStorage.removeItem(myPermKey(projectId))
    return
  }
  const keys: string[] = []
  for (let i = 0; i < sessionStorage.length; i++) {
    const k = sessionStorage.key(i)
    if (k && k.startsWith(MY_PERMS_PREFIX)) keys.push(k)
  }
  keys.forEach((k) => sessionStorage.removeItem(k))
}

/** 登出 / 登录态失效时清空全部缓存（内存缓存 + 权限持久化） */
export function clearAllCaches(): void {
  mem.clear()
  clearMyPermissions()
}
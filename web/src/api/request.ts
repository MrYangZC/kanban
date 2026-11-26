import axios, { AxiosError, type AxiosResponse } from 'axios'
import { AUTH_TOKEN_KEY, AUTH_USER_KEY, clearAllCaches } from '@/utils/cache'
import { notifyError } from '@/utils/error'

/**
 * 统一响应结构（与后端 R<T> 一致）：
 * code=200 成功，其余复用 HTTP 语义码。
 */
export interface R<T> {
  code: number
  msg: string
  data: T
}

/**
 * 统一错误对象：保留 Error.message 兼容，同时暴露 code/msg，
 * 调用方既可读 e.message 也可读 e.msg / e.code。
 */
export class ApiError extends Error {
  code: number
  msg: string
  constructor(code: number, msg: string) {
    super(msg)
    this.name = 'ApiError'
    this.code = code
    this.msg = msg
  }
}

/**
 * API 前缀：nginx 给 kanban 分配的专属前缀 /kanban-api（站点根上不占 /api，避免与其他项目冲突）。
 * nginx 会把 /kanban-api/ 摘掉再转给后端 /api/，所以这里和后端的 context-path 不是同一个字符串，
 * 后端那边靠 SERVER_SERVLET_SESSION_COOKIE_PATH + KANBAN_UPLOAD_PUBLIC_PREFIX 对齐到 /kanban-api。
 */
export const API_BASE = '/kanban-api'

const http = axios.create({
  baseURL: API_BASE,
  timeout: 15000,
  // 认证走 Authorization: Bearer（JWT），不再依赖会话 Cookie。
  // 仍需 withCredentials：附件 <img> 之外的跨域场景几乎没有，但保留可避免将来拆域时踩坑。
  withCredentials: true
})

/**
 * 每个请求带上 JWT。
 *
 * <p>放在请求拦截器而不是逐个调用点：漏带一次就是 401，且排查成本高。
 * 注意 <img src> 之类浏览器原生请求不走 axios，后端为 /uploads/** 保留了
 * 路径受限的 HttpOnly Cookie 作为兜底（见后端 JwtAuthenticationFilter）。
 */
http.interceptors.request.use((config) => {
  const token = localStorage.getItem(AUTH_TOKEN_KEY)
  if (token) {
    config.headers.Authorization = `Bearer ${token}`
  }
  return config
})

/** 401 可能由多个并发请求同时触发：只跳转一次，避免重复整页刷新 */
let redirectingToLogin = false

/** 登录页地址同样跟随部署路径，避免子路径部署时跳到站点根下的 /login（404） */
const LOGIN_PATH = `${import.meta.env.BASE_URL}login`

function redirectToLogin(): void {
  if (redirectingToLogin || window.location.pathname === LOGIN_PATH) return
  redirectingToLogin = true
  localStorage.removeItem(AUTH_USER_KEY)
  localStorage.removeItem(AUTH_TOKEN_KEY)
  clearAllCaches()
  window.location.href = LOGIN_PATH
}

http.interceptors.response.use(
  // 拦截层不关心业务数据类型，具体类型由 req<T> 在调用点还原
  (resp: AxiosResponse<R<unknown>>) => {
    // 二进制响应（CSV 导出）不是 R<T> 信封：原样交给调用方（否则会被当成"请求失败"）
    if (resp.config.responseType === 'blob') return resp
    const body = resp.data
    if (body && body.code === 200) {
      return resp
    }
    // 业务/系统错误：提示并 reject（notifyError 会与调用点的提示去重，同文案只留一条）
    const msg = body?.msg || '请求失败'
    notifyError(msg)
    return Promise.reject(new ApiError(body?.code ?? 500, msg))
  },
  (error: AxiosError<{ msg?: string }>) => {
    const status = error.response?.status
    if (status === 401) {
      // 登录态失效：清理本地凭证并跳转登录页
      redirectToLogin()
      return Promise.reject(error)
    }
    if (status === 403) {
      // 已登录但无权限：保留登录态，仅提示，避免与 401 一样被误判为掉线而跳登录页
      const forbidden = error.response?.data?.msg || '权限不足，无法执行该操作'
      notifyError(forbidden)
      return Promise.reject(new ApiError(403, forbidden))
    }
    const msg = error.response?.data?.msg || error.message || '网络错误'
    notifyError(msg)
    return Promise.reject(new ApiError(status ?? 500, msg))
  }
)

/** 取 data（code=200 时） */
export async function req<T>(promise: Promise<AxiosResponse<R<T>>>): Promise<T> {
  const resp = await promise
  return resp.data.data
}

export default http

import axios, { AxiosError, type AxiosResponse } from 'axios'
import { ElMessage } from 'element-plus'
import { AUTH_USER_KEY, clearAllCaches } from '@/utils/cache'

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

const http = axios.create({
  baseURL: '/api',
  timeout: 15000,
  // 同源 Session 认证：携带 Cookie 以维持登录态
  withCredentials: true
})

http.interceptors.response.use(
  // 拦截层不关心业务数据类型，具体类型由 req<T> 在调用点还原
  (resp: AxiosResponse<R<unknown>>) => {
    const body = resp.data
    if (body && body.code === 200) {
      return resp
    }
    // 业务/系统错误：提示并 reject
    const msg = body?.msg || '请求失败'
    ElMessage.error(msg)
    return Promise.reject(new ApiError(body?.code ?? 500, msg))
  },
  (error: AxiosError<{ msg?: string }>) => {
    const status = error.response?.status
    if (status === 401) {
      // 登录态失效：清理本地凭证并跳转登录页
      localStorage.removeItem(AUTH_USER_KEY)
      clearAllCaches()
      if (window.location.pathname !== '/login') {
        window.location.href = '/login'
      }
      return Promise.reject(error)
    }
    if (status === 403) {
      // 已登录但无权限：保留登录态，仅提示，避免与 401 一样被误判为掉线而跳登录页
      const forbidden = error.response?.data?.msg || '权限不足，无法执行该操作'
      ElMessage.error(forbidden)
      return Promise.reject(new ApiError(403, forbidden))
    }
    const msg = error.response?.data?.msg || error.message || '网络错误'
    ElMessage.error(msg)
    return Promise.reject(new ApiError(status ?? 500, msg))
  }
)

/** 取 data（code=200 时） */
export async function req<T>(promise: Promise<AxiosResponse<R<T>>>): Promise<T> {
  const resp = await promise
  return resp.data.data
}

export default http

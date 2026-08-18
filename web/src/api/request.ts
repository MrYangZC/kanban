import axios, { type AxiosResponse } from 'axios'
import { ElMessage } from 'element-plus'

/**
 * 统一响应结构（与后端 R<T> 一致）：
 * code=200 成功，其余复用 HTTP 语义码。
 */
export interface R<T> {
  code: number
  msg: string
  data: T
}

const http = axios.create({
  baseURL: '/api',
  timeout: 15000,
  // 同源 Session 认证：携带 Cookie 以维持登录态
  withCredentials: true
})

http.interceptors.response.use(
  (resp: AxiosResponse<R<any>>) => {
    const body = resp.data
    if (body && body.code === 200) {
      return resp
    }
    // 业务/系统错误：提示并 reject
    const msg = body?.msg || '请求失败'
    ElMessage.error(msg)
    return Promise.reject(new Error(msg))
  },
  (error) => {
    const msg = error.response?.data?.msg || error.message || '网络错误'
    ElMessage.error(msg)
    return Promise.reject(error)
  }
)

/** 取 data（code=200 时） */
export async function req<T>(promise: Promise<AxiosResponse<R<T>>>): Promise<T> {
  const resp = await promise
  return resp.data.data
}

export default http

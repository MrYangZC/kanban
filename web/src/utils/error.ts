/**
 * 异常信息提取与统一提示。
 *
 * 请求层（api/request.ts）抛出的错误同时可能带有：
 * - message：Error 标准字段，多为前端统一文案
 * - msg：后端统一响应体中的业务提示
 * 这里统一归一化，避免各调用处重复写 `(e: any) => e?.msg || e?.message || 'xx'`。
 */
import { ElMessage } from 'element-plus'

interface ApiErrorLike {
  msg?: string
  message?: string
  code?: number
}

/** 从任意异常中提取用户可读消息，提取不到时回退到 fallback */
export function errorMessage(e: unknown, fallback: string): string {
  if (typeof e === 'string') return e || fallback
  if (typeof e === 'object' && e !== null) {
    const err = e as ApiErrorLike
    return err.msg || err.message || fallback
  }
  return fallback
}

/**
 * 统一的错误轻提示。
 *
 * 请求拦截器与业务 catch 都会提示同一个错误，开启 grouping 后
 * Element Plus 会把内容相同的消息合并成一条，避免"弹两次一模一样的红条"。
 */
export function notifyError(msg: string): void {
  ElMessage({ message: msg, type: 'error', grouping: true })
}

/** catch 里的一步到位提示：从异常提取消息（失败时用 fallback）并弹出 */
export function reportError(e: unknown, fallback: string): void {
  notifyError(errorMessage(e, fallback))
}

/** el-message-box 的用户取消标记：取消是正常操作，不应作为错误提示 */
export function isCancel(e: unknown): boolean {
  return e === 'cancel' || e === 'close'
}

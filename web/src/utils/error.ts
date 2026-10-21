/**
 * 异常信息提取工具。
 *
 * 请求层（api/request.ts）抛出的错误同时可能带有：
 * - message：Error 标准字段，多为前端统一文案
 * - msg：后端统一响应体中的业务提示
 * 这里统一归一化，避免各调用处重复写 `(e: any) => e?.msg || e?.message || 'xx'`。
 */

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

/** el-message-box 的用户取消标记：取消是正常操作，不应作为错误提示 */
export function isCancel(e: unknown): boolean {
  return e === 'cancel' || e === 'close'
}

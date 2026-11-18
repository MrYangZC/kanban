import { h } from 'vue'
import { ElNotification } from 'element-plus'

/**
 * 带「撤销」按钮的反馈。
 *
 * <p>ElMessage 不支持动作按钮，这里用 ElNotification + 自定义 VNode 实现：
 * 提示语 + 一个次要按钮，点击后先关闭提示再执行撤销回调。</p>
 *
 * <p>只用于**服务端真的可逆**的操作（归档/取消归档、收藏/取消收藏这类 toggle）；
 * 删除是不可逆的硬删除，不能挂"撤销"——那会给出错误的安全感，这类操作仍然用确认框挡在前面。</p>
 */
export function toastUndo(options: {
  message: string
  undoLabel?: string
  onUndo: () => void | Promise<void>
  duration?: number
}): void {
  const { message, undoLabel = '撤销', onUndo, duration = 5000 } = options

  let closed = false
  const close = () => {
    closed = true
    instance?.close()
  }

  const vnode = h('div', { class: 'wi-toast-undo' }, [
    h('span', { class: 'wi-toast-undo-text' }, message),
    h(
      'button',
      {
        class: 'wi-toast-undo-btn',
        type: 'button',
        onClick: async () => {
          if (closed) return
          close()
          try {
            await onUndo()
          } catch {
            // 撤销失败由调用方自己的错误处理接管，这里只保证提示条关掉
          }
        }
      },
      undoLabel
    )
  ])

  const instance = ElNotification({
    message: vnode,
    duration,
    position: 'bottom-right',
    customClass: 'wi-toast-undo-wrap',
    showClose: true
  })
}

import { ElMessageBox } from 'element-plus'

/**
 * 删除/危险操作确认框的统一入口。
 *
 * 原先 10 个文件各写一遍 `ElMessageBox.confirm(..., { type: 'warning', confirmButtonText, cancelButtonText })`，
 * 文案和按钮配置靠复制粘贴传播，改一处必然漏掉其余。
 *
 * 用户点「取消」时和 ElMessageBox 一样以 reject 结束，调用方沿用既有的
 * `try { ... } catch { return }` 或 `catch (e) { if (!isCancel(e)) ... }` 写法即可。
 */
export function confirmDelete(message: string, title = '提示', confirmText = '确定'): Promise<void> {
  return ElMessageBox.confirm(message, title, {
    type: 'warning',
    confirmButtonText: confirmText,
    cancelButtonText: '取消'
  }).then(() => undefined)
}

/**
 * 最常见的删除确认文案：`确认删除{label}「{name}」？`
 * 不传 label 时退化为 `确认删除「{name}」？`（角色、菜单这类对象本身已说明类型）。
 */
export function confirmRemove(name: string, label?: string): Promise<void> {
  return confirmDelete(`确认删除${label ? label : ''}「${name}」？`)
}

/**
 * 返回 boolean 的版本，省掉调用方 `try { await confirm() } catch { return }` 样板。
 * 典型用法：`if (!(await confirmAction('确定删除该评论吗？', '删除评论'))) return`
 */
export function confirmAction(message: string, title = '提示', confirmText = '确定'): Promise<boolean> {
  return confirmDelete(message, title, confirmText).then(
    () => true,
    () => false
  )
}

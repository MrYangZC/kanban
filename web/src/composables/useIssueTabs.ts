import { ref } from 'vue'
import { ElMessage } from 'element-plus'
import {
  listActivities, addComment, deleteComment,
  listAttachments, uploadAttachment, addAttachment, deleteAttachment,
  listLinks, addLink, deleteLink,
  listIssueTags, addIssueTag, removeIssueTag
} from '@/api'
import type { IssueActivity, AttachmentVO, LinkVO, LabelVO } from '@/api/types'
import { PRIMARY_HEX } from '@/theme/colorMap'

export type IssueTabKind = 'activity' | 'attachment' | 'link' | 'tag'

export interface UseIssueTabsOptions {
  /** 当前工作项 id（取值函数保证响应式） */
  issueId: () => string
  /** 当前项目 id：新增标签时后端需要归属项目 */
  projectId: () => string
  /** 数据变更后的回调，供宿主刷新列表或做埋点 */
  onChanged?: (kind: IssueTabKind) => void
}

/**
 * 工作项详情「动态&评论 / 附件 / 关联 / 标签」四个页签的数据与增删逻辑。
 *
 * 路由级详情页（DefectDetailView）与抽屉面板（IssueDetailPanel）此前各自实现了一份，
 * 行为存在细微漂移（如面板删除评论后不刷新、标签口径不一致）。统一收敛到这里。
 *
 * 失败策略：写操作返回布尔值，错误提示由 api 拦截器统一弹出，这里不再重复提示，
 * 避免出现「一次失败弹两条消息」；读取失败时保留上一次数据，界面不闪空。
 * 成功反馈：增删成功统一在此弹 success 提示，保证路由页与抽屉面板行为一致。
 */
export function useIssueTabs(options: UseIssueTabsOptions) {
  const activities = ref<IssueActivity[]>([])
  const attachments = ref<AttachmentVO[]>([])
  const links = ref<LinkVO[]>([])
  const tags = ref<LabelVO[]>([])
  const loading = ref(false)

  const notify = (kind: IssueTabKind) => options.onChanged?.(kind)

  async function loadActivities() {
    try {
      // 接口按时间升序返回（楼层组装需要），展示统一倒序：最新的贴在最上面
      const list = await listActivities(options.issueId())
      activities.value = [...list].sort((x, y) => (y.createdAt || '').localeCompare(x.createdAt || ''))
    } catch {
      // 接口层已提示失败原因，保留现有列表避免内容闪空
    }
  }

  async function loadAttachments() {
    try {
      attachments.value = await listAttachments(options.issueId())
    } catch {
      // 同上
    }
  }

  async function loadLinks() {
    try {
      links.value = await listLinks(options.issueId())
    } catch {
      // 同上
    }
  }

  async function loadTags() {
    try {
      tags.value = await listIssueTags(options.issueId())
    } catch {
      // 同上
    }
  }

  /** 并行拉取四个页签数据（切换工作项时调用） */
  async function reload() {
    if (!options.issueId()) return
    loading.value = true
    try {
      await Promise.all([loadActivities(), loadAttachments(), loadLinks(), loadTags()])
    } finally {
      loading.value = false
    }
  }

  // ---- 评论 ----
  async function sendComment(content: string, parentId?: string | null): Promise<boolean> {
    const text = content.trim()
    if (!text) return false
    try {
      await addComment(options.issueId(), text, parentId ?? null)
      await loadActivities()
      notify('activity')
      ElMessage.success('评论已发布')
      return true
    } catch {
      // 接口层已提示
      return false
    }
  }

  async function removeComment(commentId: string): Promise<boolean> {
    try {
      await deleteComment(options.issueId(), commentId)
      await loadActivities()
      notify('activity')
      ElMessage.success('评论已删除')
      return true
    } catch {
      return false
    }
  }

  // ---- 附件 ----
  /** 真实文件上传 */
  async function uploadFile(file: File): Promise<boolean> {
    try {
      await uploadAttachment(options.issueId(), file)
      await loadAttachments()
      notify('attachment')
      ElMessage.success('上传成功')
      return true
    } catch {
      return false
    }
  }

  /** 仅登记文件名（无实际文件内容的历史能力） */
  async function addNamedAttachment(fileName: string): Promise<boolean> {
    const name = fileName.trim()
    if (!name) return false
    try {
      await addAttachment(options.issueId(), name, 'application/octet-stream', 0)
      await loadAttachments()
      notify('attachment')
      ElMessage.success('附件已添加')
      return true
    } catch {
      return false
    }
  }

  async function removeAttachment(attachmentId: string): Promise<boolean> {
    try {
      await deleteAttachment(options.issueId(), attachmentId)
      await loadAttachments()
      notify('attachment')
      ElMessage.success('附件已删除')
      return true
    } catch {
      return false
    }
  }

  // ---- 关联 ----
  /** 批量新增关联后只回查一次，避免多选场景出现 N 次 GET */
  async function addLinkItems(targetIssueIds: string[], relationType: string): Promise<boolean> {
    const ids = targetIssueIds.filter(Boolean)
    if (!ids.length) return false
    try {
      await Promise.all(ids.map((t) => addLink(options.issueId(), t, relationType)))
      await loadLinks()
      notify('link')
      ElMessage.success('关联已添加')
      return true
    } catch {
      // 接口层已提示失败原因
      return false
    }
  }

  async function addLinkItem(targetIssueId: string, relationType: string): Promise<boolean> {
    return addLinkItems([targetIssueId], relationType)
  }

  async function removeLink(linkId: string): Promise<boolean> {
    try {
      await deleteLink(options.issueId(), linkId)
      await loadLinks()
      notify('link')
      ElMessage.success('关联已移除')
      return true
    } catch {
      return false
    }
  }

  // ---- 标签 ----
  async function addTag(name: string): Promise<boolean> {
    const n = name.trim()
    if (!n) return false
    try {
      await addIssueTag(options.issueId(), options.projectId(), n, PRIMARY_HEX)
      await loadTags()
      notify('tag')
      ElMessage.success('标签已添加')
      return true
    } catch {
      return false
    }
  }

  async function removeTag(labelId: string): Promise<boolean> {
    try {
      await removeIssueTag(options.issueId(), labelId)
      // 删除接口无返回值，按本地过滤即可，无需回查
      tags.value = tags.value.filter((t) => t.id !== labelId)
      notify('tag')
      ElMessage.success('标签已移除')
      return true
    } catch {
      return false
    }
  }

  return {
    activities,
    attachments,
    links,
    tags,
    loading,
    reload,
    loadActivities,
    loadAttachments,
    loadLinks,
    loadTags,
    sendComment,
    removeComment,
    uploadFile,
    addNamedAttachment,
    removeAttachment,
    addLinkItem,
    addLinkItems,
    removeLink,
    addTag,
    removeTag
  }
}

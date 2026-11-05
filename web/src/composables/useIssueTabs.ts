import { computed, ref } from 'vue'
import { ElMessage } from 'element-plus'
import {
  listActivities, addComment, deleteComment,
  listAttachments, uploadAttachment, addAttachment, deleteAttachment,
  listLinks, addLink, deleteLink,
  listIssueTags, addIssueTag, removeIssueTag,
  revokeHandoff as revokeHandoffApi
} from '@/api'
import { confirmAction } from '@/utils/confirm'
import type { Issue, IssueActivity, AttachmentVO, LinkVO, LabelVO } from '@/api/types'
import { PRIMARY_HEX } from '@/theme/colorMap'

export type IssueTabKind = 'activity' | 'attachment' | 'link' | 'tag'

/** 时间线每页条数：首屏 50 条足够覆盖绝大多数工作项，超出靠"加载更早" */
const ACTIVITY_PAGE_SIZE = 50

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
 * 这套逻辑此前在路由级详情页（DefectDetailView）与抽屉面板里各实现了一份，
 * 行为存在细微漂移（如删除评论后不刷新、标签口径不一致）。统一收敛到这里。
 * 抽屉（IssueDetailDrawer）现在也直接内嵌 DefectDetailView，全站只有一份详情实现。
 *
 * 失败策略：写操作返回布尔值，错误提示由 api 拦截器统一弹出，这里不再重复提示，
 * 避免出现「一次失败弹两条消息」；读取失败时保留上一次数据，界面不闪空。
 * 成功反馈：增删成功统一在此弹 success 提示，保证各入口行为一致。
 */
export function useIssueTabs(options: UseIssueTabsOptions) {
  const activities = ref<IssueActivity[]>([])
  const attachments = ref<AttachmentVO[]>([])
  const links = ref<LinkVO[]>([])
  const tags = ref<LabelVO[]>([])
  const loading = ref(false)

  // ---- 时间线分页状态 ----
  /** 时间线总条数（评论 + 动态） */
  const activityTotal = ref(0)
  /** 评论总条数（「评论·N」角标用；分页后当前页数量不等于总数） */
  const commentTotal = ref(0)
  /** 已加载到第几页（1 = 最新一页） */
  const activityPage = ref(1)
  /** 是否还有更早的数据未加载 */
  const hasEarlier = computed(() => activities.value.length < activityTotal.value)
  const loadingEarlier = ref(false)

  const notify = (kind: IssueTabKind) => options.onChanged?.(kind)

  /** 加载第一页（最新一页）。发评论/删评论后调用，等价于刷新 */
  async function loadActivities() {
    try {
      const page = await listActivities(options.issueId(), 1, ACTIVITY_PAGE_SIZE)
      // 接口按时间倒序返回（最新在前），与页面展示顺序一致
      activities.value = page.list
      activityTotal.value = page.total
      commentTotal.value = page.commentCount
      activityPage.value = 1
    } catch {
      // 接口层已提示失败原因，保留现有列表避免内容闪空
    }
  }

  /** 加载更早的下一页并追加到列表末尾 */
  async function loadEarlierActivities() {
    if (loadingEarlier.value || !hasEarlier.value) return
    loadingEarlier.value = true
    try {
      const next = activityPage.value + 1
      const page = await listActivities(options.issueId(), next, ACTIVITY_PAGE_SIZE)
      const known = new Set(activities.value.map((a) => a.id))
      activities.value = [...activities.value, ...page.list.filter((a) => !known.has(a.id))]
      activityTotal.value = page.total
      commentTotal.value = page.commentCount
      activityPage.value = next
    } catch {
      // 失败保持现状，"加载更早"按钮仍可重试
    } finally {
      loadingEarlier.value = false
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

  // ---- 交接 ----
  /**
   * 撤销最近一次交接：负责人退回交接前的人。
   * 成功返回改写后的工作项，由宿主同步自身状态（负责人已经变了）；失败返回 null。
   * 动态在这里顺手重拉，撤销记录才会出现在时间线上。
   */
  async function revokeHandoff(): Promise<Issue | null> {
    if (!(await confirmAction('确认撤销这次交接？负责人将退回交接前的人。', '撤销交接', '撤销'))) {
      return null
    }
    try {
      const updated = await revokeHandoffApi(options.issueId())
      await loadActivities()
      notify('activity')
      ElMessage.success('已撤销交接')
      return updated
    } catch {
      // 接口层已提示（如「负责人已被再次变更，无法撤销这次交接」）
      return null
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
    activityTotal,
    commentTotal,
    hasEarlier,
    loadingEarlier,
    reload,
    loadActivities,
    loadEarlierActivities,
    loadAttachments,
    loadLinks,
    loadTags,
    sendComment,
    removeComment,
    revokeHandoff,
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

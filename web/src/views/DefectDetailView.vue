<script setup lang="ts">
import { onMounted, ref, reactive, computed, watch, nextTick } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import {
  filterIssues, updateIssue, listProjectStatuses, listWorkflowColumns,
  listProjectTags, listProjectMembers, listUsers, getProject, deleteIssue
} from '@/api'
import type {
  Issue, LabelVO, ProjectMember, UserOption, IssueStatusVO,
  IssueActivity, Project, WorkflowColumnVO
} from '@/api/types'
import { REQUIREMENT_TYPE_CODES, TASK_TYPE_CODES, DEFECT_TYPE_CODES, categoryOfTypeCode } from '@/api/types'
import type { WorkItemCategory } from '@/api/types'
import { ElMessage } from 'element-plus'
import { confirmAction } from '@/utils/confirm'
import { confirmHandoff } from '@/utils/handoff'
import {
  ArrowLeft, ChevronLeft, ChevronRight, Plus, X,
  Paperclip, Link2, Trash2,
  AlertTriangle, Send, Search
} from 'lucide-vue-next'
import CommentThread from '@/components/CommentThread.vue'
import RichTextEditor from '@/components/RichTextEditor.vue'
import IssueBasicFields from '@/components/IssueBasicFields.vue'
import CreateIssueDialog from '@/components/CreateIssueDialog.vue'
import { useUserStore } from '@/stores/user'
import { useProjectPermission } from '@/composables/useProjectPermission'
import { useIssueTabs } from '@/composables/useIssueTabs'
import { buildCommentTree, type CommentNode } from '@/utils/commentTree'
import ActivityItem from '@/components/ActivityItem.vue'
import UserAvatar from '@/components/common/UserAvatar.vue'
import { fmtTime, fmtDate } from '@/utils/datetime'

const props = withDefaults(defineProps<{
  projectId?: string
  issueId?: string
  embedded?: boolean
}>(), {
  embedded: false
})

const emit = defineEmits<{
  (e: 'close'): void
  (e: 'update:issueId', id: string): void
}>()

const route = useRoute()
const router = useRouter()
const pid = computed(() => props.projectId || (route.params.projectId as string) || '')



const project = ref<Project | null>(null)
const loading = ref(false)
const allIssues = ref<Issue[]>([])
const allTags = ref<LabelVO[]>([])
const members = ref<ProjectMember[]>([])
const users = ref<UserOption[]>([])
const statuses = ref<IssueStatusVO[]>([])
// statusId → 该列的交接配置，用于改状态前的转交确认
const handoffByStatus = ref<Record<string, WorkflowColumnVO>>({})
const { hasPermission, refresh: refreshPermission } = useProjectPermission(() => pid.value)
const canWrite = computed(() => hasPermission('issue:edit'))
const canComment = computed(() => hasPermission('issue:comment'))
// 删除是破坏性操作：与后端 Permission.ISSUE_DELETE 同一权限码，无权限时按钮不出现
const canDelete = computed(() => hasPermission('issue:delete'))

const currentIssue = ref<Issue | null>(null)
const currentIndex = computed(() =>
  currentIssue.value ? allIssues.value.findIndex((i) => i.id === currentIssue.value!.id) : -1
)

// 按工作项类型区分标题/字段（任务/需求不再展示缺陷专属文案）
const issueTypeCode = computed(() => currentIssue.value?.issueTypeCode || '')
const isDefect = computed(() => DEFECT_TYPE_CODES.includes(issueTypeCode.value))
const typeLabel = computed(() => {
  if (TASK_TYPE_CODES.includes(issueTypeCode.value)) return '任务'
  if (REQUIREMENT_TYPE_CODES.includes(issueTypeCode.value)) return '需求'
  if (DEFECT_TYPE_CODES.includes(issueTypeCode.value)) return '缺陷'
  return '工作项'
})
const titlePlaceholder = computed(() => `${typeLabel.value}标题`)
const prevIssue = computed(() => currentIndex.value > 0 ? allIssues.value[currentIndex.value - 1] : null)
const nextIssue = computed(() =>
  currentIndex.value >= 0 && currentIndex.value < allIssues.value.length - 1
    ? allIssues.value[currentIndex.value + 1] : null
)

const priorityOptions = [
  { v: 'P0', label: '紧急' },
  { v: 'P1', label: '高' },
  { v: 'P2', label: '中' },
  { v: 'P3', label: '低' }
]
const severityOptions = [
  { v: 'S1', label: '致命' },
  { v: 'S2', label: '严重' },
  { v: 'S3', label: '一般' },
  { v: 'S4', label: '轻微' }
]

// 动态&评论 / 附件 / 关联 / 标签四个页签的数据与增删逻辑收敛到共享 composable
// （与 IssueDetailPanel 共用，避免两处实现漂移）
const {
  activities,
  attachments,
  links,
  tags,
  hasEarlier,
  loadingEarlier,
  loadEarlierActivities,
  commentTotal,
  reload: reloadTabs,
  sendComment: sendCommentApi,
  uploadFile: uploadFileApi,
  addNamedAttachment,
  addLinkItems,
  removeComment,
  removeAttachment: removeAttach,
  removeLink: removeLinkApi,
  addTag: addTagApi,
  removeTag,
  revokeHandoff: revokeHandoffApi
} = useIssueTabs({
  issueId: () => currentIssue.value?.id || '',
  projectId: () => pid.value
})

const mainTab = ref<'activity' | 'link' | 'other'>('activity')
const subTab = ref<'all' | 'comment' | 'dynamic'>('all')
const commentText = ref('')
const commenting = ref(false)
const replyTo = ref<IssueActivity | null>(null)   // 当前回复目标评论（嵌套回复）
const commentEditorRef = ref<InstanceType<typeof RichTextEditor> | null>(null)
const attachInputRef = ref<HTMLInputElement | null>(null)
const linkType = ref('关联')
const linkFilter = ref<'all' | 'requirement' | 'task' | 'defect'>('all')
const linkOptions = ref<Issue[]>([])
const linkLoading = ref(false)
const linkSelected = ref<string[]>([])
const createDialogVisible = ref(false)
const LINK_TYPE_MAP: Record<string, string[]> = {
  all: [],
  requirement: REQUIREMENT_TYPE_CODES,
  task: TASK_TYPE_CODES,
  defect: DEFECT_TYPE_CODES
}
const linkFilters: { k: 'all' | 'requirement' | 'task' | 'defect'; label: string }[] = [
  { k: 'all', label: '全部' },
  { k: 'requirement', label: '需求' },
  { k: 'task', label: '任务' },
  { k: 'defect', label: '缺陷' }
]
const attachName = ref('')
const fieldsRef = ref<InstanceType<typeof IssueBasicFields> | null>(null)

const form = reactive({
  title: '',
  description: '',
  priority: 'P2',
  severity: 'S3',
  module: '',
  statusId: '',
  assigneeId: '',
  verifierId: '',
  fixVersion: '',
  source: '',
  dueDate: '' as string | null,
  planStart: '' as string | null,
  noFixReason: '',
  iteration: '',
  ccUsers: [] as string[],
  participants: [] as string[]
})

const editingDescription = ref(false)

const filteredActivities = computed(() => {
  if (subTab.value === 'comment') return activities.value.filter((a) => a.type === 'COMMENT')
  if (subTab.value === 'dynamic') return activities.value.filter((a) => a.type !== 'COMMENT')
  return activities.value
})
// 评论总数由后端随分页一起返回（分页后当前页长度不等于总数）
const commentCount = computed(() => commentTotal.value)

// 评论楼层：由纯函数构建，与抽屉面板共用同一套嵌套/排序规则
const commentTree = computed<(IssueActivity | CommentNode)[]>(() =>
  buildCommentTree(filteredActivities.value)
)
const userStore = useUserStore()
// 当前登录用户真实 id（避免硬编码 admin 导致角色/权限误判）
const currentUserName = computed(() => userStore.user?.displayName || userStore.user?.username || '')

// 元数据（项目/状态/标签/成员/用户）各自独立加载：任一失败只降级对应下拉数据，
// 不阻塞详情正文渲染；错误提示由 api 拦截器统一给出，此处不重复弹出。
async function loadMeta() {
  try { project.value = await getProject(pid.value) } catch { /* 项目名降级为标题占位 */ }
  // 状态下拉按工作项类别取（任务/需求/缺陷各一套工作流），在 selectIssue 拿到类型后再加载
  try { allTags.value = await listProjectTags(pid.value) } catch { /* 项目标签库为空 */ }
  try { members.value = await listProjectMembers(pid.value) } catch { /* 负责人候选为空 */ }
  try { users.value = await listUsers() } catch { /* 创建人/报告人名称显示为 '—' */ }
  refreshPermission()
}

/** 同类工作项的类型码集合：上一条/下一条只在同一类之间导航 */
function siblingTypeCodes(code?: string | null): string[] {
  switch (categoryOfTypeCode(code)) {
    case 'BUG': return DEFECT_TYPE_CODES
    case 'REQUIREMENT': return REQUIREMENT_TYPE_CODES
    default: return TASK_TYPE_CODES
  }
}

async function loadIssues() {
  loading.value = true
  try {
    const targetId = props.embedded
      ? props.issueId
      : route.params.issueId as string | undefined
    // 这里只做定位用：一次拉回项目内全部工作项，找到当前项后再按它的类别收窄
    const res = await filterIssues({ projectId: pid.value, page: 1, size: 500 })
    const target = targetId
      ? res.list.find((i) => i.id === targetId)
      : res.list[0]
    // 上一条/下一条只在同类工作项间导航。此前三类全量入列，
    // 在缺陷页一路翻页会翻到任务/需求，右栏「工作项类型」随即显示成「故事」，
    // 看着像类型取错了，其实是列表把三类混在了一起。
    const codes = target ? siblingTypeCodes(target.issueTypeCode) : null
    allIssues.value = codes
      ? res.list.filter((i) => codes.includes(i.issueTypeCode || ''))
      : res.list
    if (target) await selectIssue(target)
  } catch {
    // 列表加载失败已在请求层提示；保留空列表展示空态，避免抛未捕获的 rejection
  } finally {
    loading.value = false
  }
}

/**
 * 按当前工作项的类别加载状态下拉。
 * 同一个详情组件承接任务/需求/缺陷，三类状态的节点不同，必须跟随 issueTypeCode 切换；
 * 类别未变时跳过，避免相邻工作项间来回切换时空转。
 */
let statusCategory: WorkItemCategory | '' = ''
async function ensureStatuses(category: WorkItemCategory) {
  if (statusCategory === category) return
  statusCategory = category
  try {
    statuses.value = await listProjectStatuses(pid.value, category)
  } catch {
    statuses.value = []
  }
  try {
    const columns = await listWorkflowColumns(pid.value, category)
    handoffByStatus.value = Object.fromEntries(columns.map((c) => [c.statusId, c]))
  } catch {
    // 交接配置缺失只降级为「不提示交接」，不影响状态修改
    handoffByStatus.value = {}
  }
}

/**
 * 字段变更统一入口。状态是唯一带交接副作用的字段：
 * 换状态前若目标列会换人，先确认；用户取消则把下拉还原，不落库。
 */
async function onFieldChange(e: { field: string; payload: { patch: Record<string, unknown>; clear?: string[] } }) {
  const patch = e.payload?.patch || {}
  const nextStatus = patch.statusId
  if (typeof nextStatus === 'string' && nextStatus !== currentIssue.value?.statusId) {
    const confirmed = await confirmHandoff(
      handoffByStatus.value[nextStatus],
      form.assigneeId || currentIssue.value?.assigneeId
    )
    if (!confirmed) {
      form.statusId = currentIssue.value?.statusId || ''
      return
    }
  }
  await saveField(e.field, patch, e.payload.clear)
}

/**
 * 撤销交接后负责人回到交接前的人：本地状态要同步更新，
 * 否则页面上仍显示刚接手的人，继续操作会带着过期的负责人提交。
 */
async function onRevokeHandoff() {
  const updated = await revokeHandoffApi()
  if (!updated) return
  currentIssue.value = updated
  const idx = allIssues.value.findIndex((i) => i.id === updated.id)
  if (idx >= 0) allIssues.value[idx] = updated
  form.assigneeId = updated.assigneeId || ''
}

async function selectIssue(issue: Issue) {
  currentIssue.value = issue
  await ensureStatuses(categoryOfTypeCode(issue.issueTypeCode))
  if (props.embedded) {
    emit('update:issueId', issue.id)
  } else {
    router.replace(`/projects/${pid.value}/defects/${issue.id}`)
  }
  await loadDetail(issue.id)
}

async function loadDetail(issueId: string) {
  // 四个页签并行加载（读取失败时保留上一次数据，不阻断下方表单回填）
  await reloadTabs()
  // 重置关联区临时状态，确保不同工作项之间数据隔离
  linkFilter.value = 'all'
  linkOptions.value = []
  linkSelected.value = []
  const issue = allIssues.value.find((i) => i.id === issueId)
  if (issue) {
    form.title = issue.title
    form.description = issue.description || ''
    form.priority = issue.priority || 'P2'
    form.severity = issue.severity || 'S3'
    form.module = issue.module || ''
    form.statusId = issue.statusId || ''
    form.assigneeId = issue.assigneeId || ''
    form.verifierId = issue.verifierId || ''
    form.fixVersion = issue.fixVersion || ''
    form.source = issue.source || ''
    form.dueDate = issue.dueDate || ''
    form.iteration = issue.iteration || ''
    form.planStart = issue.planStart || ''
    form.noFixReason = issue.noFixReason || ''
    form.ccUsers = issue.ccUsers || []
    form.participants = issue.participants || []
  }
}

watch(() => props.issueId, (id) => {
  if (!props.embedded || !id) return
  if (currentIssue.value?.id !== id) {
    const issue = allIssues.value.find((i) => i.id === id)
    // 列表已按当前类别收窄，跨类跳转时不在其中 → 重新装载，否则页面停在旧工作项上
    if (issue) selectIssue(issue)
    else loadIssues()
  }
})

watch(() => route.params.issueId, (id) => {
  if (props.embedded) return
  if (id && currentIssue.value?.id !== id) {
    const issue = allIssues.value.find((i) => i.id === id)
    if (issue) selectIssue(issue)
    else loadIssues()
  }
})

async function saveField(field: string, payload: Record<string, unknown>, clearFields?: string[]): Promise<boolean> {
  if (!currentIssue.value || !canWrite.value) {
    ElMessage.warning('无编辑权限')
    return false
  }
  try {
    const body: Record<string, unknown> = { ...payload }
    if (clearFields && clearFields.length) body.clearFields = clearFields
    const updated = await updateIssue(currentIssue.value.id, body)
    Object.assign(currentIssue.value, updated)
    const idx = allIssues.value.findIndex((i) => i.id === updated.id)
    if (idx >= 0) Object.assign(allIssues.value[idx], updated)
    ElMessage.success(`${field}已更新`)
    return true
  } catch {
    ElMessage.error(`${field}更新失败`)
    return false
  }
}

async function saveTitle() {
  if (!form.title.trim()) return
  await saveField('标题', { title: form.title })
}

function startEditDescription() {
  if (!canWrite.value) return
  editingDescription.value = true
}
function cancelEditDescription() {
  editingDescription.value = false
  if (currentIssue.value) form.description = currentIssue.value.description || ''
}
async function saveDescription() {
  const ok = await saveField('描述', { description: form.description })
  if (ok) {
    editingDescription.value = false
    if (currentIssue.value) form.description = currentIssue.value.description || ''
  }
}

function startComment() {
  replyTo.value = null
  commenting.value = true
  commentText.value = ''
  nextTick(() => commentEditorRef.value?.focus())
}
function startReply(node: IssueActivity) {
  replyTo.value = node
  commenting.value = true
  commentText.value = `@${node.actorName} `
  nextTick(() => commentEditorRef.value?.focus())
}
function cancelComment() {
  commenting.value = false
  commentText.value = ''
  replyTo.value = null
}
async function sendComment() {
  if (!commentText.value.trim()) return
  if (!canComment.value) {
    ElMessage.warning('无评论权限')
    return
  }
  const ok = await sendCommentApi(commentText.value, replyTo.value?.id || null)
  if (!ok) return
  commentText.value = ''
  commenting.value = false
  replyTo.value = null
}
async function onAttachChange(e: Event) {
  const input = e.target as HTMLInputElement
  const file = input.files?.[0]
  if (file && currentIssue.value) {
    // 上传成功后把附件引用插入评论文本，失败提示由接口层统一给出
    const ok = await uploadFileApi(file)
    if (ok) commentEditorRef.value?.insert(`\n[附件](${file.name})\n`)
  }
  input.value = ''
}
function triggerAttach() {
  attachInputRef.value?.click()
}

// 描述只读渲染：把 ![alt](url) 转安全 <img>，其余转义（与评论渲染一致）
function escapeHtml(s: string): string {
  return s.replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;')
}
function renderDescription(content: string | null | undefined): string {
  if (!content) return '<span class="text-ink-placeholder">暂无描述</span>'
  const imgRe = /!\[([^\]]*)\]\(([^)\s]+)\)/g
  let out = ''
  let last = 0
  let m: RegExpExecArray | null
  while ((m = imgRe.exec(content))) {
    out += escapeHtml(content.slice(last, m.index))
    const url = m[2]
    if (isSafeImageUrl(url)) {
      out += `<img src="${url.replace(/"/g, '%22')}" alt="${m[1].replace(/"/g, '')}" style="max-width:100%;border-radius:6px;margin:4px 0" />`
    } else {
      out += escapeHtml(m[0])
    }
    last = imgRe.lastIndex
  }
  out += escapeHtml(content.slice(last))
  return out
}
/**
 * 描述里的图片是否可作为 img src：允许外链 http(s) 与站内绝对路径，
 * 但不放行协议相对地址（//evil.com）。
 * 不再写死 /api 前缀：上传地址随服务端 context-path 变化。
 */
function isSafeImageUrl(url: string): boolean {
  return /^https?:\/\//.test(url) || (url.startsWith('/') && !url.startsWith('//'))
}

// 从描述中提取图片列表，用于只读缩略图回显
const descriptionImages = computed(() => {
  const desc = currentIssue.value?.description || ''
  const res: { url: string; name: string }[] = []
  const imgRe = /!\[([^\]]*)\]\(([^)\s]+)\)/g
  let m: RegExpExecArray | null
  while ((m = imgRe.exec(desc))) {
    const url = m[2]
    if (isSafeImageUrl(url)) {
      res.push({ url, name: m[1] || '图片' })
    }
  }
  return res
})

async function addAttach() {
  if (await addNamedAttachment(attachName.value)) attachName.value = ''
}

/**
 * 删除类操作统一二次确认：评论与附件一旦删除不可恢复，
 * 直接落库容易因误触丢失协作记录。
 */
async function confirmRemove(text: string, title: string): Promise<boolean> {
  // 用户取消时返回 false，静默不触发接口
  return confirmAction(text, title)
}

async function onRemoveComment(commentId: string) {
  if (await confirmRemove('确定删除该评论吗？删除后不可恢复。', '删除评论')) {
    await removeComment(commentId)
  }
}

async function onRemoveAttach(attachmentId: string) {
  if (await confirmRemove('确定删除该附件吗？', '删除附件')) {
    await removeAttach(attachmentId)
  }
}

/** 附件只能由上传者本人删除；系统管理员可代删（与后端权限判定一致） */
function canRemoveAttachment(uploadedBy: string): boolean {
  return uploadedBy === userStore.user?.id || userStore.isAdmin
}

async function searchLinks(keyword?: string) {
  if (!currentIssue.value) return
  linkLoading.value = true
  try {
    const codes = LINK_TYPE_MAP[linkFilter.value]
    const res = await filterIssues({
      projectId: currentIssue.value.projectId,
      keyword: keyword?.trim() || undefined,
      issueTypeCodes: codes.length ? codes : undefined,
      page: 1,
      size: 200,
    })
    const linkedIds = new Set(links.value.map((l) => l.targetIssueId))
    linkOptions.value = res.list.filter((i) => i.id !== currentIssue.value!.id && !linkedIds.has(i.id))
  } finally {
    linkLoading.value = false
  }
}

// 下拉展开时：若尚未加载过选项，则加载一次
function onLinkSelectVisible(visible: boolean) {
  if (visible && linkOptions.value.length === 0) {
    searchLinks()
  }
}

// 下拉远程搜索
async function remoteSearchLinks(keyword: string) {
  await searchLinks(keyword)
}

// 切换工作项类型筛选
async function onLinkFilterChange(k: 'all' | 'requirement' | 'task' | 'defect') {
  linkFilter.value = k
  linkOptions.value = []
  linkSelected.value = []
  await searchLinks()
}

// 选中变化：只处理「新增关联」。移除统一由已关联列表的删除按钮按 link id 精确处理，
// 避免下拉只展示当前筛选类型（如任务）时，把其他类型（需求/缺陷）已关联项误判为移除而误删。
async function onLinkSelectionChange(val: string[]) {
  if (!currentIssue.value) return
  const linkedIds = new Set(links.value.map((l) => l.targetIssueId))
  const added = val.filter((id) => !linkedIds.has(id))
  if (added.length) await addLinkItems(added, linkType.value)
  // 选中后立即清空临时选择，便于下次再选，且不会触发误删
  linkSelected.value = []
}

async function removeLink(id: string) {
  // 移除成功后刷新候选列表，避免刚删除的项仍留在下拉中
  if (await removeLinkApi(id)) await searchLinks()
}

watch(() => mainTab.value, (v) => {
  if (v === 'link') {
    linkSelected.value = []
    searchLinks()
  }
})

async function onAddTag() {
  if (await addTagApi(fieldsRef.value?.tagInput || '')) fieldsRef.value?.clearTagInput()
}

function back() {
  // 返回缺陷列表，保留「缺陷」上下文（此前跳到 /table 会退化为「默认看板」）
  router.push(`/projects/${pid.value}/defects`)
}
function close() {
  emit('close')
}

/**
 * 删除当前工作项。
 *
 * 后端会连子任务、评论、附件、标签一起删（BaseWorkItemService#delete），无法恢复，
 * 所以确认框里把"会一并删除"写清楚，而不是只问"确认删除吗"。
 */
async function onDeleteIssue() {
  const issue = currentIssue.value
  if (!issue) return
  const ok = await confirmAction(
    `删除「${issue.title}」后不可恢复，其子任务、评论与附件会一并删除。`,
    '删除工作项',
    '删除'
  )
  if (!ok) return
  try {
    await deleteIssue(issue.id)
    ElMessage.success('已删除')
    // 抽屉里删除后直接关闭；路由模式返回列表，否则详情页会停在一个已不存在的 id 上
    if (props.embedded) close()
    else back()
  } catch {
    ElMessage.error('删除失败')
  }
}
function goPrev() { if (prevIssue.value) selectIssue(prevIssue.value) }
function goNext() { if (nextIssue.value) selectIssue(nextIssue.value) }


const userName = (id: string | null) => users.value.find((u) => u.id === id)?.displayName || '—'



onMounted(async () => {
  // 元数据与列表串行：先拿到状态/成员字典，列表回落表单时下拉才有可选项
  await loadMeta()
  await loadIssues()
})
</script>

<template>
  <div class="app-canvas flex h-full flex-col">
    <!-- ======== 顶部导航（embedded：紧凑顶栏） ======== -->
    <header
      v-if="embedded"
      class="glass-bar flex h-12 shrink-0 items-center gap-2 border-b px-3"
    >
      <AlertTriangle :size="16" class="shrink-0 text-danger" />
      <span
        class="shrink-0 rounded bg-primary px-1.5 py-0.5 font-mono text-xs text-white"
      >{{ currentIssue?.issueKey || '—' }}</span>
      <!-- 删除入口放在显眼位置：此前只有一个点了没反应的「更多操作」死按钮，用户找不到删除 -->
      <button
        v-if="canDelete"
        class="flex h-7 shrink-0 items-center gap-1 rounded px-2 text-xs text-ink-secondary transition hover:bg-danger/10 hover:text-danger"
        title="删除该工作项"
        @click="onDeleteIssue"
      ><Trash2 :size="14" /> 删除</button>
      <div class="ml-auto flex items-center gap-2">
        <button
          class="flex h-7 w-7 items-center justify-center rounded border border-line disabled:opacity-40"
          :disabled="!prevIssue"
          @click="goPrev"
        ><ChevronLeft :size="16" /></button>
        <span class="text-xs tabular-nums text-ink-secondary">
          {{ currentIndex >= 0 ? currentIndex + 1 : 0 }} / {{ allIssues.length }}
        </span>
        <button
          class="flex h-7 w-7 items-center justify-center rounded border border-line disabled:opacity-40"
          :disabled="!nextIssue"
          @click="goNext"
        ><ChevronRight :size="16" /></button>
        <span class="text-line">|</span>
        <button
          class="flex h-7 w-7 items-center justify-center rounded text-ink-secondary transition hover:bg-surface-page hover:text-danger"
          title="关闭"
          @click="close"
        ><X :size="16" /></button>
      </div>
    </header>

    <!-- ======== 顶部导航（路由模式） ======== -->
    <header
      v-else
      class="glass-bar flex h-12 shrink-0 items-center gap-3 border-b px-4"
    >
      <button class="flex items-center gap-1 text-sm text-ink-regular hover:opacity-80" @click="back">
        <ArrowLeft :size="16" /> 返回列表
      </button>
      <span class="text-line">|</span>
      <span class="text-sm text-ink-regular">{{ project?.name || '工作项详情' }}</span>
      <div class="ml-auto flex items-center gap-2">
        <button
          v-if="canDelete"
          class="flex h-7 items-center gap-1 rounded px-2 text-xs text-ink-secondary transition hover:bg-danger/10 hover:text-danger"
          title="删除该工作项"
          @click="onDeleteIssue"
        ><Trash2 :size="14" /> 删除</button>
        <button
          class="flex h-7 w-7 items-center justify-center rounded border border-line disabled:opacity-40"
          :disabled="!prevIssue"
          @click="goPrev"
        ><ChevronLeft :size="16" /></button>
        <span class="text-xs text-ink-secondary">
          {{ currentIndex >= 0 ? currentIndex + 1 : 0 }} / {{ allIssues.length }}
        </span>
        <button
          class="flex h-7 w-7 items-center justify-center rounded border border-line disabled:opacity-40"
          :disabled="!nextIssue"
          @click="goNext"
        ><ChevronRight :size="16" /></button>
      </div>
    </header>

    <div v-if="currentIssue" class="flex-1 overflow-y-auto">
      <!-- 两栏布局（容器撑满滚动视口，保证两栏等高） -->
      <div class="flex h-full items-stretch gap-4 p-4">
        <!-- ======== 左侧主内容 ======== -->
        <main class="flex min-w-0 flex-1 flex-col">
          <!-- 标题区：缺陷名称 + 元信息行（白卡片） -->
          <section class="glass-panel mb-4 rounded-md p-4">
            <input
              v-model="form.title"
              class="w-full border-none bg-transparent text-xl font-semibold text-ink outline-none"
              :placeholder="titlePlaceholder"
              @blur="saveTitle"
            />
            <div class="mt-1 flex flex-wrap items-center gap-x-3 gap-y-1 text-xs text-ink-secondary">
              <span>ID {{ currentIssue.issueKey }}</span>
              <span>·</span>
              <span>{{ userName(currentIssue.reporterId) }} {{ fmtTime(currentIssue.createdAt) }}创建</span>
              <span>·</span>
              <span>{{ fmtTime(currentIssue.updatedAt) }}更新</span>
            </div>
          </section>

          <!-- 描述卡片（支持编辑） -->
          <section class="glass-panel rounded-md p-4">
            <div class="mb-3 flex items-center justify-between">
              <h3 class="text-sm font-medium text-ink">描述</h3>
              <div v-if="canWrite" class="flex items-center gap-2">
                <button
                  v-if="!editingDescription"
                  class="text-xs text-primary transition hover:opacity-75"
                  @click="startEditDescription"
                >编辑</button>
                <template v-else>
                  <button
                    class="rounded bg-primary px-3 py-1 text-xs text-white transition hover:opacity-90"
                    @click="saveDescription"
                  >保存</button>
                  <button
                    class="rounded bg-surface-page px-3 py-1 text-xs text-ink-regular transition"
                    @click="cancelEditDescription"
                  >取消</button>
                </template>
              </div>
            </div>
            <!-- 编辑态：富文本编辑器，支持 Markdown 与粘贴截图 -->
            <RichTextEditor
              v-if="editingDescription"
              v-model="form.description"
              :project-id="projectId"
              :rows="8"
              :placeholder="`请输入${typeLabel}描述，支持 Markdown，可 Ctrl+V 粘贴截图`"
            />
            <!-- 只读态：渲染描述正文，支持 ![alt](url) 图片语法 -->
            <template v-else>
              <div
                v-if="currentIssue"
                class="description-content whitespace-pre-wrap rounded bg-surface-page px-3 py-2 text-sm text-ink-regular"
                v-html="renderDescription(currentIssue.description)"
              ></div>
              <!-- 描述中若含图片，在此以缩略图形式回显（只读） -->
              <div v-if="descriptionImages.length" class="mt-3 grid grid-cols-2 gap-3">
                <div
                  v-for="img in descriptionImages"
                  :key="img.url"
                  class="overflow-hidden rounded-lg border border-line-light"
                >
                  <img :src="img.url" :alt="img.name" class="h-44 w-full object-cover" />
                </div>
              </div>
            </template>
          </section>

          <!-- 评论区（动态&评论 / 关联项 / 其他）→ 描述卡片下方，flex-1 拉伸填满至底部 -->
          <section class="glass-panel mt-4 flex-1 rounded-md p-4">
            <!-- 主标签页 -->
            <div class="mb-4 flex items-center gap-1 border-b border-line-light">
              <button
                v-for="t in [
                  { k: 'activity', label: '动态&评论', count: commentCount },
                  { k: 'link', label: '关联项', count: links.length },
                  { k: 'other', label: '其他', count: 0 }
                ]"
                :key="t.k"
                class="relative px-4 py-2.5 text-sm transition"
                :class="mainTab === t.k ? 'text-primary' : 'text-ink-secondary'"
                @click="mainTab = t.k as 'activity' | 'link' | 'other'"
              >
                {{ t.label }}
                <span class="ml-1 text-xs">{{ t.count }}</span>
                <span
                  v-if="mainTab === t.k"
                  class="absolute bottom-0 left-2 right-2 h-0.5 rounded-full bg-primary"
                />
              </button>
            </div>

            <!-- 动态&评论 -->
            <div v-if="mainTab === 'activity'">
              <!-- 子标签 -->
              <div class="mb-4 flex items-center gap-2">
                <button
                  v-for="s in [
                    { k: 'all', label: '全部' },
                    { k: 'comment', label: '评论·' + commentCount },
                    { k: 'dynamic', label: '动态' }
                  ]"
                  :key="s.k"
                  class="rounded-full px-3 py-1 text-xs transition"
                  :class="subTab === s.k
                    ? 'bg-primary-light text-primary'
                    : 'bg-surface-page text-ink-secondary'"
                  @click="subTab = s.k as 'all' | 'comment' | 'dynamic'"
                >{{ s.label }}</button>
              </div>

              <!-- 评论列表（评论以嵌套楼层展示，状态活动平铺） -->
              <div class="space-y-5">
                <template v-for="a in commentTree" :key="a.id">
                  <CommentThread
                    v-if="a.type === 'COMMENT'"
                    :node="a"
                    :depth="0"
                    :can-write="canComment"
                    :current-user-name="currentUserName"
                    @reply="startReply"
                    @remove="onRemoveComment"
                  />
                  <!-- 动态与评论共用 ActivityItem：同款头像 + 姓名 + 时间 + 正文 -->
                  <ActivityItem
                    v-else
                    :node="a"
                    :can-write="canWrite"
                    @revoke="onRevokeHandoff"
                  />
                </template>
                <div v-if="!filteredActivities.length" class="py-8 text-center text-sm text-ink-placeholder">
                  暂无内容
                </div>
                <!-- 时间线分页：首屏最新 50 条，更早的按需追加 -->
                <div v-if="hasEarlier" class="pt-1 text-center">
                  <button
                    class="rounded px-3 py-1.5 text-xs text-ink-placeholder transition hover:bg-surface-hover hover:text-ink"
                    :disabled="loadingEarlier"
                    @click="loadEarlierActivities"
                  >
                    {{ loadingEarlier ? '加载中…' : '加载更早的动态' }}
                  </button>
                </div>
              </div>
            </div>

            <!-- 关联项 -->
            <div v-else-if="mainTab === 'link'">
              <!-- 下拉搜索选择 + 关联类型 -->
              <div class="mb-3 flex gap-2">
                <el-select
                  v-model="linkSelected"
                  multiple
                  filterable
                  remote
                  reserve-keyword
                  :remote-method="remoteSearchLinks"
                  :loading="linkLoading"
                  clearable
                  collapse-tags
                  collapse-tags-tooltip
                  placeholder="点击选择并搜索关联工作项"
                  class="flex-1"
                  popper-class="issue-link-popper"
                  @visible-change="onLinkSelectVisible"
                  @change="onLinkSelectionChange"
                >
                  <template #prefix><Search :size="14" class="text-ink-placeholder" /></template>
                  <template #header>
                    <div class="flex items-center gap-2 border-b border-line-light px-3 py-2">
                      <span class="text-xs text-ink-secondary">类型</span>
                      <button
                        v-for="f in linkFilters"
                        :key="f.k"
                        class="rounded-full px-3 py-0.5 text-xs transition"
                        :class="linkFilter === f.k ? 'bg-primary-light text-primary' : 'bg-surface-page text-ink-secondary'"
                        @click="onLinkFilterChange(f.k)"
                      >{{ f.label }}</button>
                    </div>
                  </template>
                  <el-option
                    v-for="opt in linkOptions"
                    :key="opt.id"
                    :label="`${opt.issueKey} ${opt.title}`"
                    :value="opt.id"
                  >
                    <span class="shrink-0 text-base leading-none">{{ opt.issueTypeIcon }}</span>
                    <span class="shrink-0 font-mono text-xs text-ink-secondary">{{ opt.issueKey }}</span>
                    <span class="min-w-0 flex-1 truncate">{{ opt.title }}</span>
                    <span class="shrink-0 items-center gap-1 text-xs text-ink-secondary">
                      {{ opt.assigneeName || '未分配' }}
                    </span>
                  </el-option>
                </el-select>
                <el-select
                  v-model="linkType"
                  class="w-28 shrink-0"
                  placeholder="关联类型"
                >
                  <el-option label="关联" value="关联" />
                  <el-option label="阻塞" value="阻塞" />
                  <el-option label="重复" value="重复" />
                  <el-option label="修复" value="修复" />
                </el-select>
              </div>

              <!-- 新建工作项入口 -->
              <button class="mt-2 flex items-center gap-1 text-sm text-primary transition hover:opacity-80" @click="createDialogVisible = true">
                <Plus :size="14" /> 新建工作项
              </button>

              <!-- 已关联列表 -->
              <div class="mt-4 border-t border-line-light pt-3">
                <div class="mb-2 text-xs text-ink-secondary">已关联</div>
                <div class="space-y-2">
                  <div v-for="l in links" :key="l.id" class="flex items-center justify-between rounded-lg bg-surface-page px-3 py-2 text-sm">
                    <div class="flex items-center gap-2">
                      <Link2 :size="14" class="text-ink-secondary" />
                      <span class="rounded bg-primary px-1.5 py-0.5 text-xs text-white">{{ l.relationType }}</span>
                      <span class="font-mono text-ink">{{ l.targetKey }}</span>
                      <span class="text-ink-secondary">{{ l.targetTitle }}</span>
                    </div>
                    <button class="text-ink-placeholder hover:text-danger" @click="removeLink(l.id)"><Trash2 :size="14" /></button>
                  </div>
                  <div v-if="!links.length" class="py-8 text-center text-sm text-ink-placeholder">暂无关联</div>
                </div>
              </div>

              <!-- 新建工作项弹窗 -->
              <CreateIssueDialog
                v-model="createDialogVisible"
                :project-id="pid"
                title="新建工作项"
                default-issue-type-code="BUG"
                @created="createDialogVisible = false; searchLinks()"
              />
            </div>

            <!-- 其他：附件 -->
            <div v-else>
              <div class="mb-4 flex gap-2">
                <input
                  v-model="attachName"
                  placeholder="文件名，如 crash.log"
                  class="flex-1 rounded border border-line px-3 py-1.5 text-sm outline-none"
                  @keyup.enter="addAttach"
                />
                <button
                  class="flex items-center gap-1 rounded bg-primary px-3 text-sm text-white hover:opacity-90"
                  @click="addAttach"
                ><Paperclip :size="14" /> 添加</button>
              </div>
              <div class="space-y-2">
                <div v-for="a in attachments" :key="a.id" class="flex items-center justify-between rounded-lg bg-surface-page px-3 py-2 text-sm">
                  <div class="flex items-center gap-2">
                    <Paperclip :size="14" class="text-ink-secondary" />
                    <span class="text-ink">{{ a.fileName }}</span>
                    <span class="text-xs text-ink-placeholder">{{ a.uploaderName }} · {{ fmtTime(a.createdAt) }}</span>
                  </div>
                  <button v-if="canRemoveAttachment(a.uploadedBy)" class="text-ink-placeholder hover:text-danger" @click="onRemoveAttach(a.id)"><Trash2 :size="14" /></button>
                </div>
                <div v-if="!attachments.length" class="py-8 text-center text-sm text-ink-placeholder">暂无附件</div>
              </div>
            </div>
          </section>

          <!-- 评论输入框：始终钉在左侧面板最底部，默认占位，点击展开富文本编辑器 -->
          <section
            class="glass-bar sticky bottom-0 z-10 shadow-[0_-6px_12px_-6px_rgba(0,0,0,0.06)]"
          >
            <!-- 默认占位：未编辑时显示，点击展开富文本 -->
            <div
              v-if="!commenting"
              class="glass-inset flex cursor-text items-center gap-3 rounded-md p-3"
              @click="startComment"
            >
              <UserAvatar :name="currentUserName" :src="userStore.user?.avatarUrl" :size="36" />
              <span class="text-sm text-ink-placeholder">点击输入评论…</span>
            </div>

            <!-- 编辑态：富文本（Markdown 工具栏） -->
            <div
              v-else
              class="glass-inset rounded-md p-3"
            >
              <div class="mb-2 flex items-center gap-2 text-xs text-ink-secondary">
                <span v-if="replyTo">正在回复 <b class="text-primary">@{{ replyTo.actorName }}</b></span>
                <span v-else>发表评论</span>
                <button v-if="replyTo" class="text-ink-placeholder hover:underline" @click="cancelComment">取消回复</button>
              </div>
              <div class="flex items-start gap-3">
                <UserAvatar :name="currentUserName" :src="userStore.user?.avatarUrl" :size="36" />
                <div class="flex-1">
                  <!-- 富文本编辑器（含 Markdown 工具栏 / 图片上传 / Ctrl+V 粘贴） -->
                  <RichTextEditor
                    ref="commentEditorRef"
                    v-model="commentText"
                    :project-id="projectId"
                    :rows="4"
                    :disabled="!canWrite"
                    placeholder="Ctrl+V 直接上传截图，支持 Markdown"
                  />

                  <!-- 附件按钮（组件不含附件，独立触发） -->
                  <div class="mt-1 flex items-center gap-2">
                    <button
                      type="button"
                      class="flex items-center gap-1 rounded px-2 py-1 text-xs text-gray-500 hover:bg-gray-100"
                      @click="triggerAttach"
                    >
                      <Paperclip :size="13" /> 附件
                    </button>
                    <input
                      ref="attachInputRef"
                      type="file"
                      class="hidden"
                      @change="onAttachChange"
                    />
                  </div>

                  <!-- 底部操作 -->
                  <div class="mt-2 flex items-center justify-between">
                    <span class="text-xs text-ink-placeholder">Ctrl + Enter 发送</span>
                    <div class="flex gap-2">
                      <button
                        class="rounded border border-line bg-surface-page px-4 py-1.5 text-sm text-ink-regular hover:opacity-90"
                        @click="cancelComment"
                      >取消</button>
                      <button
                        class="flex items-center gap-1 rounded bg-primary px-4 py-1.5 text-sm text-white transition hover:opacity-90 disabled:opacity-50"
                        :disabled="!commentText.trim()"
                        @click="sendComment"
                      ><Send :size="14" /> 提交</button>
                    </div>
                  </div>
                </div>
              </div>
            </div>
          </section>
        </main>

        <!-- ======== 右侧信息栏 ======== -->
        <aside class="flex w-[380px] shrink-0 flex-col self-start">
          <div class="glass-panel rounded-md p-4">
            <h3 class="mb-4 text-sm font-medium text-ink">基础字段</h3>

            <!-- 工作项类型 -->
            <div class="mb-4 flex items-center justify-between">
              <label class="text-sm text-ink-regular">工作项类型</label>
              <div class="flex items-center gap-1.5 text-sm text-ink">
                <span>{{ currentIssue.issueTypeIcon || '📋' }}</span>
                {{ currentIssue.issueTypeName || typeLabel }}
              </div>
            </div>

            <IssueBasicFields
              ref="fieldsRef"
              :model-value="form"
              @update:model-value="(e) => Object.assign(form, e)"
              :statuses="statuses"
              :handoff-by-status="handoffByStatus"
              :members="members"
              :priority-options="priorityOptions"
              :severity-options="severityOptions"
              :tags="tags"
              :can-write="canWrite"
              :show-severity="isDefect"
              :show-no-fix-reason="isDefect"
              @field-change="onFieldChange"
              @add-tag="onAddTag"
              @remove-tag="(id) => removeTag(id)"
            />

            <!-- 归属项目（缺陷专属，新建走 props） -->
            <div class="mt-4">
              <label class="mb-1 block text-sm text-ink-regular">归属项目<span class="text-danger">*</span></label>
              <div class="truncate rounded border border-line px-2 py-1 text-sm text-ink" :title="project?.name">
                {{ project?.name || '—' }}
              </div>
            </div>

            <!-- 迭代 -->
            <div class="mt-4 flex items-center justify-between">
              <label class="text-sm text-ink-regular">迭代</label>
              <input
                v-model="form.iteration"
                placeholder="请选择"
                class="w-44 rounded border border-line bg-transparent px-2 py-1 text-sm text-ink outline-none"
                :disabled="!canWrite"
                @blur="saveField('迭代', { iteration: form.iteration || null }, form.iteration ? [] : ['iteration'])"
              />
            </div>
          </div>

          <!-- 附件区 -->
          <div class="glass-panel mt-4 rounded-md p-4">
            <div class="mb-3 flex items-center justify-between">
              <h3 class="text-sm font-medium text-ink">
                附件 <span class="text-xs font-normal text-ink-secondary">{{ attachments.length }}</span>
              </h3>
              <button
                class="flex h-7 w-7 items-center justify-center rounded bg-surface-page text-lg text-ink-secondary"
                @click="addAttach"
              ><Plus :size="16" /></button>
            </div>
            <div class="space-y-2">
              <div
                v-for="a in attachments"
                :key="a.id"
                class="flex items-center gap-2 rounded-lg px-2 py-2 text-sm"
              >
                <Paperclip :size="14" class="text-ink-secondary" />
                <span class="flex-1 truncate text-ink">{{ a.fileName }}</span>
                <span class="text-xs text-ink-placeholder">{{ fmtDate(a.createdAt) }}</span>
                <button v-if="canRemoveAttachment(a.uploadedBy)" class="text-ink-placeholder hover:text-danger" @click="onRemoveAttach(a.id)"><Trash2 :size="13" /></button>
              </div>
              <div v-if="!attachments.length" class="py-4 text-center text-xs text-ink-placeholder">
                暂无附件，点击 + 上传
              </div>
            </div>
          </div>
        </aside>
      </div>
    </div>

    <!-- 加载/空状态 -->
    <div v-else class="flex flex-1 items-center justify-center text-sm text-ink-secondary">
      <span v-if="loading">加载中…</span>
      <span v-else>暂无缺陷</span>
    </div>
  </div>
</template>

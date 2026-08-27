<script setup lang="ts">
import { onMounted, ref, reactive, computed, watch, nextTick } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import {
  filterIssues, updateIssue, listProjectStatuses,
  listProjectTags, listProjectMembers, listUsers, getProject,
  listActivities, addComment, deleteComment, listAttachments, addAttachment, deleteAttachment,
  uploadImage, uploadAttachment,
  listLinks, addLink, deleteLink, listIssueTags, addIssueTag, removeIssueTag
} from '@/api'
import type {
  Issue, LabelVO, ProjectMember, UserVO, IssueStatusVO,
  IssueActivity, AttachmentVO, LinkVO, Project
} from '@/api/types'
import { REQUIREMENT_TYPE_CODES, TASK_TYPE_CODES, DEFECT_TYPE_CODES } from '@/api/types'
import { ElMessage } from 'element-plus'
import {
  ArrowLeft, ChevronLeft, ChevronRight, Plus, X,
  Paperclip, Link2, Trash2,
  AlertTriangle, Send, MoreHorizontal, Search, Check
} from 'lucide-vue-next'
import CommentThread from '@/components/CommentThread.vue'
import RichTextEditor from '@/components/RichTextEditor.vue'
import IssueBasicFields from '@/components/IssueBasicFields.vue'
import CreateIssueDialog from '@/components/CreateIssueDialog.vue'
import { useUserStore } from '@/stores/user'
import { useProjectPermission } from '@/composables/useProjectPermission'
import { avatarChar, avatarColor } from '@/utils/avatar'
import { fmtTime, fmtDate } from '@/utils/datetime'

type CommentNode = IssueActivity & { children?: CommentNode[] }

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

const PRIMARY = '#409EFF'
const PRIMARY_LIGHT = '#E1F0FF'
const PRIMARY_LIGHTER = '#DBECFF'

const project = ref<Project | null>(null)
const loading = ref(false)
const allIssues = ref<Issue[]>([])
const allTags = ref<LabelVO[]>([])
const members = ref<ProjectMember[]>([])
const users = ref<UserVO[]>([])
const statuses = ref<IssueStatusVO[]>([])
const { hasPermission, refresh: refreshPermission } = useProjectPermission(() => pid.value)
const canWrite = computed(() => hasPermission('issue:edit'))
const canComment = computed(() => hasPermission('issue:comment'))

const currentIssue = ref<Issue | null>(null)
const currentIndex = computed(() =>
  currentIssue.value ? allIssues.value.findIndex((i) => i.id === currentIssue.value!.id) : -1
)
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

const mainTab = ref<'activity' | 'link' | 'other'>('activity')
const subTab = ref<'all' | 'comment' | 'dynamic'>('all')
const activities = ref<IssueActivity[]>([])
const attachments = ref<AttachmentVO[]>([])
const links = ref<LinkVO[]>([])
const tags = ref<LabelVO[]>([])
const commentText = ref('')
const commenting = ref(false)
const replyTo = ref<IssueActivity | null>(null)   // 当前回复目标评论（嵌套回复）
const commentEditorRef = ref<InstanceType<typeof RichTextEditor> | null>(null)
const attachInputRef = ref<HTMLInputElement | null>(null)
const linkType = ref('关联')
const linkFilter = ref<'all' | 'requirement' | 'task' | 'defect'>('all')
const linkKeyword = ref('')
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
  planEnd: '',
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
const commentCount = computed(() => activities.value.filter((a) => a.type === 'COMMENT').length)

// 评论树：把 COMMENT 类型按 parentId 组装为嵌套楼层（顶层 parentId 为空），状态活动保持平铺展示
const commentTree = computed<(IssueActivity | CommentNode)[]>(() => {
  const base = filteredActivities.value
  const byId = new Map<string, CommentNode>()
  const roots: CommentNode[] = []
  for (const a of base) {
    if (a.type !== 'COMMENT') continue
    byId.set(a.id, { ...a, children: [] })
  }
  for (const a of base) {
    if (a.type !== 'COMMENT') continue
    const node = byId.get(a.id)!
    const parent = a.parentId ? byId.get(a.parentId) : null
    if (parent) parent.children!.push(node)
    else roots.push(node)
  }
  roots.sort((x, y) => (x.createdAt || '').localeCompare(y.createdAt || ''))
  const sortChildren = (n: CommentNode) => {
    n.children!.sort((x, y) => (x.createdAt || '').localeCompare(y.createdAt || ''))
    n.children!.forEach(sortChildren)
  }
  roots.forEach(sortChildren)
  // 顶层评论作为「评论树根」，状态活动与之按时间平铺
  const result: (IssueActivity | CommentNode)[] = []
  for (const a of base) {
    if (a.type !== 'COMMENT') result.push(a)
    else if (!a.parentId) result.push(byId.get(a.id)!)
  }
  result.sort((x, y) => (x.createdAt || '').localeCompare(y.createdAt || ''))
  return result
})
const userStore = useUserStore()
// 当前登录用户真实 id（避免硬编码 admin 导致角色/权限误判）
const currentUserId = computed(() => userStore.user?.id || '')
const currentUserName = computed(() => userStore.user?.displayName || userStore.user?.username || '')

async function loadMeta() {
  try { project.value = await getProject(pid.value) } catch { /* ignore */ }
  try { statuses.value = await listProjectStatuses(pid.value) } catch { /* ignore */ }
  try { allTags.value = await listProjectTags(pid.value) } catch { /* ignore */ }
  try { members.value = await listProjectMembers(pid.value) } catch { /* ignore */ }
  try { users.value = await listUsers() } catch { /* ignore */ }
  refreshPermission()
}

async function loadIssues() {
  loading.value = true
  try {
    const res = await filterIssues({ projectId: pid.value, page: 1, size: 500 })
    allIssues.value = res.list
    const targetId = props.embedded
      ? props.issueId
      : route.params.issueId as string | undefined
    const target = targetId
      ? allIssues.value.find((i) => i.id === targetId)
      : allIssues.value[0]
    if (target) selectIssue(target)
  } finally {
    loading.value = false
  }
}

function selectIssue(issue: Issue) {
  currentIssue.value = issue
  if (props.embedded) {
    emit('update:issueId', issue.id)
  } else {
    router.replace(`/projects/${pid.value}/defects/${issue.id}`)
  }
  loadDetail(issue.id)
}

async function loadDetail(issueId: string) {
  try { activities.value = await listActivities(issueId) } catch { /* ignore */ }
  try { attachments.value = await listAttachments(issueId) } catch { /* ignore */ }
  try { links.value = await listLinks(issueId) } catch { /* ignore */ }
  // 重置关联区临时状态，确保不同工作项之间数据隔离
  linkFilter.value = 'all'
  linkOptions.value = []
  linkSelected.value = []
  try { tags.value = await listIssueTags(issueId) } catch { /* ignore */ }
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
    if (issue) selectIssue(issue)
  }
})

watch(() => route.params.issueId, (id) => {
  if (props.embedded) return
  if (id && currentIssue.value?.id !== id) {
    const issue = allIssues.value.find((i) => i.id === id)
    if (issue) selectIssue(issue)
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
  if (!currentIssue.value || !commentText.value.trim() || !canComment.value) {
    if (!canComment.value) ElMessage.warning('无评论权限')
    return
  }
  try {
    await addComment(currentIssue.value.id, commentText.value.trim(), replyTo.value?.id || null)
    commentText.value = ''
    commenting.value = false
    replyTo.value = null
    activities.value = await listActivities(currentIssue.value.id)
  } catch {
    ElMessage.error('评论发送失败')
  }
}
function onAttachChange(e: Event) {
  const input = e.target as HTMLInputElement
  const file = input.files?.[0]
  if (file && currentIssue.value) {
    uploadAttachment(currentIssue.value.id, file)
      .then(() => {
        commentEditorRef.value?.insert(`\n[附件](${file.name})\n`)
        return listAttachments(currentIssue.value!.id)
      })
      .then((list) => (attachments.value = list))
      .catch(() => ElMessage.error('附件上传失败'))
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
  if (!content) return '<span style="color:#c0c4cc">暂无描述</span>'
  const imgRe = /!\[([^\]]*)\]\(([^)\s]+)\)/g
  let out = ''
  let last = 0
  let m: RegExpExecArray | null
  while ((m = imgRe.exec(content))) {
    out += escapeHtml(content.slice(last, m.index))
    const url = m[2]
    if (/^https?:\/\//.test(url) || url.startsWith('/api/')) {
      out += `<img src="${url.replace(/"/g, '%22')}" alt="${m[1].replace(/"/g, '')}" style="max-width:100%;border-radius:6px;margin:4px 0" />`
    } else {
      out += escapeHtml(m[0])
    }
    last = imgRe.lastIndex
  }
  out += escapeHtml(content.slice(last))
  return out
}
// 从描述中提取图片列表，用于只读缩略图回显
const descriptionImages = computed(() => {
  const desc = currentIssue.value?.description || ''
  const res: { url: string; name: string }[] = []
  const imgRe = /!\[([^\]]*)\]\(([^)\s]+)\)/g
  let m: RegExpExecArray | null
  while ((m = imgRe.exec(desc))) {
    const url = m[2]
    if (/^https?:\/\//.test(url) || url.startsWith('/api/')) {
      res.push({ url, name: m[1] || '图片' })
    }
  }
  return res
})

async function removeComment(id: string) {
  if (!currentIssue.value) return
  await deleteComment(currentIssue.value.id, id)
  activities.value = await listActivities(currentIssue.value.id)
}

async function addAttach() {
  if (!currentIssue.value || !attachName.value.trim()) return
  await addAttachment(currentIssue.value.id, attachName.value.trim(), 'application/octet-stream', 0)
  attachName.value = ''
  attachments.value = await listAttachments(currentIssue.value.id)
}

async function removeAttach(id: string) {
  if (!currentIssue.value) return
  await deleteAttachment(currentIssue.value.id, id)
  attachments.value = await listAttachments(currentIssue.value.id)
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
  if (added.length) {
    await Promise.all(added.map((t) => addLink(currentIssue.value!.id, t, linkType.value)))
    links.value = await listLinks(currentIssue.value.id)
  }
  // 选中后立即清空临时选择，便于下次再选，且不会触发误删
  linkSelected.value = []
}

async function removeLink(id: string) {
  if (!currentIssue.value) return
  await deleteLink(currentIssue.value.id, id)
  links.value = await listLinks(currentIssue.value.id)
  await searchLinks()
}

watch(() => mainTab.value, (v) => {
  if (v === 'link') {
    linkSelected.value = []
    searchLinks()
  }
})

async function onAddTag() {
  const input = fieldsRef.value?.tagInput?.trim()
  if (!currentIssue.value || !input) return
  await addIssueTag(currentIssue.value.id, pid.value, input, PRIMARY)
  fieldsRef.value?.clearTagInput()
  tags.value = await listIssueTags(currentIssue.value.id)
}

async function removeTag(labelId: string) {
  if (!currentIssue.value) return
  await removeIssueTag(currentIssue.value.id, labelId)
  tags.value = tags.value.filter((t) => t.id !== labelId)
}

function back() {
  router.push(`/projects/${pid.value}/table`)
}
function close() {
  emit('close')
}
function goPrev() { if (prevIssue.value) selectIssue(prevIssue.value) }
function goNext() { if (nextIssue.value) selectIssue(nextIssue.value) }

const statusColor = (id: string) => statuses.value.find((s) => s.id === id)?.color || PRIMARY
const userName = (id: string | null) => users.value.find((u) => u.id === id)?.displayName || '—'
const memberName = (m: ProjectMember) => m.displayName || m.username


onMounted(() => {
  loadMeta().then(loadIssues)
})
</script>

<template>
  <div class="flex h-full flex-col" style="background:#f5f7fa">
    <!-- ======== 顶部导航（embedded：紧凑顶栏） ======== -->
    <header
      v-if="embedded"
      class="flex h-12 shrink-0 items-center gap-2 border-b bg-white px-3"
      style="border-color:#e4e7ed"
    >
      <AlertTriangle :size="16" class="shrink-0" style="color:#F56C6C" />
      <span
        class="shrink-0 rounded px-1.5 py-0.5 font-mono text-xs text-white"
        :style="{ background: PRIMARY }"
      >{{ currentIssue?.issueKey || '—' }}</span>
      <button
        class="flex h-7 w-7 shrink-0 items-center justify-center rounded transition hover:bg-[#f5f7fa]"
        style="color:#909399"
        title="更多操作"
      ><MoreHorizontal :size="16" /></button>
      <div class="ml-auto flex items-center gap-2">
        <button
          class="flex h-7 w-7 items-center justify-center rounded border disabled:opacity-40"
          style="border-color:#dcdfe6"
          :disabled="!prevIssue"
          @click="goPrev"
        ><ChevronLeft :size="16" /></button>
        <span class="text-xs tabular-nums" style="color:#909399">
          {{ currentIndex >= 0 ? currentIndex + 1 : 0 }} / {{ allIssues.length }}
        </span>
        <button
          class="flex h-7 w-7 items-center justify-center rounded border disabled:opacity-40"
          style="border-color:#dcdfe6"
          :disabled="!nextIssue"
          @click="goNext"
        ><ChevronRight :size="16" /></button>
        <span style="color:#dcdfe6">|</span>
        <button
          class="flex h-7 w-7 items-center justify-center rounded transition hover:bg-[#f5f7fa] hover:text-[#F56C6C]"
          style="color:#909399"
          title="关闭"
          @click="close"
        ><X :size="16" /></button>
      </div>
    </header>

    <!-- ======== 顶部导航（路由模式） ======== -->
    <header
      v-else
      class="flex h-12 shrink-0 items-center gap-3 border-b bg-white px-4"
      style="border-color:#e4e7ed"
    >
      <button class="flex items-center gap-1 text-sm hover:opacity-80" style="color:#606266" @click="back">
        <ArrowLeft :size="16" /> 返回列表
      </button>
      <span style="color:#dcdfe6">|</span>
      <span class="text-sm" style="color:#606266">{{ project?.name || '缺陷详情' }}</span>
      <div class="ml-auto flex items-center gap-2">
        <button
          class="flex h-7 w-7 items-center justify-center rounded border disabled:opacity-40"
          style="border-color:#dcdfe6"
          :disabled="!prevIssue"
          @click="goPrev"
        ><ChevronLeft :size="16" /></button>
        <span class="text-xs" style="color:#909399">
          {{ currentIndex >= 0 ? currentIndex + 1 : 0 }} / {{ allIssues.length }}
        </span>
        <button
          class="flex h-7 w-7 items-center justify-center rounded border disabled:opacity-40"
          style="border-color:#dcdfe6"
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
          <section class="mb-4 rounded-md bg-white p-4" style="box-shadow:0 1px 2px rgba(0,0,0,0.04)">
            <input
              v-model="form.title"
              class="w-full border-none bg-transparent text-xl font-semibold outline-none"
              style="color:#303133"
              placeholder="缺陷标题"
              @blur="saveTitle"
            />
            <div class="mt-1 flex flex-wrap items-center gap-x-3 gap-y-1 text-xs" style="color:#909399">
              <span>ID {{ currentIssue.issueKey }}</span>
              <span>·</span>
              <span>{{ userName(currentIssue.reporterId) }} {{ fmtTime(currentIssue.createdAt) }}创建</span>
              <span>·</span>
              <span>{{ fmtTime(currentIssue.updatedAt) }}更新</span>
            </div>
          </section>

          <!-- 描述卡片（支持编辑） -->
          <section class="rounded-md bg-white p-4" style="box-shadow:0 1px 2px rgba(0,0,0,0.04)">
            <div class="mb-3 flex items-center justify-between">
              <h3 class="text-sm font-medium" style="color:#303133">描述</h3>
              <div v-if="canWrite" class="flex items-center gap-2">
                <button
                  v-if="!editingDescription"
                  class="text-xs transition hover:opacity-75"
                  style="color:#409EFF"
                  @click="startEditDescription"
                >编辑</button>
                <template v-else>
                  <button
                    class="rounded px-3 py-1 text-xs text-white transition hover:opacity-90"
                    style="background:#409EFF"
                    @click="saveDescription"
                  >保存</button>
                  <button
                    class="rounded px-3 py-1 text-xs transition"
                    style="background:#f5f7fa;color:#606266"
                    @click="cancelEditDescription"
                  >取消</button>
                </template>
              </div>
            </div>
            <!-- 编辑态：富文本编辑器，支持 Markdown 与粘贴截图 -->
            <RichTextEditor
              v-if="editingDescription"
              v-model="form.description"
              :rows="8"
              placeholder="请输入缺陷描述，支持 Markdown，可 Ctrl+V 粘贴截图"
            />
            <!-- 只读态：渲染描述正文，支持 ![alt](url) 图片语法 -->
            <template v-else>
              <div
                v-if="currentIssue"
                class="description-content whitespace-pre-wrap rounded px-3 py-2 text-sm"
                style="background:#f5f7fa;color:#606266"
                v-html="renderDescription(currentIssue.description)"
              ></div>
              <!-- 描述中若含图片，在此以缩略图形式回显（只读） -->
              <div v-if="descriptionImages.length" class="mt-3 grid grid-cols-2 gap-3">
                <div
                  v-for="img in descriptionImages"
                  :key="img.url"
                  class="overflow-hidden rounded-lg border"
                  style="border-color:#e4e7ed"
                >
                  <img :src="img.url" :alt="img.name" class="h-44 w-full object-cover" />
                </div>
              </div>
            </template>
          </section>

          <!-- 评论区（动态&评论 / 关联项 / 其他）→ 描述卡片下方，flex-1 拉伸填满至底部 -->
          <section class="mt-4 flex-1 rounded-md bg-white p-4" style="box-shadow:0 1px 2px rgba(0,0,0,0.04)">
            <!-- 主标签页 -->
            <div class="mb-4 flex items-center gap-1 border-b" style="border-color:#e4e7ed">
              <button
                v-for="t in [
                  { k: 'activity', label: '动态&评论', count: commentCount },
                  { k: 'link', label: '关联项', count: links.length },
                  { k: 'other', label: '其他', count: 0 }
                ]"
                :key="t.k"
                class="relative px-4 py-2.5 text-sm transition"
                :style="mainTab === t.k ? { color: PRIMARY } : { color: '#909399' }"
                @click="mainTab = t.k as 'activity' | 'link' | 'other'"
              >
                {{ t.label }}
                <span class="ml-1 text-xs">{{ t.count }}</span>
                <span
                  v-if="mainTab === t.k"
                  class="absolute bottom-0 left-2 right-2 h-0.5 rounded-full"
                  :style="{ background: PRIMARY }"
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
                  :style="subTab === s.k
                    ? { background: PRIMARY_LIGHTER, color: PRIMARY }
                    : { background: '#f5f7fa', color: '#909399' }"
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
                    @remove="removeComment"
                  />
                  <div v-else class="flex gap-3">
                    <div
                      class="flex h-9 w-9 shrink-0 items-center justify-center rounded-full text-xs"
                      style="background:#f5f7fa;color:#909399"
                    >·</div>
                    <div class="min-w-0 flex-1">
                      <div class="flex items-center gap-1 text-xs" style="color:#909399">
                        <span>{{ a.actorName }}</span>
                        <span>{{ fmtTime(a.createdAt) }}</span>
                        <span class="ml-1">{{ a.type === 'STATUS_CHANGED' ? '变更了状态' : '更新了字段' }}</span>
                        <code class="rounded px-1.5 py-0.5" style="background:#f5f7fa;color:#606266">{{ a.oldValue || '—' }}</code>
                        <span>→</span>
                        <code class="rounded px-1.5 py-0.5" :style="{ background: PRIMARY_LIGHT, color: PRIMARY }">{{ a.newValue || '—' }}</code>
                      </div>
                    </div>
                  </div>
                </template>
                <div v-if="!filteredActivities.length" class="py-8 text-center text-sm" style="color:#c0c4cc">
                  暂无内容
                </div>
                <!-- 添加评论按钮：非编辑状态时显示在评论列表下方 -->
                <div v-if="!commenting" class="mt-4 flex justify-center">
                  <button
                    v-if="canWrite"
                    class="flex items-center gap-1 rounded px-4 py-1.5 text-sm text-white transition-opacity hover:opacity-90"
                    :style="{ background: PRIMARY }"
                    @click="startComment"
                  >
                    <Plus :size="14" /> 添加评论
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
                  <template #prefix><Search :size="14" style="color:#c0c4cc" /></template>
                  <template #header>
                    <div class="flex items-center gap-2 border-b px-3 py-2" style="border-color:#e4e7ed">
                      <span class="text-xs" style="color:#909399">类型</span>
                      <button
                        v-for="f in linkFilters"
                        :key="f.k"
                        class="rounded-full px-3 py-0.5 text-xs transition"
                        :style="linkFilter === f.k ? { background: PRIMARY_LIGHTER, color: PRIMARY } : { background: '#f5f7fa', color: '#909399' }"
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
                    <span class="shrink-0 font-mono text-xs" style="color:#909399">{{ opt.issueKey }}</span>
                    <span class="min-w-0 flex-1 truncate">{{ opt.title }}</span>
                    <span class="shrink-0 items-center gap-1 text-xs" style="color:#909399">
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
              <button class="mt-2 flex items-center gap-1 text-sm transition hover:opacity-80" style="color: PRIMARY" @click="createDialogVisible = true">
                <Plus :size="14" /> 新建工作项
              </button>

              <!-- 已关联列表 -->
              <div class="mt-4 border-t pt-3" style="border-color:#e4e7ed">
                <div class="mb-2 text-xs" style="color:#909399">已关联</div>
                <div class="space-y-2">
                  <div v-for="l in links" :key="l.id" class="flex items-center justify-between rounded-lg px-3 py-2 text-sm" style="background:#f5f7fa">
                    <div class="flex items-center gap-2">
                      <Link2 :size="14" style="color:#909399" />
                      <span class="rounded px-1.5 py-0.5 text-xs text-white" :style="{ background: PRIMARY }">{{ l.relationType }}</span>
                      <span class="font-mono" style="color:#303133">{{ l.targetKey }}</span>
                      <span style="color:#909399">{{ l.targetTitle }}</span>
                    </div>
                    <button class="hover:text-[#F56C6C]" style="color:#c0c4cc" @click="removeLink(l.id)"><Trash2 :size="14" /></button>
                  </div>
                  <div v-if="!links.length" class="py-8 text-center text-sm" style="color:#c0c4cc">暂无关联</div>
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
                  class="flex-1 rounded border px-3 py-1.5 text-sm outline-none"
                  style="border-color:#dcdfe6"
                  @keyup.enter="addAttach"
                />
                <button
                  class="flex items-center gap-1 rounded px-3 text-sm text-white hover:opacity-90"
                  :style="{ background: PRIMARY }"
                  @click="addAttach"
                ><Paperclip :size="14" /> 添加</button>
              </div>
              <div class="space-y-2">
                <div v-for="a in attachments" :key="a.id" class="flex items-center justify-between rounded-lg px-3 py-2 text-sm" style="background:#f5f7fa">
                  <div class="flex items-center gap-2">
                    <Paperclip :size="14" style="color:#909399" />
                    <span style="color:#303133">{{ a.fileName }}</span>
                    <span class="text-xs" style="color:#c0c4cc">{{ a.uploaderName }} · {{ fmtTime(a.createdAt) }}</span>
                  </div>
                  <button class="hover:text-[#F56C6C]" style="color:#c0c4cc" @click="removeAttach(a.id)"><Trash2 :size="14" /></button>
                </div>
                <div v-if="!attachments.length" class="py-8 text-center text-sm" style="color:#c0c4cc">暂无附件</div>
              </div>
            </div>
          </section>

          <!-- 评论输入框：始终钉在左侧面板最底部，默认占位，点击展开富文本编辑器 -->
          <section
            class="sticky bottom-0 z-10"
            style="background:#f5f7fa;box-shadow:0 -6px 12px -6px rgba(0,0,0,0.06)"
          >
            <!-- 默认占位：未编辑时显示，点击展开富文本 -->
            <div
              v-if="!commenting"
              class="flex cursor-text items-center gap-3 rounded-md bg-white p-3"
              style="box-shadow:0 1px 2px rgba(0,0,0,0.04)"
              @click="startComment"
            >
              <div
                class="flex h-9 w-9 shrink-0 items-center justify-center rounded-full text-sm font-medium text-white"
                :style="{ background: avatarColor(userName(currentIssue.reporterId)) }"
              >{{ avatarChar(userName(currentIssue.reporterId)) }}</div>
              <span class="text-sm" style="color:#c0c4cc">点击输入评论…</span>
            </div>

            <!-- 编辑态：富文本（Markdown 工具栏） -->
            <div
              v-else
              class="rounded-md bg-white p-3"
              style="box-shadow:0 1px 2px rgba(0,0,0,0.04)"
            >
              <div class="mb-2 flex items-center gap-2 text-xs" style="color:#909399">
                <span v-if="replyTo">正在回复 <b style="color:#409EFF">@{{ replyTo.actorName }}</b></span>
                <span v-else>发表评论</span>
                <button v-if="replyTo" class="hover:underline" style="color:#c0c4cc" @click="cancelComment">取消回复</button>
              </div>
              <div class="flex items-start gap-3">
                <div
                  class="flex h-9 w-9 shrink-0 items-center justify-center rounded-full text-sm font-medium text-white"
                  :style="{ background: avatarColor(userName(currentIssue.reporterId)) }"
                >{{ avatarChar(userName(currentIssue.reporterId)) }}</div>
                <div class="flex-1">
                  <!-- 富文本编辑器（含 Markdown 工具栏 / 图片上传 / Ctrl+V 粘贴） -->
                  <RichTextEditor
                    ref="commentEditorRef"
                    v-model="commentText"
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
                    <span class="text-xs" style="color:#c0c4cc">Ctrl + Enter 发送</span>
                    <div class="flex gap-2">
                      <button
                        class="rounded px-4 py-1.5 text-sm hover:opacity-90"
                        style="background:#f5f7fa;color:#606266;border:1px solid #dcdfe6"
                        @click="cancelComment"
                      >取消</button>
                      <button
                        class="flex items-center gap-1 rounded px-4 py-1.5 text-sm text-white transition hover:opacity-90 disabled:opacity-50"
                        :style="{ background: PRIMARY }"
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
          <div class="rounded-md bg-white p-4" style="box-shadow:0 1px 2px rgba(0,0,0,0.04)">
            <h3 class="mb-4 text-sm font-medium" style="color:#303133">基础字段</h3>

            <!-- 工作项类型 -->
            <div class="mb-4 flex items-center justify-between">
              <label class="text-sm" style="color:#606266">工作项类型</label>
              <div class="flex items-center gap-1.5 text-sm" style="color:#303133">
                <AlertTriangle :size="14" style="color:#F56C6C" />
                {{ currentIssue.issueTypeName || '缺陷' }}
              </div>
            </div>

            <IssueBasicFields
              ref="fieldsRef"
              :model-value="form"
              @update:model-value="(e) => Object.assign(form, e)"
              :statuses="statuses"
              :members="members"
              :priority-options="priorityOptions"
              :severity-options="severityOptions"
              :tags="tags"
              :can-write="canWrite"
              :show-severity="true"
              @field-change="(e) => saveField(e.field, e.payload.patch, e.payload.clear)"
              @add-tag="onAddTag"
              @remove-tag="(id) => removeTag(id)"
            />

            <!-- 归属项目（缺陷专属，新建走 props） -->
            <div class="mt-4">
              <label class="mb-1 block text-sm" style="color:#606266">归属项目<span style="color:#F56C6C">*</span></label>
              <div class="truncate rounded border px-2 py-1 text-sm" style="border-color:#dcdfe6;color:#303133" :title="project?.name">
                {{ project?.name || '—' }}
              </div>
            </div>

            <!-- 迭代 -->
            <div class="mt-4 flex items-center justify-between">
              <label class="text-sm" style="color:#606266">迭代</label>
              <input
                v-model="form.iteration"
                placeholder="请选择"
                class="w-44 rounded border bg-transparent px-2 py-1 text-sm outline-none"
                style="border-color:#dcdfe6;color:#303133"
                :disabled="!canWrite"
                @blur="saveField('迭代', { iteration: form.iteration || null }, form.iteration ? [] : ['iteration'])"
              />
            </div>
          </div>

          <!-- 附件区 -->
          <div class="mt-4 rounded-md bg-white p-4" style="box-shadow:0 1px 2px rgba(0,0,0,0.04)">
            <div class="mb-3 flex items-center justify-between">
              <h3 class="text-sm font-medium" style="color:#303133">
                附件 <span class="text-xs font-normal" style="color:#909399">{{ attachments.length }}</span>
              </h3>
              <button
                class="flex h-7 w-7 items-center justify-center rounded text-lg"
                style="background:#f5f7fa;color:#909399"
                @click="addAttach"
              ><Plus :size="16" /></button>
            </div>
            <div class="space-y-2">
              <div
                v-for="a in attachments"
                :key="a.id"
                class="flex items-center gap-2 rounded-lg px-2 py-2 text-sm"
              >
                <Paperclip :size="14" style="color:#909399" />
                <span class="flex-1 truncate" style="color:#303133">{{ a.fileName }}</span>
                <span class="text-xs" style="color:#c0c4cc">{{ fmtDate(a.createdAt) }}</span>
                <button class="hover:text-[#F56C6C]" style="color:#c0c4cc" @click="removeAttach(a.id)"><Trash2 :size="13" /></button>
              </div>
              <div v-if="!attachments.length" class="py-4 text-center text-xs" style="color:#c0c4cc">
                暂无附件，点击 + 上传
              </div>
            </div>
          </div>
        </aside>
      </div>
    </div>

    <!-- 加载/空状态 -->
    <div v-else class="flex flex-1 items-center justify-center text-sm" style="color:#909399">
      <span v-if="loading">加载中…</span>
      <span v-else>暂无缺陷</span>
    </div>
  </div>
</template>

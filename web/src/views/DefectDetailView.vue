<script setup lang="ts">
import { onMounted, ref, reactive, computed, watch, nextTick } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import {
  filterIssues, updateIssue, listProjectStatuses,
  listProjectTags, listProjectMembers, listUsers, getProject,
  listActivities, addComment, deleteComment, listAttachments, addAttachment, deleteAttachment,
  listLinks, addLink, deleteLink, listIssueTags, addIssueTag, removeIssueTag
} from '@/api'
import type {
  Issue, LabelVO, ProjectMember, UserVO, IssueStatusVO,
  IssueActivity, AttachmentVO, LinkVO, Project
} from '@/api/types'
import { ElMessage } from 'element-plus'
import {
  ArrowLeft, ChevronLeft, ChevronRight, Plus, X,
  Paperclip, Link2, Trash2, Edit3, Sparkles,
  AlertTriangle, Image as ImageIcon, Send, MoreHorizontal,
  Bold, Italic, List, ListOrdered, Quote, Smile
} from 'lucide-vue-next'

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
const canWrite = ref(true)

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
  { v: 'S1', label: '1-致命' },
  { v: 'S2', label: '2-严重' },
  { v: 'S3', label: '3-一般' },
  { v: 'S4', label: '4-轻微' }
]

const mainTab = ref<'activity' | 'link' | 'other'>('activity')
const subTab = ref<'all' | 'comment' | 'dynamic'>('all')
const activities = ref<IssueActivity[]>([])
const attachments = ref<AttachmentVO[]>([])
const links = ref<LinkVO[]>([])
const tags = ref<LabelVO[]>([])
const commentText = ref('')
const commenting = ref(false)
const commentTextareaRef = ref<HTMLTextAreaElement | null>(null)
const attachInputRef = ref<HTMLInputElement | null>(null)
const linkTarget = ref('')
const linkType = ref('关联')
const tagInput = ref('')
const attachName = ref('')

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
  dueDate: '',
  planStart: '',
  planEnd: '',
  noFixReason: '',
  iteration: '',
  ccUsers: [] as string[],
  participants: [] as string[]
})

const filteredActivities = computed(() => {
  if (subTab.value === 'comment') return activities.value.filter((a) => a.type === 'COMMENT')
  if (subTab.value === 'dynamic') return activities.value.filter((a) => a.type !== 'COMMENT')
  return activities.value
})
const commentCount = computed(() => activities.value.filter((a) => a.type === 'COMMENT').length)

async function loadMeta() {
  try { project.value = await getProject(pid.value) } catch { /* ignore */ }
  try { statuses.value = await listProjectStatuses(pid.value) } catch { /* ignore */ }
  try { allTags.value = await listProjectTags(pid.value) } catch { /* ignore */ }
  try { members.value = await listProjectMembers(pid.value) } catch { /* ignore */ }
  try { users.value = await listUsers() } catch { /* ignore */ }
  const me = members.value.find((m) => m.userId === users.value.find((u) => u.username === 'admin')?.id)
  canWrite.value = !me || !me.roles.includes('VIEWER')
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

async function saveField(field: string, payload: Record<string, unknown>) {
  if (!currentIssue.value || !canWrite.value) {
    ElMessage.warning('无编辑权限')
    return
  }
  try {
    const updated = await updateIssue(currentIssue.value.id, payload)
    Object.assign(currentIssue.value, updated)
    const idx = allIssues.value.findIndex((i) => i.id === updated.id)
    if (idx >= 0) Object.assign(allIssues.value[idx], updated)
    ElMessage.success(`${field}已更新`)
  } catch { /* ignore */ }
}

async function saveTitle() {
  if (!form.title.trim()) return
  await saveField('标题', { title: form.title })
}

function startComment() {
  commenting.value = true
  nextTick(() => commentTextareaRef.value?.focus())
}
function cancelComment() {
  commenting.value = false
  commentText.value = ''
}
async function sendComment() {
  if (!currentIssue.value || !commentText.value.trim() || !canWrite.value) {
    if (!canWrite.value) ElMessage.warning('无评论权限')
    return
  }
  try {
    await addComment(currentIssue.value.id, commentText.value.trim())
    commentText.value = ''
    commenting.value = false
    activities.value = await listActivities(currentIssue.value.id)
  } catch {
    ElMessage.error('评论发送失败')
  }
}
function wrapSelection(prefix: string, suffix: string, placeholder = '') {
  const ta = commentTextareaRef.value
  if (!ta) return
  const start = ta.selectionStart
  const end = ta.selectionEnd
  const before = commentText.value.slice(0, start)
  const selected = commentText.value.slice(start, end) || placeholder
  const after = commentText.value.slice(end)
  const inserted = prefix + selected + suffix
  commentText.value = before + inserted + after
  nextTick(() => {
    ta.focus()
    const cursor = before.length + prefix.length + selected.length
    ta.setSelectionRange(cursor, cursor)
  })
}
function insertAtCursor(text: string, cursorOffset?: number) {
  const ta = commentTextareaRef.value
  if (!ta) return
  const start = ta.selectionStart
  const end = ta.selectionEnd
  const before = commentText.value.slice(0, start)
  const after = commentText.value.slice(end)
  commentText.value = before + text + after
  nextTick(() => {
    ta.focus()
    const pos = before.length + (cursorOffset ?? text.length)
    ta.setSelectionRange(pos, pos)
  })
}
function insertAtLineStart(prefix: string) {
  const ta = commentTextareaRef.value
  if (!ta) return
  const start = ta.selectionStart
  const before = commentText.value.slice(0, start)
  const lineStart = before.lastIndexOf('\n') + 1
  commentText.value =
    commentText.value.slice(0, lineStart) + prefix + commentText.value.slice(lineStart)
  nextTick(() => {
    ta.focus()
    const pos = start + prefix.length
    ta.setSelectionRange(pos, pos)
  })
}
function onAttachChange(e: Event) {
  const input = e.target as HTMLInputElement
  const file = input.files?.[0]
  if (file) {
    insertAtCursor(`![${file.name}](attachment:${file.name})`)
    if (currentIssue.value) {
      addAttachment(currentIssue.value.id, file.name, file.type || 'application/octet-stream', file.size || 0)
        .then(() => listAttachments(currentIssue.value!.id))
        .then((list) => (attachments.value = list))
        .catch(() => {/* ignore */})
    }
  }
  input.value = ''
}
function triggerAttach() {
  attachInputRef.value?.click()
}

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

async function addLinkItem() {
  if (!currentIssue.value || !linkTarget.value.trim()) return
  await addLink(currentIssue.value.id, linkTarget.value.trim(), linkType.value)
  linkTarget.value = ''
  links.value = await listLinks(currentIssue.value.id)
}

async function removeLink(id: string) {
  if (!currentIssue.value) return
  await deleteLink(currentIssue.value.id, id)
  links.value = await listLinks(currentIssue.value.id)
}

async function addTag() {
  if (!currentIssue.value || !tagInput.value.trim()) return
  await addIssueTag(currentIssue.value.id, pid.value, tagInput.value.trim(), PRIMARY)
  tagInput.value = ''
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

function fmtTime(t?: string) {
  if (!t) return ''
  return t.slice(0, 19).replace('T', ' ')
}
function fmtDate(t?: string) {
  if (!t) return ''
  return t.slice(0, 10)
}
function avatarChar(name: string | null) {
  return name ? name.charAt(0) : '·'
}
function avatarColor(name: string | null) {
  if (!name) return '#C0C4CC'
  const colors = ['#409EFF', '#67C23A', '#E6A23C', '#F56C6C', '#909399', '#9B59B6', '#1ABC9C']
  return colors[name.charCodeAt(0) % colors.length]
}

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

          <!-- 描述卡片 -->
          <section class="rounded-md bg-white p-4" style="box-shadow:0 1px 2px rgba(0,0,0,0.04)">
            <div class="mb-3 flex items-center justify-between">
              <h3 class="text-sm font-medium" style="color:#303133">描述</h3>
              <div class="flex items-center gap-2">
                <button class="flex items-center gap-1 rounded px-3 py-1.5 text-xs" style="background:#f5f7fa;color:#909399">
                  <Sparkles :size="13" /> 智能优化内容
                </button>
                <button class="flex items-center gap-1 rounded border bg-white px-3 py-1.5 text-xs" style="border-color:#dcdfe6;color:#606266">
                  <Edit3 :size="13" /> 编辑
                </button>
              </div>
            </div>
            <textarea
              v-model="form.description"
              rows="3"
              class="w-full resize-none rounded border px-3 py-2 text-sm outline-none"
              style="border-color:#dcdfe6;color:#606266"
              placeholder="补充缺陷描述…"
              @blur="saveField('描述', { description: form.description })"
            />
            <!-- 截图占位 -->
            <div class="mt-4 grid grid-cols-2 gap-3">
              <div class="group relative overflow-hidden rounded-lg border" style="border-color:#e4e7ed">
                <div class="flex h-44 items-center justify-center" style="background:#f5f7fa">
                  <ImageIcon :size="32" style="color:#c0c4cc" />
                </div>
                <div class="absolute bottom-0 left-0 right-0 bg-gradient-to-t from-black/60 to-transparent px-3 py-2 text-xs text-white opacity-0 transition group-hover:opacity-100">
                  地图界面截图
                </div>
              </div>
              <div class="group relative overflow-hidden rounded-lg border" style="border-color:#e4e7ed">
                <div class="flex h-44 items-center justify-center" style="background:#f5f7fa">
                  <ImageIcon :size="32" style="color:#c0c4cc" />
                </div>
                <div class="absolute bottom-0 left-0 right-0 bg-gradient-to-t from-black/60 to-transparent px-3 py-2 text-xs text-white opacity-0 transition group-hover:opacity-100">
                  报错截图
                </div>
              </div>
            </div>
          </section>

          <!-- 评论区（动态&评论 / 关联项 / 其他）→ 描述卡片下方 -->
          <section class="mt-4 rounded-md bg-white p-4" style="box-shadow:0 1px 2px rgba(0,0,0,0.04)">
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

              <!-- 评论列表 -->
              <div class="space-y-5">
                <div v-for="a in filteredActivities" :key="a.id" class="flex gap-3">
                  <div
                    class="flex h-9 w-9 shrink-0 items-center justify-center rounded-full text-sm font-medium text-white"
                    :style="{ background: avatarColor(a.actorName) }"
                  >{{ avatarChar(a.actorName) }}</div>
                  <div class="min-w-0 flex-1">
                    <div class="mb-1.5 flex items-center gap-2 text-xs">
                      <span class="font-medium" style="color:#303133">{{ a.actorName }}</span>
                      <span style="color:#c0c4cc">{{ fmtTime(a.createdAt) }}</span>
                      <div class="ml-auto flex items-center gap-3">
                        <button v-if="a.type === 'COMMENT'" class="hover:underline" :style="{ color: PRIMARY }">回复</button>
                        <button
                          v-if="a.type === 'COMMENT'"
                          class="hover:text-[#F56C6C]"
                          style="color:#c0c4cc"
                          @click="removeComment(a.id)"
                        ><Trash2 :size="13" /></button>
                      </div>
                    </div>
                    <div v-if="a.type === 'COMMENT'" class="rounded-lg p-3 text-sm" style="background:#f5f7fa;color:#606266">
                      <div class="mb-2 overflow-hidden rounded border" style="border-color:#e4e7ed">
                        <div class="flex h-32 items-center justify-center" style="background:#ebeef5">
                          <ImageIcon :size="24" style="color:#c0c4cc" />
                        </div>
                      </div>
                      {{ a.content }}
                    </div>
                    <div v-else class="flex items-center gap-1 text-xs" style="color:#909399">
                      <span>{{ a.type === 'STATUS_CHANGED' ? '变更了状态' : '更新了字段' }}</span>
                      <code class="rounded px-1.5 py-0.5" style="background:#f5f7fa;color:#606266">{{ a.oldValue || '—' }}</code>
                      <span>→</span>
                      <code class="rounded px-1.5 py-0.5" :style="{ background: PRIMARY_LIGHT, color: PRIMARY }">{{ a.newValue || '—' }}</code>
                    </div>
                  </div>
                </div>
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
              <div class="mb-4 flex gap-2">
                <input
                  v-model="linkTarget"
                  placeholder="任务编号，如 12"
                  class="flex-1 rounded border px-3 py-1.5 text-sm outline-none"
                  style="border-color:#dcdfe6"
                />
                <select v-model="linkType" class="rounded border px-2 text-sm outline-none" style="border-color:#dcdfe6">
                  <option>关联</option><option>阻塞</option><option>重复</option><option>修复</option>
                </select>
                <button
                  class="flex items-center gap-1 rounded px-3 text-sm text-white hover:opacity-90"
                  :style="{ background: PRIMARY }"
                  @click="addLinkItem"
                ><Plus :size="14" /> 添加</button>
              </div>
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

          <!-- 弹性占位：内容不足时把评论输入框推至左栏底部 -->
          <div class="min-h-[1px] flex-1"></div>

          <!-- 评论输入框：始终钉在左侧面板最底部（不滚动时贴底 / 滚动时 sticky 跟随） -->
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
              <div class="flex items-start gap-3">
                <div
                  class="flex h-9 w-9 shrink-0 items-center justify-center rounded-full text-sm font-medium text-white"
                  :style="{ background: avatarColor(userName(currentIssue.reporterId)) }"
                >{{ avatarChar(userName(currentIssue.reporterId)) }}</div>
                <div class="flex-1">
                  <!-- 工具栏 -->
                  <div
                    class="mb-2 flex items-center gap-1 rounded border px-1.5 py-1"
                    style="border-color:#e4e7ed;background:#fafbfc"
                  >
                    <button
                      v-for="t in [
                        { tip: '粗体', icon: Bold, fn: () => wrapSelection('**', '**', '粗体文字') },
                        { tip: '斜体', icon: Italic, fn: () => wrapSelection('*', '*', '斜体文字') },
                        { tip: '无序列表', icon: List, fn: () => insertAtLineStart('- ') },
                        { tip: '有序列表', icon: ListOrdered, fn: () => insertAtLineStart('1. ') },
                        { tip: '链接', icon: Link2, fn: () => insertAtCursor('[文字](https://)', 1) },
                        { tip: '引用', icon: Quote, fn: () => insertAtLineStart('> ') },
                        { tip: '附件', icon: Paperclip, fn: triggerAttach },
                        { tip: '表情', icon: Smile, fn: () => insertAtCursor('😊') }
                      ]"
                      :key="t.tip"
                      :title="t.tip"
                      class="flex h-7 w-7 items-center justify-center rounded text-gray-500 hover:bg-gray-100 hover:text-gray-700"
                      @click="t.fn"
                    >
                      <component :is="t.icon" :size="14" />
                    </button>
                    <span class="mx-1 text-gray-300">|</span>
                    <span class="px-1 text-xs" style="color:#909399">Markdown</span>
                  </div>

                  <!-- 隐藏的文件 input（附件按钮触发） -->
                  <input
                    ref="attachInputRef"
                    type="file"
                    class="hidden"
                    @change="onAttachChange"
                  />

                  <!-- 编辑区 -->
                  <textarea
                    ref="commentTextareaRef"
                    v-model="commentText"
                    rows="4"
                    placeholder="Ctrl+V直接上传截图（仅在Chrome浏览器生效），支持Markdown"
                    class="w-full resize-none border-none text-sm leading-6 outline-none"
                    style="color:#303133;background:transparent"
                    @keyup.ctrl.enter="sendComment"
                  />

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
        <aside class="flex w-[320px] shrink-0 flex-col">
          <div class="flex-1 rounded-md bg-white p-4" style="box-shadow:0 1px 2px rgba(0,0,0,0.04)">
            <h3 class="mb-4 text-sm font-medium" style="color:#303133">基础字段</h3>
            <div class="space-y-4">
              <!-- 工作项类型 -->
              <div class="flex items-center justify-between">
                <label class="text-sm" style="color:#606266">工作项类型</label>
                <div class="flex items-center gap-1.5 text-sm" style="color:#303133">
                  <AlertTriangle :size="14" style="color:#F56C6C" />
                  {{ currentIssue.issueTypeName || '缺陷' }}
                </div>
              </div>

              <!-- 状态 -->
              <div class="flex items-center justify-between">
                <label class="text-sm" style="color:#606266">状态</label>
                <select
                  v-model="form.statusId"
                  class="cursor-pointer rounded border-none px-2.5 py-1 text-sm outline-none"
                  :style="{ background: statusColor(form.statusId) + '1A', color: statusColor(form.statusId), border: '1px solid ' + statusColor(form.statusId) + '40' }"
                  :disabled="!canWrite"
                  @change="saveField('状态', { statusId: form.statusId })"
                >
                  <option v-for="s in statuses" :key="s.id" :value="s.id">{{ s.name }}</option>
                </select>
              </div>

              <!-- 负责人 -->
              <div class="flex items-center justify-between">
                <label class="text-sm" style="color:#606266">负责人<span style="color:#F56C6C">*</span></label>
                <select
                  v-model="form.assigneeId"
                  class="cursor-pointer rounded border bg-transparent px-2 py-1 text-sm outline-none"
                  style="border-color:#dcdfe6;color:#303133"
                  :disabled="!canWrite"
                  @change="saveField('负责人', { assigneeId: form.assigneeId || null })"
                >
                  <option value="">未分配</option>
                  <option v-for="m in members" :key="m.userId" :value="m.userId">{{ m.displayName }}</option>
                </select>
              </div>

              <!-- 验证者 -->
              <div class="flex items-center justify-between">
                <label class="text-sm" style="color:#606266">验证者</label>
                <select
                  v-model="form.verifierId"
                  class="cursor-pointer rounded border bg-transparent px-2 py-1 text-sm outline-none"
                  style="border-color:#dcdfe6;color:#303133"
                  :disabled="!canWrite"
                  @change="saveField('验证者', { verifierId: form.verifierId || null })"
                >
                  <option value="">未指派</option>
                  <option v-for="m in members" :key="m.userId" :value="m.userId">{{ m.displayName }}</option>
                </select>
              </div>

              <!-- 优先级（单选按钮风格） -->
              <div>
                <label class="mb-1.5 block text-sm" style="color:#606266">优先级<span style="color:#F56C6C">*</span></label>
                <div class="flex flex-wrap gap-1.5">
                  <button
                    v-for="p in priorityOptions"
                    :key="p.v"
                    class="flex items-center gap-1 rounded border px-2 py-1 text-xs transition"
                    :style="form.priority === p.v
                      ? { background: PRIMARY_LIGHT, color: PRIMARY, borderColor: PRIMARY }
                      : { background: '#fff', color: '#909399', borderColor: '#dcdfe6' }"
                    :disabled="!canWrite"
                    @click="form.priority = p.v; saveField('优先级', { priority: p.v })"
                  >
                    <span
                      class="h-1.5 w-1.5 rounded-full"
                      :style="{ background: form.priority === p.v ? PRIMARY : '#c0c4cc' }"
                    />
                    {{ p.label }}
                  </button>
                </div>
              </div>

              <!-- 归属项目 -->
              <div>
                <label class="mb-1 block text-sm" style="color:#606266">归属项目<span style="color:#F56C6C">*</span></label>
                <div class="truncate rounded border px-2 py-1 text-sm" style="border-color:#dcdfe6;color:#303133" :title="project?.name">
                  {{ project?.name || '—' }}
                </div>
              </div>

              <!-- 迭代 -->
              <div class="flex items-center justify-between">
                <label class="text-sm" style="color:#606266">迭代</label>
                <input
                  v-model="form.iteration"
                  placeholder="请选择"
                  class="w-40 rounded border bg-transparent px-2 py-1 text-right text-sm outline-none"
                  style="border-color:#dcdfe6;color:#303133"
                />
              </div>

              <!-- 严重程度 -->
              <div class="flex items-center justify-between">
                <label class="text-sm" style="color:#606266">严重程度<span style="color:#F56C6C">*</span></label>
                <select
                  v-model="form.severity"
                  class="cursor-pointer rounded border bg-transparent px-2 py-1 text-sm outline-none"
                  style="border-color:#dcdfe6;color:#303133"
                  :disabled="!canWrite"
                  @change="saveField('严重程度', { severity: form.severity })"
                >
                  <option v-for="s in severityOptions" :key="s.v" :value="s.v">{{ s.label }}</option>
                </select>
              </div>

              <!-- 参与者 -->
              <div class="flex items-center justify-between">
                <label class="text-sm" style="color:#606266">参与者</label>
                <input
                  placeholder="请选择"
                  class="w-40 rounded border bg-transparent px-2 py-1 text-right text-sm outline-none"
                  style="border-color:#dcdfe6;color:#c0c4cc"
                />
              </div>

              <!-- 抄送 -->
              <div>
                <label class="mb-1.5 block text-sm" style="color:#606266">抄送</label>
                <div class="flex flex-wrap items-center gap-1.5 rounded border px-2 py-1.5" style="border-color:#dcdfe6">
                  <span
                    v-for="(u, i) in form.ccUsers"
                    :key="i"
                    class="flex items-center gap-1 rounded px-2 py-0.5 text-xs"
                    :style="{ background: PRIMARY_LIGHT, color: PRIMARY }"
                  >
                    {{ u }}
                    <button class="hover:opacity-70" @click="form.ccUsers.splice(i, 1)"><X :size="11" /></button>
                  </span>
                  <input
                    placeholder="请选择"
                    class="min-w-[60px] flex-1 bg-transparent text-sm outline-none"
                    style="color:#c0c4cc"
                    @keyup.enter="(e: any) => { if (e.target.value.trim()) { form.ccUsers.push(e.target.value.trim()); e.target.value = '' } }"
                  />
                </div>
              </div>

              <!-- 标签 -->
              <div>
                <label class="mb-1.5 block text-sm" style="color:#606266">标签</label>
                <div class="flex flex-wrap items-center gap-1.5 rounded border px-2 py-1.5" style="border-color:#dcdfe6">
                  <span
                    v-for="t in tags"
                    :key="t.id"
                    class="flex items-center gap-1 rounded px-2 py-0.5 text-xs text-white"
                    :style="{ background: t.color || PRIMARY }"
                  >
                    {{ t.name }}
                    <button class="hover:opacity-70" @click="removeTag(t.id)"><X :size="11" /></button>
                  </span>
                  <input
                    v-model="tagInput"
                    placeholder="加标签"
                    class="min-w-[60px] flex-1 bg-transparent text-sm outline-none"
                    style="color:#c0c4cc"
                    @keyup.enter="addTag"
                  />
                </div>
              </div>

              <!-- 计划开始时间 -->
              <div class="flex items-center justify-between">
                <label class="text-sm" style="color:#606266">计划开始时间</label>
                <input
                  v-model="form.planStart"
                  type="date"
                  placeholder="请选择日期"
                  class="w-40 rounded border bg-transparent px-2 py-1 text-right text-sm outline-none"
                  style="border-color:#dcdfe6;color:#303133"
                />
              </div>

              <!-- 计划完成时间 -->
              <div class="flex items-center justify-between">
                <label class="text-sm" style="color:#606266">计划完成时间</label>
                <input
                  v-model="form.dueDate"
                  type="date"
                  placeholder="请选择日期"
                  class="w-40 rounded border bg-transparent px-2 py-1 text-right text-sm outline-none"
                  style="border-color:#dcdfe6;color:#303133"
                  :disabled="!canWrite"
                  @change="saveField('截止时间', { dueDate: form.dueDate || null })"
                />
              </div>

              <!-- 不修复理由 -->
              <div>
                <label class="mb-1 block text-sm" style="color:#606266">不修复理由</label>
                <input
                  v-model="form.noFixReason"
                  placeholder="请输入"
                  class="w-full rounded border bg-transparent px-2 py-1 text-sm outline-none"
                  style="border-color:#dcdfe6;color:#303133"
                />
              </div>
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

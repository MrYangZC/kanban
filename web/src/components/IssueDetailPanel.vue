<script setup lang="ts">
import { ref, watch, computed } from 'vue'
import type { Issue, AttachmentVO, IssueStatusVO } from '@/api/types'
import { categoryOfTypeCode } from '@/api/types'
import {
  updateIssue,
  listProjectStatuses, listTransitions,
  listUsers,
  uploadImage
} from '@/api'
import type { UserVO } from '@/api/types'
import { ElMessage } from 'element-plus'
import { confirmAction } from '@/utils/confirm'
import { MessageSquare, Paperclip, Image as ImageIcon, X, Plus, Trash2, UserCircle2, ShieldCheck } from 'lucide-vue-next'
import { useIssueTabs } from '@/composables/useIssueTabs'
import ActivityItem from '@/components/ActivityItem.vue'
import { useUserStore } from '@/stores/user'
import { fmtTime } from '@/utils/datetime'

const props = defineProps<{ issue: Issue; projectId: string }>()
const emit = defineEmits<{ updated: [Issue] }>()

const activeTab = ref('activity')
const title = ref('')
const description = ref('')
const priority = ref('P2')
const severity = ref('S3')
const module = ref('')
const assigneeId = ref('')
const verifierId = ref('')
const fixVersion = ref('')
const source = ref('')
const dueDate = ref('')
const reporterName = ref('')
const statusId = ref('')
const statuses = ref<IssueStatusVO[]>([])
const users = ref<UserVO[]>([])

// 工作流流转规则：fromStatusId → 可达 toStatusId（与看板拖拽一致）
const transitionMap = ref<Record<string, Set<string>>>({})
const statusOptions = computed(() => {
  const current = statusId.value
  const reachable: Set<string> | undefined = current ? transitionMap.value[current] : undefined
  if (!reachable || reachable.size === 0) return statuses.value
  return statuses.value.filter((s) => s.id === current || reachable.has(s.id))
})

const userStore = useUserStore()
// 删除按钮只对自己的评论显示，口径与路由级详情页一致
const currentUserName = computed(() => userStore.user?.displayName || userStore.user?.username || '')

const commentText = ref('')
const fileInput = ref<HTMLInputElement>()
const attachInput = ref<HTMLInputElement>()
const attachUploading = ref(false)
const linkTarget = ref('')
const linkType = ref('关联')
const tagInput = ref('')

// 动态&评论 / 附件 / 关联 / 标签的数据与增删逻辑与路由级详情页（DefectDetailView）共用，
// 避免面板与详情页出现两套行为（如删除评论后是否刷新、标签配色口径不一致）
const {
  activities,
  attachments,
  links,
  tags,
  reload: reloadTabs,
  sendComment: sendCommentApi,
  uploadFile: uploadFileApi,
  addLinkItem: addLinkItemApi,
  removeComment,
  removeLink,
  removeAttachment: removeAttach,
  addTag: addTagApi,
  removeTag
} = useIssueTabs({
  issueId: () => props.issue.id,
  projectId: () => props.projectId
})

watch(
  () => props.issue,
  (v) => {
    title.value = v.title
    description.value = v.description || ''
    priority.value = v.priority || 'P2'
    severity.value = v.severity || 'S3'
    module.value = v.module || ''
    assigneeId.value = v.assigneeId || ''
    verifierId.value = v.verifierId || ''
    fixVersion.value = v.fixVersion || ''
    source.value = v.source || ''
    dueDate.value = v.dueDate || ''
    reporterName.value = v.reporterName || ''
    statusId.value = v.statusId || ''
    tags.value = v.labels || []
    reloadTabs()
    loadUsers()
  },
  { immediate: true }
)

// 状态下拉与流转规则都按工作项类别取：任务/需求/缺陷各有自己的状态节点，
// 因此监视 projectId + 当前工作项类型（类型切换时换一套字典）
watch(
  () => [props.projectId, categoryOfTypeCode(props.issue?.issueTypeCode)] as const,
  async ([pid, category]) => {
    if (!pid) return
    try {
      statuses.value = await listProjectStatuses(pid, category)
    } catch {
      // 状态字典拉取失败时下拉为空，保存时因 statusId 为空不会误改状态
    }
    try {
      const transitions = await listTransitions(pid, category)
      const map: Record<string, Set<string>> = {}
      for (const t of transitions) {
        if (!map[t.fromStatusId]) map[t.fromStatusId] = new Set()
        map[t.fromStatusId].add(t.toStatusId)
      }
      transitionMap.value = map
    } catch { /* 流转规则缺失时回退为全部状态可选 */ }
  },
  { immediate: true }
)

async function loadUsers() {
  try {
    users.value = await listUsers()
  } catch {
    // 接口层已提示失败原因；负责人/验证人下拉退化为仅"未指派"
    users.value = []
  }
}

async function save() {
  const clearFields: string[] = []
  if (!assigneeId.value) clearFields.push('assigneeId')
  if (!verifierId.value) clearFields.push('verifierId')
  if (!dueDate.value) clearFields.push('dueDate')
  const updated = await updateIssue(props.issue.id, {
    title: title.value,
    description: description.value,
    priority: priority.value,
    severity: severity.value,
    statusId: statusId.value || undefined,
    module: module.value || undefined,
    assigneeId: assigneeId.value || undefined,
    verifierId: verifierId.value || undefined,
    dueDate: dueDate.value || undefined,
    fixVersion: fixVersion.value || undefined,
    source: source.value || undefined,
    // 空值显式置空：解决"清空后保存无效"
    ...(clearFields.length ? { clearFields } : {}),
  })
  ElMessage.success('已保存')
  emit('updated', updated)
}

// ---- 评论（支持图片） ----
async function onPickImage(e: Event) {
  const input = e.target as HTMLInputElement
  const file = input.files?.[0]
  if (!file) return
  try {
    const res = await uploadImage(file, props.projectId)
    commentText.value += `\n<img src="${res.url}" style="max-width:100%" alt="图片" />\n`
  } finally {
    input.value = ''
  }
}

async function sendComment() {
  if (await sendCommentApi(commentText.value)) commentText.value = ''
}

// 评论正文的白名单渲染统一交给 utils/commentContent（与详情页同一套规则）

// ---- 附件（真实文件上传） ----
async function onPickAttachment(e: Event) {
  const input = e.target as HTMLInputElement
  const file = input.files?.[0]
  if (!file) return
  attachUploading.value = true
  try {
    if (await uploadFileApi(file)) ElMessage.success('上传成功')
  } finally {
    attachUploading.value = false
    input.value = ''
  }
}

const isImage = (a: AttachmentVO) => a.url && /\.(jpg|jpeg|png|gif|webp)$/i.test(a.fileName)

// ---- 关联 ----
async function addLinkItem() {
  const num = parseInt(linkTarget.value.replace(/\D/g, ''), 10)
  if (!num) {
    ElMessage.warning('请输入任务编号，如 12')
    return
  }
  if (await addLinkItemApi(linkTarget.value.trim(), linkType.value)) linkTarget.value = ''
}

// ---- 标签 ----
async function addTag() {
  if (await addTagApi(tagInput.value)) tagInput.value = ''
}

// ---- 删除确认（与详情页保持一致，避免误触丢失协作记录） ----
async function onRemoveComment(commentId: string) {
  if (!(await confirmAction('确定删除该评论吗？删除后不可恢复。', '删除评论'))) return
  await removeComment(commentId)
}

async function onRemoveAttach(attachmentId: string) {
  if (!(await confirmAction('确定删除该附件吗？', '删除附件'))) return
  await removeAttach(attachmentId)
}

</script>

<template>
  <div class="flex h-full flex-col overflow-hidden">
    <!-- 顶部标题区 -->
    <div class="shrink-0 border-b border-surface-muted px-5 py-4">
      <input
        v-model="title"
        class="w-full border-none text-lg font-semibold text-ink-900 outline-none"
        placeholder="任务标题"
      />
      <div class="mt-1 flex items-center gap-2 text-xs text-ink-400">
        <span class="rounded bg-surface-muted px-1.5 py-0.5 font-mono">{{ issue.issueKey }}</span>
        <span>创建人：{{ reporterName || '未知' }}</span>
      </div>
    </div>

    <!-- 两栏内容区 -->
    <div class="flex min-h-0 flex-1">
      <!-- 左栏：Tab + 内容 -->
      <div class="flex min-w-0 flex-1 flex-col">
        <div class="flex shrink-0 border-b border-surface-muted px-5 text-sm">
          <button
            v-for="t in [
              { k: 'activity', label: '动态&评论' },
              { k: 'link', label: '关联' },
              { k: 'attach', label: '附件' }
            ]"
            :key="t.k"
            class="border-b-2 px-3 py-2.5 transition"
            :class="activeTab === t.k ? 'border-brand text-brand' : 'border-transparent text-ink-600 hover:text-ink-900'"
            @click="activeTab = t.k"
          >{{ t.label }}</button>
        </div>

        <div class="min-h-0 flex-1 overflow-y-auto px-5 py-4">
          <!-- 动态&评论 -->
          <div v-if="activeTab === 'activity'" class="space-y-4">
            <div class="space-y-2">
              <textarea
                v-model="commentText"
                rows="3"
                placeholder="写下评论…（可插入图片）"
                class="w-full resize-y rounded-lg border border-surface-muted px-3 py-2 text-sm outline-none focus:border-brand"
              />
              <div class="flex items-center gap-2">
                <input
                  ref="fileInput"
                  type="file"
                  accept="image/*"
                  class="hidden"
                  @change="onPickImage"
                />
                <button
                  class="flex items-center gap-1 rounded-lg border border-surface-muted px-3 py-2 text-sm text-ink-600 transition hover:border-brand hover:text-brand"
                  @click="fileInput?.click()"
                ><ImageIcon :size="14" />图片</button>
                <button
                  class="flex items-center gap-1 rounded-lg bg-brand px-4 py-2 text-sm text-white transition hover:bg-brand-dark"
                  @click="sendComment"
                ><MessageSquare :size="14" />发送</button>
              </div>
            </div>

            <!-- 动态与评论共用 ActivityItem，与详情页版式一致（头像 + 姓名 + 时间 + 正文） -->
            <div class="space-y-4">
              <ActivityItem
                v-for="a in activities"
                :key="a.id"
                :node="a"
                can-write
                :current-user-name="currentUserName"
                @remove="onRemoveComment"
              />
              <div v-if="!activities.length" class="text-center text-sm text-ink-400">暂无动态</div>
            </div>
          </div>

          <!-- 关联 -->
          <div v-else-if="activeTab === 'link'" class="space-y-4">
            <div class="flex gap-2">
              <input
                v-model="linkTarget"
                placeholder="任务编号，如 12"
                class="flex-1 rounded-lg border border-surface-muted px-3 py-2 text-sm outline-none focus:border-brand"
              />
              <select v-model="linkType" class="rounded-lg border border-surface-muted px-2 text-sm outline-none focus:border-brand">
                <option>关联</option><option>阻塞</option><option>重复</option><option>修复</option>
              </select>
              <button class="flex items-center gap-1 rounded-lg bg-brand px-3 text-sm text-white hover:bg-brand-dark" @click="addLinkItem"><Plus :size="14" /></button>
            </div>
            <div class="space-y-2">
              <div v-for="l in links" :key="l.id" class="flex items-center justify-between rounded-lg bg-surface-muted/50 p-3 text-sm">
                <div>
                  <span class="rounded bg-brand/10 px-1.5 py-0.5 text-[11px] text-brand">{{ l.relationType }}</span>
                  <span class="ml-2 text-ink-900">{{ l.targetKey }}</span>
                  <span class="ml-2 text-ink-400">{{ l.targetTitle }}</span>
                </div>
                <button class="text-ink-400 hover:text-red-500" @click="removeLink(l.id)"><Trash2 :size="14" /></button>
              </div>
              <div v-if="!links.length" class="text-center text-sm text-ink-400">暂无关联</div>
            </div>
          </div>

          <!-- 附件 -->
          <div v-else class="space-y-4">
            <input
              ref="attachInput"
              type="file"
              accept="image/jpeg,image/png,image/gif,image/webp"
              class="hidden"
              @change="onPickAttachment"
            />
            <button
              class="flex items-center gap-1 rounded-lg bg-brand px-3 text-sm text-white hover:bg-brand-dark disabled:opacity-60"
              :disabled="attachUploading"
              @click="attachInput?.click()"
            ><Paperclip :size="14" />{{ attachUploading ? '上传中…' : '上传附件' }}</button>
            <div class="space-y-2">
              <div v-for="a in attachments" :key="a.id" class="flex items-center justify-between rounded-lg bg-surface-muted/50 p-3 text-sm">
                <div class="flex min-w-0 items-center gap-2">
                  <img v-if="isImage(a)" :src="a.url" class="h-10 w-10 shrink-0 rounded object-cover" :alt="a.fileName" />
                  <Paperclip v-else :size="14" class="shrink-0 text-ink-400" />
                  <a
                    v-if="a.url"
                    :href="a.url"
                    target="_blank"
                    class="truncate text-ink-900 hover:text-brand"
                  >{{ a.fileName }}</a>
                  <span v-else class="truncate text-ink-900">{{ a.fileName }}</span>
                  <span class="shrink-0 text-xs text-ink-400">{{ a.uploaderName }} · {{ fmtTime(a.createdAt) }}</span>
                </div>
                <button class="shrink-0 text-ink-400 hover:text-red-500" @click="onRemoveAttach(a.id)"><Trash2 :size="14" /></button>
              </div>
              <div v-if="!attachments.length" class="text-center text-sm text-ink-400">暂无附件</div>
            </div>
          </div>
        </div>
      </div>

      <!-- 右栏：基础字段 -->
      <div class="glass-bar w-[280px] shrink-0 space-y-3.5 overflow-y-auto border-l px-4 py-4">
        <div class="text-xs font-semibold uppercase tracking-wide text-ink-400">基础字段</div>
        <div>
          <label class="mb-1 block text-xs text-ink-400">状态</label>
          <select v-model="statusId" class="w-full rounded-lg border border-surface-muted px-3 py-2 text-sm outline-none focus:border-brand">
            <option v-for="s in statusOptions" :key="s.id" :value="s.id">{{ s.name }}</option>
          </select>
        </div>
        <div>
          <label class="mb-1 block text-xs text-ink-400">负责人</label>
          <div class="relative">
            <UserCircle2 :size="14" class="pointer-events-none absolute left-2.5 top-1/2 -translate-y-1/2 text-ink-400" />
            <select v-model="assigneeId" class="w-full rounded-lg border border-surface-muted py-2 pl-8 pr-3 text-sm outline-none focus:border-brand">
              <option value="">未指派</option>
              <option v-for="u in users" :key="u.id" :value="u.id">{{ u.displayName }}</option>
            </select>
          </div>
        </div>
        <div>
          <label class="mb-1 block text-xs text-ink-400">验证人</label>
          <div class="relative">
            <ShieldCheck :size="14" class="pointer-events-none absolute left-2.5 top-1/2 -translate-y-1/2 text-ink-400" />
            <select v-model="verifierId" class="w-full rounded-lg border border-surface-muted py-2 pl-8 pr-3 text-sm outline-none focus:border-brand">
              <option value="">未指派</option>
              <option v-for="u in users" :key="u.id" :value="u.id">{{ u.displayName }}</option>
            </select>
          </div>
        </div>
        <div>
          <label class="mb-1 block text-xs text-ink-400">优先级</label>
          <select v-model="priority" class="w-full rounded-lg border border-surface-muted px-3 py-2 text-sm outline-none focus:border-brand">
            <option value="P0">紧急</option>
            <option value="P1">高</option>
            <option value="P2">中</option>
            <option value="P3">低</option>
          </select>
        </div>
        <div>
          <label class="mb-1 block text-xs text-ink-400">严重程度</label>
          <select v-model="severity" class="w-full rounded-lg border border-surface-muted px-3 py-2 text-sm outline-none focus:border-brand">
            <option value="S1">致命</option>
            <option value="S2">严重</option>
            <option value="S3">一般</option>
            <option value="S4">轻微</option>
          </select>
        </div>
        <div>
          <label class="mb-1 block text-xs text-ink-400">模块</label>
          <input v-model="module" placeholder="如 支付" class="w-full rounded-lg border border-surface-muted px-3 py-2 text-sm outline-none focus:border-brand" />
        </div>
        <div>
          <label class="mb-1 block text-xs text-ink-400">修复版本</label>
          <input v-model="fixVersion" placeholder="如 v1.4.0" class="w-full rounded-lg border border-surface-muted px-3 py-2 text-sm outline-none focus:border-brand" />
        </div>
        <div>
          <label class="mb-1 block text-xs text-ink-400">来源</label>
          <input v-model="source" placeholder="如 测试/线上/客户反馈" class="w-full rounded-lg border border-surface-muted px-3 py-2 text-sm outline-none focus:border-brand" />
        </div>
        <div>
          <label class="mb-1 block text-xs text-ink-400">报告人</label>
          <div class="rounded-lg border border-surface-muted px-3 py-2 text-sm text-ink-600">{{ reporterName || '未知' }}</div>
        </div>
        <div>
          <label class="mb-1 block text-xs text-ink-400">截止时间</label>
          <input v-model="dueDate" type="date" class="w-full rounded-lg border border-surface-muted px-3 py-2 text-sm outline-none focus:border-brand" />
        </div>
        <div>
          <label class="mb-1 block text-xs text-ink-400">标签</label>
          <div class="flex flex-wrap items-center gap-1.5">
            <span
              v-for="t in tags"
              :key="t.id"
              class="flex items-center gap-1 rounded px-1.5 py-0.5 text-[11px] text-white"
              :style="{ background: t.color || 'var(--primary)' }"
            >
              {{ t.name }}
              <button class="hover:opacity-70" @click="removeTag(t.id)"><X :size="10" /></button>
            </span>
            <input
              v-model="tagInput"
              placeholder="加标签"
              class="w-20 rounded border border-surface-muted px-1.5 py-0.5 text-[11px] outline-none focus:border-brand"
              @keyup.enter="addTag"
            />
          </div>
        </div>
      </div>
    </div>

    <!-- 底部保存 -->
    <div class="glass-bar shrink-0 border-t px-5 py-3">
      <button
        class="w-full rounded-lg bg-brand py-2.5 text-sm font-medium text-white transition hover:bg-brand-dark"
        @click="save"
      >保存修改</button>
    </div>
  </div>
</template>

<style scoped>
.comment-content :deep(img) {
  max-width: 100%;
  margin-top: 6px;
  border-radius: 8px;
}
</style>

<script setup lang="ts">
import { ref, watch, computed } from 'vue'
import type { Issue, IssueActivity, AttachmentVO, LinkVO, IssueStatusVO } from '@/api/types'
import {
  updateIssue,
  listActivities, addComment, deleteComment,
  listAttachments, addAttachment, deleteAttachment,
  listLinks, addLink, deleteLink,
  listIssueTags, addIssueTag, removeIssueTag,
  listProjectStatuses,
  listUsers
} from '@/api'
import type { UserVO } from '@/api/types'
import { ElMessage } from 'element-plus'
import { MessageSquare, Paperclip, Link2, X, Plus, Trash2, UserCircle2, ShieldCheck } from 'lucide-vue-next'

const props = defineProps<{ issue: Issue | null; open: boolean; projectId: string }>()
const emit = defineEmits<{ close: []; updated: [Issue] }>()

const activeTab = ref('base')
const title = ref('')
const description = ref('')
const priority = ref('P2')
const severity = ref('S3')
const module = ref('')
const assigneeId = ref('')
const assigneeName = ref('')
const verifierId = ref('')
const verifierName = ref('')
const fixVersion = ref('')
const source = ref('')
const dueDate = ref('')
const reporterName = ref('')
const statusId = ref('')
const statuses = ref<IssueStatusVO[]>([])
const users = ref<UserVO[]>([])

// 动态&评论
const activities = ref<IssueActivity[]>([])
const commentText = ref('')
// 附件
const attachments = ref<AttachmentVO[]>([])
const attachName = ref('')
// 关联
const links = ref<LinkVO[]>([])
const linkTarget = ref('')
const linkType = ref('关联')
// 标签
const tags = ref<{ id: string; name: string; color: string }[]>([])
const tagInput = ref('')

watch(
  () => props.issue,
  (v) => {
    if (v) {
      title.value = v.title
      description.value = v.description || ''
      priority.value = v.priority || 'P2'
      severity.value = v.severity || 'S3'
      module.value = v.module || ''
      assigneeId.value = v.assigneeId || ''
      assigneeName.value = v.assigneeName || ''
      verifierId.value = v.verifierId || ''
      verifierName.value = v.verifierName || ''
      fixVersion.value = v.fixVersion || ''
      source.value = v.source || ''
      dueDate.value = v.dueDate || ''
      reporterName.value = v.reporterName || ''
      statusId.value = v.statusId || ''
      tags.value = v.labels || []
      loadTabs(v.id)
    }
  },
  { immediate: true }
)

watch(
  () => props.projectId,
  async (pid) => {
    if (!pid) return
    try {
      statuses.value = await listProjectStatuses(pid)
    } catch { /* ignore */ }
  },
  { immediate: true }
)

async function loadTabs(issueId: string) {
  try {
    activities.value = await listActivities(issueId)
  } catch { /* ignore */ }
  try {
    attachments.value = await listAttachments(issueId)
  } catch { /* ignore */ }
  try {
    links.value = await listLinks(issueId)
  } catch { /* ignore */ }
  try {
    tags.value = await listIssueTags(issueId)
  } catch { /* ignore */ }
  try {
    users.value = await listUsers()
  } catch { /* ignore */ }
}

async function save() {
  if (!props.issue) return
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
  })
  ElMessage.success('已保存')
  emit('updated', updated)
}

async function sendComment() {
  if (!props.issue || !commentText.value.trim()) return
  await addComment(props.issue.id, commentText.value.trim())
  commentText.value = ''
  activities.value = await listActivities(props.issue.id)
}

async function removeComment(id: string) {
  if (!props.issue) return
  await deleteComment(props.issue.id, id)
  activities.value = await listActivities(props.issue.id)
}

async function addAttach() {
  if (!props.issue || !attachName.value.trim()) return
  await addAttachment(props.issue.id, attachName.value.trim(), 'application/octet-stream', 0)
  attachName.value = ''
  attachments.value = await listAttachments(props.issue.id)
}

async function removeAttach(id: string) {
  if (!props.issue) return
  await deleteAttachment(props.issue.id, id)
  attachments.value = await listAttachments(props.issue.id)
}

async function addLinkItem() {
  if (!props.issue || !linkTarget.value.trim()) return
  const num = parseInt(linkTarget.value.replace(/\D/g, ''), 10)
  if (!num) {
    ElMessage.warning('请输入任务编号，如 12')
    return
  }
  await addLink(props.issue.id, linkTarget.value.trim(), linkType.value)
  linkTarget.value = ''
  links.value = await listLinks(props.issue.id)
}

async function removeLink(id: string) {
  if (!props.issue) return
  await deleteLink(props.issue.id, id)
  links.value = await listLinks(props.issue.id)
}

async function addTag() {
  if (!props.issue || !tagInput.value.trim()) return
  await addIssueTag(props.issue.id, props.projectId, tagInput.value.trim(), '#1677FF')
  tagInput.value = ''
  tags.value = await listIssueTags(props.issue.id)
}

async function removeTag(labelId: string) {
  if (!props.issue) return
  await removeIssueTag(props.issue.id, labelId)
  tags.value = tags.value.filter((t) => t.id !== labelId)
}

const fmtTime = (t?: string) => (t ? new Date(t).toLocaleString('zh-CN') : '')
</script>

<template>
  <div v-if="open" class="fixed inset-0 z-40 flex justify-end">
    <div class="absolute inset-0 bg-black/20" @click="emit('close')" />
    <div class="relative z-50 flex h-full w-[460px] flex-col bg-white shadow-2xl">
      <div class="flex items-center justify-between border-b border-surface-muted px-5 py-4">
        <div class="flex items-center gap-2">
          <span class="text-lg">{{ props.issue?.issueTypeIcon || '📋' }}</span>
          <span class="text-sm font-medium text-ink-400">{{ props.issue?.issueKey }}</span>
        </div>
        <button class="text-ink-400 hover:text-ink-900" @click="emit('close')"><X :size="18" /></button>
      </div>

      <div v-if="props.issue" class="flex flex-1 flex-col overflow-hidden">
        <!-- Tabs -->
        <div class="flex border-b border-surface-muted px-3 text-sm">
          <button
            v-for="t in [
              { k: 'base', label: '基础信息' },
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

        <div class="flex-1 overflow-auto px-5 py-5">
          <!-- 基础信息 -->
          <div v-if="activeTab === 'base'" class="space-y-4">
            <input v-model="title" class="w-full border-none text-lg font-semibold text-ink-900 outline-none" />
            <div class="space-y-4">
              <div>
                <label class="mb-1 block text-xs text-ink-400">状态</label>
                <select v-model="statusId" class="w-full rounded-lg border border-surface-muted px-3 py-2 outline-none focus:border-brand">
                  <option v-for="s in statuses" :key="s.id" :value="s.id">{{ s.name }}</option>
                </select>
              </div>
            </div>
            <div class="grid grid-cols-2 gap-3 text-sm">
              <div>
                <label class="mb-1 block text-xs text-ink-400">优先级</label>
                <select v-model="priority" class="w-full rounded-lg border border-surface-muted px-3 py-2 outline-none focus:border-brand">
                  <option value="P0">P0 紧急</option>
                  <option value="P1">P1 高</option>
                  <option value="P2">P2 普通</option>
                  <option value="P3">P3 低</option>
                </select>
              </div>
              <div>
                <label class="mb-1 block text-xs text-ink-400">严重程度</label>
                <select v-model="severity" class="w-full rounded-lg border border-surface-muted px-3 py-2 outline-none focus:border-brand">
                  <option value="S1">S1 致命</option>
                  <option value="S2">S2 严重</option>
                  <option value="S3">S3 一般</option>
                  <option value="S4">S4 轻微</option>
                </select>
              </div>
              <div>
                <label class="mb-1 block text-xs text-ink-400">负责人</label>
                <div class="relative">
                  <UserCircle2 :size="14" class="pointer-events-none absolute left-2.5 top-1/2 -translate-y-1/2 text-ink-400" />
                  <select v-model="assigneeId" class="w-full rounded-lg border border-surface-muted py-2 pl-8 pr-3 outline-none focus:border-brand">
                    <option value="">未指派</option>
                    <option v-for="u in users" :key="u.id" :value="u.id">{{ u.displayName }}</option>
                  </select>
                </div>
              </div>
              <div>
                <label class="mb-1 block text-xs text-ink-400">验证人</label>
                <div class="relative">
                  <ShieldCheck :size="14" class="pointer-events-none absolute left-2.5 top-1/2 -translate-y-1/2 text-ink-400" />
                  <select v-model="verifierId" class="w-full rounded-lg border border-surface-muted py-2 pl-8 pr-3 outline-none focus:border-brand">
                    <option value="">未指派</option>
                    <option v-for="u in users" :key="u.id" :value="u.id">{{ u.displayName }}</option>
                  </select>
                </div>
              </div>
              <div>
                <label class="mb-1 block text-xs text-ink-400">截止时间</label>
                <input v-model="dueDate" type="date" class="w-full rounded-lg border border-surface-muted px-3 py-2 outline-none focus:border-brand" />
              </div>
              <div>
                <label class="mb-1 block text-xs text-ink-400">报告人</label>
                <div class="rounded-lg border border-surface-muted px-3 py-2 text-ink-600">{{ reporterName || '未知' }}</div>
              </div>
              <div>
                <label class="mb-1 block text-xs text-ink-400">模块</label>
                <input v-model="module" placeholder="如 支付" class="w-full rounded-lg border border-surface-muted px-3 py-2 outline-none focus:border-brand" />
              </div>
              <div>
                <label class="mb-1 block text-xs text-ink-400">修复版本</label>
                <input v-model="fixVersion" placeholder="如 v1.4.0" class="w-full rounded-lg border border-surface-muted px-3 py-2 outline-none focus:border-brand" />
              </div>
              <div>
                <label class="mb-1 block text-xs text-ink-400">来源</label>
                <input v-model="source" placeholder="如 测试/线上/客户反馈" class="w-full rounded-lg border border-surface-muted px-3 py-2 outline-none focus:border-brand" />
              </div>
            </div>
            <div>
              <label class="mb-1 block text-xs text-ink-400">标签</label>
              <div class="flex flex-wrap items-center gap-1.5">
                <span v-for="t in tags" :key="t.id" class="flex items-center gap-1 rounded px-1.5 py-0.5 text-[11px] text-white" :style="{ background: t.color || '#1677FF' }">
                  {{ t.name }}
                  <button class="hover:opacity-70" @click="removeTag(t.id)"><X :size="10" /></button>
                </span>
                <input v-model="tagInput" placeholder="加标签" class="w-20 rounded border border-surface-muted px-1.5 py-0.5 text-[11px] outline-none focus:border-brand" @keyup.enter="addTag" />
              </div>
            </div>
            <div>
              <label class="mb-1 block text-xs text-ink-400">描述</label>
              <textarea v-model="description" rows="6" class="w-full rounded-lg border border-surface-muted px-3 py-2 text-sm outline-none focus:border-brand" placeholder="补充任务细节…" />
            </div>
          </div>

          <!-- 动态&评论 -->
          <div v-else-if="activeTab === 'activity'" class="space-y-4">
            <div class="flex gap-2">
              <input v-model="commentText" placeholder="写下评论…" class="flex-1 rounded-lg border border-surface-muted px-3 py-2 text-sm outline-none focus:border-brand" @keyup.enter="sendComment" />
              <button class="flex items-center gap-1 rounded-lg bg-brand px-3 text-sm text-white hover:bg-brand-dark" @click="sendComment"><MessageSquare :size="14" />发送</button>
            </div>
            <div class="space-y-3">
              <div v-for="a in activities" :key="a.id" class="rounded-lg bg-surface-muted/50 p-3 text-sm">
                <div class="mb-1 flex items-center justify-between">
                  <span class="font-medium text-ink-900">{{ a.actorName }}</span>
                  <span class="text-xs text-ink-400">{{ fmtTime(a.createdAt) }}</span>
                </div>
                <div v-if="a.type === 'COMMENT'" class="text-ink-600">
                  {{ a.content }}
                  <button class="ml-2 text-xs text-ink-400 hover:text-red-500" @click="removeComment(a.id)"><Trash2 :size="12" /></button>
                </div>
                <div v-else class="text-ink-400">
                  {{ a.type === 'STATUS_CHANGED' ? '变更了状态' : '更新了字段' }}
                  <code class="ml-1 text-xs text-ink-600">{{ a.oldValue }} → {{ a.newValue }}</code>
                </div>
              </div>
              <div v-if="!activities.length" class="text-center text-sm text-ink-400">暂无动态</div>
            </div>
          </div>

          <!-- 关联 -->
          <div v-else-if="activeTab === 'link'" class="space-y-4">
            <div class="flex gap-2">
              <input v-model="linkTarget" placeholder="任务编号，如 12" class="flex-1 rounded-lg border border-surface-muted px-3 py-2 text-sm outline-none focus:border-brand" />
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
          <div v-else-if="activeTab === 'attach'" class="space-y-4">
            <div class="flex gap-2">
              <input v-model="attachName" placeholder="文件名，如 crash.log" class="flex-1 rounded-lg border border-surface-muted px-3 py-2 text-sm outline-none focus:border-brand" @keyup.enter="addAttach" />
              <button class="flex items-center gap-1 rounded-lg bg-brand px-3 text-sm text-white hover:bg-brand-dark" @click="addAttach"><Paperclip :size="14" />添加</button>
            </div>
            <div class="space-y-2">
              <div v-for="a in attachments" :key="a.id" class="flex items-center justify-between rounded-lg bg-surface-muted/50 p-3 text-sm">
                <div class="flex items-center gap-2">
                  <Paperclip :size="14" class="text-ink-400" />
                  <span class="text-ink-900">{{ a.fileName }}</span>
                  <span class="text-xs text-ink-400">{{ a.uploaderName }} · {{ fmtTime(a.createdAt) }}</span>
                </div>
                <button class="text-ink-400 hover:text-red-500" @click="removeAttach(a.id)"><Trash2 :size="14" /></button>
              </div>
              <div v-if="!attachments.length" class="text-center text-sm text-ink-400">暂无附件</div>
            </div>
          </div>
        </div>

        <div class="border-t border-surface-muted px-5 py-4">
          <button class="w-full rounded-lg bg-brand py-2.5 text-sm font-medium text-white transition hover:bg-brand-dark" @click="save">保存修改</button>
        </div>
      </div>
    </div>
  </div>
</template>

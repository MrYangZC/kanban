<script setup lang="ts">
import type { Issue } from '@/api/types'
import { MessageSquare, CalendarClock } from 'lucide-vue-next'

const props = defineProps<{ issue: Issue }>()

const priorityColor: Record<string, string> = {
  P0: '#FF4D4F',
  P1: '#FAAD14',
  P2: '#1677FF',
  P3: '#8F959E'
}
const severityColor: Record<string, string> = {
  S1: '#FF4D4F',
  S2: '#FA8C16',
  S3: '#1677FF',
  S4: '#8F959E'
}
function dueText(due?: string) {
  if (!due) return ''
  const date = new Date(due)
  const now = new Date()
  const diff = Math.ceil((date.getTime() - now.getTime()) / 86400000)
  if (diff < 0) return '已逾期'
  if (diff === 0) return '今天'
  return `${diff}天后`
}
</script>

<template>
  <div class="group cursor-pointer rounded-lg bg-white p-3 shadow-sm ring-1 ring-black/5 transition hover:-translate-y-0.5 hover:shadow-md">
    <div class="mb-1.5 flex items-center gap-2">
      <span class="h-1.5 w-1.5 rounded-full" :style="{ background: priorityColor[props.issue.priority] || '#8F959E' }" />
      <span class="text-xs font-medium text-ink-400">{{ props.issue.issueKey }}</span>
      <span
        v-if="props.issue.severity"
        class="rounded px-1 py-0.5 text-[10px] font-medium text-white"
        :style="{ background: severityColor[props.issue.severity] || '#8F959E' }"
      >{{ props.issue.severity }}</span>
      <span class="ml-auto text-sm" :title="props.issue.issueTypeName">{{ props.issue.issueTypeIcon || '📋' }}</span>
    </div>

    <div class="text-sm leading-snug text-ink-900">{{ props.issue.title }}</div>

    <div v-if="props.issue.labels?.length" class="mt-2 flex flex-wrap gap-1">
      <span
        v-for="t in props.issue.labels"
        :key="t.id"
        class="rounded px-1.5 py-0.5 text-[10px] text-white"
        :style="{ background: t.color || '#1677FF' }"
      >{{ t.name }}</span>
    </div>

    <div class="mt-2.5 flex items-center justify-between">
      <div class="flex items-center gap-1.5 text-ink-400">
        <span class="rounded bg-surface-muted px-1.5 py-0.5 text-[11px] text-ink-600">{{ props.issue.priority }}</span>
        <span v-if="props.issue.module" class="truncate text-[11px] text-ink-400">#{{ props.issue.module }}</span>
        <span v-if="props.issue.commentCount" class="flex items-center gap-0.5 text-[11px]">
          <MessageSquare :size="12" />{{ props.issue.commentCount }}
        </span>
        <span v-if="props.issue.dueDate" class="flex items-center gap-0.5 text-[11px]">
          <CalendarClock :size="12" />{{ dueText(props.issue.dueDate) }}
        </span>
      </div>
      <div
        class="flex h-6 w-6 items-center justify-center rounded-full bg-brand/10 text-[11px] font-medium text-brand"
        :title="props.issue.assigneeName || '未指派'"
      >
        {{ props.issue.assigneeName ? props.issue.assigneeName.charAt(0) : '·' }}
      </div>
    </div>
  </div>
</template>

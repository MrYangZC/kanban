<script setup lang="ts">
import type { Issue } from '@/api/types'
import { MessageSquare, CalendarClock } from 'lucide-vue-next'
import { PRIORITY_LABEL, PRIORITY_COLOR, SEVERITY_LABEL, SEVERITY_COLOR } from '@/constants/issueDict'
import UserAvatar from '@/components/common/UserAvatar.vue'

const props = defineProps<{ issue: Issue }>()

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
  <div class="glass-panel group cursor-pointer rounded-lg p-3 transition hover:-translate-y-0.5 hover:shadow-md">
    <div class="mb-1.5 flex items-center gap-2">
      <span class="h-1.5 w-1.5 rounded-full" :style="{ background: PRIORITY_COLOR[props.issue.priority] || 'var(--ink-secondary)' }" />
      <span class="text-xs font-medium text-ink-400">{{ props.issue.issueKey }}</span>
      <span
        v-if="props.issue.severity"
        class="rounded px-1 py-0.5 text-[10px] font-medium text-white"
        :style="{ background: SEVERITY_COLOR[props.issue.severity] || 'var(--ink-secondary)' }"
      >{{ SEVERITY_LABEL[props.issue.severity] || props.issue.severity }}</span>
      <span class="ml-auto text-sm" :title="props.issue.issueTypeName">{{ props.issue.issueTypeIcon || '📋' }}</span>
    </div>

    <div class="text-sm leading-snug text-ink-900">{{ props.issue.title }}</div>

    <div v-if="props.issue.labels?.length" class="mt-2 flex flex-wrap gap-1">
      <span
        v-for="t in props.issue.labels"
        :key="t.id"
        class="rounded px-1.5 py-0.5 text-[10px] text-white"
        :style="{ background: t.color || 'var(--primary)' }"
      >{{ t.name }}</span>
    </div>

    <div class="mt-2.5 flex items-center justify-between">
      <div class="flex items-center gap-1.5 text-ink-400">
        <span class="rounded bg-surface-muted px-1.5 py-0.5 text-[11px] text-ink-600">{{ PRIORITY_LABEL[props.issue.priority] || props.issue.priority }}</span>
        <span v-if="props.issue.module" class="truncate text-[11px] text-ink-400">#{{ props.issue.module }}</span>
        <span v-if="props.issue.commentCount" class="flex items-center gap-0.5 text-[11px]">
          <MessageSquare :size="12" />{{ props.issue.commentCount }}
        </span>
        <span v-if="props.issue.dueDate" class="flex items-center gap-0.5 text-[11px]">
          <CalendarClock :size="12" />{{ dueText(props.issue.dueDate) }}
        </span>
      </div>
      <div class="flex items-center gap-1.5">
        <UserAvatar
          v-if="props.issue.assigneeName"
          :name="props.issue.assigneeName"
          :src="props.issue.assigneeAvatarUrl"
          :size="24"
        />
        <span
          v-if="props.issue.assigneeName"
          class="max-w-[110px] truncate text-[11px] text-ink-600"
          :title="props.issue.assigneeName"
        >{{ props.issue.assigneeName }}</span>
        <span v-else class="text-[11px] text-ink-400">未指派</span>
      </div>
    </div>
  </div>
</template>

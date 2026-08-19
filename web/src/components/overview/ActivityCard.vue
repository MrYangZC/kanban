<script setup lang="ts">
import { Activity } from 'lucide-vue-next'
import type { ProjectActivityVO } from '@/api/types'

interface Props {
  activities?: ProjectActivityVO[]
}

const props = withDefaults(defineProps<Props>(), {
  activities: () => []
})

function formatDate(dateStr: string): string {
  return dateStr?.slice(0, 10) || ''
}

function formatTime(dateStr: string): string {
  return dateStr?.slice(11, 16) || ''
}

const ACTION_LABEL: Record<string, string> = {
  CREATED: '创建了',
  UPDATED: '更新了',
  STATUS_CHANGED: '变更了状态',
  ASSIGNEE_CHANGED: '指派了',
  COMMENT: '评论了',
  DELETED: '删除了'
}

function actionLabel(action: string): string {
  return ACTION_LABEL[action] || action || '操作了'
}
</script>

<template>
  <div class="rounded-lg border border-[#E9ECEF] bg-white shadow-sm">
    <div class="flex items-center justify-between border-b border-[#E9ECEF] px-4 py-3">
      <div class="flex items-center gap-2 text-sm font-medium text-ink-900">
        <Activity :size="16" class="text-brand" /> 最近动态
      </div>
    </div>
    <div class="max-h-[400px] overflow-y-auto p-4">
      <div v-if="!props.activities.length" class="py-6 text-center text-sm text-ink-400">暂无动态</div>
      <div v-else class="space-y-4">
        <div v-for="item in props.activities" :key="item.id" class="flex gap-3">
          <div class="flex h-8 w-8 shrink-0 items-center justify-center rounded-full bg-brand/10 text-xs font-medium text-brand">
            {{ item.actorName?.charAt(0) || 'U' }}
          </div>
          <div class="flex-1 min-w-0">
            <div class="text-sm text-ink-900">
              <span class="font-medium">{{ item.actorName || '未知用户' }}</span>
              <span class="text-ink-400"> {{ actionLabel(item.action) }} </span>
              <span class="font-medium text-brand">#{{ item.issueNumber }} {{ item.issueTitle }}</span>
            </div>
            <div class="mt-0.5 text-xs text-ink-400">
              {{ formatDate(item.createdAt) }} {{ formatTime(item.createdAt) }}
            </div>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>
<script setup lang="ts">
import { Activity } from 'lucide-vue-next'
import UserAvatar from '@/components/common/UserAvatar.vue'
import type { ProjectActivityVO } from '@/api/types'
import { fmtDate, fmtHM } from '@/utils/datetime'

interface Props {
  activities?: ProjectActivityVO[]
}

const props = withDefaults(defineProps<Props>(), {
  activities: () => []
})

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
  <div class="glass-panel rounded-lg">
    <div class="flex items-center justify-between border-b border-line-light px-4 py-3">
      <div class="flex items-center gap-2 text-sm font-medium text-ink-900">
        <Activity :size="16" class="text-brand" /> 最近动态
      </div>
    </div>
    <div class="max-h-[400px] overflow-y-auto p-4">
      <div v-if="!props.activities.length" class="py-6 text-center text-sm text-ink-400">暂无动态</div>
      <div v-else class="space-y-4">
        <div v-for="item in props.activities" :key="item.id" class="flex gap-3">
          <UserAvatar :name="item.actorName" :src="item.actorAvatarUrl" :size="32" />
          <div class="flex-1 min-w-0">
            <div class="text-sm text-ink-900">
              <span class="font-medium">{{ item.actorName || '未知用户' }}</span>
              <span class="text-ink-400"> {{ actionLabel(item.action) }} </span>
              <span class="font-medium text-brand">#{{ item.issueNumber }} {{ item.issueTitle }}</span>
            </div>
            <div class="mt-0.5 text-xs text-ink-400">
              {{ fmtDate(item.createdAt) }} {{ fmtHM(item.createdAt) }}
            </div>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

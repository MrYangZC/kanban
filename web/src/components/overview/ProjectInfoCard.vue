<script setup lang="ts">
import { computed } from 'vue'
import type { Project } from '@/api/types'
import { FolderKanban, Calendar, User } from 'lucide-vue-next'
import StatusBadge from '@/components/StatusBadge.vue'
import type { StatusStyle } from '@/components/StatusBadge.vue'
import { fmtDate } from '@/utils/datetime'

interface Props {
  project: Project
  leadName?: string
}

const props = defineProps<Props>()

const STATUS_MAP: Record<string, StatusStyle> = {
  ACTIVE: { label: '进行中', color: '#28A745', bg: 'rgba(40,167,69,0.1)' },
  ARCHIVED: { label: '已归档', color: '#8F959E', bg: 'rgba(143,149,158,0.1)' },
  CLOSED: { label: '已关闭', color: '#DC3545', bg: 'rgba(220,53,69,0.1)' }
}

const fallback: StatusStyle = { label: '未知', color: '#1677FF', bg: 'rgba(22,119,255,0.1)' }

const createdAt = computed(() => fmtDate(props.project.createdAt))
</script>

<template>
  <div class="rounded-lg border border-[#E9ECEF] bg-white p-5 shadow-sm">
    <div class="flex items-start gap-4">
      <div class="flex h-12 w-12 shrink-0 items-center justify-center rounded-lg bg-surface-muted text-brand">
        <FolderKanban :size="24" />
      </div>
      <div class="flex-1 min-w-0">
        <div class="flex items-center gap-2">
          <h2 class="truncate text-lg font-semibold text-ink-900">{{ project.name }}</h2>
          <StatusBadge :status="project.status" :dict="STATUS_MAP" :fallback="fallback" />
        </div>
        <div class="mt-1 flex items-center gap-4 text-xs text-ink-400">
          <span class="flex items-center gap-1">
            <FolderKanban :size="12" /> {{ project.projectKey }}
          </span>
          <span v-if="leadName" class="flex items-center gap-1">
            <User :size="12" /> {{ leadName }}
          </span>
          <span v-if="createdAt" class="flex items-center gap-1">
            <Calendar :size="12" /> {{ createdAt }}
          </span>
        </div>
        <p class="mt-2 text-sm text-ink-600">{{ project.description || '暂无描述' }}</p>
      </div>
    </div>
  </div>
</template>

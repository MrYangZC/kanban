<script setup lang="ts">
import { computed } from 'vue'
import type { Issue } from '@/api/types'
import { X } from 'lucide-vue-next'
import IssueDetailPanel from './IssueDetailPanel.vue'

const props = defineProps<{
  issue: Issue | null
  open: boolean
  projectId: string
  mode?: 'overlay' | 'inline'
}>()
const emit = defineEmits<{ close: []; updated: [Issue] }>()

const isInline = computed(() => props.mode === 'inline')

// el-drawer 双向绑定：关闭（遮罩/ESC/关闭按钮）时同步 emit close
const drawerVisible = computed({
  get: () => props.open,
  set: (v: boolean) => {
    if (!v) emit('close')
  }
})
</script>

<template>
  <!-- overlay 模式：el-drawer 弹窗（超宽：至少 800px，随屏宽 65vw，上限 1280px） -->
  <el-drawer
    v-if="!isInline"
    v-model="drawerVisible"
    :size="'clamp(800px, 65vw, 1280px)'"
    direction="rtl"
    :with-header="true"
    :body-style="{ padding: 0, height: '100%', overflow: 'hidden' }"
    :header-style="{ borderBottom: '1px solid var(--el-border-color-lighter)', margin: '0' }"
  >
    <template #header>
      <div class="flex items-center gap-2">
        <span class="text-lg">{{ props.issue?.issueTypeIcon || '📋' }}</span>
        <span class="text-sm font-medium text-ink-400">{{ props.issue?.issueKey }}</span>
      </div>
    </template>

    <IssueDetailPanel
      v-if="props.issue"
      :issue="props.issue"
      :project-id="props.projectId"
      @updated="emit('updated', $event)"
    />
  </el-drawer>

  <!-- inline 模式：页面内嵌右侧详情面板（宽度与 overlay 一致） -->
  <div
    v-else-if="props.open && props.issue"
    class="flex h-full w-[clamp(800px,65vw,1280px)] shrink-0 flex-col border-l border-surface-muted bg-white"
  >
    <div class="flex shrink-0 items-center justify-between border-b border-surface-muted px-5 py-4">
      <div class="flex items-center gap-2">
        <span class="text-lg">{{ props.issue?.issueTypeIcon || '📋' }}</span>
        <span class="text-sm font-medium text-ink-400">{{ props.issue?.issueKey }}</span>
      </div>
      <button class="text-ink-400 hover:text-ink-900" @click="emit('close')"><X :size="18" /></button>
    </div>

    <IssueDetailPanel
      :issue="props.issue"
      :project-id="props.projectId"
      @updated="emit('updated', $event)"
    />
  </div>
</template>

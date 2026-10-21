<script setup lang="ts">
import { computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { BarChart3, ChevronLeft } from 'lucide-vue-next'
import ProjectMetricsPanel from '@/components/ProjectMetricsPanel.vue'

const route = useRoute()
const router = useRouter()
const projectId = computed(() => route.params.projectId as string)

function back() { router.push('/projects') }
function toBoard() { router.push(`/projects/${projectId.value}/board`) }
</script>

<template>
  <div class="app-canvas min-h-full">
    <header class="glass-bar sticky top-0 z-10 flex h-14 items-center gap-3 border-b px-4">
      <button class="flex items-center gap-1 text-sm text-ink-600 hover:text-ink-900" @click="back">
        <ChevronLeft :size="18" /> 项目
      </button>
      <div class="flex items-center gap-2">
        <BarChart3 :size="18" class="text-brand" />
        <span class="font-semibold text-ink-900">统计报表</span>
      </div>
      <button class="ml-auto rounded-lg border border-surface-muted px-3 py-1.5 text-sm text-ink-600 transition hover:bg-surface-muted" @click="toBoard">
        返回看板
      </button>
    </header>

    <div class="mx-auto max-w-6xl space-y-5 p-5">
      <ProjectMetricsPanel :project-id="projectId" variant="ring" />
    </div>
  </div>
</template>

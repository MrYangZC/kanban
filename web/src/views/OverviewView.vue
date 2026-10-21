<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { useRoute } from 'vue-router'
import { getDashboard, listProjectMembers, listMilestones, listAnnouncements, listProjectActivities } from '@/api'
import { useProjectStore } from '@/stores/project'
import type { DashboardVO, ProjectMember, MilestoneVO, AnnouncementVO, ProjectActivityVO } from '@/api/types'
import { Clock, CheckCircle2, AlertTriangle, FileText } from 'lucide-vue-next'
import ProjectInfoCard from '@/components/overview/ProjectInfoCard.vue'
import StatCard from '@/components/overview/StatCard.vue'
import MilestoneCard from '@/components/overview/MilestoneCard.vue'
import AnnouncementCard from '@/components/overview/AnnouncementCard.vue'
import ActivityCard from '@/components/overview/ActivityCard.vue'
import MemberCard from '@/components/overview/MemberCard.vue'
import { PRIMARY, WARNING, SUCCESS, DANGER } from '@/theme/colorMap'

const route = useRoute()
const projectStore = useProjectStore()
const projectId = computed(() => route.params.projectId as string)

const dashboard = ref<DashboardVO | null>(null)
const members = ref<ProjectMember[]>([])
const milestones = ref<MilestoneVO[]>([])
const announcements = ref<AnnouncementVO[]>([])
const activities = ref<ProjectActivityVO[]>([])
const loading = ref(true)

const leadName = computed(() => {
  if (!projectStore.current?.leadUserId) return ''
  const m = members.value.find((x) => x.userId === projectStore.current?.leadUserId)
  return m?.displayName || ''
})

/** 概览各区块刷新入口：卡片内新建里程碑/发布公告后由子组件 emit('refresh') 触发 */
async function load() {
  // 各区块数据相互独立：单块失败降级为空，避免整页空白（接口层已统一提示错误）
  const pid = projectId.value
  const [dashRes, memRes, msRes, annRes, actRes] = await Promise.all([
    getDashboard(pid).catch(() => null),
    listProjectMembers(pid).catch(() => []),
    listMilestones(pid).catch(() => []),
    listAnnouncements(pid).catch(() => []),
    listProjectActivities(pid, 10).catch(() => [])
  ])
  dashboard.value = dashRes
  members.value = memRes as ProjectMember[]
  milestones.value = msRes as MilestoneVO[]
  announcements.value = annRes as AnnouncementVO[]
  activities.value = actRes as ProjectActivityVO[]
}

onMounted(async () => {
  loading.value = true
  try {
    await load()
  } finally {
    loading.value = false
  }
})
</script>

<template>
  <div class="h-full overflow-auto p-6">
    <div v-if="loading" class="py-20 text-center text-sm text-ink-400">加载中…</div>
    <div v-else class="space-y-4">
      <ProjectInfoCard v-if="projectStore.current" :project="projectStore.current" :lead-name="leadName" />

      <div class="grid grid-cols-2 gap-4 md:grid-cols-4">
        <StatCard label="工作项总数" :value="dashboard?.summary.total ?? 0" :icon="FileText" :color="PRIMARY" />
        <StatCard label="进行中" :value="dashboard?.summary.inProgress ?? 0" :icon="Clock" :color="WARNING" />
        <StatCard label="已完成" :value="dashboard?.summary.done ?? 0" :icon="CheckCircle2" :color="SUCCESS" />
        <StatCard label="逾期" :value="dashboard?.summary.overdue ?? 0" :icon="AlertTriangle" :color="DANGER" />
      </div>

      <div class="grid grid-cols-1 gap-4 lg:grid-cols-2">
        <MilestoneCard :milestones="milestones" :project-id="projectId" @refresh="load" />
        <AnnouncementCard :announcements="announcements" :project-id="projectId" @refresh="load" />
      </div>

      <div class="grid grid-cols-1 gap-4 lg:grid-cols-2">
        <ActivityCard :activities="activities" />
        <MemberCard :members="members" />
      </div>
    </div>
  </div>
</template>
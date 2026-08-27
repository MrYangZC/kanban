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

const route = useRoute()
const projectStore = useProjectStore()
const projectId = route.params.projectId as string

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

onMounted(async () => {
  loading.value = true
  try {
    const [dashRes, memRes, msRes, annRes, actRes] = await Promise.all([
      getDashboard(projectId).catch(() => null),
      listProjectMembers(projectId).catch(() => []),
      listMilestones(projectId).catch(() => []),
      listAnnouncements(projectId).catch(() => []),
      listProjectActivities(projectId, 10).catch(() => [])
    ])
    dashboard.value = dashRes
    members.value = memRes as ProjectMember[]
    milestones.value = msRes as MilestoneVO[]
    announcements.value = annRes as AnnouncementVO[]
    activities.value = actRes as ProjectActivityVO[]
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
        <StatCard label="工作项总数" :value="dashboard?.summary.total ?? 0" :icon="FileText" color="#1677FF" />
        <StatCard label="进行中" :value="dashboard?.summary.inProgress ?? 0" :icon="Clock" color="#FFC107" />
        <StatCard label="已完成" :value="dashboard?.summary.done ?? 0" :icon="CheckCircle2" color="#22C55E" />
        <StatCard label="逾期" :value="dashboard?.summary.overdue ?? 0" :icon="AlertTriangle" color="#DC3545" />
      </div>

      <div class="grid grid-cols-1 gap-4 lg:grid-cols-2">
        <MilestoneCard :milestones="milestones" />
        <AnnouncementCard :announcements="announcements" />
      </div>

      <div class="grid grid-cols-1 gap-4 lg:grid-cols-2">
        <ActivityCard :activities="activities" />
        <MemberCard :members="members" />
      </div>
    </div>
  </div>
</template>
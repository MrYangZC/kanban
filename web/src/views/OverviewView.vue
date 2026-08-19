<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { useRoute } from 'vue-router'
import { getDashboard, listProjectMembers, listUsers, listMilestones, listAnnouncements, listProjectActivities } from '@/api'
import { useProjectStore } from '@/stores/project'
import type { DashboardVO, ProjectMember, UserVO, MilestoneVO, AnnouncementVO, ProjectActivityVO } from '@/api/types'
import { Clock, CheckCircle2, AlertTriangle, Bug, FileText, ListTodo } from 'lucide-vue-next'
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
const users = ref<UserVO[]>([])
const milestones = ref<MilestoneVO[]>([])
const announcements = ref<AnnouncementVO[]>([])
const activities = ref<ProjectActivityVO[]>([])
const loading = ref(true)

const leadName = computed(() => {
  if (!projectStore.current?.leadUserId) return ''
  const u = users.value.find((x) => x.id === projectStore.current?.leadUserId)
  return u?.displayName || ''
})

onMounted(async () => {
  loading.value = true
  try {
    const [dashRes, memRes, userRes, msRes, annRes, actRes] = await Promise.all([
      getDashboard(projectId).catch(() => null),
      listProjectMembers(projectId).catch(() => []),
      listUsers().catch(() => []),
      listMilestones(projectId).catch(() => []),
      listAnnouncements(projectId).catch(() => []),
      listProjectActivities(projectId, 10).catch(() => [])
    ])
    dashboard.value = dashRes
    members.value = memRes as ProjectMember[]
    users.value = userRes as UserVO[]
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
        <StatCard label="进行中需求" :value="dashboard?.summary.inProgress ?? 0" :icon="FileText" color="#1677FF" />
        <StatCard label="活动缺陷" :value="dashboard?.summary.total ?? 0" :icon="Bug" color="#DC3545" />
        <StatCard label="未完成任务" :value="dashboard?.summary.inProgress ?? 0" :icon="ListTodo" color="#FFC107" />
        <StatCard label="逾期任务" :value="dashboard?.summary.overdue ?? 0" :icon="AlertTriangle" color="#DC3545" />
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
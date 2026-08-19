<script setup lang="ts">
import { Users } from 'lucide-vue-next'
import type { ProjectMember } from '@/api/types'

interface Props {
  members?: ProjectMember[]
}

const props = withDefaults(defineProps<Props>(), {
  members: () => []
})

const ROLE_LABEL: Record<string, string> = {
  OWNER: '负责人',
  ADMIN: '管理员',
  PM: '项目经理',
  DEV: '开发',
  QA: '测试',
  MEMBER: '成员',
  VIEWER: '访客'
}

const ROLE_ORDER: string[] = ['OWNER', 'ADMIN', 'PM', 'DEV', 'QA', 'MEMBER', 'VIEWER']

import { computed } from 'vue'

const groupedMembers = computed(() => {
  const groups: Record<string, ProjectMember[]> = {}
  for (const m of props.members) {
    const role = m.roles[0] || 'MEMBER'
    if (!groups[role]) groups[role] = []
    groups[role].push(m)
  }
  return ROLE_ORDER
    .filter((r) => groups[r]?.length)
    .map((r) => ({ role: r, label: ROLE_LABEL[r] || r, members: groups[r] }))
})
</script>

<template>
  <div class="rounded-lg border border-[#E9ECEF] bg-white shadow-sm">
    <div class="flex items-center justify-between border-b border-[#E9ECEF] px-4 py-3">
      <div class="flex items-center gap-2 text-sm font-medium text-ink-900">
        <Users :size="16" class="text-brand" /> 项目成员
      </div>
      <span class="text-xs text-ink-400">共 {{ props.members.length }} 人</span>
    </div>
    <div class="p-4">
      <div v-if="!groupedMembers.length" class="py-6 text-center text-sm text-ink-400">暂无成员</div>
      <div v-else class="space-y-4">
        <div v-for="g in groupedMembers" :key="g.role">
          <div class="mb-2 text-xs font-medium text-ink-400">{{ g.label }}（{{ g.members.length }}）</div>
          <div class="flex flex-wrap gap-2">
            <div
              v-for="m in g.members"
              :key="m.userId"
              class="flex items-center gap-1.5 rounded-md border border-[#E9ECEF] px-2 py-1"
              :title="m.username"
            >
              <div class="flex h-6 w-6 items-center justify-center rounded-full bg-brand text-xs font-medium text-white">
                {{ m.displayName?.charAt(0) || 'U' }}
              </div>
              <span class="text-sm text-ink-600">{{ m.displayName }}</span>
            </div>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>
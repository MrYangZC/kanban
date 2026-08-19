import { ref, type Ref } from 'vue'
import { listProjectMembers } from '@/api'
import type { ProjectMember } from '@/api/types'
import { useUserStore } from '@/stores/user'

export interface ProjectPermission {
  canWrite: Ref<boolean>
  canAdmin: Ref<boolean>
  members: Ref<ProjectMember[]>
  refresh: () => Promise<void>
}

const WRITE_ROLES = ['OWNER', 'ADMIN', 'PM', 'DEV', 'QA', 'MEMBER']
const ADMIN_ROLES = ['OWNER', 'ADMIN']

export function useProjectPermission(projectId: () => string): ProjectPermission {
  const userStore = useUserStore()
  const canWrite = ref(true)
  const canAdmin = ref(false)
  const members = ref<ProjectMember[]>([])

  async function refresh() {
    try {
      members.value = await listProjectMembers(projectId())
      const me = members.value.find((m) => m.userId === userStore.user?.id)
      canWrite.value = !me || WRITE_ROLES.some((r) => me.roles.includes(r))
      canAdmin.value = !!me && ADMIN_ROLES.some((r) => me.roles.includes(r))
    } catch {
      canWrite.value = true
      canAdmin.value = false
    }
  }

  return { canWrite, canAdmin, members, refresh }
}
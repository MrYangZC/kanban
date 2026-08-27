import { ref, computed, type Ref, type ComputedRef } from 'vue'
import { listProjectMembers, getMyPermissions } from '@/api'
import type { ProjectMember } from '@/api/types'
import { useUserStore } from '@/stores/user'

export interface ProjectPermission {
  canWrite: Ref<boolean>
  canAdmin: Ref<boolean>
  hasPermission: (code: string) => boolean
  permissions: Ref<Set<string>>
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
  const permissions = ref<Set<string>>(new Set())

  async function refresh() {
    try {
      const pid = projectId()
      const [memberList, perms] = await Promise.all([
        listProjectMembers(pid),
        getMyPermissions(pid)
      ])
      members.value = memberList
      const me = memberList.find((m) => m.userId === userStore.user?.id)
      canWrite.value = !me || WRITE_ROLES.some((r) => me.roles.includes(r))
      canAdmin.value = !!me && ADMIN_ROLES.some((r) => me.roles.includes(r))
      permissions.value = new Set(perms)
    } catch {
      canWrite.value = true
      canAdmin.value = false
      permissions.value = new Set()
    }
  }

  function hasPermission(code: string): boolean {
    return permissions.value.has(code)
  }

  return { canWrite, canAdmin, hasPermission, permissions, members, refresh }
}

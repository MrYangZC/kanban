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
  // 默认不可写：权限未加载完成或加载失败时不应放行
  const canWrite = ref(false)
  const canAdmin = ref(false)
  const members = ref<ProjectMember[]>([])
  const permissions = ref<Set<string>>(new Set())

  /**
   * 系统管理员未必是项目成员，但其权限由后端直接放行，
   * 前端需同步放行，否则会出现"能操作但按钮被隐藏"的错配。
   * 判定口径与后端 PermissionService#isSuperAdmin 对齐（登录接口下发的 isAdmin）。
   */
  const isSystemAdmin = computed(() => userStore.isAdmin)

  async function refresh() {
    try {
      const pid = projectId()
      const [memberList, perms] = await Promise.all([
        listProjectMembers(pid),
        getMyPermissions(pid)
      ])
      members.value = memberList
      const me = memberList.find((m) => m.userId === userStore.user?.id)
      const roles = me?.roles ?? []
      // 失败关闭：非项目成员且非系统管理员时不具备写权限（原实现 !me 会误放行）
      canWrite.value = isSystemAdmin.value || WRITE_ROLES.some((r) => roles.includes(r))
      canAdmin.value = isSystemAdmin.value || ADMIN_ROLES.some((r) => roles.includes(r))
      permissions.value = new Set(perms)
    } catch {
      // 接口异常/无访问权限时关闭写权限，避免默认放行
      canWrite.value = false
      canAdmin.value = false
      permissions.value = new Set()
    }
  }

  function hasPermission(code: string): boolean {
    return permissions.value.has(code)
  }

  return { canWrite, canAdmin, hasPermission, permissions, members, refresh }
}

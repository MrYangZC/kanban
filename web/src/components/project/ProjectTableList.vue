<script setup lang="ts">
/**
 * 项目列表的表格视图（el-table 版）。
 *
 * <p>原先由 ProjectTableRow.vue 渲染 `<tr>`，但 el-table 只接受列定义、无法插入行组件，
 * 所以把行内逻辑（收藏/归档/进入任务页/状态徽标）并入本组件，删除该子组件。
 */
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { toastUndo } from '@/utils/feedback'
import {
  Star, Pencil, Users, UserPlus, ListTodo, FolderKanban, ArrowRight, Trash2, Archive, ArchiveRestore
} from 'lucide-vue-next'
import type { Project } from '@/api/types'
import { useProjectCategoryStore } from '@/stores/projectCategory'
import { projectStatusBadge } from '@/constants/issueDict'
import { reportError } from '@/utils/error'

defineProps<{ projects: Project[] }>()

const emit = defineEmits<{
  (e: 'openBoard', p: Project): void
  (e: 'openMembers', p: Project): void
  (e: 'addMember', p: Project): void
  (e: 'openEdit', p: Project): void
  (e: 'delete', p: Project): void
}>()

const store = useProjectCategoryStore()
const router = useRouter()

/** el-table 插槽给的是宽松行类型，入口统一收窄 */
const asProject = (raw: unknown) => raw as Project

const statusOf = (raw: unknown) => projectStatusBadge(asProject(raw).status)

const dateOf = (raw: unknown) => {
  const createdAt = asProject(raw).createdAt
  return createdAt ? createdAt.slice(0, 10) : '—'
}

async function onFavorite(raw: unknown) {
  const p = asProject(raw)
  const wasFavorited = p.favorited || false
  try {
    await store.toggleFavorite(p.id)
    toastUndo({
      message: wasFavorited ? `已取消收藏「${p.name}」` : `已收藏「${p.name}」`,
      onUndo: async () => {
        await store.setFavorite(p.id, wasFavorited)
      }
    })
  } catch (e: unknown) {
    reportError(e, '操作失败')
  }
}

/** 与卡片视图一致：点「任务」直接进该项目的任务页 */
function goTasks(raw: unknown) {
  router.push(`/projects/${asProject(raw).id}/tasks`)
}

/** 与卡片视图一致：归档 / 取消归档 */
async function onArchive(raw: unknown) {
  const p = asProject(raw)
  const isArchived = p.status === 'ARCHIVED'
  try {
    await store.toggleArchive(p.id)
    // 归档是可逆的（再调一次即恢复），给出撤销入口；删除是不可逆的，不挂撤销
    toastUndo({
      message: isArchived ? `已取消归档「${p.name}」` : `已归档「${p.name}」`,
      undoLabel: isArchived ? '重新归档' : '撤销',
      onUndo: async () => {
        // 归档后项目已不在当前列表，必须显式指定目标态（toggle 会找不到项目而静默失败）
        await store.setArchived(p.id, isArchived)
        ElMessage.success(isArchived ? '已重新归档' : '已撤销归档')
      }
    })
  } catch (e: unknown) {
    reportError(e, '操作失败')
  }
}
</script>

<template>
  <div class="wi-table animate-fade-in-up">
    <el-table :data="projects" row-key="id" class="wi-el-table" empty-text="暂无项目">
      <el-table-column label="项目 Key" width="120">
        <template #default="{ row }">
          <div class="flex items-center gap-2">
            <div class="flex h-7 w-7 items-center justify-center rounded-md bg-primary-50 text-primary-500">
              <FolderKanban :size="14" />
            </div>
            <span class="text-xs font-semibold uppercase tracking-wider text-primary-500">{{ row.projectKey }}</span>
          </div>
        </template>
      </el-table-column>
      <el-table-column label="项目名称" min-width="160">
        <template #default="{ row }">
          <span
            class="cursor-pointer font-medium text-ink transition-colors hover:text-primary-600"
            @click="emit('openBoard', asProject(row))"
          >{{ row.name }}</span>
        </template>
      </el-table-column>
      <el-table-column label="状态" width="120">
        <template #default="{ row }">
          <span class="inline-flex items-center gap-1.5 rounded-full px-2.5 py-1 text-xs font-medium" :class="statusOf(row).cls">
            <span class="h-1.5 w-1.5 rounded-full" :class="statusOf(row).dot"></span>
            {{ statusOf(row).label }}
          </span>
        </template>
      </el-table-column>
      <el-table-column label="描述" min-width="200" show-overflow-tooltip>
        <template #default="{ row }">
          <span class="text-sm text-ink-secondary">{{ row.description || '暂无描述' }}</span>
        </template>
      </el-table-column>
      <el-table-column label="成员" width="90" align="center">
        <template #default="{ row }">
          <button
            class="inline-flex items-center gap-1 rounded-md px-2 py-1 text-sm text-ink-secondary transition hover:bg-surface-hover hover:text-primary-600"
            title="成员"
            @click.stop="emit('openMembers', asProject(row))"
          >
            <Users :size="14" />
            <span class="font-medium">{{ row.stats?.memberCount || 0 }}</span>
          </button>
        </template>
      </el-table-column>
      <el-table-column label="任务" width="90" align="center">
        <template #default="{ row }">
          <button
            class="inline-flex items-center gap-1 rounded-md px-2 py-1 text-sm text-ink-secondary transition hover:bg-surface-hover hover:text-primary-600"
            title="任务"
            @click.stop="goTasks(row)"
          >
            <ListTodo :size="14" />
            <span class="font-medium">{{ row.stats?.issueCount || 0 }}</span>
          </button>
        </template>
      </el-table-column>
      <el-table-column label="创建时间" width="120">
        <template #default="{ row }"><span class="text-sm text-ink-secondary">{{ dateOf(row) }}</span></template>
      </el-table-column>
      <el-table-column label="操作" width="240" align="right">
        <template #default="{ row }">
          <div class="flex items-center justify-end gap-1">
            <button
              class="rounded-md p-1.5 transition hover:bg-surface-hover"
              :class="row.favorited ? 'text-warning' : 'text-ink-secondary hover:text-warning'"
              title="收藏"
              @click.stop="onFavorite(row)"
            >
              <Star :size="15" :fill="row.favorited ? 'currentColor' : 'none'" />
            </button>
            <button
              class="rounded-md p-1.5 text-ink-secondary transition hover:bg-surface-hover hover:text-primary"
              :class="{ 'cursor-not-allowed opacity-50': !row.canEdit }"
              title="编辑项目"
              :disabled="!row.canEdit"
              @click.stop="emit('openEdit', asProject(row))"
            >
              <Pencil :size="15" />
            </button>
            <button
              class="rounded-md p-1.5 text-ink-secondary transition hover:bg-surface-hover hover:text-primary"
              :class="{ 'cursor-not-allowed opacity-50': !row.canManage }"
              :title="row.canManage ? '添加成员' : '仅项目所有者/管理员/项目经理可添加成员'"
              :disabled="!row.canManage"
              @click.stop="emit('addMember', asProject(row))"
            >
              <UserPlus :size="15" />
            </button>
            <button
              class="rounded-md p-1.5 text-ink-secondary transition hover:bg-surface-hover hover:text-primary"
              :class="{ 'cursor-not-allowed opacity-50': !row.canArchive }"
              :title="row.status === 'ARCHIVED' ? '取消归档' : '归档（仅所有者/管理员）'"
              :disabled="!row.canArchive"
              @click.stop="onArchive(row)"
            >
              <ArchiveRestore v-if="row.status === 'ARCHIVED'" :size="15" />
              <Archive v-else :size="15" />
            </button>
            <button
              class="rounded-md p-1.5 text-ink-secondary transition hover:bg-rose-50 hover:text-rose-500"
              :class="{ 'cursor-not-allowed opacity-50': !row.canDelete }"
              :title="row.canDelete ? '删除项目' : '仅项目所有者/管理员可删除项目'"
              :disabled="!row.canDelete"
              @click.stop="emit('delete', asProject(row))"
            >
              <Trash2 :size="15" />
            </button>
            <button
              class="ml-1 flex items-center gap-1 rounded-md px-2 py-1 text-xs font-medium text-primary-600 transition hover:bg-primary-500 hover:text-white"
              @click.stop="emit('openBoard', asProject(row))"
            >
              进入
              <ArrowRight :size="12" />
            </button>
          </div>
        </template>
      </el-table-column>
    </el-table>
  </div>
</template>

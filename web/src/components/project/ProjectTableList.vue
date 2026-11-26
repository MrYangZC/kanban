<script setup lang="ts">
/**
 * 项目列表的表格视图（el-table 版）。
 *
 * <p>整行可点进入项目（与卡片视图一致）：el-table 的 row-click 是行级事件，
 * 行内所有可交互元素（收藏/编辑/更多/成员/任务/进入）都必须 `.stop`，
 * 否则点按钮会顺带跳转一次。
 *
 * <p>列宽与行高按"信息紧凑但可呼吸"调过：行内边距 7px（约 44px 行高），
 * 固定列收窄到刚够内容，描述列吃掉剩余宽度；操作列只留收藏/编辑/更多/进入，
 * 归档与删除这类低频且不可逆的操作收进二级菜单。
 */
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { toastUndo } from '@/utils/feedback'
import {
  Star, Pencil, Users, ListTodo, ArrowRight, Trash2, Archive, ArchiveRestore, MoreHorizontal, UserPlus
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

/** 整行点击：与卡片视图的「点卡片进项目」对齐，空白处不用再去找「进入」按钮 */
function onRowClick(raw: unknown) {
  emit('openBoard', asProject(raw))
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

/**
 * 「更多」下拉里的低频操作。
 *
 * <p>归档/删除/加成员原本是三个常驻图标，和收藏、编辑挤在一行，
 * 五个图标 + 一个文字按钮让操作列比信息列还宽，而且删除这种不可逆操作
 * 和高频的"收藏"视觉权重一样。收进二级菜单后，行内只留高频项。
 */
function onCommand(cmd: string, raw: unknown) {
  const p = asProject(raw)
  if (cmd === 'members') {
    emit('addMember', p)
  } else if (cmd === 'archive') {
    void onArchive(p)
  } else if (cmd === 'delete') {
    emit('delete', p)
  }
}
</script>

<template>
  <div class="wi-table project-table animate-fade-in-up">
    <el-table
      :data="projects"
      row-key="id"
      class="wi-el-table"
      empty-text="暂无项目"
      @row-click="onRowClick"
    >
      <el-table-column label="项目 Key" width="124">
        <template #default="{ row }">
          <span class="project-key">{{ row.projectKey }}</span>
        </template>
      </el-table-column>

      <el-table-column label="项目名称" min-width="190">
        <template #default="{ row }">
          <span class="project-name" @click.stop="emit('openBoard', asProject(row))">{{ row.name }}</span>
        </template>
      </el-table-column>

      <el-table-column label="状态" width="104">
        <template #default="{ row }">
          <span class="project-badge" :class="statusOf(row).cls">
            <span class="h-1.5 w-1.5 rounded-full" :class="statusOf(row).dot"></span>
            {{ statusOf(row).label }}
          </span>
        </template>
      </el-table-column>

      <el-table-column label="描述" min-width="200" show-overflow-tooltip>
        <template #default="{ row }">
          <!-- 没有描述时用弱化的短横线：整列重复「暂无描述」会把留白变成噪音 -->
          <span v-if="row.description" class="text-sm text-ink-secondary">{{ row.description }}</span>
          <span v-else class="text-sm text-ink-placeholder">—</span>
        </template>
      </el-table-column>

      <el-table-column label="成员" width="82" align="center">
        <template #default="{ row }">
          <button class="project-metric" title="成员" @click.stop="emit('openMembers', asProject(row))">
            <Users :size="13" />
            <span>{{ row.stats?.memberCount || 0 }}</span>
          </button>
        </template>
      </el-table-column>

      <el-table-column label="任务" width="82" align="center">
        <template #default="{ row }">
          <button class="project-metric" title="任务" @click.stop="goTasks(row)">
            <ListTodo :size="13" />
            <span>{{ row.stats?.issueCount || 0 }}</span>
          </button>
        </template>
      </el-table-column>

      <el-table-column label="创建时间" width="108">
        <template #default="{ row }"><span class="project-date">{{ dateOf(row) }}</span></template>
      </el-table-column>

      <el-table-column label="操作" width="168" align="right">
        <template #default="{ row }">
          <div class="flex items-center justify-end gap-0.5">
            <button
              class="project-icon-btn"
              :class="row.favorited ? 'text-warning' : ''"
              title="收藏"
              @click.stop="onFavorite(row)"
            >
              <Star :size="15" :fill="row.favorited ? 'currentColor' : 'none'" />
            </button>
            <button
              class="project-icon-btn"
              :class="{ 'cursor-not-allowed opacity-40': !row.canEdit }"
              title="编辑项目"
              :disabled="!row.canEdit"
              @click.stop="emit('openEdit', asProject(row))"
            >
              <Pencil :size="15" />
            </button>
            <el-dropdown trigger="click" placement="bottom-end" @command="(cmd: string) => onCommand(cmd, row)">
              <button class="project-icon-btn" title="更多操作" @click.stop>
                <MoreHorizontal :size="15" />
              </button>
              <template #dropdown>
                <el-dropdown-menu>
                  <el-dropdown-item command="members" :disabled="!row.canManage">
                    <UserPlus :size="14" class="mr-1.5" />添加成员
                  </el-dropdown-item>
                  <el-dropdown-item command="archive" :disabled="!row.canArchive">
                    <ArchiveRestore v-if="row.status === 'ARCHIVED'" :size="14" class="mr-1.5" />
                    <Archive v-else :size="14" class="mr-1.5" />
                    {{ row.status === 'ARCHIVED' ? '取消归档' : '归档' }}
                  </el-dropdown-item>
                  <el-dropdown-item command="delete" :disabled="!row.canDelete" divided>
                    <Trash2 :size="14" class="mr-1.5 text-danger" />
                    <span class="text-danger">删除项目</span>
                  </el-dropdown-item>
                </el-dropdown-menu>
              </template>
            </el-dropdown>
            <button class="project-enter" @click.stop="emit('openBoard', asProject(row))">
              进入
              <ArrowRight :size="12" />
            </button>
          </div>
        </template>
      </el-table-column>
    </el-table>
  </div>
</template>

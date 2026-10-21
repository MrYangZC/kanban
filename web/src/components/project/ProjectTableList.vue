<script setup lang="ts">
import type { Project } from '@/api/types'
import ProjectTableRow from './ProjectTableRow.vue'

defineProps<{
  projects: Project[]
}>()

const emit = defineEmits<{
  (e: 'openBoard', p: Project): void
  (e: 'openMembers', p: Project): void
  (e: 'addMember', p: Project): void
  (e: 'openEdit', p: Project): void
  (e: 'delete', p: Project): void
}>()
</script>

<template>
  <div class="table-wrapper animate-fade-in-up overflow-x-auto">
    <table class="w-full text-left text-sm">
      <thead class="table-head">
        <tr>
          <th class="w-24 px-5 py-3.5">项目 Key</th>
          <th class="px-5 py-3.5">项目名称</th>
          <th class="w-28 px-5 py-3.5">状态</th>
          <th class="px-5 py-3.5">描述</th>
          <th class="w-20 px-5 py-3.5 text-center">成员</th>
          <th class="w-20 px-5 py-3.5 text-center">任务</th>
          <th class="w-28 px-5 py-3.5">创建时间</th>
          <th class="w-64 px-5 py-3.5 text-right">操作</th>
        </tr>
      </thead>
      <tbody>
        <ProjectTableRow
          v-for="p in projects"
          :key="p.id"
          :project="p"
          @open-board="emit('openBoard', $event)"
          @open-members="emit('openMembers', $event)"
          @add-member="emit('addMember', $event)"
          @open-edit="emit('openEdit', $event)"
          @delete="emit('delete', $event)"
        />
      </tbody>
    </table>
  </div>
</template>

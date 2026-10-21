<script setup lang="ts">
import WorkItemListPage from '@/components/workitem/WorkItemListPage.vue'
import { TASK_TYPE_CODES } from '@/api/types'
import { ListTodo } from 'lucide-vue-next'
</script>

<template>
  <WorkItemListPage
    noun="任务"
    :icon="ListTodo"
    :type-codes="TASK_TYPE_CODES"
    board-category="TASK"
    default-issue-type-code="TASK"
    storage-key="taskView"
  />
</template>

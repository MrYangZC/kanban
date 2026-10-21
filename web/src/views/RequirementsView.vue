<script setup lang="ts">
import WorkItemListPage from '@/components/workitem/WorkItemListPage.vue'
import { REQUIREMENT_TYPE_CODES } from '@/api/types'
import { FileText } from 'lucide-vue-next'
</script>

<template>
  <WorkItemListPage
    noun="需求"
    :icon="FileText"
    :type-codes="REQUIREMENT_TYPE_CODES"
    board-category="REQUIREMENT"
    default-issue-type-code="STORY"
    storage-key="reqView"
  />
</template>

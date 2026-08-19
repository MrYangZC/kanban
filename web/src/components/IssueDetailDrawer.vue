<script setup lang="ts">
import { ref } from 'vue'
import AppDrawer from '@/components/AppDrawer.vue'
import DefectDetailView from '@/views/DefectDetailView.vue'

const props = defineProps<{
  projectId: string
}>()

const emit = defineEmits<{
  (e: 'closed'): void
}>()

const visible = ref(false)
const issueId = ref('')

function open(id: string) {
  issueId.value = id
  visible.value = true
}

function close() {
  visible.value = false
}

defineExpose({ open, close })
</script>

<template>
  <AppDrawer v-model="visible" width="60%" @closed="emit('closed')">
    <DefectDetailView
      v-if="visible"
      embedded
      :project-id="props.projectId"
      :issue-id="issueId"
      @close="close"
      @update:issue-id="issueId = $event"
    />
  </AppDrawer>
</template>

<script setup lang="ts">
import { defineAsyncComponent, ref } from 'vue'
import AppDrawer from '@/components/AppDrawer.vue'

// 详情页是全库最大的组件（40KB+），且被看板/表格/缺陷/工作项四个列表页通过本抽屉间接引用。
// 静态 import 会把它塞进这些页面的首屏 chunk，抵消路由懒加载；抽屉本身 v-if 才渲染，改为异步组件后
// 只在真正打开详情时才加载对应 chunk。
const DefectDetailView = defineAsyncComponent(() => import('@/views/DefectDetailView.vue'))

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

<script setup lang="ts">
import { computed } from 'vue'
import type { IssueActivity } from '@/api/types'
import ActivityItem from '@/components/ActivityItem.vue'

/**
 * 评论楼层：头像 / 姓名 / 时间 / 正文统一交给 ActivityItem 渲染，
 * 这里只负责楼层缩进与递归子回复，保证评论与动态是同一种版式。
 */
type CommentNode = IssueActivity & { children?: CommentNode[] }

const props = defineProps<{
  node: CommentNode
  depth: number
  canWrite: boolean
  currentUserName: string
}>()

const emit = defineEmits<{
  (e: 'reply', node: IssueActivity): void
  (e: 'remove', id: string): void
}>()

// 深层回复只加一条左侧竖线，不再继续往右挤压正文
const indentStyle = computed(() =>
  props.depth > 0 ? { paddingLeft: '16px', borderLeft: '2px solid var(--line-light)' } : {}
)
</script>

<template>
  <ActivityItem
    :node="node"
    :can-write="canWrite"
    :current-user-name="currentUserName"
    replyable
    @reply="emit('reply', $event)"
    @remove="emit('remove', $event)"
  >
    <!-- 子回复：缩进楼层 -->
    <div v-if="node.children && node.children.length" class="mt-3 space-y-4" :style="indentStyle">
      <CommentThread
        v-for="child in node.children"
        :key="child.id"
        :node="child"
        :depth="depth + 1"
        :can-write="canWrite"
        :current-user-name="currentUserName"
        @reply="emit('reply', $event)"
        @remove="emit('remove', $event)"
      />
    </div>
  </ActivityItem>
</template>

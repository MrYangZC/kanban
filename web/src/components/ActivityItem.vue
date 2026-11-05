<script setup lang="ts">
import { computed } from 'vue'
import type { IssueActivity } from '@/api/types'
import { Trash2 } from 'lucide-vue-next'
import UserAvatar from '@/components/common/UserAvatar.vue'
import { fmtTime } from '@/utils/datetime'
import { activityText } from '@/utils/activity'
import { renderCommentContent } from '@/utils/commentContent'

/**
 * 「动态&评论」里的单条内容：评论与状态动态共用同一套布局。
 * 头像（首字彩色圆）+ 姓名 + 时间 + 操作 + 正文，评论为气泡、动态为一行灰字。
 * `CommentThread` 在此基础上叠加楼层缩进与递归子回复，两者骨架保持一致。
 */
const props = withDefaults(defineProps<{
  node: IssueActivity
  /** 有写权限时才显示「回复 / 删除」 */
  canWrite?: boolean
  /** 当前登录用户显示名，用于判断「是不是我发的评论」 */
  currentUserName?: string
  /** 是否允许回复（仅评论楼层开启，平铺列表不需要） */
  replyable?: boolean
}>(), {
  canWrite: false,
  currentUserName: '',
  replyable: false
})

const emit = defineEmits<{
  (e: 'reply', node: IssueActivity): void
  (e: 'remove', id: string): void
  (e: 'revoke', node: IssueActivity): void
}>()

const isComment = computed(() => props.node.type === 'COMMENT')
const isDeleted = computed(() => props.node.deleted === 1)
const isMine = computed(() => !!props.currentUserName && props.node.actorName === props.currentUserName)
const canRemove = computed(() => props.canWrite && isComment.value && isMine.value && !isDeleted.value)
const canReply = computed(() => props.replyable && props.canWrite && isComment.value && !isDeleted.value)
/** 撤销交接：可撤销性由后端判定（最后一条交接 + 接手人仍是当前负责人），这里只做权限把关 */
const canRevoke = computed(() => props.canWrite && !!props.node.revocable)
const bodyHtml = computed(() => renderCommentContent(props.node.content))
</script>

<template>
  <div class="flex gap-3">
    <!-- 头像：有上传图显示图片，否则首字彩色圆；评论与动态同一规格 -->
    <UserAvatar
      :name="node.actorName"
      :src="node.actorAvatarUrl"
      :size="36"
      :class="isDeleted ? 'opacity-40' : ''"
    />

    <div class="min-w-0 flex-1">
      <div class="mb-1.5 flex items-center gap-2 text-xs">
        <span class="font-medium text-ink" :class="isDeleted ? 'opacity-50' : ''">{{ node.actorName }}</span>
        <span class="text-ink-placeholder">{{ fmtTime(node.createdAt) }}</span>
        <div v-if="canReply || canRemove || canRevoke" class="ml-auto flex items-center gap-3">
          <button v-if="canRevoke" class="text-primary hover:underline" @click="emit('revoke', node)">撤销交接</button>
          <button v-if="canReply" class="text-primary hover:underline" @click="emit('reply', node)">回复</button>
          <button
            v-if="canRemove"
            class="text-ink-placeholder hover:text-danger"
            @click="emit('remove', node.id)"
          ><Trash2 :size="13" /></button>
        </div>
      </div>

      <!-- 已删除占位 -->
      <div v-if="isDeleted" class="rounded-lg bg-surface-page p-3 text-sm italic text-ink-placeholder">
        该评论已删除
      </div>
      <!-- 评论正文（支持 markdown 图片与白名单 <img>） -->
      <div
        v-else-if="isComment"
        class="comment-content rounded-lg bg-surface-page p-3 text-sm whitespace-pre-wrap text-ink-regular"
        v-html="bodyHtml"
      ></div>
      <!-- 动态正文：将 状态 从 A 置为 B / 创建了工作项 -->
      <div v-else class="text-sm text-ink-secondary">{{ activityText(node) }}</div>

      <!-- 子回复楼层 -->
      <slot />
    </div>
  </div>
</template>

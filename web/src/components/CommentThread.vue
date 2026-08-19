<script setup lang="ts">
import { computed } from 'vue'
import type { IssueActivity } from '@/api/types'
import { Trash2 } from 'lucide-vue-next'
import { avatarChar, avatarColor } from '@/utils/avatar'
import { fmtTime } from '@/utils/datetime'

const props = defineProps<{
  node: IssueActivity & { children?: CommentNode[] }
  depth: number
  canWrite: boolean
  currentUserName: string
}>()
type CommentNode = IssueActivity & { children?: CommentNode[] }

const emit = defineEmits<{
  (e: 'reply', node: IssueActivity): void
  (e: 'remove', id: string): void
}>()

const isDeleted = computed(() => props.node.deleted === 1)
// 超过 4 层不再继续缩进，避免过深
const indentStyle = computed(() =>
  props.depth > 0 ? { marginLeft: '0', paddingLeft: '16px', borderLeft: '2px solid #ebeef5' } : {}
)

// 将评论中的 ![alt](url) 图片语法转为安全 <img>，其余文本原样展示（不渲染任意 HTML）
function escapeHtml(s: string): string {
  return s.replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;')
}
function renderContent(content: string | null): string {
  if (!content) return ''
  const imgRe = /!\[([^\]]*)\]\(([^)\s]+)\)/g
  let out = ''
  let last = 0
  let m: RegExpExecArray | null
  while ((m = imgRe.exec(content))) {
    out += escapeHtml(content.slice(last, m.index))
    // 仅允许 http(s) 与 /api/ 开头的图片地址，避免注入
    const url = m[2]
    if (/^https?:\/\//.test(url) || url.startsWith('/api/')) {
      out += `<img src="${url.replace(/"/g, '%22')}" alt="${m[1].replace(/"/g, '')}" style="max-width:100%;border-radius:6px;margin:4px 0" />`
    } else {
      out += escapeHtml(m[0])
    }
    last = imgRe.lastIndex
  }
  out += escapeHtml(content.slice(last))
  return out
}

</script>

<template>
  <div class="flex gap-3">
    <!-- 头像（已删除评论弱化） -->
    <div
      class="flex h-9 w-9 shrink-0 items-center justify-center rounded-full text-sm font-medium text-white"
      :class="isDeleted ? 'opacity-40' : ''"
      :style="{ background: avatarColor(node.actorName) }"
    >{{ avatarChar(node.actorName) }}</div>

    <div class="min-w-0 flex-1">
      <div class="mb-1.5 flex items-center gap-2 text-xs">
        <span class="font-medium" :class="isDeleted ? 'opacity-50' : ''" style="color:#303133">{{ node.actorName }}</span>
        <span style="color:#c0c4cc">{{ fmtTime(node.createdAt) }}</span>
        <div class="ml-auto flex items-center gap-3" v-if="!isDeleted">
          <button v-if="canWrite" class="hover:underline" style="color:#409EFF" @click="emit('reply', node)">回复</button>
          <button
            v-if="canWrite && node.actorName === currentUserName"
            class="hover:text-[#F56C6C]"
            style="color:#c0c4cc"
            @click="emit('remove', node.id)"
          ><Trash2 :size="13" /></button>
        </div>
      </div>

      <!-- 已删除占位 -->
      <div v-if="isDeleted" class="rounded-lg p-3 text-sm italic" style="background:#f5f7fa;color:#c0c4cc">
        该评论已删除
      </div>

      <!-- 正常评论正文（支持 ![alt](url) 图片语法渲染真实图片） -->
      <div v-else class="comment-content rounded-lg p-3 text-sm whitespace-pre-wrap" style="background:#f5f7fa;color:#606266" v-html="renderContent(node.content)"></div>

      <!-- 递归渲染子回复（缩进楼层） -->
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
    </div>
  </div>
</template>

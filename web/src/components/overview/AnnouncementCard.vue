<script setup lang="ts">
import { ref } from 'vue'
import { Megaphone, Plus } from 'lucide-vue-next'
import { ElMessage } from 'element-plus'
import type { AnnouncementVO } from '@/api/types'
import { createAnnouncement } from '@/api'
import { errorMessage } from '@/utils/error'

interface Props {
  announcements?: AnnouncementVO[]
  /** 所属项目：概览页在其卡片内直接发布公告 */
  projectId?: string
}

const props = withDefaults(defineProps<Props>(), {
  announcements: () => [],
  projectId: ''
})

const emit = defineEmits<{ (e: 'refresh'): void }>()

const dialogVisible = ref(false)
const submitting = ref(false)
const form = ref({ title: '', content: '' })

function onAdd() {
  if (!props.projectId) return
  form.value = { title: '', content: '' }
  dialogVisible.value = true
}

async function submit() {
  if (!form.value.title.trim()) {
    ElMessage.warning('请输入公告标题')
    return
  }
  submitting.value = true
  try {
    await createAnnouncement(props.projectId, { ...form.value, pinned: 0 })
    ElMessage.success('已发布')
    dialogVisible.value = false
    emit('refresh')
  } catch (e: unknown) {
    ElMessage.error(errorMessage(e, '发布失败'))
  } finally {
    submitting.value = false
  }
}
</script>

<template>
  <div class="glass-panel rounded-lg">
    <div class="flex items-center justify-between border-b border-line-light px-4 py-3">
      <div class="flex items-center gap-2 text-sm font-medium text-ink-900">
        <Megaphone :size="16" class="text-brand" /> 公告
      </div>
      <button class="flex items-center gap-1 text-xs text-brand transition hover:opacity-80" @click="onAdd">
        <Plus :size="12" /> 发布
      </button>
    </div>
    <div class="p-4">
      <div v-if="!props.announcements.length" class="py-6 text-center text-sm text-ink-400">暂无公告</div>
      <div v-else class="space-y-3">
        <div v-for="a in props.announcements" :key="a.id" class="border-b border-line-light pb-3 last:border-0 last:pb-0">
          <div class="flex items-center justify-between">
            <span class="text-sm font-medium text-ink-900">{{ a.title }}</span>
            <span class="text-xs text-ink-400">{{ a.createdAt?.slice(0, 10) }}</span>
          </div>
          <p class="mt-1 line-clamp-2 text-sm text-ink-600">{{ a.content }}</p>
          <div class="mt-1 text-xs text-ink-400">发布人：{{ a.authorName || '—' }}</div>
        </div>
      </div>
    </div>

    <el-dialog v-model="dialogVisible" title="发布公告" width="520px">
      <el-form :model="form" label-width="80px">
        <el-form-item label="标题" required>
          <el-input v-model="form.title" placeholder="请输入公告标题" />
        </el-form-item>
        <el-form-item label="正文">
          <el-input v-model="form.content" type="textarea" :rows="4" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="submit">确定</el-button>
      </template>
    </el-dialog>
  </div>
</template>

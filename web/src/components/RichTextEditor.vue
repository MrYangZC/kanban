<script setup lang="ts">
import { ref, watch, nextTick } from 'vue'
import { ElMessage } from 'element-plus'
import {
  Bold, Italic, List, ListOrdered, Link2, Quote, Smile,
  Image as ImageIcon, Paperclip
} from 'lucide-vue-next'
import { uploadImage } from '@/api'

const props = withDefaults(defineProps<{
  modelValue: string
  placeholder?: string
  rows?: number
  disabled?: boolean
}>(), {
  placeholder: '支持 Markdown，Ctrl+V 直接粘贴截图',
  rows: 4,
  disabled: false,
})

const emit = defineEmits<{ 'update:modelValue': [val: string] }>()

const textareaRef = ref<HTMLTextAreaElement | null>(null)
const imageInputRef = ref<HTMLInputElement | null>(null)
const attachInputRef = ref<HTMLInputElement | null>(null)
const imageUploading = ref(false)

// 已上传图片预览（textarea 仅显示 markdown 文本，预览区展示真实缩略图）
interface UploadedImage { url: string; name: string }
const images = ref<UploadedImage[]>([])

function setText(v: string) {
  emit('update:modelValue', v)
}
function getText(): string {
  return props.modelValue
}

function wrapSelection(prefix: string, suffix: string, placeholder = '') {
  const ta = textareaRef.value
  if (!ta) return
  const start = ta.selectionStart
  const end = ta.selectionEnd
  const before = getText().slice(0, start)
  const selected = getText().slice(start, end) || placeholder
  const after = getText().slice(end)
  setText(before + prefix + selected + suffix)
  nextTick(() => {
    ta.focus()
    const cursor = before.length + prefix.length + selected.length
    ta.setSelectionRange(cursor, cursor)
  })
}
function insertAtCursor(text: string, cursorOffset?: number) {
  const ta = textareaRef.value
  if (!ta) return
  const start = ta.selectionStart
  const end = ta.selectionEnd
  const before = getText().slice(0, start)
  const after = getText().slice(end)
  setText(before + text + after)
  nextTick(() => {
    ta.focus()
    const pos = before.length + (cursorOffset ?? text.length)
    ta.setSelectionRange(pos, pos)
  })
}
function insertAtLineStart(prefix: string) {
  const ta = textareaRef.value
  if (!ta) return
  const start = ta.selectionStart
  const before = getText().slice(0, start)
  const lineStart = before.lastIndexOf('\n') + 1
  setText(getText().slice(0, lineStart) + prefix + getText().slice(lineStart))
  nextTick(() => {
    ta.focus()
    const pos = start + prefix.length
    ta.setSelectionRange(pos, pos)
  })
}

// ---- 图片上传（按钮 / Ctrl+V 粘贴） ----
async function uploadAndInsert(file: File) {
  if (!file.type.startsWith('image/')) {
    ElMessage.warning('仅支持图片文件')
    return
  }
  imageUploading.value = true
  try {
    const res = await uploadImage(file)
    insertAtCursor(`\n![${file.name}](${res.url})\n`)
    images.value.push({ url: res.url, name: file.name })
  } catch {
    ElMessage.error('图片上传失败')
  } finally {
    imageUploading.value = false
  }
}
function onPickImage(e: Event) {
  const input = e.target as HTMLInputElement
  const file = input.files?.[0]
  if (file) uploadAndInsert(file)
  input.value = ''
}
async function onPaste(e: ClipboardEvent) {
  const items = e.clipboardData?.items
  if (!items) return
  for (const it of items) {
    if (it.kind === 'file' && it.type.startsWith('image/')) {
      const file = it.getAsFile()
      if (file) {
        e.preventDefault()
        await uploadAndInsert(file)
        return
      }
    }
  }
}
// 移除预览：同步删除文本中对应 markdown
function removeImage(idx: number) {
  const img = images.value[idx]
  if (!img) return
  setText(getText().split(`![${img.name}](${img.url})`).join('').replace(/\n{3,}/g, '\n\n'))
  images.value.splice(idx, 1)
}

const toolbar = [
  { tip: '粗体', icon: Bold, fn: () => wrapSelection('**', '**', '粗体文字') },
  { tip: '斜体', icon: Italic, fn: () => wrapSelection('*', '*', '斜体文字') },
  { tip: '无序列表', icon: List, fn: () => insertAtLineStart('- ') },
  { tip: '有序列表', icon: ListOrdered, fn: () => insertAtLineStart('1. ') },
  { tip: '链接', icon: Link2, fn: () => insertAtCursor('[文字](https://)', 1) },
  { tip: '引用', icon: Quote, fn: () => insertAtLineStart('> ') },
  { tip: '图片', icon: ImageIcon, fn: () => imageInputRef.value?.click() },
  { tip: '表情', icon: Smile, fn: () => insertAtCursor('😊') },
]

// 父组件发送/清空后，文本变空则同步清空预览
watch(() => props.modelValue, (v) => { if (!v) images.value = [] })

// 暴露聚焦方法，供父组件在「回复」时聚焦
function focus() {
  nextTick(() => textareaRef.value?.focus())
}
// 供父组件（如附件）向编辑区插入文本
function insert(text: string) {
  insertAtCursor(text)
}
defineExpose({ focus, insert })
</script>

<template>
  <div class="overflow-hidden rounded border bg-white" style="border-color:#e4e7ed">
    <!-- 工具栏 -->
    <div
      class="flex items-center gap-1 border-b px-1.5 py-1"
      style="border-color:#e4e7ed;background:#fafbfc"
    >
      <button
        v-for="t in toolbar"
        :key="t.tip"
        :title="t.tip"
        type="button"
        :disabled="disabled"
        class="flex h-7 w-7 items-center justify-center rounded text-gray-500 hover:bg-gray-100 hover:text-gray-700 disabled:opacity-40"
        @click="t.fn"
      >
        <component :is="t.icon" :size="14" />
      </button>
      <span class="mx-1 text-gray-300">|</span>
      <span class="px-1 text-xs" style="color:#909399">Markdown</span>
    </div>

    <textarea
      ref="textareaRef"
      :value="modelValue"
      :rows="rows"
      :disabled="disabled"
      :placeholder="imageUploading ? '图片上传中…' : placeholder"
      class="block w-full resize-none border-none px-3 py-2 text-sm leading-6 outline-none"
      style="color:#303133;background:transparent"
      @input="setText(($event.target as HTMLTextAreaElement).value)"
      @paste="onPaste"
    />

    <!-- 已上传图片预览（textarea 只能显示纯文本，预览区以缩略图形式回显） -->
    <div
      v-if="images.length"
      class="flex flex-wrap gap-2 rounded-md p-2"
      style="background:#fafafa;border-top:1px solid #ebeef5"
    >
      <div
        v-for="(img, idx) in images"
        :key="img.url"
        class="group relative h-20 w-20 overflow-hidden rounded"
        style="border:1px solid #e4e7ed"
      >
        <img :src="img.url" :alt="img.name" class="h-full w-full object-cover" />
        <button
          type="button"
          class="absolute right-0.5 top-0.5 hidden h-5 w-5 items-center justify-center rounded-full text-xs text-white group-hover:flex"
          style="background:rgba(0,0,0,0.55)"
          title="移除"
          @click="removeImage(idx)"
        >×</button>
      </div>
    </div>

    <!-- 隐藏 input -->
    <input ref="imageInputRef" type="file" accept="image/*" class="hidden" @change="onPickImage" />
  </div>
</template>

<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { avatarChar, avatarColor } from '@/utils/avatar'

/**
 * 全站统一头像：有 avatarUrl 就显示图片，没有或加载失败回退首字彩色圆。
 *
 * 之前每个调用点各自画「首字 + 背景色」，用户上传头像后这些位置都不会变，
 * 只能靠逐个文件改；收敛到这里后，新增头像位只要传 `name` + `src`。
 */
const props = withDefaults(defineProps<{
  /** 用于首字与取色，通常是 displayName */
  name?: string | null
  /** 头像图片地址（/api/uploads/xxx）；空则用首字 */
  src?: string | null
  /** 尺寸：预设档位或直接给像素 */
  size?: 'xs' | 'sm' | 'md' | 'lg' | 'xl' | number
  /** 无图时的兜底字符，默认取 name 首字 */
  fallback?: string
}>(), {
  name: '',
  src: '',
  size: 'sm',
  fallback: ''
})

const broken = ref(false)
// 换人/换图后要重新尝试加载，否则上一张 404 会让新地址一直显示首字
watch(() => props.src, () => { broken.value = false })

const PX: Record<string, number> = { xs: 24, sm: 32, md: 36, lg: 60, xl: 80 }
const px = computed(() => typeof props.size === 'number' ? props.size : (PX[props.size] || 32))

// 库里存在把学号/工号误写进 avatar_url 的历史脏数据（如 20201001），
// 这种值当图片地址会平白发一次 404，先按协议/绝对路径筛一遍再交给 <img>
const validSrc = computed(() => /^(\/|https?:\/\/|data:image\/)/.test((props.src || '').trim()))
const showImage = computed(() => validSrc.value && !broken.value)
const text = computed(() => props.fallback || avatarChar(props.name))
const boxStyle = computed(() => ({ width: `${px.value}px`, height: `${px.value}px` }))
const fallbackStyle = computed(() => ({
  ...boxStyle.value,
  background: avatarColor(props.name),
  fontSize: `${Math.max(11, Math.round(px.value * 0.4))}px`
}))
</script>

<template>
  <span v-if="showImage" class="inline-flex shrink-0 overflow-hidden rounded-full" :style="boxStyle">
    <img
      :src="src!"
      :alt="name || ''"
      class="h-full w-full object-cover"
      loading="lazy"
      @error="broken = true"
    />
  </span>
  <span
    v-else
    class="inline-flex shrink-0 items-center justify-center rounded-full font-medium text-white"
    :style="fallbackStyle"
  >{{ text }}</span>
</template>

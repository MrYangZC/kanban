<script setup lang="ts">
import { useRouter } from 'vue-router'
import { Compass } from 'lucide-vue-next'

const router = useRouter()
</script>

<template>
  <div class="app-canvas flex h-full flex-col items-center justify-center gap-3 px-6 text-center">
    <Compass :size="44" class="text-ink-placeholder" />
    <h1 class="text-xl font-semibold text-ink">页面不存在</h1>
    <p class="text-sm text-ink-secondary">该地址可能已被移动或删除，请检查链接是否正确。</p>
    <div class="mt-2 flex items-center gap-3">
      <button class="btn-create" @click="router.replace('/projects')">返回项目列表</button>
      <button class="btn-ghost" @click="router.back()">返回上一页</button>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { useRoute, RouterView } from 'vue-router'
import zhCn from 'element-plus/es/locale/lang/zh-cn'
import AppHeader from '@/components/AppHeader.vue'

const route = useRoute()
const isLogin = computed(() => route.path === '/login')
</script>

<template>
  <!-- 按需引入模式下没有 app.use(ElementPlus) 的全局配置，语言包与层级基线由 ConfigProvider 提供 -->
  <el-config-provider :locale="zhCn" :z-index="4000">
    <div class="flex h-full flex-col">
      <AppHeader v-if="!isLogin" />
      <div class="flex-1 overflow-hidden">
        <RouterView />
      </div>
    </div>
  </el-config-provider>
</template>

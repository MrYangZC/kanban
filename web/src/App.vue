<script setup lang="ts">
import { computed } from 'vue'
import { useRoute, RouterView } from 'vue-router'
import AppHeader from '@/components/AppHeader.vue'

const route = useRoute()
const isLogin = computed(() => route.path === '/login')
</script>

<template>
  <div class="flex h-full flex-col">
    <AppHeader v-if="!isLogin" />
    <div class="flex-1 overflow-hidden">
      <RouterView />
    </div>
  </div>
</template>

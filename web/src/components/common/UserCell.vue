<script setup lang="ts">
import UserAvatar from '@/components/common/UserAvatar.vue'

withDefaults(defineProps<{
  avatarName?: string | null
  /** 人员头像地址，为空时回退首字头像 */
  avatarUrl?: string | null
  displayName?: string | null
}>(), {
  avatarName: '',
  avatarUrl: '',
  displayName: '',
})
</script>

<template>
  <div class="cell-user">
    <UserAvatar :name="avatarName" :src="avatarUrl" :size="24" />
    <span class="cell-text">{{ displayName || '—' }}</span>
  </div>
</template>

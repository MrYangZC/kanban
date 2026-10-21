<script setup lang="ts">
withDefaults(defineProps<{
  colspan: number
  text?: string
}>(), {
  text: '暂无数据',
})
</script>

<template>
  <tr>
    <td :colspan="colspan" class="empty-row">{{ text }}</td>
  </tr>
</template>

<script setup lang="ts">
export interface TableColumn {
  key: string
  label: string
  width?: string
  align?: 'left' | 'right' | 'center'
  class?: string
}

defineProps<{
  columns: TableColumn[]
}>()
</script>

<template>
  <table class="data-table">
    <thead class="data-thead">
      <tr>
        <th
          v-for="(col, i) in columns"
          :key="col.key"
          class="data-th"
          :class="[
            i === 0 ? 'data-th-first' : '',
            col.align === 'right' ? 'text-right' : '',
            col.align === 'center' ? 'text-center' : '',
            col.class || '',
          ]"
          :style="col.width ? { width: col.width } : undefined"
        >
          {{ col.label }}
        </th>
      </tr>
    </thead>
    <tbody>
      <slot />
    </tbody>
  </table>
</template>

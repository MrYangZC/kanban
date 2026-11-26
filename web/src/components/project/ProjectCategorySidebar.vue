<script setup lang="ts">
import { useProjectCategoryStore } from '@/stores/projectCategory'
import { storeToRefs } from 'pinia'
import { FolderKanban, ShieldCheck, Users, Star, Archive } from 'lucide-vue-next'
import { computed } from 'vue'
import type { Component } from 'vue'
import type { ProjectCategoryKey } from '@/api/types'
import SideNavList, { type SideNavItem } from '@/components/common/SideNavList.vue'

const store = useProjectCategoryStore()
const { categories, selectedCategory } = storeToRefs(store)

const iconMap: Record<ProjectCategoryKey, Component> = {
  all: FolderKanban,
  managed: ShieldCheck,
  participated: Users,
  favorited: Star,
  archived: Archive
}

const items = computed<SideNavItem[]>(() =>
  categories.value.map((c) => ({
    key: c.key,
    label: c.label,
    icon: iconMap[c.key],
    count: c.count
  }))
)

function onSelect(key: string) {
  store.selectCategory(key as ProjectCategoryKey)
}
</script>

<template>
  <SideNavList :items="items" :active="selectedCategory" @select="onSelect" />
</template>

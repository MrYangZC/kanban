<script setup lang="ts">
import { computed, watch, onBeforeUnmount } from 'vue'

const props = withDefaults(defineProps<{
  modelValue: boolean
  width?: string | number
  zIndex?: number
}>(), {
  width: '60%',
  zIndex: 3000,
})

const emit = defineEmits<{
  (e: 'update:modelValue', v: boolean): void
  (e: 'closed'): void
}>()

const panelWidth = computed(() =>
  typeof props.width === 'number' ? `${props.width}px` : props.width
)

function close() {
  emit('update:modelValue', false)
}

function onAfterLeave() {
  document.body.style.overflow = ''
  emit('closed')
}

function onKeydown(e: KeyboardEvent) {
  if (e.key === 'Escape' && props.modelValue) close()
}

watch(() => props.modelValue, (v) => {
  if (v) {
    document.body.style.overflow = 'hidden'
    window.addEventListener('keydown', onKeydown)
  } else {
    window.removeEventListener('keydown', onKeydown)
  }
})

onBeforeUnmount(() => {
  window.removeEventListener('keydown', onKeydown)
  document.body.style.overflow = ''
})
</script>

<template>
  <Teleport to="body">
    <Transition name="app-drawer" @after-leave="onAfterLeave">
      <div
        v-if="modelValue"
        class="app-drawer-overlay"
        :style="{ zIndex: zIndex }"
        @click.self="close"
      >
        <div class="app-drawer-panel" :style="{ width: panelWidth }">
          <slot />
        </div>
      </div>
    </Transition>
  </Teleport>
</template>

<style scoped>
.app-drawer-overlay {
  position: fixed;
  inset: 0;
  background: rgba(0, 0, 0, 0.35);
  display: flex;
  justify-content: flex-end;
}
.app-drawer-panel {
  height: 100%;
  background: var(--surface-page);
  display: flex;
  flex-direction: column;
  overflow: hidden;
}
.app-drawer-enter-active,
.app-drawer-leave-active {
  transition: opacity 0.22s ease;
}
.app-drawer-enter-active .app-drawer-panel,
.app-drawer-leave-active .app-drawer-panel {
  transition: transform 0.22s ease;
}
.app-drawer-enter-from,
.app-drawer-leave-to {
  opacity: 0;
}
.app-drawer-enter-from .app-drawer-panel,
.app-drawer-leave-to .app-drawer-panel {
  transform: translateX(100%);
}
@media (max-width: 768px) {
  .app-drawer-panel {
    width: 100% !important;
  }
}
</style>

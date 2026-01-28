<script setup lang="ts">
/**
 * @file DevTooltip.vue
 *
 * @description
 * 一个非交互式的、在鼠标旁出现并自动消失的提示框。
 * 用于显示“开发中”等信息，而不会阻塞 UI。
 *
 * @usage
 * 该组件旨在通过一个 Composable (例如 `useDevTooltip`) 进行全局控制。
 * 它应该在根组件 `App.vue` 中只放置一次。
 *
 * @props
 * - visible: boolean - 控制提示框的可见性。
 * - message: string - 要显示的文本内容。
 * - x: number - 水平位置 (通常是 `event.clientX`)。
 * - y: number - 垂直位置 (通常是 `event.clientY`)。
 *
 * @provides
 * - 一个跟随光标的小型、主题化提示框。
 * - 自动的淡入淡出动画。
 *
 * @api
 * - 无。这是一个纯展示性组件。
 *
 * @resources
 * - **主题变量**: 使用全局 CSS 变量进行样式设置 (`--background-color`, `--glow-color` 等)。
 *
 * @potential_issues
 * - 无。
 */
import { computed } from 'vue'

const props = defineProps<{
  visible: boolean
  message: string
  x: number
  y: number
}>()

const style = computed(() => ({
  left: `${props.x}px`,
  top: `${props.y}px`,
}))
</script>

<template>
  <Transition name="tooltip">
    <div v-if="visible" class="dev-tooltip" :style="style">
      {{ message }}
    </div>
  </Transition>
</template>

<style scoped>
.dev-tooltip {
  position: fixed;
  z-index: 2000; /* Ensure it's on top of everything */
  background-color: var(--background-color);
  color: var(--text-color-hover);
  padding: 0.5rem 1rem;
  border: 1px solid var(--glow-color-translucent);
  box-shadow: 0 0 10px var(--glow-color-translucent);
  font-family: 'Orbitron', sans-serif;
  font-size: 0.9rem;
  pointer-events: none; /* Prevent the tooltip from blocking mouse events */
  transform: translate(15px, 15px); /* Offset from the cursor */
  white-space: nowrap;
}

/* --- Transitions --- */
.tooltip-enter-active,
.tooltip-leave-active {
  transition: opacity 0.2s ease, transform 0.2s ease;
}

.tooltip-enter-from,
.tooltip-leave-to {
  opacity: 0;
  transform: translate(15px, 15px) scale(0.9);
}
</style>

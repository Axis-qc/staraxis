<script setup lang="ts">
/**
 * @file InGameSelectionRect.vue
 *
 * @description
 * RTS 左键框选的可视化矩形框（纯 UI overlay）。
 *
 * 说明：
 * - 仅负责显示选择框，不参与选择逻辑。
 * - 坐标使用 client 坐标体系（固定定位），适用于覆盖全屏画布。
 *
 * @usage
 * - 父组件传入 `rect`（null 表示不显示）。
 */

import type { ScreenRect } from '../selection/useRtsSelection'

defineProps<{
  rect: ScreenRect | null
}>()
</script>

<template>
  <div
    v-if="rect"
    class="selection-rect"
    :style="{ left: rect.x + 'px', top: rect.y + 'px', width: rect.w + 'px', height: rect.h + 'px' }"
  ></div>
</template>

<style scoped>
.selection-rect {
  position: fixed;
  z-index: 45;
  pointer-events: none;
  border: 1px solid color-mix(in srgb, var(--glow-color) 75%, transparent);
  background: color-mix(in srgb, var(--glow-color) 18%, transparent);
  box-shadow: 0 0 12px color-mix(in srgb, var(--glow-color) 22%, transparent);
}
</style>

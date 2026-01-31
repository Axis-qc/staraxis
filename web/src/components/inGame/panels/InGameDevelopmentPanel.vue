<script setup lang="ts">
/**
 * @file InGameDevelopmentPanel.vue
 *
 * @description
 * 游戏内“开发”面板（占位 + 未来扩展为太空建筑/开发类功能入口）。
 *
 * 说明：
 * - 当前将原底部建造按钮迁移到此面板内，作为开发类功能的占位入口。
 * - 该组件仅负责 UI 展示与交互事件抛出，不直接修改权威模拟。
 *
 * @usage
 * - 父组件可监听 `build` 事件，获取 `buildId` 并转为后端 Command。
 *
 * @provides
 * - 开发类建造入口（采矿站/科研站/造船厂等占位）。
 */

const emit = defineEmits<{
  (e: 'build', buildId: string): void
}>()

function onClick(buildId: string) {
  emit('build', buildId)
}
</script>

<template>
  <div class="panel-root" role="complementary" aria-label="Development Panel">
    <div class="panel-header">
      <div class="panel-title">开发</div>
    </div>

    <div class="panel-body">
      <div class="build-strip">
        <button class="build-item" type="button" @click="onClick('miningStation')">采矿站</button>
        <button class="build-item" type="button" @click="onClick('researchStation')">科研站</button>
        <button class="build-item" type="button" @click="onClick('shipyard')">造船厂</button>
        <button class="build-item" type="button" @click="onClick('defensePlatform')">防御平台</button>
        <button class="build-item" type="button" @click="onClick('habitat')">居住舱</button>
        <button class="build-item" type="button" @click="onClick('logisticsHub')">物流中心</button>
      </div>
    </div>
  </div>
</template>

<style scoped>
.panel-root {
  position: absolute;
  left: 50%;
  transform: translateX(-50%);
  bottom: 72px;
  width: max-content;
  height: auto;
  min-width: 100px;
  min-height: 100px;
  max-width: calc(100vw - 24px);
  max-height: calc(100vh - 72px - 12px);
  z-index: 20;
  pointer-events: auto;

  overflow: hidden;
  border-radius: 14px;
  background: color-mix(in srgb, var(--background-color) 65%, rgba(0, 0, 0, 0.35));
  border: 1px solid color-mix(in srgb, var(--glow-color) 25%, transparent);
  backdrop-filter: blur(12px);
  -webkit-backdrop-filter: blur(12px);
  box-shadow: 0 0 18px color-mix(in srgb, var(--glow-color) 18%, transparent);
}

.panel-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 10px 12px;
  border-bottom: 1px solid color-mix(in srgb, var(--glow-color) 18%, transparent);
}

.panel-title {
  font-size: 12px;
  letter-spacing: 1px;
  color: var(--text-color-hover);
  text-shadow: 0 0 6px color-mix(in srgb, var(--glow-color) 35%, transparent);
}

.panel-body {
  padding: 10px 12px;
  height: calc(100% - 40px);
}

.build-strip {
  display: flex;
  gap: 10px;
  overflow-x: auto;
  overflow-y: hidden;
  height: 100%;
  align-items: flex-start;
  scrollbar-width: thin;
}

.build-item {
  flex: 0 0 auto;
  min-width: 96px;
  height: 48px;
  padding: 0 10px;
  border-radius: 12px;
  background: rgba(0, 0, 0, 0.18);
  border: 1px solid color-mix(in srgb, var(--glow-color) 28%, transparent);
  color: var(--text-color);
  font-family: 'Orbitron', sans-serif;
  font-size: 12px;
  cursor: pointer;
  transition: transform 0.12s ease, border-color 0.12s ease, color 0.12s ease;
}

.build-item:hover {
  transform: translateY(-1px);
  border-color: color-mix(in srgb, var(--glow-color) 58%, transparent);
  color: var(--text-color-hover);
}
</style>

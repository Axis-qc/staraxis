<script setup lang="ts">
/**
 * @file InGameDomesticPanel.vue
 *
 * @description
 * 游戏内“内政”面板（内政管理界面占位）。
 *
 * 说明：
 * - 当前仅提供占位 UI，用于后续接入人口/生产/经济/政策等内政系统。
 * - 该组件不包含权威模拟逻辑。
 *
 * @usage
 * - 父组件可通过 `@developing` 监听点击占位按钮，并弹出“开发中”提示。
 *
 * @provides
 * - 内政管理入口占位。
 */

const emit = defineEmits<{
  (e: 'developing'): void
}>()
</script>

<template>
  <div class="panel-root" role="complementary" aria-label="Domestic Panel">
    <div class="panel-header">
      <div class="panel-title">内政</div>
    </div>

    <div class="panel-body">
      <button class="placeholder" type="button" @click="emit('developing')">内政管理（开发中）</button>
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
  display: flex;
  align-items: center;
  justify-content: center;
}

.placeholder {
  height: 42px;
  padding: 0 14px;
  border-radius: 12px;
  background: rgba(0, 0, 0, 0.18);
  border: 1px solid color-mix(in srgb, var(--glow-color) 28%, transparent);
  color: var(--text-color);
  font-family: 'Orbitron', sans-serif;
  font-size: 12px;
  cursor: pointer;
  transition: transform 0.12s ease, border-color 0.12s ease, color 0.12s ease;
}

.placeholder:hover {
  transform: translateY(-1px);
  border-color: color-mix(in srgb, var(--glow-color) 58%, transparent);
  color: var(--text-color-hover);
}
</style>

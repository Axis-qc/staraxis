<script setup lang="ts">
/**
 * @file InGameOverviewPanel.vue
 *
 * @description
 * 游戏内右侧“总览”面板。
 *
 * 说明：
 * - 该组件仅负责展示，不做权威逻辑计算。
 * - 数据来自 InGameView 注入的 `InGameDataHub`（`hub.overview`）。
 *
 * @usage
 * - 父组件传入 `dayText/tickCostText/sectorCountText`。
 *
 * @provides
 * - **日期**：游戏时间（按后端快照口径）。
 * - **Tick 耗时**：后端 tick 计算耗时。
 * - **星区**：星区数量统计。
 */

defineProps<{
  dayText: string
  tickCostText: string
  sectorCountText: string
}>()
</script>

<template>
  <div class="right-overview" role="complementary" aria-label="Right Overview">
    <div class="panel-header">
      <div class="panel-title">总览</div>
    </div>
    <div class="panel-body">
      <div class="kv">
        <div class="k">日期</div>
        <div class="v">{{ dayText }}</div>
      </div>
      <div class="kv">
        <div class="k">Tick 耗时</div>
        <div class="v">{{ tickCostText }}</div>
      </div>
      <div class="kv">
        <div class="k">星区</div>
        <div class="v">{{ sectorCountText }}</div>
      </div>

      <div class="divider"></div>
      <div class="hint">{{ '左键拖动平移 | 滚轮缩放' }}</div>
    </div>
  </div>
</template>

<style scoped>
.right-overview {
  position: absolute;
  z-index: 20;
  pointer-events: auto;

  top: 64px;
  right: 12px;
  width: 280px;
  max-height: calc(100vh - 64px - 12px);
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
}

.kv {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 6px 0;
  font-size: 12px;
}

.k {
  color: color-mix(in srgb, var(--text-color) 70%, transparent);
}

.v {
  color: var(--text-color);
}

.divider {
  height: 1px;
  margin: 10px 0;
  background: color-mix(in srgb, var(--glow-color) 18%, transparent);
}

.hint {
  font-size: 12px;
  color: color-mix(in srgb, var(--text-color) 65%, transparent);
}
</style>

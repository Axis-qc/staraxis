<script setup lang="ts">
/**
 * @file ShipPanel.vue
 *
 * @description
 * 舰船信息面板 - 显示选中舰船的详细信息，并提供基础操作喵。
 */
import type { EntitySnapshot, ShipDetails } from '../../../net/snapshotWs'

const props = defineProps<{
  ship: EntitySnapshot | null
  isOpen: boolean
}>()

const emit = defineEmits<{
  (e: 'close'): void
  (e: 'focus'): void
}>()

function getShipName(): string {
  if (!props.ship) return '未知舰船'
  const details = props.ship.details as ShipDetails | undefined
  const flags = details?.customFlags ?? []
  if (flags.includes('INITIAL_SPAWN_SHIP')) {
    return '殖民舰 (初始)'
  }
  return `舰船 #${props.ship.entityId}`
}

function getShipType(): string {
  if (!props.ship) return '未知类型'
  const details = props.ship.details as ShipDetails | undefined
  const flags = details?.customFlags ?? []
  if (flags.includes('INITIAL_SPAWN_SHIP')) {
    return '殖民地舰船'
  }
  return '通用舰船'
}

function getStatusText(): string {
  // TODO: 从后端获取舰船状态喵
  return '待机中'
}

function onFocus() {
  emit('focus')
}
</script>

<template>
  <Transition name="panel-slide">
    <div v-if="isOpen" class="ship-panel" role="dialog" aria-label="Ship Panel">
      <!-- 头部 -->
      <div class="panel-header">
        <div class="ship-icon">🚀</div>
        <div class="ship-title">
          <div class="ship-name">{{ getShipName() }}</div>
          <div class="ship-type">{{ getShipType() }}</div>
        </div>
        <button class="close-btn" @click="emit('close')" aria-label="Close panel">×</button>
      </div>

      <!-- 基本信息 -->
      <div class="panel-body">
        <div class="info-section">
          <div class="section-title">状态</div>
          <div class="status-badge" :class="getStatusText() === '待机中' ? 'idle' : 'moving'">
            {{ getStatusText() }}
          </div>
        </div>

        <div class="info-section">
          <div class="section-title">位置</div>
          <div class="coord-display">
            <div class="coord-row">
              <span class="coord-label">X:</span>
              <span class="coord-value">{{ ship ? Math.round(ship.posWorldGU.x) : '-' }}</span>
            </div>
            <div class="coord-row">
              <span class="coord-label">Y:</span>
              <span class="coord-value">{{ ship ? Math.round(ship.posWorldGU.y) : '-' }}</span>
            </div>
          </div>
        </div>

        <!-- 属性（硬编码示例，后续从后端获取） -->
        <div class="info-section">
          <div class="section-title">属性</div>
          <div class="stats-grid">
            <div class="stat-item">
              <span class="stat-label">耐久</span>
              <span class="stat-value">100%</span>
            </div>
            <div class="stat-item">
              <span class="stat-label">能源</span>
              <span class="stat-value">100</span>
            </div>
            <div class="stat-item">
              <span class="stat-label">燃料</span>
              <span class="stat-value">100</span>
            </div>
            <div class="stat-item">
              <span class="stat-label">货仓</span>
              <span class="stat-value">0/1000</span>
            </div>
          </div>
        </div>

        <!-- 操作按钮 -->
        <div class="action-section">
          <button class="action-btn primary" @click="onFocus">
            <span class="btn-icon">👁️</span>
            <span>聚焦</span>
          </button>
          <button class="action-btn" @click="emit('close')">
            <span class="btn-icon">✕</span>
            <span>关闭</span>
          </button>
        </div>
      </div>

      <!-- 提示 -->
      <div class="panel-footer">
        <div class="hint">右键点击空白处下达移动指令喵~</div>
      </div>
    </div>
  </Transition>
</template>

<style scoped>
.ship-panel {
  position: fixed;
  right: 12px;
  top: 64px;
  width: 260px;
  z-index: 30;
  pointer-events: auto;

  border-radius: 14px;
  background: color-mix(in srgb, var(--background-color) 70%, rgba(0, 0, 0, 0.4));
  border: 1px solid color-mix(in srgb, var(--glow-color) 30%, transparent);
  backdrop-filter: blur(14px);
  -webkit-backdrop-filter: blur(14px);
  box-shadow:
    0 0 24px color-mix(in srgb, var(--glow-color) 20%, transparent),
    0 4px 16px rgba(0, 0, 0, 0.3);

  overflow: hidden;
}

/* 面板滑入动画 */
.panel-slide-enter-active,
.panel-slide-leave-active {
  transition: all 0.25s cubic-bezier(0.4, 0, 0.2, 1);
}

.panel-slide-enter-from {
  opacity: 0;
  transform: translateX(20px);
}

.panel-slide-leave-to {
  opacity: 0;
  transform: translateX(20px);
}

.panel-header {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 14px 16px;
  background: color-mix(in srgb, var(--glow-color) 10%, transparent);
  border-bottom: 1px solid color-mix(in srgb, var(--glow-color) 15%, transparent);
}

.ship-icon {
  font-size: 28px;
  filter: drop-shadow(0 0 8px color-mix(in srgb, var(--glow-color) 50%, transparent));
}

.ship-title {
  flex: 1;
  min-width: 0;
}

.ship-name {
  font-size: 14px;
  font-weight: 600;
  color: var(--text-color-hover);
  letter-spacing: 0.5px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.ship-type {
  font-size: 11px;
  color: color-mix(in srgb, var(--text-color) 65%, transparent);
  margin-top: 2px;
}

.close-btn {
  width: 28px;
  height: 28px;
  display: flex;
  align-items: center;
  justify-content: center;
  border-radius: 8px;
  border: 1px solid color-mix(in srgb, var(--glow-color) 20%, transparent);
  background: transparent;
  color: var(--text-color);
  font-size: 18px;
  cursor: pointer;
  transition: all 0.15s ease;
}

.close-btn:hover {
  background: color-mix(in srgb, var(--glow-color) 20%, transparent);
  border-color: color-mix(in srgb, var(--glow-color) 50%, transparent);
}

.panel-body {
  padding: 12px 16px;
}

.info-section {
  margin-bottom: 14px;
}

.info-section:last-child {
  margin-bottom: 0;
}

.section-title {
  font-size: 10px;
  font-weight: 600;
  color: color-mix(in srgb, var(--text-color) 55%, transparent);
  text-transform: uppercase;
  letter-spacing: 1px;
  margin-bottom: 8px;
}

.status-badge {
  display: inline-flex;
  align-items: center;
  padding: 4px 10px;
  border-radius: 6px;
  font-size: 11px;
  font-weight: 500;
}

.status-badge.idle {
  background: color-mix(in srgb, #4ade80 20%, transparent);
  color: #4ade80;
  border: 1px solid color-mix(in srgb, #4ade80 30%, transparent);
}

.status-badge.moving {
  background: color-mix(in srgb, #60a5fa 20%, transparent);
  color: #60a5fa;
  border: 1px solid color-mix(in srgb, #60a5fa 30%, transparent);
}

.coord-display {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 8px;
}

.coord-row {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 6px 10px;
  background: color-mix(in srgb, var(--glow-color) 5%, transparent);
  border-radius: 6px;
  border: 1px solid color-mix(in srgb, var(--glow-color) 8%, transparent);
}

.coord-label {
  font-size: 10px;
  color: color-mix(in srgb, var(--text-color) 50%, transparent);
}

.coord-value {
  font-size: 12px;
  font-weight: 500;
  color: var(--text-color);
  font-family: 'Courier New', monospace;
}

.stats-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 8px;
}

.stat-item {
  display: flex;
  flex-direction: column;
  gap: 2px;
  padding: 8px 10px;
  background: color-mix(in srgb, var(--glow-color) 5%, transparent);
  border-radius: 6px;
  border: 1px solid color-mix(in srgb, var(--glow-color) 8%, transparent);
}

.stat-label {
  font-size: 10px;
  color: color-mix(in srgb, var(--text-color) 55%, transparent);
}

.stat-value {
  font-size: 12px;
  font-weight: 500;
  color: var(--text-color-hover);
}

.action-section {
  display: flex;
  gap: 8px;
  padding-top: 8px;
  border-top: 1px solid color-mix(in srgb, var(--glow-color) 12%, transparent);
}

.action-btn {
  flex: 1;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 6px;
  padding: 8px 12px;
  border-radius: 8px;
  border: 1px solid color-mix(in srgb, var(--glow-color) 20%, transparent);
  background: color-mix(in srgb, var(--glow-color) 8%, transparent);
  color: var(--text-color);
  font-size: 12px;
  cursor: pointer;
  transition: all 0.15s ease;
}

.action-btn:hover {
  background: color-mix(in srgb, var(--glow-color) 15%, transparent);
  border-color: color-mix(in srgb, var(--glow-color) 40%, transparent);
}

.action-btn.primary {
  background: color-mix(in srgb, var(--glow-color) 25%, transparent);
  border-color: color-mix(in srgb, var(--glow-color) 50%, transparent);
}

.action-btn.primary:hover {
  background: color-mix(in srgb, var(--glow-color) 35%, transparent);
  box-shadow: 0 0 12px color-mix(in srgb, var(--glow-color) 30%, transparent);
}

.btn-icon {
  font-size: 12px;
}

.panel-footer {
  padding: 10px 16px;
  background: color-mix(in srgb, var(--glow-color) 5%, transparent);
  border-top: 1px solid color-mix(in srgb, var(--glow-color) 10%, transparent);
}

.hint {
  font-size: 10px;
  color: color-mix(in srgb, var(--text-color) 50%, transparent);
  text-align: center;
  line-height: 1.4;
}
</style>
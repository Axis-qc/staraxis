<script setup lang="ts">
/**
 * @file InGameOverviewPanel.vue
 *
 * @description
 * 游戏内右侧"资产总览"面板。
 *
 * 说明：
 * - 该组件仅负责展示，不做权威逻辑计算。
 * - 数据来自 InGameView 注入的 `InGameDataHub`（`hub.overview`）。
 * - 显示玩家拥有的行星、舰队、太空设施分类列表。
 *
 * @provides
 * - **日期/性能**：游戏时间、Tick耗时、星区数量。
 * - **资产分类**：行星、舰队、太空设施的数量和列表。
 * - **快速导航**：点击资产项可聚焦到该实体。
 */

import type { EntitySnapshot } from '../../../net/snapshotWs'

const props = defineProps<{
  dayText: string
  tickCostText: string
  sectorCountText: string
  ownedPlanets: EntitySnapshot[]
  ownedShips: EntitySnapshot[]
  ownedStations: EntitySnapshot[]
}>()

const emit = defineEmits<{
  (e: 'focusEntity', entityId: number): void
}>()

function onEntityClick(entity: EntitySnapshot) {
  emit('focusEntity', entity.entityId)
}

function getEntityName(entity: EntitySnapshot): string {
  // 根据实体类型返回显示名称喵
  if (entity.entityType === 'PLANET') {
    const details = entity.details as { planetTypeId?: string }
    return `行星 #${entity.entityId}`
  }
  if (entity.entityType === 'SHIP') {
    const details = entity.details as { customFlags?: string[] }
    if (details?.customFlags?.includes('INITIAL_SPAWN_SHIP')) {
      return '殖民舰 (初始)'
    }
    return `舰船 #${entity.entityId}`
  }
  if (entity.entityType === 'STATION') {
    return `空间站 #${entity.entityId}`
  }
  return `${entity.entityType} #${entity.entityId}`
}

function getEntityIcon(entity: EntitySnapshot): string {
  const icons: Record<string, string> = {
    'PLANET': '🪐',
    'SHIP': '🚀',
    'STATION': '🛰️',
    'STAR': '☀️',
    'SYSTEM_BARYCENTER': '⚫'
  }
  return icons[entity.entityType] || '❓'
}
</script>

<template>
  <div class="right-overview" role="complementary" aria-label="Right Overview">
    <div class="panel-header">
      <div class="panel-title">资产总览</div>
    </div>

    <div class="panel-body">
      <!-- 基础信息 -->
      <div class="section basic-info">
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
      </div>

      <div class="divider"></div>

      <!-- 资产统计 -->
      <div class="section asset-stats">
        <div class="stat-row">
          <div class="stat-item">
            <span class="stat-icon">🪐</span>
            <span class="stat-label">行星</span>
            <span class="stat-value">{{ ownedPlanets.length }}</span>
          </div>
          <div class="stat-item">
            <span class="stat-icon">🚀</span>
            <span class="stat-label">舰队</span>
            <span class="stat-value">{{ ownedShips.length }}</span>
          </div>
          <div class="stat-item">
            <span class="stat-icon">🛰️</span>
            <span class="stat-label">设施</span>
            <span class="stat-value">{{ ownedStations.length }}</span>
          </div>
        </div>
      </div>

      <div class="divider"></div>

      <!-- 行星列表 -->
      <div class="section asset-list" v-if="ownedPlanets.length > 0">
        <div class="section-title">
          <span class="title-icon">🪐</span>
          <span>行星 ({{ ownedPlanets.length }})</span>
        </div>
        <div class="list">
          <div
            v-for="planet in ownedPlanets"
            :key="planet.entityId"
            class="list-item"
            @click="onEntityClick(planet)"
          >
            <span class="item-icon">{{ getEntityIcon(planet) }}</span>
            <span class="item-name">{{ getEntityName(planet) }}</span>
            <span class="item-action">聚焦</span>
          </div>
        </div>
      </div>

      <!-- 舰队列表 -->
      <div class="section asset-list" v-if="ownedShips.length > 0">
        <div class="section-title">
          <span class="title-icon">🚀</span>
          <span>舰队 ({{ ownedShips.length }})</span>
        </div>
        <div class="list">
          <div
            v-for="ship in ownedShips"
            :key="ship.entityId"
            class="list-item"
            @click="onEntityClick(ship)"
          >
            <span class="item-icon">{{ getEntityIcon(ship) }}</span>
            <span class="item-name">{{ getEntityName(ship) }}</span>
            <span class="item-action">聚焦</span>
          </div>
        </div>
      </div>

      <!-- 太空设施列表 -->
      <div class="section asset-list" v-if="ownedStations.length > 0">
        <div class="section-title">
          <span class="title-icon">🛰️</span>
          <span>太空设施 ({{ ownedStations.length }})</span>
        </div>
        <div class="list">
          <div
            v-for="station in ownedStations"
            :key="station.entityId"
            class="list-item"
            @click="onEntityClick(station)"
          >
            <span class="item-icon">{{ getEntityIcon(station) }}</span>
            <span class="item-name">{{ getEntityName(station) }}</span>
            <span class="item-action">聚焦</span>
          </div>
        </div>
      </div>

      <!-- 空状态提示 -->
      <div class="section empty-hint" v-if="ownedPlanets.length === 0 && ownedShips.length === 0 && ownedStations.length === 0">
        <div class="hint-text">暂无资产</div>
        <div class="hint-sub">选择出生星系开始游戏喵~</div>
      </div>

      <div class="divider"></div>
      <div class="hint">{{ '点击资产项可聚焦 | 左键拖动平移 | 滚轮缩放' }}</div>
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
  overflow-y: auto;
  border-radius: 14px;
  background: color-mix(in srgb, var(--background-color) 65%, rgba(0, 0, 0, 0.35));
  border: 1px solid color-mix(in srgb, var(--glow-color) 25%, transparent);
  backdrop-filter: blur(12px);
  -webkit-backdrop-filter: blur(12px);
  box-shadow: 0 0 18px color-mix(in srgb, var(--glow-color) 18%, transparent);
}

/* 滚动条样式 */
.right-overview::-webkit-scrollbar {
  width: 4px;
}

.right-overview::-webkit-scrollbar-track {
  background: transparent;
}

.right-overview::-webkit-scrollbar-thumb {
  background: color-mix(in srgb, var(--glow-color) 30%, transparent);
  border-radius: 2px;
}

.panel-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 10px 12px;
  border-bottom: 1px solid color-mix(in srgb, var(--glow-color) 18%, transparent);
  position: sticky;
  top: 0;
  background: inherit;
  z-index: 1;
}

.panel-title {
  font-size: 13px;
  font-weight: 600;
  letter-spacing: 1px;
  color: var(--text-color-hover);
  text-shadow: 0 0 6px color-mix(in srgb, var(--glow-color) 35%, transparent);
}

.panel-body {
  padding: 10px 12px;
}

.section {
  margin-bottom: 8px;
}

/* 基础信息 */
.kv {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 4px 0;
  font-size: 12px;
}

.k {
  color: color-mix(in srgb, var(--text-color) 70%, transparent);
}

.v {
  color: var(--text-color);
  font-weight: 500;
}

/* 资产统计 */
.asset-stats {
  padding: 4px 0;
}

.stat-row {
  display: flex;
  justify-content: space-around;
  gap: 8px;
}

.stat-item {
  display: flex;
  flex-direction: column;
  align-items: center;
  padding: 8px 12px;
  background: color-mix(in srgb, var(--glow-color) 8%, transparent);
  border-radius: 8px;
  border: 1px solid color-mix(in srgb, var(--glow-color) 12%, transparent);
  min-width: 60px;
}

.stat-icon {
  font-size: 18px;
  margin-bottom: 2px;
}

.stat-label {
  font-size: 10px;
  color: color-mix(in srgb, var(--text-color) 60%, transparent);
  text-transform: uppercase;
  letter-spacing: 0.5px;
}

.stat-value {
  font-size: 16px;
  font-weight: 600;
  color: var(--text-color-hover);
}

/* 资产列表 */
.section-title {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 11px;
  font-weight: 600;
  color: color-mix(in srgb, var(--text-color) 85%, transparent);
  text-transform: uppercase;
  letter-spacing: 0.5px;
  padding: 8px 0 4px;
  border-bottom: 1px solid color-mix(in srgb, var(--glow-color) 10%, transparent);
  margin-bottom: 4px;
}

.title-icon {
  font-size: 12px;
}

.list {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.list-item {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 6px 8px;
  background: color-mix(in srgb, var(--glow-color) 5%, transparent);
  border-radius: 6px;
  border: 1px solid color-mix(in srgb, var(--glow-color) 8%, transparent);
  cursor: pointer;
  transition: all 0.15s ease;
}

.list-item:hover {
  background: color-mix(in srgb, var(--glow-color) 15%, transparent);
  border-color: color-mix(in srgb, var(--glow-color) 25%, transparent);
  transform: translateX(-2px);
}

.item-icon {
  font-size: 14px;
}

.item-name {
  flex: 1;
  font-size: 12px;
  color: var(--text-color);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.item-action {
  font-size: 10px;
  color: color-mix(in srgb, var(--glow-color) 80%, transparent);
  opacity: 0;
  transition: opacity 0.15s ease;
}

.list-item:hover .item-action {
  opacity: 1;
}

/* 空状态 */
.empty-hint {
  text-align: center;
  padding: 20px 0;
}

.hint-text {
  font-size: 14px;
  color: color-mix(in srgb, var(--text-color) 70%, transparent);
  margin-bottom: 4px;
}

.hint-sub {
  font-size: 11px;
  color: color-mix(in srgb, var(--text-color) 50%, transparent);
}

/* 分隔线 */
.divider {
  height: 1px;
  margin: 12px 0;
  background: color-mix(in srgb, var(--glow-color) 18%, transparent);
}

/* 底部提示 */
.hint {
  font-size: 11px;
  color: color-mix(in srgb, var(--text-color) 50%, transparent);
  text-align: center;
  padding: 4px 0;
}
</style>

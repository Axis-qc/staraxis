<script setup lang="ts">
/**
 * @file InGamePlanetWindow.vue
 *
 * @description
 * 游戏内“星球窗口”（可拖动）。
 *
 * 作用：
 * - 当玩家在左侧选择列表点击星球实体时，打开该窗口展示星球的基础数据。
 * - 预留未来扩展区域：地表、市场、人口、政治等子页面/Tab。
 *
 * @usage
 * - 在 InGameView 中使用：
 *   - <InGamePlanetWindow v-if="planetWindowOpen" :entity="planetEntity" @close="..." />
 * - entity 来自 Snapshot 的 EntitySnapshot，且 entityType === 'PLANET'。
 *
 * @provides
 * - **可拖动窗口**：按住标题栏拖动；限制不拖出屏幕。
 * - **基础数据展示**：planetTypeId、radiusGU、rotationPeriodHours、orbit 等。
 * - **扩展占位区**：Surface / Market / Population / Politics。
 *
 * @api
 * - props:
 *   - entity: EntitySnapshot（必须是 PLANET）
 *   - dailySettlementState: DailySettlementState | null（低频面板数据）
 * - emits:
 *   - close(): 请求关闭窗口
 *
 * @important_notes
 * - 只消费快照数据，不发起权威计算。
 * - 拖动使用 Pointer Events，并对窗口位置做屏幕边界钳制。
 */
import { computed, ref, watch } from 'vue'
import type { DailySettlementState, EntitySnapshot, SurfaceRegionSnapshot } from '../../../net/snapshotWs'

const props = defineProps<{
  entity: EntitySnapshot
  dailySettlementState: DailySettlementState | null
}>()

const emit = defineEmits<{ (e: 'close'): void }>()

const rootRef = ref<HTMLDivElement | null>(null)

const pos = ref({ x: 0, y: 0 })
const dragOffset = ref({ x: 0, y: 0 })
const dragging = ref(false)

const activeTab = ref<'details' | 'surface' | 'market' | 'population' | 'politics'>('details')

const WINDOW_SIZE = { w: 800, h: 600 }

// 追踪当前选中的区域 ID
const selectedRegionId = ref<number | null>(null)

function clampPos(next: { x: number; y: number }, size: { w: number; h: number }) {
  const margin = 8
  const maxX = Math.max(margin, window.innerWidth - size.w - margin)
  const maxY = Math.max(margin, window.innerHeight - size.h - margin)
  return {
    x: Math.min(maxX, Math.max(margin, next.x)),
    y: Math.min(maxY, Math.max(margin, next.y)),
  }
}

function center() {
  const x = (window.innerWidth - WINDOW_SIZE.w) / 2
  const y = (window.innerHeight - WINDOW_SIZE.h) / 2
  pos.value = clampPos({ x, y }, WINDOW_SIZE)
}

center()

function onHeaderPointerDown(e: PointerEvent) {
  const el = rootRef.value
  if (!el) return

  dragging.value = true
  const rect = el.getBoundingClientRect()
  dragOffset.value = { x: e.clientX - rect.left, y: e.clientY - rect.top }

  try {
    ; (e.currentTarget as HTMLElement).setPointerCapture(e.pointerId)
  } catch {
  }

  e.preventDefault()
}

function onCloseClick(e: MouseEvent) {
  e.stopPropagation()
  e.preventDefault()
  emit('close')
}

function onHeaderPointerMove(e: PointerEvent) {
  if (!dragging.value) return
  const next = { x: e.clientX - dragOffset.value.x, y: e.clientY - dragOffset.value.y }
  pos.value = clampPos(next, WINDOW_SIZE)
  e.preventDefault()
}

function onHeaderPointerUp(e: PointerEvent) {
  if (!dragging.value) return
  dragging.value = false
  try {
    ; (e.currentTarget as HTMLElement).releasePointerCapture(e.pointerId)
  } catch {
  }
  e.preventDefault()
}

const planetDetails = computed(() => {
  const d: any = props.entity.details
  return {
    planetTypeId: d?.planetTypeId == null ? '' : String(d.planetTypeId),
    radiusGU: d?.radiusGU == null ? null : Number(d.radiusGU),
    rotationPeriodHours: d?.rotationPeriodHours == null ? null : Number(d.rotationPeriodHours),
    orbit: d?.orbit ?? null,
  }
})

const dailySettlement = computed<DailySettlementState | null>(() => {
  return props.dailySettlementState
})

const surfaceRegions = computed<SurfaceRegionSnapshot[]>(() => {
  const d = dailySettlement.value
  if (!d) return []
  const planetSurfaces = d.planetSurfaces
  const key = String(props.entity.entityId)
  const p = planetSurfaces[key]
  return p?.surfaceRegions ?? []
})

// 默认选中第一个区域
watch(surfaceRegions, (newRegions) => {
  const first = newRegions[0]
  if (first && selectedRegionId.value === null) {
    selectedRegionId.value = first.regionId
  }
}, { immediate: true })

const selectedRegion = computed(() => {
  const id = selectedRegionId.value
  if (id == null) return null
  return surfaceRegions.value.find((r) => r.regionId === id) ?? null
})

const surfaceSummary = computed(() => {
  const regions = surfaceRegions.value
  if (!regions.length) return { count: 0, landPct: null as number | null, oceanPct: null as number | null }

  let land = 0
  let ocean = 0
  for (const r of regions) {
    if (r.regionType === 'CONTINENT') land += r.surfacePercentage
    else if (r.regionType === 'OCEAN') ocean += r.surfacePercentage
  }

  return { count: regions.length, landPct: land, oceanPct: ocean }
})

function fmtOrbit(orbit: any) {
  if (!orbit) return null
  return {
    orbitCenterEntityId: orbit.orbitCenterEntityId == null ? null : Number(orbit.orbitCenterEntityId),
    semiMajorAxisGU: orbit.semiMajorAxisGU == null ? null : Number(orbit.semiMajorAxisGU),
    eccentricity: orbit.eccentricity == null ? null : Number(orbit.eccentricity),
    inclinationDeg: orbit.inclinationDeg == null ? null : Number(orbit.inclinationDeg),
    periapsisArgDeg: orbit.periapsisArgDeg == null ? null : Number(orbit.periapsisArgDeg),
    meanAnomalyDegAtEpoch: orbit.meanAnomalyDegAtEpoch == null ? null : Number(orbit.meanAnomalyDegAtEpoch),
    orbitalPeriodDays: orbit.orbitalPeriodDays == null ? null : Number(orbit.orbitalPeriodDays),
  }
}

function fmtNum(n: number | null, digits: number = 2) {
  if (n == null || Number.isNaN(n)) return '-'
  return n.toFixed(digits)
}

function fmtPct(x: number | null, digits: number = 1) {
  if (x == null || Number.isNaN(x)) return '-'
  return `${(x * 100).toFixed(digits)}%`
}
</script>

<template>
  <div ref="rootRef" class="planet-window" :style="{ transform: `translate(${pos.x}px, ${pos.y}px)` }" role="dialog"
    aria-label="Planet Window">
    <div class="planet-window-header" @pointerdown="onHeaderPointerDown" @pointermove="onHeaderPointerMove"
      @pointerup="onHeaderPointerUp" @pointercancel="onHeaderPointerUp">
      <div class="planet-window-title">星球 #{{ props.entity.entityId }}</div>
      <button class="planet-window-close" type="button" @click.stop="onCloseClick" @pointerdown.stop="onCloseClick"
        @pointerup.stop="() => { }">
        ×
      </button>
    </div>

    <div class="planet-window-body">
      <div class="planet-window-content">
        <div v-if="activeTab === 'details'" class="section">
          <div class="section-title">详情</div>

          <div class="kv">
            <div class="k">星球类型</div>
            <div class="v">{{ planetDetails.planetTypeId || '-' }}</div>
          </div>
          <div class="kv">
            <div class="k">半径(GU)</div>
            <div class="v">{{ fmtNum(planetDetails.radiusGU) }}</div>
          </div>
          <div class="kv">
            <div class="k">自转周期(h)</div>
            <div class="v">{{ fmtNum(planetDetails.rotationPeriodHours) }}</div>
          </div>

          <div class="kv">
            <div class="k">轨道中心实体ID</div>
            <div class="v">{{ fmtOrbit(planetDetails.orbit)?.orbitCenterEntityId ?? '-' }}</div>
          </div>
          <div class="kv">
            <div class="k">半长轴(GU)</div>
            <div class="v">{{ fmtNum(fmtOrbit(planetDetails.orbit)?.semiMajorAxisGU ?? null) }}</div>
          </div>
          <div class="kv">
            <div class="k">离心率</div>
            <div class="v">{{ fmtNum(fmtOrbit(planetDetails.orbit)?.eccentricity ?? null, 4) }}</div>
          </div>
          <div class="kv">
            <div class="k">轨道倾角(°)</div>
            <div class="v">{{ fmtNum(fmtOrbit(planetDetails.orbit)?.inclinationDeg ?? null) }}</div>
          </div>
          <div class="kv">
            <div class="k">近地点幅角(°)</div>
            <div class="v">{{ fmtNum(fmtOrbit(planetDetails.orbit)?.periapsisArgDeg ?? null) }}</div>
          </div>
          <div class="kv">
            <div class="k">历元平近点角(°)</div>
            <div class="v">{{ fmtNum(fmtOrbit(planetDetails.orbit)?.meanAnomalyDegAtEpoch ?? null) }}</div>
          </div>
          <div class="kv">
            <div class="k">公转周期(天)</div>
            <div class="v">{{ fmtNum(fmtOrbit(planetDetails.orbit)?.orbitalPeriodDays ?? null) }}</div>
          </div>
        </div>

        <div v-else-if="activeTab === 'surface'" class="section surface-section">
          <div class="section-title">地表环境</div>

          <div class="summary-line">
            <div class="mini-summary">区域: {{ surfaceSummary.count }}</div>
            <div class="mini-summary">陆地: {{ fmtPct(surfaceSummary.landPct) }}</div>
            <div class="mini-summary">海洋: {{ fmtPct(surfaceSummary.oceanPct) }}</div>
          </div>

          <div v-if="!surfaceRegions.length" class="tab-placeholder">暂无地表区域数据</div>

          <div v-else class="surface-layout">
            <!-- 左侧区域列表 -->
            <div class="region-sidebar">
              <div v-for="r in surfaceRegions" :key="r.regionId" class="region-list-item"
                :class="{ active: selectedRegionId === r.regionId }" @click="selectedRegionId = r.regionId">
                <div class="region-item-main">
                  <div class="region-icon-small" :class="r.regionType.toLowerCase()"></div>
                  <div class="region-name-small">{{ r.name || '未命名' }}</div>
                  <div class="region-pct-small">{{ fmtPct(r.surfacePercentage, 0) }}</div>
                </div>
                <div class="region-item-secondary">
                  <div class="mini-info">可用 {{ fmtPct(r.developableSpaceRatio, 0) }}</div>
                  <div class="mini-info">🏙️ 0</div>
                  <div class="mini-info">💎 0</div>
                </div>
              </div>
            </div>

            <!-- 右侧详情面板 -->
            <div class="region-detail-panel">
              <div v-if="selectedRegion" class="selected-region-content">
                <div class="detail-header">
                  <div class="detail-title-row">
                    <span class="detail-name">{{ selectedRegion.name }}</span>
                    <span class="detail-type-tag">{{ selectedRegion.regionType }}</span>
                    <span class="detail-main-stat">全球占比 {{ fmtPct(selectedRegion.surfacePercentage) }}</span>
                  </div>

                  <!-- 合并到头部的进度条 -->
                  <div class="header-progress-block">
                    <div class="compact-progress-bar">
                      <div class="fill" style="width: 15%"></div>
                    </div>
                    <div class="progress-info-mini">
                      地表开发占用 <span class="highlight">15% / {{ fmtPct(selectedRegion.developableSpaceRatio) }}</span>
                    </div>
                  </div>
                </div>

                <div class="detail-sub-section">
                  <div class="landform-list">
                    <!-- 地貌项 1 -->
                    <div class="landform-item">
                      <div class="landform-header">
                        <div class="landform-tag">山脉 (预留)</div>
                        <div class="landform-desc">影响矿产发现率</div>
                      </div>

                      <!-- 子区块 1：资源 -->
                      <div class="landform-content-block">
                        <div class="content-sub-title">资源探测</div>
                        <div class="resource-nested-list">
                          <div class="resource-row-mini">
                            <div class="res-dot metal"></div>
                            <div class="res-text">金属矿脉 [丰富]</div>
                          </div>
                        </div>
                      </div>

                      <!-- 子区块 2：城市 -->
                      <div class="landform-content-block">
                        <div class="landform-header-row">
                          <div class="content-sub-title">行政中心</div>
                          <button class="btn-create-city" type="button" disabled>+ 建立城市</button>
                        </div>
                        <div class="city-card-compact">
                          <div class="city-card-main">
                            <div class="city-identity">
                              <div class="city-rank">等级 1</div>
                              <div class="city-name">新君士坦丁 (预留)</div>
                            </div>
                            <div class="city-spec-tag">科研专精</div>
                          </div>
                          <div class="city-stats-grid">
                            <div class="stat-item">
                              <div class="stat-k">人口</div>
                              <div class="stat-v">1.2M</div>
                            </div>
                            <div class="stat-item">
                              <div class="stat-k">生产力</div>
                              <div class="stat-v highlight">124.5</div>
                            </div>
                            <div class="stat-item">
                              <div class="stat-k">费用</div>
                              <div class="stat-v negative">12.0</div>
                            </div>
                          </div>
                        </div>
                      </div>
                    </div>

                    <!-- 地貌项 2 -->
                    <div class="landform-item">
                      <div class="landform-header">
                        <div class="landform-tag">森林 (预留)</div>
                        <div class="landform-desc">影响生物资源发现率</div>
                      </div>

                      <!-- 子区块 1：资源 -->
                      <div class="landform-content-block">
                        <div class="content-sub-title">资源探测</div>
                        <div class="resource-nested-list">
                          <div class="resource-row-mini">
                            <div class="res-dot bio"></div>
                            <div class="res-text">原生景观 [独特]</div>
                          </div>
                        </div>
                      </div>

                      <!-- 子区块 2：城市 -->
                      <div class="landform-content-block">
                        <div class="landform-header-row">
                          <div class="content-sub-title">行政中心</div>
                          <button class="btn-create-city" type="button" disabled>+ 建立城市</button>
                        </div>
                        <div class="city-placeholder-box">暂无城市建立</div>
                      </div>
                    </div>
                  </div>
                </div>

              </div>
              <div v-else class="empty-detail">
                请选择一个区域以查看详情
              </div>
            </div>
          </div>
        </div>

        <div v-else class="section">
          <div class="section-title">
            {{ activeTab === 'market' ? '市场' : activeTab === 'population' ? '人口' : '政治' }}
          </div>
          <div class="tab-placeholder">开发中</div>
        </div>
      </div>

      <div class="planet-window-tabs" role="tablist" aria-label="Planet tabs">
        <button class="tab" type="button" :class="{ active: activeTab === 'details' }" @click="activeTab = 'details'"
          role="tab">详情</button>
        <button class="tab" type="button" :class="{ active: activeTab === 'surface' }" @click="activeTab = 'surface'"
          role="tab">地表</button>
        <button class="tab" type="button" :class="{ active: activeTab === 'market' }" @click="activeTab = 'market'"
          role="tab">市场</button>
        <button class="tab" type="button" :class="{ active: activeTab === 'population' }"
          @click="activeTab = 'population'" role="tab">人口</button>
        <button class="tab" type="button" :class="{ active: activeTab === 'politics' }" @click="activeTab = 'politics'"
          role="tab">政治</button>
      </div>
    </div>
  </div>
</template>

<style scoped>
.planet-window {
  position: absolute;
  left: 0;
  top: 0;
  width: 800px;
  height: 600px;
  border-radius: 14px;
  background: color-mix(in srgb, var(--background-color) 65%, rgba(0, 0, 0, 0.35));
  border: 1px solid color-mix(in srgb, var(--glow-color) 25%, transparent);
  backdrop-filter: blur(12px);
  -webkit-backdrop-filter: blur(12px);
  box-shadow: 0 0 18px color-mix(in srgb, var(--glow-color) 18%, transparent);
  z-index: 34;
  pointer-events: auto;
}

.planet-window-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 10px 12px;
  border-bottom: 1px solid color-mix(in srgb, var(--glow-color) 18%, transparent);
  cursor: grab;
}

.planet-window-header:active {
  cursor: grabbing;
}

.planet-window-title {
  font-size: 12px;
  letter-spacing: 1px;
  color: var(--text-color-hover);
  text-shadow: 0 0 6px color-mix(in srgb, var(--glow-color) 35%, transparent);
}

.planet-window-close {
  width: 26px;
  height: 26px;
  border-radius: 8px;
  border: 1px solid color-mix(in srgb, var(--glow-color) 22%, transparent);
  background: transparent;
  color: var(--text-color);
  font-family: 'Orbitron', sans-serif;
  cursor: pointer;
}

.planet-window-close:hover {
  border-color: color-mix(in srgb, var(--glow-color) 58%, transparent);
  color: var(--text-color-hover);
}

.planet-window-body {
  padding: 0;
  display: flex;
  flex-direction: column;
  height: calc(100% - 48px);
}

.planet-window-content {
  flex: 1;
  overflow-y: hidden;
  /* 由内部 tab 控制滚动 */
  padding: 10px 12px;
  min-height: 0;
  display: flex;
  flex-direction: column;
}

.section {
  flex: 1;
  display: flex;
  flex-direction: column;
  min-height: 0;
}

.section-title {
  font-size: 12px;
  opacity: 0.9;
  margin-bottom: 8px;
  flex-shrink: 0;
}

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
}

/* Surface Tab Styles */
.summary-line {
  display: flex;
  gap: 16px;
  margin-bottom: 10px;
  padding: 6px 10px;
  background: color-mix(in srgb, var(--glow-color) 5%, transparent);
  border-radius: 8px;
  border: 1px solid color-mix(in srgb, var(--glow-color) 10%, transparent);
}

.mini-summary {
  font-size: 11px;
  color: var(--text-color-hover);
  opacity: 0.8;
}

.surface-layout {
  flex: 1;
  display: flex;
  gap: 12px;
  min-height: 0;
}

.region-sidebar {
  width: 220px;
  overflow-y: auto;
  display: flex;
  flex-direction: column;
  gap: 6px;
  padding-right: 4px;
}

.region-list-item {
  display: flex;
  flex-direction: column;
  gap: 4px;
  padding: 8px;
  border-radius: 8px;
  background: color-mix(in srgb, var(--background-color) 85%, rgba(0, 0, 0, 0.1));
  border: 1px solid color-mix(in srgb, var(--glow-color) 10%, transparent);
  cursor: pointer;
  transition: all 0.2s;
}

.region-item-main {
  display: flex;
  align-items: center;
  gap: 8px;
}

.region-item-secondary {
  display: flex;
  justify-content: space-between;
  padding-top: 4px;
  border-top: 1px solid color-mix(in srgb, var(--glow-color) 8%, transparent);
}

.mini-info {
  font-size: 9px;
  opacity: 0.6;
  white-space: nowrap;
}

.region-list-item:hover {
  background: color-mix(in srgb, var(--glow-color) 8%, transparent);
}

.region-list-item.active {
  background: color-mix(in srgb, var(--glow-color) 15%, transparent);
  border-color: var(--glow-color);
  box-shadow: inset 0 0 8px color-mix(in srgb, var(--glow-color) 10%, transparent);
}

.region-icon-small {
  width: 12px;
  height: 12px;
  border-radius: 3px;
  border: 1px solid color-mix(in srgb, var(--glow-color) 30%, transparent);
}

.region-icon-small.continent {
  background: rgba(120, 255, 180, 0.6);
}

.region-icon-small.ocean {
  background: rgba(127, 211, 255, 0.6);
}

.region-name-small {
  flex: 1;
  font-size: 11px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.region-pct-small {
  font-size: 10px;
  opacity: 0.6;
}

.region-detail-panel {
  flex: 1;
  background: color-mix(in srgb, var(--background-color) 70%, rgba(0, 0, 0, 0.2));
  border-radius: 12px;
  border: 1px solid color-mix(in srgb, var(--glow-color) 15%, transparent);
  overflow-y: auto;
  padding: 12px;
}

.detail-header {
  margin-bottom: 12px;
  padding-bottom: 10px;
  border-bottom: 1px solid color-mix(in srgb, var(--glow-color) 12%, transparent);
}

.header-progress-block {
  margin-top: 8px;
}

.progress-info-mini {
  margin-top: 4px;
  font-size: 10px;
  opacity: 0.7;
}

.detail-title-row {
  display: flex;
  align-items: baseline;
  gap: 8px;
}

.detail-name {
  font-size: 16px;
  font-weight: 700;
  color: var(--text-color-hover);
}

.detail-type-tag {
  font-size: 10px;
  padding: 1px 6px;
  border-radius: 4px;
  background: color-mix(in srgb, var(--glow-color) 15%, transparent);
  border: 1px solid color-mix(in srgb, var(--glow-color) 20%, transparent);
}

.detail-main-stat {
  font-size: 11px;
  opacity: 0.6;
  margin-top: 4px;
}

.detail-sub-section {
  margin-top: 16px;
}

.detail-sub-title {
  font-size: 11px;
  font-weight: 600;
  opacity: 0.8;
  margin-bottom: 8px;
  letter-spacing: 0.5px;
  color: var(--text-color-hover);
}

/* Landform and Nested Resources */
.landform-list {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.landform-item {
  padding: 8px;
  border-radius: 8px;
  background: color-mix(in srgb, var(--glow-color) 4%, transparent);
  border: 1px solid color-mix(in srgb, var(--glow-color) 8%, transparent);
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.landform-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.landform-content-block {
  display: flex;
  flex-direction: column;
  gap: 6px;
  padding-top: 6px;
  border-top: 1px solid color-mix(in srgb, var(--glow-color) 6%, transparent);
}

.landform-header-row {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.content-sub-title {
  font-size: 9px;
  font-weight: 600;
  text-transform: uppercase;
  letter-spacing: 0.5px;
  opacity: 0.5;
}

.landform-tag {
  font-size: 10px;
  padding: 2px 8px;
  border-radius: 4px;
  background: color-mix(in srgb, var(--glow-color) 15%, transparent);
  border: 1px solid color-mix(in srgb, var(--glow-color) 20%, transparent);
  color: var(--text-color-hover);
}

.landform-desc {
  font-size: 9px;
  opacity: 0.5;
  font-style: italic;
}

.resource-nested-list {
  display: flex;
  flex-direction: column;
  gap: 4px;
  margin-left: 8px;
  padding-left: 8px;
  border-left: 1px solid color-mix(in srgb, var(--glow-color) 15%, transparent);
}

.resource-row-mini {
  display: flex;
  align-items: center;
  gap: 6px;
}

.res-dot {
  width: 4px;
  height: 4px;
  border-radius: 50%;
  background: var(--glow-color);
}

.res-dot.metal {
  background: #ffd700;
  box-shadow: 0 0 4px #ffd700;
}

.res-dot.bio {
  background: #7fffb4;
  box-shadow: 0 0 4px #7fffb4;
}

.res-text {
  font-size: 10px;
  opacity: 0.8;
}

/* Compact City Card */
.city-card-compact {
  padding: 10px;
  border-radius: 10px;
  background: linear-gradient(135deg,
      color-mix(in srgb, var(--background-color) 80%, rgba(0, 0, 0, 0.3)),
      color-mix(in srgb, var(--background-color) 60%, rgba(0, 0, 0, 0.4)));
  border: 1px solid color-mix(in srgb, var(--glow-color) 20%, transparent);
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.2);
}

.city-card-main {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  margin-bottom: 10px;
}

.city-rank {
  font-size: 9px;
  text-transform: uppercase;
  letter-spacing: 1px;
  opacity: 0.6;
}

.city-name {
  font-size: 13px;
  font-weight: 700;
  color: var(--text-color-hover);
  margin-top: 2px;
}

.city-spec-tag {
  font-size: 9px;
  padding: 2px 6px;
  border-radius: 4px;
  background: color-mix(in srgb, var(--glow-color) 10%, transparent);
  border: 1px solid color-mix(in srgb, var(--glow-color) 30%, transparent);
  color: var(--glow-color);
}

.city-stats-grid {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 8px;
  padding-top: 8px;
  border-top: 1px solid color-mix(in srgb, var(--glow-color) 10%, transparent);
}

.stat-item {
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.stat-k {
  font-size: 9px;
  opacity: 0.5;
}

.stat-v {
  font-size: 11px;
  font-weight: 600;
}

.stat-v.negative {
  color: #ff6b6b;
}


.highlight {
  color: var(--glow-color);
}

.compact-progress-bar {
  height: 6px;
  background: color-mix(in srgb, var(--background-color) 60%, #000);
  border-radius: 3px;
  overflow: hidden;
  border: 1px solid color-mix(in srgb, var(--glow-color) 10%, transparent);
}

.compact-progress-bar .fill {
  height: 100%;
  background: linear-gradient(90deg, var(--glow-color), #7fffb4);
  box-shadow: 0 0 10px var(--glow-color);
}

.city-area {
  margin-top: 20px;
}

.city-header-row {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 8px;
}

.btn-create-city {
  height: 20px;
  padding: 0 10px;
  font-size: 10px;
  border-radius: 4px;
  background: transparent;
  border: 1px solid color-mix(in srgb, var(--glow-color) 40%, transparent);
  color: var(--glow-color);
  cursor: not-allowed;
  opacity: 0.6;
}

.city-placeholder-box {
  padding: 16px;
  border: 1px dashed color-mix(in srgb, var(--glow-color) 15%, transparent);
  border-radius: 8px;
  text-align: center;
  font-size: 11px;
  opacity: 0.5;
}

.empty-detail {
  height: 100%;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 12px;
  opacity: 0.5;
}

/* Tabs and generic utils */
.planet-window-tabs {
  display: flex;
  border-top: 1px solid color-mix(in srgb, var(--glow-color) 18%, transparent);
  background: color-mix(in srgb, var(--background-color) 85%, rgba(0, 0, 0, 0.15));
  border-radius: 0 0 14px 14px;
  overflow: hidden;
  height: 32px;
  flex-shrink: 0;
}

.tab {
  flex: 1;
  height: 32px;
  border: none;
  background: transparent;
  color: var(--text-color);
  font-size: 12px;
  letter-spacing: 1px;
  cursor: pointer;
  transition: background 0.2s;
  border-bottom: 2px solid transparent;
}

.tab:hover {
  background: color-mix(in srgb, var(--glow-color) 10%, transparent);
}

.tab.active {
  background: color-mix(in srgb, var(--glow-color) 15%, transparent);
  border-bottom-color: var(--glow-color);
  color: var(--text-color-hover);
}

.tab-placeholder {
  flex: 1;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 12px;
  opacity: 0.75;
}
</style>

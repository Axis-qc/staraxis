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
 * - emits:
 *   - close(): 请求关闭窗口
 *
 * @important_notes
 * - 只消费快照数据，不发起权威计算。
 * - 拖动使用 Pointer Events，并对窗口位置做屏幕边界钳制。
 */
import { computed, ref } from 'vue'
import type { EntitySnapshot } from '../../../net/snapshotWs'

const props = defineProps<{ entity: EntitySnapshot }>()

const emit = defineEmits<{ (e: 'close'): void }>()

const rootRef = ref<HTMLDivElement | null>(null)

const pos = ref({ x: 0, y: 0 })
const dragOffset = ref({ x: 0, y: 0 })
const dragging = ref(false)

const activeTab = ref<'details' | 'surface' | 'market' | 'population' | 'politics'>('details')

const WINDOW_SIZE = { w: 800, h: 600 }

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
    ;(e.currentTarget as HTMLElement).setPointerCapture(e.pointerId)
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
    ;(e.currentTarget as HTMLElement).releasePointerCapture(e.pointerId)
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
</script>

<template>
  <div
    ref="rootRef"
    class="planet-window"
    :style="{ transform: `translate(${pos.x}px, ${pos.y}px)` }"
    role="dialog"
    aria-label="Planet Window"
  >
    <div
      class="planet-window-header"
      @pointerdown="onHeaderPointerDown"
      @pointermove="onHeaderPointerMove"
      @pointerup="onHeaderPointerUp"
      @pointercancel="onHeaderPointerUp"
    >
      <div class="planet-window-title">星球 #{{ props.entity.entityId }}</div>
      <button
        class="planet-window-close"
        type="button"
        @click.stop="onCloseClick"
        @pointerdown.stop="onCloseClick"
        @pointerup.stop="() => {}"
      >
        ×
      </button>
    </div>

    <div class="planet-window-body">
      <div class="planet-window-content">
        <div v-if="activeTab === 'details'" class="section">
          <div class="section-title">详情</div>

          <div class="kv"><div class="k">星球类型</div><div class="v">{{ planetDetails.planetTypeId || '-' }}</div></div>
          <div class="kv"><div class="k">半径(GU)</div><div class="v">{{ fmtNum(planetDetails.radiusGU) }}</div></div>
          <div class="kv"><div class="k">自转周期(h)</div><div class="v">{{ fmtNum(planetDetails.rotationPeriodHours) }}</div></div>

          <div class="kv"><div class="k">轨道中心实体ID</div><div class="v">{{ fmtOrbit(planetDetails.orbit)?.orbitCenterEntityId ?? '-' }}</div></div>
          <div class="kv"><div class="k">半长轴(GU)</div><div class="v">{{ fmtNum(fmtOrbit(planetDetails.orbit)?.semiMajorAxisGU ?? null) }}</div></div>
          <div class="kv"><div class="k">离心率</div><div class="v">{{ fmtNum(fmtOrbit(planetDetails.orbit)?.eccentricity ?? null, 4) }}</div></div>
          <div class="kv"><div class="k">轨道倾角(°)</div><div class="v">{{ fmtNum(fmtOrbit(planetDetails.orbit)?.inclinationDeg ?? null) }}</div></div>
          <div class="kv"><div class="k">近地点幅角(°)</div><div class="v">{{ fmtNum(fmtOrbit(planetDetails.orbit)?.periapsisArgDeg ?? null) }}</div></div>
          <div class="kv"><div class="k">历元平近点角(°)</div><div class="v">{{ fmtNum(fmtOrbit(planetDetails.orbit)?.meanAnomalyDegAtEpoch ?? null) }}</div></div>
          <div class="kv"><div class="k">公转周期(天)</div><div class="v">{{ fmtNum(fmtOrbit(planetDetails.orbit)?.orbitalPeriodDays ?? null) }}</div></div>
        </div>

        <div v-else class="section">
          <div class="section-title">
            {{ activeTab === 'surface' ? '地表' : activeTab === 'market' ? '市场' : activeTab === 'population' ? '人口' : '政治' }}
          </div>
          <div class="tab-placeholder">开发中</div>
        </div>
      </div>

      <div class="planet-window-tabs" role="tablist" aria-label="Planet tabs">
        <button class="tab" type="button" :class="{ active: activeTab === 'details' }" @click="activeTab = 'details'" role="tab">详情</button>
        <button class="tab" type="button" :class="{ active: activeTab === 'surface' }" @click="activeTab = 'surface'" role="tab">地表</button>
        <button class="tab" type="button" :class="{ active: activeTab === 'market' }" @click="activeTab = 'market'" role="tab">市场</button>
        <button class="tab" type="button" :class="{ active: activeTab === 'population' }" @click="activeTab = 'population'" role="tab">人口</button>
        <button class="tab" type="button" :class="{ active: activeTab === 'politics' }" @click="activeTab = 'politics'" role="tab">政治</button>
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

.section-title {
  font-size: 12px;
  opacity: 0.9;
  margin-bottom: 6px;
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

.planet-window-content {
  flex: 1;
  overflow-y: auto;
  padding: 10px 12px;
  min-height: 0;
}

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
  height: 120px;
  border-radius: 10px;
  border: 1px dashed color-mix(in srgb, var(--glow-color) 22%, transparent);
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 12px;
  opacity: 0.75;
}
</style>

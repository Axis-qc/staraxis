<script setup lang="ts">
/**
 * @file InGameView.vue
 *
 * @description
 * 游戏主窗口（/in-game）。
 *
 * 需求：
 * - 全屏拦截右键：屏蔽浏览器默认菜单，后续接入自定义右键菜单。
 * - 禁用文本选中/拖拽选框复制：避免误操作。
 * - 防回退：监听 popstate，把历史栈拉回，尽量降低误触后退造成的跳页。
 *
 * 注意：
 * - 浏览器级“返回”无法 100% 禁止；这里只做尽力拦截。
 */
import { computed, onMounted, onUnmounted, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { useRouter } from 'vue-router'
import { useAstroAssets } from '../composables/useAstroAssets'
import { connectSnapshotWs, type SnapshotMessage } from '../net/snapshotWs'
import { createThreeWorldRenderer, type ThreeWorldRenderer } from '../rendering/threeWorldRenderer'

const { t } = useI18n()
const router = useRouter()
const { getSpritePath } = useAstroAssets()

const rootRef = ref<HTMLDivElement | null>(null)
const containerRef = ref<HTMLDivElement | null>(null)

let renderer: ThreeWorldRenderer | null = null
let wsClient: ReturnType<typeof connectSnapshotWs> | null = null

const lastSnapshot = ref<SnapshotMessage | null>(null)

const overviewDayText = computed(() => {
  const s = lastSnapshot.value
  if (!s || !s.ok || !s.realTimeWorldState) return '-'
  return `Day ${s.realTimeWorldState.gameDatetimeDay}`
})

const overviewTickCostText = computed(() => {
  const s = lastSnapshot.value
  if (!s || !s.ok || s.tickCostMs == null) return '-'
  return `${s.tickCostMs} ms`
})

const overviewSectorCountText = computed(() => {
  const s = lastSnapshot.value
  if (!s || !s.ok) return '-'
  return String(s.dailySettlementState?.sectorCount ?? s.realTimeWorldState?.sectorCenters?.length ?? '-')
})

const isDebugHudEnabled = import.meta.env.DEV

const overviewZoomText = computed(() => {
  if (!renderer) return '-'
  return String(renderer.zoom.value)
})

const debugSnapshotText = computed(() => {
  const s = lastSnapshot.value
  if (!s) return 'no_snapshot'

  const ok = s.ok
  const err = s.error ?? ''
  const count = s.realTimeWorldState?.sectorCenters?.length ?? 0

  let minX = 0
  let maxX = 0
  let minY = 0
  let maxY = 0
  const centers = s.realTimeWorldState?.sectorCenters ?? []
  const first = centers[0]
  if (first) {
    minX = first.x
    maxX = first.x
    minY = first.y
    maxY = first.y
    for (let i = 1; i < centers.length; i++) {
      const p = centers[i]
      if (!p) continue
      if (p.x < minX) minX = p.x
      if (p.x > maxX) maxX = p.x
      if (p.y < minY) minY = p.y
      if (p.y > maxY) maxY = p.y
    }
  }

  const cost = s.tickCostMs == null ? '' : `${s.tickCostMs}ms`
  const range = centers.length === 0 ? '' : `x=[${minX.toFixed(0)},${maxX.toFixed(0)}], y=[${minY.toFixed(0)},${maxY.toFixed(0)}]`

  return `ok=${ok} ${cost} count=${count} ${err} ${range}`.trim()
})

function onContextMenu(e: MouseEvent) {
  e.preventDefault()
}

function onPopState() {
  try {
    history.pushState({ saLock: true }, '', location.href)
  } catch {
  }
}

onMounted(() => {
  const el = rootRef.value
  if (el) {
    el.addEventListener('contextmenu', onContextMenu)
  }

  try {
    history.pushState({ saLock: true }, '', location.href)
  } catch {
  }
  window.addEventListener('popstate', onPopState)

  const container = containerRef.value
  if (container) {
    renderer = createThreeWorldRenderer(container, { minZoom: 0.1, maxZoom: 2_000_000, getSpritePath })
  }

  wsClient = connectSnapshotWs({
    reconnectDelayMs: 3000,
    onSnapshot: (s) => {
      lastSnapshot.value = s
      renderer?.updateFromSnapshot(s)
    },
  })
})

onUnmounted(() => {
  const el = rootRef.value
  if (el) {
    el.removeEventListener('contextmenu', onContextMenu)
  }
  window.removeEventListener('popstate', onPopState)

  wsClient?.close()
  wsClient = null

  renderer?.dispose()
  renderer = null
})
</script>

<template>
  <div ref="rootRef" class="in-game-root">
    <div class="top-hud">
      <div class="left">
        <div class="title">{{ t('app.title') }}</div>
        <div class="subtitle">{{ t('inGame.subtitle') }}</div>
      </div>
      <div class="right">
        <button class="btn" @click="router.push('/main-menu')">{{ t('common.back') }}</button>
      </div>
    </div>

    <div ref="containerRef" class="render-container"></div>

    <div class="right-overview" role="complementary" aria-label="Right Overview">
      <div class="panel-header">
        <div class="panel-title">总览</div>
      </div>
      <div class="panel-body">
        <div class="kv">
          <div class="k">日期</div>
          <div class="v">{{ overviewDayText }}</div>
        </div>
        <div class="kv">
          <div class="k">Tick 耗时</div>
          <div class="v">{{ overviewTickCostText }}</div>
        </div>
        <div class="kv">
          <div class="k">星区</div>
          <div class="v">{{ overviewSectorCountText }}</div>
        </div>
        <div class="kv">
          <div class="k">缩放</div>
          <div class="v">{{ overviewZoomText }}</div>
        </div>
        <div v-if="isDebugHudEnabled" class="divider"></div>
        <div v-if="isDebugHudEnabled" class="kv debug-row">
          <div class="k">调试</div>
          <div class="v debug-text">{{ debugSnapshotText }}</div>
        </div>

        <div class="divider"></div>
        <div class="hint">
          {{ lastSnapshot?.error || '左键拖动平移 | 滚轮缩放' }}
        </div>
      </div>
    </div>

    <div class="bottom-build" role="complementary" aria-label="Bottom Build">
      <div class="panel-header">
        <div class="panel-title">建造</div>
      </div>
      <div class="build-strip">
        <button class="build-item">采矿站</button>
        <button class="build-item">科研站</button>
        <button class="build-item">造船厂</button>
        <button class="build-item">防御平台</button>
        <button class="build-item">居住舱</button>
        <button class="build-item">物流中心</button>
      </div>
    </div>
  </div>
</template>

<style scoped>
.in-game-root {
  position: relative;
  width: 100vw;
  height: 100vh;
  overflow: hidden;
  background: var(--background-color);
  color: var(--text-color);
  font-family: 'Orbitron', sans-serif;

  user-select: none;
  -webkit-user-select: none;
  -ms-user-select: none;
}

.top-hud {
  position: absolute;
  top: 0;
  left: 0;
  right: 0;
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 12px 16px;
  z-index: 10;
  background: color-mix(in srgb, var(--background-color) 65%, rgba(0, 0, 0, 0.25));
  border-bottom: 1px solid color-mix(in srgb, var(--glow-color) 25%, transparent);
  backdrop-filter: blur(12px);
  -webkit-backdrop-filter: blur(12px);
}

.title {
  font-weight: 700;
  color: var(--text-color-hover);
  text-shadow: 0 0 8px var(--glow-color), 0 0 15px var(--glow-color);
}

.subtitle {
  margin-top: 2px;
  font-size: 12px;
  letter-spacing: 1px;
  color: color-mix(in srgb, var(--text-color) 70%, transparent);
}

.btn {
  background: transparent;
  border: 1px solid color-mix(in srgb, var(--glow-color) 32%, transparent);
  color: var(--text-color);
  padding: 8px 10px;
  font-family: 'Orbitron', sans-serif;
  font-size: 12px;
  cursor: pointer;
  border-radius: 10px;
  transition: transform 0.15s ease, color 0.15s ease, border-color 0.15s ease;
}

.btn:hover {
  transform: translateY(-1px);
  color: var(--text-color-hover);
  border-color: color-mix(in srgb, var(--glow-color) 58%, transparent);
}

.render-container {
  position: absolute;
  inset: 0;
  touch-action: none;
}

.right-overview,
.bottom-build {
  position: absolute;
  z-index: 20;
  pointer-events: auto;
}

.right-overview {
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

.bottom-build {
  left: 12px;
  right: 12px;
  bottom: 12px;
  height: 120px;
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

.debug-row {
  align-items: flex-start;
}

.debug-text {
  max-width: 180px;
  word-break: break-all;
  text-align: right;
  font-size: 11px;
  color: color-mix(in srgb, var(--text-color) 65%, transparent);
}

.build-strip {
  display: flex;
  gap: 10px;
  padding: 10px 12px 12px;
  overflow-x: auto;
  overflow-y: hidden;
  height: calc(100% - 40px);
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

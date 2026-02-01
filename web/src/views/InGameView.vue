<script setup lang="ts">
/**
 * @file InGameView.vue
 *
 * @description
 * 游戏主界面视图（/in-game）。
 *
 * 本视图负责：
 * - 初始化并承载 three 渲染画布（`createThreeWorldRenderer`）。
 * - 建立与后端的快照 WebSocket 连接（`connectSnapshotWs`），接收权威世界快照用于渲染与 UI 展示。
 * - 提供游戏内 HUD/UI：右侧总览、底部主控制栏、ESC 菜单。
 * - 在 DEV 环境提供可拖拽调试浮窗（`O` 键开关），展示缩放倍率/镜头中心/鼠标世界坐标/世界状态摘要等。
 *
 * 说明：
 * - 本视图不实现任何会改变游戏结果的权威逻辑；仅消费后端推送快照进行渲染与展示。
 * - 游戏内 UI 数据聚合通过 `useInGameDataHub()` 统一处理与分发，避免页面组件中散落数据拼装逻辑。
 *
 * @usage
 * - 通过 Vue Router 进入（路由 `/in-game`）。
 * - 创建 renderer 后调用 `hub.setRenderer(renderer)`，在 WS 收到快照后调用 `hub.setLastSnapshot(snapshot)`。
 * - 将画布 pointermove 事件绑定到 `hub.onCanvasPointerMove`，用于计算鼠标世界坐标（GU）。
 *
 * @provides
 * - **渲染承载**：全屏渲染容器（WebGL/Three）。
 * - **UI 面板**：总览、底部主控制栏、分类面板（开发/军事/科技/内政/外交）。
 * - **ESC 菜单**：返回游戏/保存/加载/退出。
 * - **调试浮窗（DEV）**：可拖拽、居中打开、限制不出屏幕、跟随主题色。
 *
 * @api
 * - WebSocket：由 `connectSnapshotWs` 连接（具体 WS 地址与协议由该模块内部实现）。
 *
 * @resources
 * - `../rendering/threeWorldRenderer`：三维/二维世界渲染。
 * - `../net/snapshotWs`：快照 WS 客户端。
 * - `../composables/useInGameDataHub`：UI/渲染所需数据聚合与派发。
 * - `../composables/useAstroAssets`：恒星/行星贴图资源路径。
 * - `../composables/useDevTooltip`：开发中提示。
 * - `../components/inGame/*`：游戏内 UI 组件。
 * - 全局主题变量：`ui.css/theme.css/controls.css`。
 *
 * @potential_issues
 * - **性能**：渲染与调试显示可能产生额外开销（调试浮窗数据更新更频繁）。
 * - **快捷键冲突**：`Esc` 打开菜单、`O` 打开调试窗；输入框聚焦时会忽略快捷键。
 * - **布局/交互**：浮窗拖拽受屏幕边界限制；面板叠层需要正确的 z-index。
 * - **全屏拦截右键**：屏蔽浏览器默认菜单，后续接入自定义右键菜单。
 * - **禁用文本选中/拖拽选框复制**：避免误操作。
 * - **浏览器级“返回”无法 100% 禁止**：监听 popstate，把历史栈拉回，尽量降低误触后退造成的跳页。
 */
import { onMounted, onUnmounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { useAstroAssets } from '../composables/useAstroAssets'
import { useDevTooltip } from '../composables/useDevTooltip'
import InGameBuildBar from '../features/inGame/components/InGameBuildBar.vue'
import InGameEscMenu from '../features/inGame/components/InGameEscMenu.vue'
import InGameOverviewPanel from '../features/inGame/components/InGameOverviewPanel.vue'
import InGameDevelopmentPanel from '../features/inGame/panels/InGameDevelopmentPanel.vue'
import InGameTechPanel from '../features/inGame/panels/InGameTechPanel.vue'
import InGameMilitaryPanel from '../features/inGame/panels/InGameMilitaryPanel.vue'
import InGameDomesticPanel from '../features/inGame/panels/InGameDomesticPanel.vue'
import InGameDiplomacyPanel from '../features/inGame/panels/InGameDiplomacyPanel.vue'
import { connectSnapshotWs } from '../net/snapshotWs'
import { createThreeWorldRenderer } from '../rendering/threeWorldRenderer'
import { useInGameDataHub } from '../features/inGame/composables/useInGameDataHub'
import { useInGameInputController } from '../features/inGame/input/useInGameInputController'
import { useInGameUiInputBindings } from '../features/inGame/input/useInGameUiInputBindings'
import type { GameAction, InGameBottomTab } from '../features/inGame/input/gameActions'
import { useRtsSelection } from '../features/inGame/selection/useRtsSelection'
import InGameSelectionRect from '../features/inGame/components/InGameSelectionRect.vue'
import { useRtsRightClickCommand } from '../features/inGame/commands/useRtsRightClickCommand'

const router = useRouter()
const { getSpritePath } = useAstroAssets()

const devTooltip = useDevTooltip()

const isEscMenuOpen = ref(false)

function showDevelopingHintAtCenter() {
  devTooltip.show(
    '该功能正在开发中',
    new MouseEvent('click', {
      clientX: window.innerWidth / 2,
      clientY: window.innerHeight / 2,
    }),
  )
}

const rootRef = ref<HTMLDivElement | null>(null)
const containerRef = ref<HTMLDivElement | null>(null)

const hub = useInGameDataHub()

let wsClient: ReturnType<typeof connectSnapshotWs> | null = null

const isDebugHudEnabled = import.meta.env.DEV

const isDebugWindowVisible = ref(false)
const debugWindowPos = ref({ x: 0, y: 0 })
const debugWindowRef = ref<HTMLDivElement | null>(null)

const isDraggingDebugWindow = ref(false)
const debugDragOffset = ref({ x: 0, y: 0 })

const activeBottomTab = ref<InGameBottomTab | null>(null)

const selection = useRtsSelection()

const rightClickCommand = useRtsRightClickCommand({
  getRenderer: () => hub.getRenderer(),
  getSelectedIds: () => selection.selectedIds.value,
  onCommandIntent: (intent) => {
    void intent
    showDevelopingHintAtCenter()
  },
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

function getClampedDebugWindowPos(next: { x: number; y: number }, size: { w: number; h: number }) {
  const margin = 8
  const maxX = Math.max(margin, window.innerWidth - size.w - margin)
  const maxY = Math.max(margin, window.innerHeight - size.h - margin)
  return {
    x: Math.min(maxX, Math.max(margin, next.x)),
    y: Math.min(maxY, Math.max(margin, next.y)),
  }
}

function centerDebugWindow(size: { w: number; h: number }) {
  const x = (window.innerWidth - size.w) / 2
  const y = (window.innerHeight - size.h) / 2
  debugWindowPos.value = getClampedDebugWindowPos({ x, y }, size)
}

function onDebugWindowHeaderPointerDown(e: PointerEvent) {
  if (!isDebugWindowVisible.value) return
  const el = debugWindowRef.value
  if (!el) return

  isDraggingDebugWindow.value = true
  const rect = el.getBoundingClientRect()
  debugDragOffset.value = { x: e.clientX - rect.left, y: e.clientY - rect.top }

  try {
    ;(e.currentTarget as HTMLElement).setPointerCapture(e.pointerId)
  } catch {
  }

  e.preventDefault()
}

function onDebugWindowHeaderPointerMove(e: PointerEvent) {
  if (!isDraggingDebugWindow.value) return

  const next = { x: e.clientX - debugDragOffset.value.x, y: e.clientY - debugDragOffset.value.y }
  debugWindowPos.value = getClampedDebugWindowPos(next, DEBUG_WINDOW_SIZE)

  e.preventDefault()
}

function onDebugWindowHeaderPointerUp(e: PointerEvent) {
  if (!isDraggingDebugWindow.value) return
  isDraggingDebugWindow.value = false
  try {
    ;(e.currentTarget as HTMLElement).releasePointerCapture(e.pointerId)
  } catch {
  }
  e.preventDefault()
}

const DEBUG_WINDOW_SIZE = { w: 320, h: 220 }

function toggleDebugWindow() {
  if (!isDebugHudEnabled) return
  const next = !isDebugWindowVisible.value
  isDebugWindowVisible.value = next
  if (next) {
    centerDebugWindow(DEBUG_WINDOW_SIZE)
  }
}

function dispatch(action: GameAction) {
  switch (action.type) {
    case 'ToggleEscMenu':
      isEscMenuOpen.value = !isEscMenuOpen.value
      return
    case 'ToggleDebugWindow':
      toggleDebugWindow()
      return
    case 'ToggleBottomTab':
      activeBottomTab.value = activeBottomTab.value === action.tab ? null : action.tab
      return
    case 'RequestSaveGame':
    case 'RequestLoadGame':
    case 'ShowDevelopingHint':
      showDevelopingHintAtCenter()
      return
    case 'QuitToMainMenu':
      router.push('/main-menu')
      return
  }
}

const uiBindings = useInGameUiInputBindings({ onAction: dispatch })
const inputController = useInGameInputController({ onAction: dispatch })

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
  inputController.attach()

  const container = containerRef.value
  if (container) {
    const r = createThreeWorldRenderer(container, { minZoom: 0.1, maxZoom: 2_000_000, getSpritePath })
    hub.setRenderer(r)
  }

  wsClient = connectSnapshotWs({
    reconnectDelayMs: 3000,
    onSnapshot: (s) => {
      hub.setLastSnapshot(s)
      hub.getRenderer()?.updateFromSnapshot(s)
    },
  })
})

onUnmounted(() => {
  const el = rootRef.value
  if (el) {
    el.removeEventListener('contextmenu', onContextMenu)
  }
  window.removeEventListener('popstate', onPopState)
  inputController.detach()

  wsClient?.close()
  wsClient = null

  hub.getRenderer()?.dispose()
  hub.setRenderer(null)
})
</script>

<template>
  <div ref="rootRef" class="in-game-root">
    <div
      ref="containerRef"
      class="render-container"
      @pointermove="hub.onCanvasPointerMove"
      @pointerdown="(e) => { selection.onPointerDown(e); rightClickCommand.onPointerDown(e) }"
      @pointermove.capture="selection.onPointerMove"
      @pointerup.capture="selection.onPointerUp"
      @pointercancel.capture="selection.cancelSelection"
    ></div>

    <InGameSelectionRect :rect="selection.selectionRect.value" />

    <InGameEscMenu
      v-model:open="isEscMenuOpen"
      @resume="uiBindings.onResume"
      @save="uiBindings.onClickSave"
      @load="uiBindings.onClickLoad"
      @quit="uiBindings.onClickQuit"
    />

    <div
      v-if="isDebugHudEnabled && isDebugWindowVisible"
      ref="debugWindowRef"
      class="debug-window"
      :style="{ transform: `translate(${debugWindowPos.x}px, ${debugWindowPos.y}px)` }"
      role="dialog"
      aria-label="Debug Window"
    >
      <div
        class="debug-window-header"
        @pointerdown="onDebugWindowHeaderPointerDown"
        @pointermove="onDebugWindowHeaderPointerMove"
        @pointerup="onDebugWindowHeaderPointerUp"
        @pointercancel="onDebugWindowHeaderPointerUp"
      >
        <div class="debug-window-title">调试</div>
        <button class="debug-window-close" type="button" @click="toggleDebugWindow">×</button>
      </div>
      <div class="debug-window-body">
        <div class="kv">
          <div class="k">缩放倍率</div>
          <div class="v">{{ hub.debug.zoomText }}</div>
        </div>
        <div class="kv">
          <div class="k">世界状态</div>
          <div class="v debug-text">{{ hub.debug.worldStateText }}</div>
        </div>
        <div class="kv">
          <div class="k">镜头中心(GU)</div>
          <div class="v">{{ hub.debug.cameraCenterText }}</div>
        </div>
        <div class="kv">
          <div class="k">鼠标位置(GU)</div>
          <div class="v">{{ hub.debug.mouseWorldText }}</div>
        </div>
      </div>
    </div>

    <InGameOverviewPanel
      :day-text="hub.overview.dayText.value"
      :tick-cost-text="hub.overview.tickCostText.value"
      :sector-count-text="hub.overview.sectorCountText.value"
    />

    <InGameDevelopmentPanel v-if="activeBottomTab === 'development'" @build="uiBindings.onBuild" />
    <InGameMilitaryPanel v-if="activeBottomTab === 'military'" @developing="uiBindings.onDeveloping" />
    <InGameTechPanel v-if="activeBottomTab === 'tech'" @developing="uiBindings.onDeveloping" />
    <InGameDomesticPanel v-if="activeBottomTab === 'domestic'" @developing="uiBindings.onDeveloping" />
    <InGameDiplomacyPanel v-if="activeBottomTab === 'diplomacy'" @developing="uiBindings.onDeveloping" />

    <InGameBuildBar :active-tab="activeBottomTab" @select="uiBindings.onSelectBottomTab" />
  </div>
</template>

<style scoped>
.debug-window {
  position: absolute;
  left: 0;
  top: 0;
  width: 320px;
  max-width: calc(100vw - 16px);
  border-radius: 14px;
  background: color-mix(in srgb, var(--background-color) 65%, rgba(0, 0, 0, 0.35));
  border: 1px solid color-mix(in srgb, var(--glow-color) 25%, transparent);
  backdrop-filter: blur(12px);
  -webkit-backdrop-filter: blur(12px);
  box-shadow: 0 0 18px color-mix(in srgb, var(--glow-color) 18%, transparent);
  z-index: 30;
  pointer-events: auto;
}

.debug-window-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 10px 12px;
  border-bottom: 1px solid color-mix(in srgb, var(--glow-color) 18%, transparent);
  cursor: grab;
}

.debug-window-header:active {
  cursor: grabbing;
}

.debug-window-title {
  font-size: 12px;
  letter-spacing: 1px;
  color: var(--text-color-hover);
  text-shadow: 0 0 6px color-mix(in srgb, var(--glow-color) 35%, transparent);
}

.debug-window-close {
  width: 26px;
  height: 26px;
  border-radius: 8px;
  border: 1px solid color-mix(in srgb, var(--glow-color) 22%, transparent);
  background: transparent;
  color: var(--text-color);
  font-family: 'Orbitron', sans-serif;
  cursor: pointer;
}

.debug-window-close:hover {
  border-color: color-mix(in srgb, var(--glow-color) 58%, transparent);
  color: var(--text-color-hover);
}

.debug-window-body {
  padding: 10px 12px;
}

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

.render-container {
  position: absolute;
  inset: 0;
  touch-action: none;
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

.debug-text {
  max-width: 180px;
  word-break: break-all;
  text-align: right;
  font-size: 11px;
  color: color-mix(in srgb, var(--text-color) 65%, transparent);
}
</style>

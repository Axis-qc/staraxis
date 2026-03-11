<script setup lang="ts">
/**
 * @file InGameView.vue
 *
 * @description
 * 游戏主界面视图（/in-game）。
 *
 * 本视图负责：
 * - 初始化并承载渲染画布（`createWorldRenderManager`）。
 * - 建立与后端的快照 WebSocket 连接（`connectSnapshotWs`）。
 * - 提供游戏内 HUD/UI 的顶层布局编排。
 * - 协调视野订阅（`useVisibleSectors`）、调试窗口（`useInGameDebugWindow`）与弹窗管理（`useInGameWindows`）。
 *
 * 说明：
 * - 本视图作为“编排层”，不实现具体权威逻辑，主要负责初始化核心组件并挂载 UI。
 * - 核心交互与状态管理逻辑已下沉至专用的 composables，保持视图组件精简喵。
 *
 * @usage
 * - 通过 Vue Router 进入（路由 `/in-game`）。
 * - 初始化渲染器与 WS 客户端后，由 `useVisibleSectors` 自动处理视野星区上报。
 * - 将画布 pointermove 事件绑定到 `hub.onCanvasPointerMove`，用于计算鼠标世界坐标（GU）。
 *
 * @provides
 * - **渲染承载**：全屏渲染容器（WebGL/Three）。
 * - **UI 编排**：HUD 布局（时间、总览、底栏）与业务面板切换。
 * - **窗口系统**：ESC 菜单、行星详情窗口、可拖拽调试浮窗（DEV）。
 *
 * @api
 * - WebSocket：由 `connectSnapshotWs` 连接，并通过 `useVisibleSectors` 实现按需订阅。
 *
 * @resources
 * - `../rendering/worldRenderManager`：渲染系统入口。
 * - `../features/inGame/composables/useVisibleSectors`：视野计算、按需订阅与缓存清理。
 * - `../features/inGame/composables/useInGameDebugWindow`：调试窗口交互与性能监控开关。
 * - `../features/inGame/composables/useInGameWindows`：UI 弹窗（ESC、行星窗口）状态管理。
 * - `../features/inGame/composables/useInGameDataHub`：实时数据聚合与派发。
 *
 * @potential_issues
 * - **性能**：大量实体渲染时的同步压力，已通过按需推送与增量更新缓解喵。
 * - **输入拦截**：需确保 UI 面板打开时正确拦截底层相机操作。
 */
import { onMounted, onUnmounted, ref, watch } from 'vue'
import { storeToRefs } from 'pinia'
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
import { connectSnapshotWs, type SnapshotWsClient, type EntitySnapshot } from '../net/snapshotWs'
import { createWorldRenderManager, type WorldRenderer } from '../rendering/worldRenderManager'
import ShipPanel from '../features/inGame/components/ShipPanel.vue'
import { sendMoveShipCommand } from '../net/shipCommandsApi'
import { useInGameDataHub } from '../features/inGame/composables/useInGameDataHub'
import { useInGameInputController } from '../features/inGame/input/useInGameInputController'
import { useInGameUiInputBindings } from '../features/inGame/input/useInGameUiInputBindings'
import type { GameAction, InGameBottomTab } from '../features/inGame/input/gameActions'
import { useRtsSelection } from '../features/inGame/selection/useRtsSelection'
import InGameSelectionRect from '../features/inGame/components/InGameSelectionRect.vue'
import InGameTimeHud from '../features/inGame/components/InGameTimeHud.vue'
import InGameSelectionListHud from '../features/inGame/components/InGameSelectionListHud.vue'
import InGamePlanetWindow from '../features/inGame/components/InGamePlanetWindow.vue'
import { useRtsRightClickCommand } from '../features/inGame/commands/useRtsRightClickCommand'
import { useVisibleSectors } from '../features/inGame/composables/useVisibleSectors'
import { useInGameDebugWindow } from '../features/inGame/composables/useInGameDebugWindow'
import { useInGameWindows } from '../features/inGame/composables/useInGameWindows'
import { createCameraStatePersister, loadPersistedCameraState } from '../features/inGame/composables/useCameraPersist'

import { useAuthStore } from '../stores/auth'
import { useWorldSessionStore } from '../stores/worldSession'
import { manualSaveWorld, listAvailableSpawns, confirmSpawn } from '../net/worldSavesApi'

const router = useRouter()
const { getSpritePath } = useAstroAssets()
const devTooltip = useDevTooltip()

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
const debugWindowRef = ref<HTMLDivElement | null>(null)

const hub = useInGameDataHub()
const wsClient = ref<SnapshotWsClient | null>(null)
const renderer = ref<WorldRenderer | null>(null)
const auth = useAuthStore()
const worldSession = useWorldSessionStore()

/**
 * 出生流程（并入 in-game）状态喵。
 */
const spawnSystems = ref<Array<{ systemId: number; sectorQ: number; sectorR: number; centerX: number; centerY: number; starCount: number; planetCount: number }>>([])
const selectedSpawnSystemId = ref<number | null>(null)
const spawnLoading = ref(false)
const spawnError = ref('')
const { playerId } = storeToRefs(auth)

// 镜头状态持久化（sessionStorage）喵
const cameraPersister = createCameraStatePersister(renderer, playerId)

let lastSnapshotLogTime = 0
let isFirstSnapshotLog = true

const activeBottomTab = ref<InGameBottomTab | null>(null)

// 标记是否需要执行初始出生舰船聚焦（如果没有缓存镜头状态）喵
let needsInitialFocusOnInitialShip = false
let hasAppliedInitialShipFocus = false

// --- 逻辑下沉集成 --- //
const {
  isEscMenuOpen, planetWindowOpen, planetEntity,
  openPlanetWindow, closePlanetWindow, toggleEscMenu
} = useInGameWindows()

const {
  isDebugHudEnabled, isDebugWindowVisible, debugWindowPos,
  toggleDebugWindow, onDebugWindowHeaderPointerDown,
  onDebugWindowHeaderPointerMove, onDebugWindowHeaderPointerUp
} = useInGameDebugWindow(hub)

// 选中的舰船面板状态喵
const selectedShipPanelOpen = ref(false)
const selectedShipEntity = ref<EntitySnapshot | null>(null)

// RTS 选择系统喵
const selection = useRtsSelection({
  getEntities: () => {
    const r = hub.getRenderer()
    if (!r) return []
    const entities = hub.entities.value
    const out: Array<{ id: number; type: 'STAR' | 'PLANET' | 'SHIP'; worldPosGU: { x: number; y: number } }> = []
    for (const e of entities) {
      const p = r.getEntityWorldPosGU(e.entityId)
      if (!p) continue
      if (e.entityType === 'STAR' || e.entityType === 'PLANET' || e.entityType === 'SHIP') {
        out.push({ id: e.entityId, type: e.entityType, worldPosGU: p })
      }
    }
    return out
  },
  worldToClient: (world) => {
    const el = containerRef.value
    const r = hub.getRenderer()
    if (!el || !r) return { x: 0, y: 0 }
    const rect = el.getBoundingClientRect()
    const cx = rect.left + rect.width / 2
    const cy = rect.top + rect.height / 2
    const sx = cx + (world.x - r.cameraWorldPosGU.x) / r.zoom.value
    const sy = cy - (world.y - r.cameraWorldPosGU.y) / r.zoom.value
    return { x: sx, y: sy }
  },
  onSelectionChange: (selectedIds: number[]) => {
    // 如果选中的是舰船，显示舰船面板喵
    if (selectedIds.length === 1) {
      const entity = hub.entities.value.find(e => e.entityId === selectedIds[0])
      if (entity && entity.entityType === 'SHIP') {
        selectedShipEntity.value = entity
        selectedShipPanelOpen.value = true
        return
      }
    }
    // 否则关闭舰船面板
    selectedShipPanelOpen.value = false
    selectedShipEntity.value = null
  },
})

// 视野订阅与分区加载管理喵
const visibleSectors = useVisibleSectors(
  renderer,
  wsClient,
  hub.entities,
  selection.selectedIds
)

// 监听渲染器实例的变化，绑定事件驱动的视野更新喵
watch(
  renderer,
  (newR) => {
    visibleSectors.handleRendererChange(newR)
  },
  { immediate: true }
)

// 监听 WS 和渲染器就绪状态，同步国家 ID 并触发首次视野上报喵
watch(
  [wsClient, renderer],
  ([ws, r]) => {
    if (ws && r) {
      const nationId = auth.selectedNationId
      if (nationId) {
        ws.setNationId(nationId)
        if ((r as any).setCurrentNationId) {
          (r as any).setCurrentNationId(nationId)
        }
      }
      // 这里的首次强制上报由 handleRendererChange 内部处理喵
    }
  },
  { immediate: true }
)

// 右键命令系统喵
const rightClickCommand = useRtsRightClickCommand({
  getRenderer: () => hub.getRenderer(),
  getSelectedIds: () => selection.selectedIds.value,
  getSelectedEntities: () => {
    const ids = selection.selectedIds.value
    return hub.entities.value.filter(e => ids.includes(e.entityId))
  },
  onCommandIntent: (intent) => {
    console.log('[Command Intent]', intent)
  },
  sendCommand: async (intent, entities) => {
    if (intent.type !== 'Move') return

    const worldId = worldSession.selectedWorldId
    const nationId = auth.selectedNationId
    if (!worldId || !nationId) return

    // 只发送移动命令给舰船喵
    for (const entity of entities) {
      if (entity.entityType !== 'SHIP') continue

      console.log(`[Ship Command] Moving ship ${entity.entityId} to (${intent.targetWorldGU.x.toFixed(0)}, ${intent.targetWorldGU.y.toFixed(0)})`)

      // 发送移动命令到后端喵
      const result = await sendMoveShipCommand({
        worldId,
        nationId,
        shipEntityId: entity.entityId,
        targetX: intent.targetWorldGU.x,
        targetY: intent.targetWorldGU.y,
      })

      if (!result.ok) {
        console.error('[Ship Command] Failed:', result.error)
      } else {
        console.log('[Ship Command] Success!')
      }
    }
  },
})

// 同步渲染器选中状态喵
watch(
  () => selection.selectedIds.value,
  (ids) => {
    hub.getRenderer()?.setSelectedEntityIds(ids)
  },
  { deep: true },
)

function onContextMenu(e: MouseEvent) {
  e.preventDefault()
}

function chooseRandomSpawnInGame() {
  if (!spawnSystems.value.length) {
    selectedSpawnSystemId.value = null
    return
  }
  const idx = Math.floor(Math.random() * spawnSystems.value.length)
  selectedSpawnSystemId.value = spawnSystems.value[idx]?.systemId ?? null
}

async function confirmSpawnInGame() {
  if (!auth.playerId || !worldSession.selectedWorldId) {
    spawnError.value = 'player_or_world_required'
    return
  }
  if (!selectedSpawnSystemId.value) {
    spawnError.value = 'spawn_system_required'
    return
  }

  spawnLoading.value = true
  spawnError.value = ''
  try {
    const resp = await confirmSpawn({
      worldId: worldSession.selectedWorldId,
      playerId: auth.playerId,
      chosenSystemId: selectedSpawnSystemId.value,
    })
    if (!resp.ok) {
      throw new Error(resp.error || 'spawn_failed')
    }

    if (resp.nationId) {
      auth.setSelectedNationId(resp.nationId)
    }
    worldSession.setPlayerWorldState('SPAWNED')
  } catch (e: any) {
    spawnError.value = String(e?.message || e)
  } finally {
    spawnLoading.value = false
  }
}

function onPopState() {
  try {
    history.pushState({ saLock: true }, '', location.href)
  } catch { }
}

async function requestManualSave() {
  if (!worldSession.selectedWorldId) {
    showDevelopingHintAtCenter()
    return
  }

  try {
    const resp = await manualSaveWorld(worldSession.selectedWorldId)
    if (!resp.ok) {
      throw new Error(resp.error || 'manual_save_failed')
    }
    devTooltip.show('手动存档完成喵', new MouseEvent('click', { clientX: window.innerWidth / 2, clientY: 60 }))
  } catch (e: any) {
    devTooltip.show(`手动存档失败: ${String(e?.message || e)}喵`, new MouseEvent('click', { clientX: window.innerWidth / 2, clientY: 60 }))
  }
}

function dispatch(action: GameAction) {
  switch (action.type) {
    case 'ToggleEscMenu':
      toggleEscMenu()
      return
    case 'ToggleDebugWindow':
      toggleDebugWindow()
      return
    case 'ToggleBottomTab':
      activeBottomTab.value = activeBottomTab.value === action.tab ? null : action.tab
      return
    case 'RequestSaveGame':
      void requestManualSave()
      return
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

onMounted(async () => {
  const el = rootRef.value
  if (el) {
    el.addEventListener('contextmenu', onContextMenu)
  }

  try {
    history.pushState({ saLock: true }, '', location.href)
  } catch { }
  window.addEventListener('popstate', onPopState)
  inputController.attach()

  const container = containerRef.value
  if (container) {
    const persisted = loadPersistedCameraState(playerId.value)
    needsInitialFocusOnInitialShip = !persisted
    const r = createWorldRenderManager(container, {
      minZoom: 0.1,
      maxZoom: 2_000_000,
      getSpritePath,
      initialCameraPos: persisted?.cameraWorldPosGU,
      initialZoom: persisted?.zoom,
    })
    renderer.value = r
    hub.setRenderer(r)

    cameraPersister.attach()
    cameraPersister.schedulePersist()
  }

  wsClient.value = connectSnapshotWs({
    reconnectDelayMs: 3000,
    onSnapshot: (s) => {
      const now = Date.now()
      if (isFirstSnapshotLog || now - lastSnapshotLogTime >= 60000) {
        const ok = !!s.ok
        const sectorCentersCount = s.realTimeWorldState?.sectorCenters?.length ?? -1
        const entities = s.realTimeWorldState?.entities ?? []
        const entitiesCount = entities.length

        // 专门统计行星数据喵
        const planets = entities.filter(e => e.entityType === 'PLANET')
        const planetsWithDetails = planets.filter(p => p.details !== null)

        // 统计私有实体中的舰船喵
        const privateTiers = s.realTimeWorldState?.privateEntitiesByIntelLevel ?? {}
        const privateEntities = Object.values(privateTiers).flatMap(arr => arr ?? [])
        const ships = privateEntities.filter(e => e.entityType === 'SHIP')
        const shipsWithDetails = ships.filter(s => s.details !== null)

        console.log(
          `[Snapshot Debug] first=${isFirstSnapshotLog} ok=${ok} sectors=${sectorCentersCount} entities=${entitiesCount} planets=${planets.length}(withDetails:${planetsWithDetails.length}) ships=${ships.length}(withDetails:${shipsWithDetails.length}) 喵`,
        )
        if (planets.length > 0 && planetsWithDetails.length === 0) {
          console.error('[Snapshot Debug] Warning: Planets exist but all details are NULL! 喵')
        }
        if (ships.length > 0) {
          console.log('[Snapshot Debug] Ships found:', ships.map(s => ({ id: s.entityId, owner: s.ownerNationId, pos: s.posWorldGU })))
        }
        isFirstSnapshotLog = false
        lastSnapshotLogTime = now
      }

      hub.setLastSnapshot(s)
      hub.getRenderer()?.updateFromSnapshot(s)

      // 若本会话没有镜头缓存，则扫描本国实体列表找到初始舰船并做一次初始聚焦喵。
      if (!hasAppliedInitialShipFocus && needsInitialFocusOnInitialShip) {
        const r = hub.getRenderer()
        const entities = s.realTimeWorldState?.entities ?? []
        const nationId = auth.selectedNationId

        if (r && nationId) {
          // 合并公开和私有实体来查找初始舰船喵
          const privateTiers = s.realTimeWorldState?.privateEntitiesByIntelLevel ?? {}
          const privateEntities = Object.values(privateTiers).flatMap(arr => arr ?? [])
          const allEntities = [...entities, ...privateEntities]

          // 新策略：优先聚焦携带固定 flag 的初始出生舰船喵。
          const initialShip = allEntities.find((e) => {
            if (e.entityType !== 'SHIP') return false
            const d: any = e.details
            const flags: string[] = Array.isArray(d?.customFlags) ? d.customFlags : []
            return e.ownerNationId === nationId && flags.includes('INITIAL_SPAWN_SHIP')
          })

          if (initialShip && initialShip.posWorldGU) {
            r.cameraWorldPosGU.set(initialShip.posWorldGU.x, initialShip.posWorldGU.y)
            r.applyCameraTransform()
            hasAppliedInitialShipFocus = true
            needsInitialFocusOnInitialShip = false
            cameraPersister.schedulePersist()
          }
        }
      }
    },
  })

  // 出生流程并入 in-game：进入游戏后若未出生，加载可选出生点喵。
  if (worldSession.playerWorldState === 'SPAWN_PENDING' && worldSession.selectedWorldId) {
    spawnLoading.value = true
    try {
      const spawnsResp = await listAvailableSpawns(worldSession.selectedWorldId)
      if (!spawnsResp.ok) {
        throw new Error(spawnsResp.error || 'list_spawns_failed')
      }
      spawnSystems.value = spawnsResp.systems || []
      selectedSpawnSystemId.value = spawnSystems.value[0]?.systemId ?? null
    } catch (e: any) {
      spawnError.value = String(e?.message || e)
    } finally {
      spawnLoading.value = false
    }
  }
})

onUnmounted(() => {
  const el = rootRef.value
  if (el) {
    el.removeEventListener('contextmenu', onContextMenu)
  }
  window.removeEventListener('popstate', onPopState)
  inputController.detach()

  wsClient.value = null

  cameraPersister.detach()

  hub.getRenderer()?.dispose()
  hub.setRenderer(null)
  renderer.value = null
})
</script>

<template>
  <div ref="rootRef" class="in-game-root">
    <div ref="containerRef" class="render-container" @pointermove="hub.onCanvasPointerMove"
      @pointerdown="(e) => { selection.onPointerDown(e); rightClickCommand.onPointerDown(e) }"
      @pointermove.capture="selection.onPointerMove" @pointerup.capture="selection.onPointerUp"
      @pointercancel.capture="selection.cancelSelection"></div>

    <InGameSelectionListHud :selected-ids="selection.selectedIds.value" :entities="hub.entities.value" @open="({ entityId }) => {
      const entity = hub.entities.value.find(e => e.entityId === entityId)
      if (entity && entity.entityType === 'PLANET') {
        openPlanetWindow(entity)
      } else {
        showDevelopingHintAtCenter()
      }
    }" @focus="({ entityId }) => {
      const r = hub.getRenderer()
      if (!r) return
      const p = r.getEntityWorldPosGU(entityId)
      if (!p) return
      r.cameraWorldPosGU.set(p.x, p.y)
      r.applyCameraTransform()
    }" />

    <InGameTimeHud :snapshot="hub.lastSnapshot.value" :ws-client="wsClient" />

    <InGamePlanetWindow v-if="planetWindowOpen && planetEntity" :entity="planetEntity"
      :snapshot="hub.lastSnapshot.value" @close="closePlanetWindow" />

    <InGameSelectionRect :rect="selection.selectionRect.value" />

    <div v-if="worldSession.playerWorldState === 'SPAWN_PENDING'" class="spawn-overlay">
      <div class="spawn-panel">
        <h3>选择出生点</h3>
        <div class="row">
          <button class="sa-btn" :disabled="spawnLoading" @click="chooseRandomSpawnInGame">随机位置</button>
          <button class="sa-btn primary" :disabled="spawnLoading" @click="confirmSpawnInGame">确认出生</button>
        </div>
        <div class="list">
          <label v-for="s in spawnSystems" :key="s.systemId" class="spawn-item">
            <input type="radio" name="spawn-in-game" :value="s.systemId" v-model.number="selectedSpawnSystemId" />
            <span>system={{ s.systemId }} sector=({{ s.sectorQ }},{{ s.sectorR }}) planets={{ s.planetCount }}</span>
          </label>
        </div>
        <div v-if="spawnError" class="spawn-error">{{ spawnError }}</div>
      </div>
    </div>

    <InGameEscMenu v-model:open="isEscMenuOpen" @resume="uiBindings.onResume" @save="uiBindings.onClickSave"
      @load="uiBindings.onClickLoad" @quit="uiBindings.onClickQuit" />

    <div v-if="isDebugHudEnabled && isDebugWindowVisible" ref="debugWindowRef" class="debug-window"
      :style="{ transform: `translate(${debugWindowPos.x}px, ${debugWindowPos.y}px)` }" role="dialog"
      aria-label="Debug Window">
      <div class="debug-window-header" @pointerdown="(e) => onDebugWindowHeaderPointerDown(e, debugWindowRef)"
        @pointermove="onDebugWindowHeaderPointerMove" @pointerup="onDebugWindowHeaderPointerUp"
        @pointercancel="onDebugWindowHeaderPointerUp">
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
          <div class="k">性能(FPS)</div>
          <div class="v">{{ hub.performance.fpsText }}</div>
        </div>
        <div class="perf-chart" aria-label="FPS history chart">
          <svg :width="296" :height="44" viewBox="0 0 296 44" role="img" aria-label="FPS history">
            <rect x="0" y="0" width="296" height="44" fill="transparent" />
            <polyline v-if="hub.performance.fpsHistory.value.length" :points="hub.performance.fpsHistory.value
              .map((fps, i) => {
                const n = hub.performance.fpsHistory.value.length
                const x = n <= 1 ? 0 : (i / (n - 1)) * 296
                const y = 44 - Math.min(44, (fps / 60) * 44)
                return `${x},${y}`
              })
              .join(' ')" fill="none" stroke="rgba(127,211,255,0.9)" stroke-width="2" stroke-linejoin="round"
              stroke-linecap="round" />
          </svg>
          <div class="perf-chart-caption">最近 60 秒 FPS（上限按 60 归一化）</div>
        </div>
        <div class="kv">
          <div class="k">镜头中心(GU)</div>
          <div class="v">{{ hub.debug.cameraCenterText }}</div>
        </div>
        <div class="kv">
          <div class="k">鼠标位置(GU)</div>
          <div class="v">{{ hub.debug.mouseWorldText }}</div>
        </div>
        <div class="kv">
          <div class="k">国家ID</div>
          <div class="v">{{ auth.selectedNationId || '未设置' }}</div>
        </div>
      </div>
    </div>

    <InGameOverviewPanel
      :day-text="hub.overview.dayText.value"
      :tick-cost-text="hub.overview.tickCostText.value"
      :sector-count-text="hub.overview.sectorCountText.value"
      :owned-planets="hub.overview.ownedPlanets.value"
      :owned-ships="hub.overview.ownedShips.value"
      :owned-stations="hub.overview.ownedStations.value"
      @focus-entity="(entityId: number) => {
        const r = hub.getRenderer()
        if (!r) return
        const p = r.getEntityWorldPosGU(entityId)
        if (!p) return
        r.cameraWorldPosGU.set(p.x, p.y)
        r.applyCameraTransform()
      }"
    />

    <!-- 舰船信息面板喵 -->
    <ShipPanel
      :ship="selectedShipEntity"
      :is-open="selectedShipPanelOpen"
      @close="selectedShipPanelOpen = false"
      @focus="() => {
        if (!selectedShipEntity) return
        const r = hub.getRenderer()
        if (!r) return
        const p = r.getEntityWorldPosGU(selectedShipEntity.entityId)
        if (!p) return
        r.cameraWorldPosGU.set(p.x, p.y)
        r.applyCameraTransform()
      }"
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

.perf-chart {
  padding: 6px 0;
}

.perf-chart svg {
  display: block;
  width: 100%;
  border-radius: 10px;
  border: 1px solid color-mix(in srgb, var(--glow-color) 18%, transparent);
  background: color-mix(in srgb, var(--background-color) 85%, rgba(0, 0, 0, 0.25));
}

.perf-chart-caption {
  margin-top: 4px;
  font-size: 11px;
  color: color-mix(in srgb, var(--text-color) 60%, transparent);
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

.spawn-overlay {
  position: absolute;
  inset: 0;
  background: rgba(0, 0, 0, 0.45);
  display: grid;
  place-items: center;
  z-index: 60;
}

.spawn-panel {
  width: min(880px, 92vw);
  max-height: 80vh;
  overflow: auto;
  border: 1px solid color-mix(in srgb, var(--glow-color) 25%, transparent);
  border-radius: 16px;
  background: color-mix(in srgb, var(--panel-bg) 65%, rgba(0, 0, 0, 0.35));
  backdrop-filter: blur(12px);
  -webkit-backdrop-filter: blur(12px);
  box-shadow: 0 0 32px color-mix(in srgb, var(--glow-color) 15%, transparent);
  padding: 24px;
}

.spawn-panel .row {
  display: flex;
  gap: 8px;
  margin: 8px 0;
}

.spawn-panel .list {
  display: grid;
  gap: 8px;
}

.spawn-item {
  display: flex;
  align-items: center;
  gap: 12px;
  border: 1px solid color-mix(in srgb, var(--border-color) 40%, transparent);
  border-radius: 10px;
  padding: 12px;
  background: color-mix(in srgb, var(--panel-bg) 30%, rgba(0, 0, 0, 0.2));
  transition: all 0.2s ease;
  cursor: pointer;
}

.spawn-item:hover {
  border-color: var(--glow-color);
  background: color-mix(in srgb, var(--panel-bg) 45%, rgba(0, 0, 0, 0.3));
  box-shadow: 0 0 12px color-mix(in srgb, var(--glow-color) 20%, transparent);
  transform: translateY(-2px);
}

.spawn-item input[type="radio"] {
  accent-color: var(--glow-color);
}

.spawn-error {
  color: var(--danger-color);
  margin-top: 12px;
  padding: 10px 14px;
  border-radius: 8px;
  background: color-mix(in srgb, var(--danger-color) 10%, transparent);
  border: 1px solid color-mix(in srgb, var(--danger-color) 30%, transparent);
  font-size: 0.9rem;
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

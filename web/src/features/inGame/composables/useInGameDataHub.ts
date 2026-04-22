import { computed, onUnmounted, ref, shallowRef, type Ref } from 'vue'
import { useAuthStore } from '../../../stores/auth'
import type { EntitySnapshot, SnapshotHighFreqMessage } from '../../../net/snapshotWs'
import type { WorldRenderer as ThreeWorldRenderer } from '../../../rendering/worldRenderManager'
import { getLocalVisibleWorld, type LowFreqWorldState } from '../../../game/world'

type Vec2 = { x: number; y: number }

type DebugUiModel = {
  cameraPoseText: Ref<string>
  cameraCenterText: Ref<string>
  mouseWorldText: Ref<string>
  worldStateText: Ref<string>
}

type PerformanceUiModel = {
  fpsText: Ref<string>
  fpsHistory: Ref<number[]>
}

type OverviewUiModel = {
  dayText: Ref<string>
  tickCostText: Ref<string>
  sectorCountText: Ref<string>
  ownedEntities: Ref<EntitySnapshot[]>
  ownedPlanets: Ref<EntitySnapshot[]>
  ownedShips: Ref<EntitySnapshot[]>
  ownedStations: Ref<EntitySnapshot[]>
}

export type InGameDataHub = {
  setRenderer: (r: ThreeWorldRenderer | null) => void
  getRenderer: () => ThreeWorldRenderer | null
  setLastHighFreqSnapshot: (s: SnapshotHighFreqMessage | null) => void
  syncLowFreqStateFromWorld: () => void
  syncEntitiesFromWorld: () => void
  onCanvasPointerMove: (e: PointerEvent) => void
  entities: Ref<EntitySnapshot[]>
  lastHighFreqSnapshot: Ref<SnapshotHighFreqMessage | null>
  lowFreqState: Ref<LowFreqWorldState | null>
  overview: OverviewUiModel
  debug: DebugUiModel
  performance: PerformanceUiModel
  setPerformanceTracking: (active: boolean) => void
}

function buildDebugSnapshotText(
  highFreqSnapshot: SnapshotHighFreqMessage | null,
  lowFreqState: LowFreqWorldState | null,
  entityCount: number,
) {
  if (!highFreqSnapshot || !highFreqSnapshot.ok) {
    return 'no_snapshot'
  }

  const tickCost = highFreqSnapshot.tickCostMs == null ? '' : `${highFreqSnapshot.tickCostMs}ms`
  const highFreqTick = highFreqSnapshot.simulationTick
  const lowFreqVersion = lowFreqState?.version ?? '-'
  return `hf=${highFreqTick} lf=${lowFreqVersion} ${tickCost} entities=${entityCount} ${highFreqSnapshot.error ?? ''}`.trim()
}

export function useInGameDataHub() {
  const renderer = ref<ThreeWorldRenderer | null>(null)
  const lastHighFreqSnapshot = ref<SnapshotHighFreqMessage | null>(null)
  const lowFreqState = ref<LowFreqWorldState | null>(null)
  const entities = shallowRef<EntitySnapshot[]>([])
  const mouseWorldPosGU = ref<Vec2 | null>(null)
  const debugUiTick = ref(0)
  const fpsText = ref('-')
  const fpsHistory = shallowRef<number[]>([])

  const FPS_HISTORY_MAX_SECONDS = 60

  let trackingEnabled = false
  let trackingStartedAtMs = 0
  let frameCountInSecond = 0
  let secondWindowStartMs = 0
  let rafId = 0

  const rafLoop = (timeMs: number) => {
    debugUiTick.value++

    if (trackingEnabled) {
      if (trackingStartedAtMs === 0) {
        trackingStartedAtMs = timeMs
        secondWindowStartMs = timeMs
        frameCountInSecond = 0
        fpsHistory.value = []
      }

      frameCountInSecond++
      const elapsedInSecond = timeMs - secondWindowStartMs
      if (elapsedInSecond >= 1000) {
        const fps = Math.round((frameCountInSecond * 1000) / elapsedInSecond)
        fpsText.value = String(fps)

        const nextHistory = fpsHistory.value.slice()
        nextHistory.push(fps)
        if (nextHistory.length > FPS_HISTORY_MAX_SECONDS) {
          nextHistory.splice(0, nextHistory.length - FPS_HISTORY_MAX_SECONDS)
        }
        fpsHistory.value = nextHistory

        secondWindowStartMs = timeMs
        frameCountInSecond = 0
      }
    }

    rafId = requestAnimationFrame(rafLoop)
  }
  rafId = requestAnimationFrame(rafLoop)

  onUnmounted(() => {
    cancelAnimationFrame(rafId)
  })

  function setPerformanceTracking(active: boolean) {
    trackingEnabled = active
    fpsText.value = '-'
    fpsHistory.value = []
    trackingStartedAtMs = 0
    secondWindowStartMs = 0
    frameCountInSecond = 0
  }

  function syncEntitiesFromWorld() {
    entities.value = getLocalVisibleWorld().getAllEntitySnapshots()
  }

  function syncLowFreqStateFromWorld() {
    lowFreqState.value = getLocalVisibleWorld().getLatestLowFreqState()
  }

  function setRenderer(r: ThreeWorldRenderer | null) {
    renderer.value = r
  }

  function getRenderer() {
    return renderer.value
  }

  function setLastHighFreqSnapshot(s: SnapshotHighFreqMessage | null) {
    lastHighFreqSnapshot.value = s
    if (s?.ok) {
      syncEntitiesFromWorld()
    }
  }

  function onCanvasPointerMove(e: PointerEvent) {
    const currentRenderer = renderer.value
    if (!currentRenderer) {
      mouseWorldPosGU.value = null
      return
    }

    const rect = (e.currentTarget as HTMLElement).getBoundingClientRect()
    const localX = e.clientX - rect.left
    const localY = e.clientY - rect.top
    const cx = rect.width / 2
    const cy = rect.height / 2
    const worldX = currentRenderer.cameraWorldPosGU.x + (localX - cx) * currentRenderer.zoom.value
    const worldY = currentRenderer.cameraWorldPosGU.y - (localY - cy) * currentRenderer.zoom.value
    mouseWorldPosGU.value = { x: worldX, y: worldY }
  }

  const overviewDayText = computed(() => {
    const latestLowFreqState = lowFreqState.value
    if (
      !latestLowFreqState ||
      latestLowFreqState.year == null ||
      latestLowFreqState.month == null ||
      latestLowFreqState.day == null
    ) {
      return '-'
    }

    const year = latestLowFreqState.year
    const month = String(latestLowFreqState.month).padStart(2, '0')
    const day = String(latestLowFreqState.day).padStart(2, '0')
    return `${year}-${month}-${day}`
  })

  const overviewTickCostText = computed(() => {
    const snapshot = lastHighFreqSnapshot.value
    if (!snapshot || !snapshot.ok || snapshot.tickCostMs == null) {
      return '-'
    }
    return `${snapshot.tickCostMs} ms`
  })

  const overviewSectorCountText = computed(() => {
    const latestLowFreqState = lowFreqState.value
    if (!latestLowFreqState) {
      return '-'
    }
    return String(latestLowFreqState.sectorCenters.length)
  })

  const auth = useAuthStore()
  const ownedEntities = computed(() => {
    if (!auth.selectedNationId) {
      return []
    }
    return entities.value.filter(entity => entity.ownerNationId === auth.selectedNationId)
  })

  const ownedPlanets = computed(() => ownedEntities.value.filter(entity => entity.entityType === 'PLANET'))
  const ownedShips = computed(() => ownedEntities.value.filter(entity => entity.entityType === 'SHIP'))
  const ownedStations = computed(() => ownedEntities.value.filter(entity => entity.entityType === 'STATION'))

  const debugCameraPoseText = computed(() => {
    void debugUiTick.value
    const currentRenderer = renderer.value
    if (!currentRenderer) {
      return '-'
    }
    return `(${currentRenderer.cameraWorldPosGU.x.toFixed(2)}, ${currentRenderer.cameraWorldPosGU.y.toFixed(2)}, ${currentRenderer.cameraHeight.value.toFixed(2)}) (zoom=${currentRenderer.zoom.value.toFixed(4)})`
  })

  const debugCameraCenterText = computed(() => {
    void debugUiTick.value
    const currentRenderer = renderer.value
    if (!currentRenderer) {
      return '-'
    }
    return `(${currentRenderer.cameraWorldPosGU.x.toFixed(2)}, ${currentRenderer.cameraWorldPosGU.y.toFixed(2)})`
  })

  const debugMouseWorldText = computed(() => {
    const position = mouseWorldPosGU.value
    if (!position) {
      return '-'
    }
    return `(${position.x.toFixed(2)}, ${position.y.toFixed(2)})`
  })

  const debugWorldStateText = computed(() =>
    buildDebugSnapshotText(lastHighFreqSnapshot.value, lowFreqState.value, entities.value.length),
  )

  return {
    setRenderer,
    getRenderer,
    setLastHighFreqSnapshot,
    syncLowFreqStateFromWorld,
    syncEntitiesFromWorld,
    onCanvasPointerMove,
    entities,
    lastHighFreqSnapshot,
    lowFreqState,
    overview: {
      dayText: overviewDayText,
      tickCostText: overviewTickCostText,
      sectorCountText: overviewSectorCountText,
      ownedEntities,
      ownedPlanets,
      ownedShips,
      ownedStations,
    },
    debug: {
      cameraPoseText: debugCameraPoseText,
      cameraCenterText: debugCameraCenterText,
      mouseWorldText: debugMouseWorldText,
      worldStateText: debugWorldStateText,
    },
    performance: {
      fpsText,
      fpsHistory,
    },
    setPerformanceTracking,
  } satisfies InGameDataHub
}

import { computed, onUnmounted, ref, shallowRef, type Ref } from 'vue'
import { useAuthStore } from '../../../stores/auth'
import type { EntitySnapshot, SnapshotMessage } from '../../../net/snapshotWs'
import type { WorldRenderer as ThreeWorldRenderer } from '../../../rendering/worldRenderManager'
import { getLocalVisibleWorld } from '../../../game/world'

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
  setLastSnapshot: (s: SnapshotMessage | null) => void
  syncEntitiesFromWorld: () => void
  onCanvasPointerMove: (e: PointerEvent) => void
  entities: Ref<EntitySnapshot[]>
  lastSnapshot: Ref<SnapshotMessage | null>
  overview: OverviewUiModel
  debug: DebugUiModel
  performance: PerformanceUiModel
  setPerformanceTracking: (active: boolean) => void
}

function buildDebugSnapshotText(snapshot: SnapshotMessage | null) {
  if (!snapshot || !snapshot.ok || !snapshot.realTimeWorldState) {
    return 'no_snapshot'
  }

  const entityCount = snapshot.realTimeWorldState.entities?.length ?? 0
  const tickCost = snapshot.tickCostMs == null ? '' : `${snapshot.tickCostMs}ms`
  return `ok=${snapshot.ok} ${tickCost} entities=${entityCount} ${snapshot.error ?? ''}`.trim()
}

export function useInGameDataHub() {
  const renderer = ref<ThreeWorldRenderer | null>(null)
  const lastSnapshot = ref<SnapshotMessage | null>(null)
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

  function setRenderer(r: ThreeWorldRenderer | null) {
    renderer.value = r
  }

  function getRenderer() {
    return renderer.value
  }

  function setLastSnapshot(s: SnapshotMessage | null) {
    lastSnapshot.value = s
    if (s?.ok && s.realTimeWorldState) {
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
    const snapshot = lastSnapshot.value
    if (!snapshot || !snapshot.ok || !snapshot.realTimeWorldState) {
      return '-'
    }

    const totalGameSeconds = snapshot.realTimeWorldState.totalGameSeconds ?? 0
    return `Day ${Math.floor(totalGameSeconds / 86400) + 1}`
  })

  const overviewTickCostText = computed(() => {
    const snapshot = lastSnapshot.value
    if (!snapshot || !snapshot.ok || snapshot.tickCostMs == null) {
      return '-'
    }
    return `${snapshot.tickCostMs} ms`
  })

  const overviewSectorCountText = computed(() => {
    const snapshot = lastSnapshot.value
    if (!snapshot || !snapshot.ok) {
      return '-'
    }
    return String(snapshot.realTimeWorldState?.sectorCenters?.length ?? '-')
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

  const debugWorldStateText = computed(() => buildDebugSnapshotText(lastSnapshot.value))

  return {
    setRenderer,
    getRenderer,
    setLastSnapshot,
    syncEntitiesFromWorld,
    onCanvasPointerMove,
    entities,
    lastSnapshot,
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

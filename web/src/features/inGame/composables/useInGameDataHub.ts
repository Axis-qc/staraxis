import { computed, onUnmounted, ref, shallowRef, type Ref } from 'vue'
import { useAuthStore } from '../../../stores/auth'
import type { EntitySnapshot, SnapshotHighFreqMessage } from '../../../net/snapshotWs'
import type { WorldRenderer as ThreeWorldRenderer } from '../../../rendering/worldRenderManager'
import {
  getEntityInterpolationCaptureState,
  getEntityInterpolationDebugState,
  getInterpolationDebugState,
  getLocalVisibleWorld,
  startEntityInterpolationCapture,
  type LowFreqWorldState,
} from '../../../game/world'

type Vec2 = { x: number; y: number }

type DebugUiModel = {
  cameraPoseText: Ref<string>
  cameraCenterText: Ref<string>
  mouseWorldText: Ref<string>
  worldStateText: Ref<string>
  interpolationStateText: Ref<string>
  entityInterpolationStateText: Ref<string>
  interpolationCaptureText: Ref<string>
  receivedTickSequenceText: Ref<string>
  receivedTickSequenceStatusText: Ref<string>
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

type ReceivedHighFreqTickSample = {
  simulationTick: number
  totalGameSecondsExact: number
  receivedAtRealMs: number
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
  setDebugEntityId: (entityId: number | null) => void
  requestEntityInterpolationCapture: () => void
  startReceivedTickSequenceCapture: (durationMs?: number) => void
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
  const debugEntityId = ref<number | null>(null)
  const fpsText = ref('-')
  const fpsHistory = shallowRef<number[]>([])
  const receivedHighFreqTickSamples = shallowRef<ReceivedHighFreqTickSample[]>([])
  const receivedTickTraceEndAtRealMs = ref<number | null>(null)

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
      const nowMs = Date.now()
      if (
        receivedTickTraceEndAtRealMs.value !== null
        && nowMs <= receivedTickTraceEndAtRealMs.value
      ) {
        const nextSamples = receivedHighFreqTickSamples.value.slice()
        nextSamples.push({
          simulationTick: s.simulationTick,
          totalGameSecondsExact: s.totalGameSecondsExact,
          receivedAtRealMs: nowMs,
        })
        receivedHighFreqTickSamples.value = nextSamples
      } else if (
        receivedTickTraceEndAtRealMs.value !== null
        && nowMs > receivedTickTraceEndAtRealMs.value
      ) {
        receivedTickTraceEndAtRealMs.value = null
      }
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
  const debugInterpolationStateText = computed(() => {
    void debugUiTick.value

    const interpolationState = getInterpolationDebugState()
    if (!interpolationState) {
      return 'no_window'
    }

    const nextTickText = interpolationState.nextTick == null ? '-' : String(interpolationState.nextTick)
    const resetTag = interpolationState.didResetWindow ? ' reset' : ''
    return `cur=${interpolationState.currentTick} next=${nextTickText} latest=${interpolationState.latestTick} alpha=${interpolationState.renderAlpha.toFixed(2)} mode=${interpolationState.mode}${resetTag}`
  })

  const debugEntityInterpolationStateText = computed(() => {
    void debugUiTick.value

    if (debugEntityId.value == null) {
      return 'no_selected_entity'
    }

    const debugState = getEntityInterpolationDebugState(debugEntityId.value)
    if (!debugState) {
      return 'no_entity_debug'
    }

    const formatVec = (value: Vec2 | null) =>
      value ? `(${value.x.toFixed(2)}, ${value.y.toFixed(2)})` : '-'
    const formatHeading = (value: number | null) =>
      value == null ? '-' : value.toFixed(2)

    return [
      `entity=${debugState.entityId}`,
      `prev=${formatVec(debugState.previousRenderPosition)}`,
      `target=${formatVec(debugState.targetRenderPosition)}`,
      `shown=${formatVec(debugState.presentedRenderPosition)}`,
      `mesh=${formatVec(debugState.renderedMeshPosition)}`,
      `prevHeading=${formatHeading(debugState.previousRenderHeadingDeg)}`,
      `targetHeading=${formatHeading(debugState.targetRenderHeadingDeg)}`,
      `shownHeading=${formatHeading(debugState.presentedRenderHeadingDeg)}`,
      `meshHeading=${formatHeading(debugState.renderedMeshHeadingDeg)}`,
      `blend=${debugState.frameBlendAlpha.toFixed(3)}`,
      `dt=${debugState.renderFrameDeltaMs.toFixed(2)}ms`,
      `tick=${debugState.currentTick}->${debugState.nextTick ?? '-'}`,
      `src=${debugState.targetSource}`,
    ].join('\n')
  })

  const debugInterpolationCaptureText = computed(() => {
    void debugUiTick.value

    const captureState = getEntityInterpolationCaptureState()
    if (!captureState.frames.length) {
      if (captureState.entityId == null) {
        return 'capture_idle'
      }
      return `capturing entity=${captureState.entityId} remaining=${captureState.remainingFrames}`
    }

    return captureState.frames
      .map((frame, index) => {
        const formatVec = (value: Vec2 | null) =>
          value ? `(${value.x.toFixed(2)},${value.y.toFixed(2)})` : '-'
        const formatHeading = (value: number | null) =>
          value == null ? '-' : value.toFixed(2)
        return [
          `#${index + 1}`,
          `prev=${formatVec(frame.previousRenderPosition)}`,
          `target=${formatVec(frame.targetRenderPosition)}`,
          `shown=${formatVec(frame.presentedRenderPosition)}`,
          `mesh=${formatVec(frame.renderedMeshPosition)}`,
          `prevHeading=${formatHeading(frame.previousRenderHeadingDeg)}`,
          `targetHeading=${formatHeading(frame.targetRenderHeadingDeg)}`,
          `shownHeading=${formatHeading(frame.presentedRenderHeadingDeg)}`,
          `meshHeading=${formatHeading(frame.renderedMeshHeadingDeg)}`,
          `blend=${frame.frameBlendAlpha.toFixed(3)}`,
          `dt=${frame.renderFrameDeltaMs.toFixed(2)}ms`,
          `tick=${frame.currentTick}->${frame.nextTick ?? '-'}`,
        ].join(' ')
      })
      .join('\n')
  })

  const debugReceivedTickSequenceText = computed(() => {
    const samples = receivedHighFreqTickSamples.value
    if (!samples.length) {
      return 'no_received_ticks'
    }

    return samples
      .map((sample, index) => {
        const clock = new Date(sample.receivedAtRealMs).toLocaleTimeString('zh-CN', {
          hour12: false,
          hour: '2-digit',
          minute: '2-digit',
          second: '2-digit',
          fractionalSecondDigits: 3,
        })
        const prev = index > 0 ? samples[index - 1] : null
        const tickGap = prev ? sample.simulationTick - prev.simulationTick : 0
        const realGapMs = prev ? sample.receivedAtRealMs - prev.receivedAtRealMs : 0
        return [
          `#${index + 1}`,
          `tick=${sample.simulationTick}`,
          `rxClock=${clock}`,
          `rxRealMs=${sample.receivedAtRealMs}`,
          `tickGap=${prev ? tickGap : '-'}`,
          `realGapMs=${prev ? realGapMs : '-'}`,
          `gameSec=${sample.totalGameSecondsExact.toFixed(3)}`,
        ].join(' ')
      })
      .join('\n')
  })

  const debugReceivedTickSequenceStatusText = computed(() => {
    void debugUiTick.value

    if (receivedTickTraceEndAtRealMs.value !== null) {
      const remainingMs = Math.max(0, receivedTickTraceEndAtRealMs.value - Date.now())
      return `recording remaining=${(remainingMs / 1000).toFixed(1)}s samples=${receivedHighFreqTickSamples.value.length}`
    }

    if (!receivedHighFreqTickSamples.value.length) {
      return 'idle'
    }

    const first = receivedHighFreqTickSamples.value[0]
    const last = receivedHighFreqTickSamples.value[receivedHighFreqTickSamples.value.length - 1]
    return `recorded samples=${receivedHighFreqTickSamples.value.length} range=${first.simulationTick}->${last.simulationTick}`
  })

  function setDebugEntityId(entityId: number | null) {
    debugEntityId.value = entityId
  }

  function requestEntityInterpolationCapture() {
    if (debugEntityId.value == null) {
      return
    }
    startEntityInterpolationCapture(debugEntityId.value, 30)
  }

  function startReceivedTickSequenceCapture(durationMs = 10_000) {
    receivedHighFreqTickSamples.value = []
    receivedTickTraceEndAtRealMs.value = Date.now() + Math.max(1000, Math.floor(durationMs))
  }

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
      interpolationStateText: debugInterpolationStateText,
      entityInterpolationStateText: debugEntityInterpolationStateText,
      interpolationCaptureText: debugInterpolationCaptureText,
      receivedTickSequenceText: debugReceivedTickSequenceText,
      receivedTickSequenceStatusText: debugReceivedTickSequenceStatusText,
    },
    performance: {
      fpsText,
      fpsHistory,
    },
    setPerformanceTracking,
    setDebugEntityId,
    requestEntityInterpolationCapture,
    startReceivedTickSequenceCapture,
  } satisfies InGameDataHub
}

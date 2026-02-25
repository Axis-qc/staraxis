/**
 * @file useInGameDataHub.ts
 *
 * @description
 * 游戏内数据中枢（In-Game Data Hub）。
 *
 * 目标：将 InGame 视图层所需的“数据处理、聚合与派发”从页面组件中抽离，集中在 composable 内完成，
 * 便于后续向 UI、渲染系统以及实体/舰船/特效/行星/恒星等模块持续扩展。
 *
 * 说明：
 * - 本模块不负责权威模拟逻辑，仅对后端推送的快照（Snapshot）与本地渲染器状态做 UI 侧整理。
 * - 当前阶段会读取 three renderer 的非响应式字段（如 zoom、cameraWorldPosGU）。为保证 UI 实时显示，
 *   这里使用 requestAnimationFrame 驱动的 tick 刷新；后续 renderer 若提供事件回调可替换为更低频的同步方式。
 *
 * @usage
 * - 在 InGameView 内调用 `useInGameDataHub()`。
 * - 在创建/销毁渲染器时调用 `setRenderer(...)`。
 * - 在收到 WS 快照时调用 `setLastSnapshot(...)`。
 * - 将画布 pointermove 事件绑定到 `onCanvasPointerMove`，用于计算鼠标世界坐标。
 *
 * @provides
 * - **overview**：总览面板所需的展示模型（日期、Tick 耗时、星区数量等）。
 * - **debug**：调试浮窗所需的展示模型（缩放倍率、镜头中心、鼠标世界坐标、世界状态摘要等）。
 * - **entities**：扁平化的实体快照列表，供渲染器等模块消费。
 * - **输入函数**：`setRenderer/setLastSnapshot/onCanvasPointerMove`。
 *
 * @inputs
 * - `SnapshotMessage`：来自 `connectSnapshotWs` 的快照推送。
 * - `ThreeWorldRenderer`：来自 `createThreeWorldRenderer` 的渲染器实例。
 *
 * @potential_issues
 * - **性能**：`requestAnimationFrame` tick 为每帧更新；建议仅用于调试显示，后续可按需开关或换为事件驱动。
 * - **坐标口径**：鼠标世界坐标计算依赖当前相机中心与 zoom（GU/像素换算）；若渲染坐标系变更需同步调整。
 */

import { computed, onUnmounted, ref, shallowRef, type Ref } from 'vue'
import { useAuthStore } from '../../../stores/auth'
import type { EntitySnapshot, SnapshotMessage } from '../../../net/snapshotWs'
import type { WorldRenderer as ThreeWorldRenderer } from '../../../rendering/worldRenderManager'

type Vec2 = { x: number; y: number }

type DebugUiModel = {
    zoomText: Ref<string>
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
}

export type InGameDataHub = {
    setRenderer: (r: ThreeWorldRenderer | null) => void
    getRenderer: () => ThreeWorldRenderer | null
    setLastSnapshot: (s: SnapshotMessage | null) => void
    onCanvasPointerMove: (e: PointerEvent) => void

    entities: Ref<EntitySnapshot[]>

    lastSnapshot: Ref<SnapshotMessage | null>

    overview: OverviewUiModel
    debug: DebugUiModel
    performance: PerformanceUiModel

    setPerformanceTracking: (active: boolean) => void
}

function buildDebugSnapshotText(s: SnapshotMessage | null) {
    if (!s || !s.ok || !s.realTimeWorldState) return 'no_snapshot'

    const ok = s.ok
    const err = s.error ?? ''
    const entityCount = s.realTimeWorldState?.entities?.length ?? 0
    const cost = s.tickCostMs == null ? '' : `${s.tickCostMs}ms`

    return `ok=${ok} ${cost} entities=${entityCount} ${err}`.trim()
}

export function useInGameDataHub() {
    const renderer = ref<ThreeWorldRenderer | null>(null)
    const lastSnapshot = ref<SnapshotMessage | null>(null)

    const entities = shallowRef<EntitySnapshot[]>([])

    // 鼠标世界坐标：由 UI 事件驱动，确保实时刷新
    const mouseWorldPosGU = ref<Vec2 | null>(null)

    // 用于刷新 renderer 内部非响应式字段（zoom/cameraWorldPosGU）
    // 目前按帧刷新：后续 renderer 支持事件回调时可替换为更低频方案
    const debugUiTick = ref(0)

    const fpsText = ref('-')
    const fpsHistory = shallowRef<number[]>([])

    const FPS_HISTORY_MAX_SECONDS = 60

    let trackingEnabled = false
    let trackingStartedAtMs = 0

    let frameCountInSecond = 0
    let secondWindowStartMs = 0

    let rafId = 0
    const rafLoop = (t: number) => {
        debugUiTick.value++

        if (trackingEnabled) {
            if (trackingStartedAtMs === 0) {
                trackingStartedAtMs = t
                secondWindowStartMs = t
                frameCountInSecond = 0
                fpsHistory.value = []
            }

            frameCountInSecond++

            const elapsedInSecond = t - secondWindowStartMs
            if (elapsedInSecond >= 1000) {
                const fps = Math.round((frameCountInSecond * 1000) / elapsedInSecond)
                fpsText.value = String(fps)

                const next = fpsHistory.value.slice()
                next.push(fps)
                if (next.length > FPS_HISTORY_MAX_SECONDS) {
                    next.splice(0, next.length - FPS_HISTORY_MAX_SECONDS)
                }
                fpsHistory.value = next

                secondWindowStartMs = t
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

    function setRenderer(r: ThreeWorldRenderer | null) {
        renderer.value = r
    }

    function getRenderer() {
        return renderer.value
    }

    function setLastSnapshot(s: SnapshotMessage | null) {
        lastSnapshot.value = s
        if (s?.ok && s.realTimeWorldState) {
            const rt = s.realTimeWorldState
            const combined: EntitySnapshot[] = []

            // 1. 加入公开实体喵
            if (rt.entities) {
                combined.push(...rt.entities)
            }

            // 2. 加入按情报等级分层的私有实体喵
            if (rt.privateEntitiesByIntelLevel) {
                for (const levelEntities of Object.values(rt.privateEntitiesByIntelLevel)) {
                    if (Array.isArray(levelEntities)) {
                        combined.push(...levelEntities)
                    }
                }
            }

            // 按 entityId 去重（以防万一后端重复下发）喵
            const seen = new Set<number>()
            entities.value = combined.filter(e => {
                if (seen.has(e.entityId)) return false
                seen.add(e.entityId)
                return true
            })
        }
    }

    function onCanvasPointerMove(e: PointerEvent) {
        const r = renderer.value
        if (!r) {
            mouseWorldPosGU.value = null
            return
        }

        const rect = (e.currentTarget as HTMLElement).getBoundingClientRect()
        const localX = e.clientX - rect.left
        const localY = e.clientY - rect.top
        const cx = rect.width / 2
        const cy = rect.height / 2
        const worldX = r.cameraWorldPosGU.x + (localX - cx) * r.zoom.value
        const worldY = r.cameraWorldPosGU.y - (localY - cy) * r.zoom.value
        mouseWorldPosGU.value = { x: worldX, y: worldY }
    }

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
        return String(s.realTimeWorldState?.sectorCenters?.length ?? '-')
    })

    const auth = useAuthStore()
    const ownedEntities = computed(() => {
        const s = lastSnapshot.value
        if (!s || !s.ok || !s.realTimeWorldState || !auth.selectedNationId) return []

        // 过滤出属于玩家国家的实体喵
        return entities.value.filter(e => e.ownerNationId === auth.selectedNationId)
    })

    const debugZoomText = computed(() => {
        void debugUiTick.value
        const r = renderer.value
        if (!r) return '-'
        return String(r.zoom.value)
    })

    const debugCameraCenterText = computed(() => {
        void debugUiTick.value
        const r = renderer.value
        if (!r) return '-'
        return `(${r.cameraWorldPosGU.x.toFixed(2)}, ${r.cameraWorldPosGU.y.toFixed(2)})`
    })

    const debugMouseWorldText = computed(() => {
        const p = mouseWorldPosGU.value
        if (!p) return '-'
        return `(${p.x.toFixed(2)}, ${p.y.toFixed(2)})`
    })

    const debugWorldStateText = computed(() => buildDebugSnapshotText(lastSnapshot.value))

    return {
        setRenderer,
        getRenderer,
        setLastSnapshot,
        onCanvasPointerMove,
        entities,
        lastSnapshot,
        overview: {
            dayText: overviewDayText,
            tickCostText: overviewTickCostText,
            sectorCountText: overviewSectorCountText,
            ownedEntities: ownedEntities,
        },
        debug: {
            zoomText: debugZoomText,
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

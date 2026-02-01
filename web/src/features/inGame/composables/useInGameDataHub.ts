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

import { computed, onUnmounted, ref, type Ref } from 'vue'
import type { SnapshotMessage } from '../net/snapshotWs'
import type { ThreeWorldRenderer } from '../rendering/threeWorldRenderer'

type Vec2 = { x: number; y: number }

type DebugUiModel = {
    zoomText: Ref<string>
    cameraCenterText: Ref<string>
    mouseWorldText: Ref<string>
    worldStateText: Ref<string>
}

type OverviewUiModel = {
    dayText: Ref<string>
    tickCostText: Ref<string>
    sectorCountText: Ref<string>
}

export type InGameDataHub = {
    setRenderer: (r: ThreeWorldRenderer | null) => void
    getRenderer: () => ThreeWorldRenderer | null
    setLastSnapshot: (s: SnapshotMessage | null) => void
    onCanvasPointerMove: (e: PointerEvent) => void

    overview: OverviewUiModel
    debug: DebugUiModel
}

function buildDebugSnapshotText(s: SnapshotMessage | null) {
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
}

export function useInGameDataHub() {
    const renderer = ref<ThreeWorldRenderer | null>(null)
    const lastSnapshot = ref<SnapshotMessage | null>(null)

    // 鼠标世界坐标：由 UI 事件驱动，确保实时刷新
    const mouseWorldPosGU = ref<Vec2 | null>(null)

    // 用于刷新 renderer 内部非响应式字段（zoom/cameraWorldPosGU）
    // 目前按帧刷新：后续 renderer 支持事件回调时可替换为更低频方案
    const debugUiTick = ref(0)
    let rafId = 0
    const rafLoop = () => {
        debugUiTick.value++
        rafId = requestAnimationFrame(rafLoop)
    }
    rafId = requestAnimationFrame(rafLoop)

    onUnmounted(() => {
        cancelAnimationFrame(rafId)
    })

    function setRenderer(r: ThreeWorldRenderer | null) {
        renderer.value = r
    }

    function getRenderer() {
        return renderer.value
    }

    function setLastSnapshot(s: SnapshotMessage | null) {
        lastSnapshot.value = s
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
        return String(s.dailySettlementState?.sectorCount ?? s.realTimeWorldState?.sectorCenters?.length ?? '-')
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
        overview: {
            dayText: overviewDayText,
            tickCostText: overviewTickCostText,
            sectorCountText: overviewSectorCountText,
        },
        debug: {
            zoomText: debugZoomText,
            cameraCenterText: debugCameraCenterText,
            mouseWorldText: debugMouseWorldText,
            worldStateText: debugWorldStateText,
        },
    } satisfies InGameDataHub
}

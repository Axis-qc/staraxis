import * as THREE from 'three'
import type { SnapshotMessage, SnapshotHighFreqMessage, EntitySnapshot } from '../net/snapshotWs'
import type { LowFreqWorldState } from '../game/world'

import { createInputSystem } from '../input/inputSystem'
import { BackgroundLayer } from './layers/background'
import { CelestialLayer } from './layers/celestial'
import { EntityLayer } from './layers/entity'
import { EntityEffectsLayer } from './layers/entityEffects'
import type { RenderLayer } from './layers'
import { SimpleLayerManager } from './layers/layerManager'
import { createCameraSystem } from './systems/cameraSystem'
import { createEntityQuerySystem } from './systems/entityQuerySystem'
import { createFrameStateBuilder } from './systems/frameStateBuilder'
import { createRenderLoop } from './systems/renderLoop'
import { VisibilityStateManager } from './systems/visibilityState'
import type { LodOptions, LodState } from './subsystems/lodSystem'
import { createTextureManager } from './subsystems/textureManager'
import { GridRenderer } from './subsystems/gridRenderer'
import { HexOutlineRenderer } from './subsystems/hexOutlineRenderer'
import { MouseInteractionManager } from './subsystems/mouseInteractionManager'
import { createCameraAnimationSubsystem } from './subsystems/cameraAnimationSubsystem'

/**
 * 将 worldId 字符串哈希为数值种子喵。
 * 使用 FNV-1a 算法，保证相同字符串始终产生相同数值喵。
 */
function hashWorldId(worldId: string): number {
    let h = 0x811c9dc5 // FNV offset basis (32-bit)
    for (let i = 0; i < worldId.length; i++) {
        h ^= worldId.charCodeAt(i)
        h = Math.imul(h, 0x01000193) // FNV prime
    }
    return h | 0
}

export type { LodState, LodOptions } from './subsystems/lodSystem'
export { LodLevel } from './subsystems/lodSystem'
export type { FrameState } from './systems/frameStateBuilder'
export type { CameraSystem } from './systems/cameraSystem'
export type { EntityQuerySystem } from './systems/entityQuerySystem'

export type WorldRenderer = {
    zoom: { value: number }
    cameraHeight: { value: number }
    cameraWorldPosGU: THREE.Vector2
    setZoom: (z: number) => void
    setCameraHeight: (height: number) => void
    applyCameraTransform: () => void
    getCullingAabbGU: () => { minX: number; maxX: number; minY: number; maxY: number }
    setSelectedEntityIds: (ids: number[]) => void
    updateFromHighFreqSnapshot: (snapshot: SnapshotHighFreqMessage) => void
    updateLowFreqState: (state: LowFreqWorldState | null) => void
    updateFromSnapshot: (snapshot: SnapshotMessage) => void
    removeEntitiesFromCache: (entityIds: number[]) => void
    removeSectorsFromCache: (sectorKeys: string[]) => void
    clearAllSectorsFromCache: () => void
    getEntityWorldPosGU: (entityId: number) => { x: number; y: number } | null
    setCurrentNationId: (nationId: string | null) => void
    setGridVisible: (visible: boolean) => void
    /** 将相机中心移动到指定世界坐标喵 */
    focusOnWorldPos: (worldPos: { x: number; y: number }) => void
    onCameraChanged: (cb: () => void) => () => void
    dispose: () => void
    getLayer: (name: string) => RenderLayer | null
    setLayerVisible: (name: string, visible: boolean) => void
    setLayerQuality: (name: string, quality: number) => void
}

export type WorldRendererOptions = {
    minZoom?: number
    maxZoom?: number
    initialCameraPos?: { x: number; y: number }
    initialZoom?: number
    getSpritePath?: (typeId: string) => string | undefined
    lod?: LodOptions
    /** 世界唯一标识（字符串），用于生成确定性纹理种子喵 */
    worldId?: string
}

export type WorldRenderContext = {
    renderer: THREE.WebGLRenderer
    scene: THREE.Scene
    camera: THREE.PerspectiveCamera
    worldGroup: THREE.Group
    entitiesGroup: THREE.Group
    zoom: { value: number }
    cameraHeight: { value: number }
    cameraWorldPosGU: THREE.Vector2
    getTexture: (path: string) => Promise<THREE.Texture>
    getEntityWorldPosGU: (entityId: number) => { x: number; y: number } | null
    getViewSizeGU: () => { widthGU: number; heightGU: number }
    getWorldUnitsPerPixel: () => number
    getViewSizeAtDepth: (distanceFromCamera: number) => { widthGU: number; heightGU: number }
    options: WorldRendererOptions
    /** 世界种子（数值），由 worldId 字符串哈希而来，用于确定性纹理生成喵 */
    worldSeed: number
    /**
     * 将世界坐标转换为相机相对渲染坐标喵。
     * 所有写入 Three.js position 的值都必须经过此转换，避免 float32 精度问题喵。
     * 减法在 float64（JS number）端完成，结果（小数值）写入 float32 喵。
     */
    toRenderPos: (worldPos: { x: number; y: number }) => { x: number; y: number }
}

export type WorldFrameState = {
    entitiesById: Map<number, EntitySnapshot>
    sectorCenters: { q: number; r: number; x: number; y: number }[]
    sectorOwnerNationIdByCoord: Record<string, string>
    selectedIds: Set<number>
    cullingAabb: { minX: number; maxX: number; minY: number; maxY: number }
    lod: LodState
    totalDays: number
    visibilityManager: VisibilityStateManager | null
}

export function createWorldRenderManager(
    container: HTMLDivElement,
    options: WorldRendererOptions = {},
): WorldRenderer {
    const minZoom = options.minZoom ?? 0.1
    const maxZoom = options.maxZoom ?? 2_000_000

    const zoom = { value: 1 }
    const cameraHeight = { value: 1 }
    const cameraWorldPosGU = new THREE.Vector2(0, 0)
    const visibilityManager = new VisibilityStateManager()

    if (typeof options.initialZoom === 'number' && Number.isFinite(options.initialZoom)) {
        zoom.value = Math.max(minZoom, Math.min(maxZoom, options.initialZoom))
    }
    if (
        options.initialCameraPos &&
        Number.isFinite(options.initialCameraPos.x) &&
        Number.isFinite(options.initialCameraPos.y)
    ) {
        cameraWorldPosGU.set(options.initialCameraPos.x, options.initialCameraPos.y)
    }

    const cameraSystem = createCameraSystem(container)
    const { renderer, scene, camera, worldGroup, entitiesGroup, canvas } = cameraSystem
    cameraHeight.value = cameraSystem.worldUnitsPerPixelToHeight(zoom.value)

    const layerManager = new SimpleLayerManager()
    layerManager.registerLayer(new BackgroundLayer())
    layerManager.registerLayer(new CelestialLayer())
    layerManager.registerLayer(new EntityLayer())
    layerManager.registerLayer(new EntityEffectsLayer())

    const textureManager = createTextureManager()
    const entityQuery = createEntityQuerySystem()
    const gridRenderer = new GridRenderer()
    const hexOutlineRenderer = new HexOutlineRenderer()

    // 将 worldId 字符串哈希为数值种子，用于确定性纹理生成喵
    const worldSeed = hashWorldId(options.worldId ?? '')

    const ctx: WorldRenderContext = {
        renderer,
        scene,
        camera,
        worldGroup,
        entitiesGroup,
        zoom,
        cameraHeight,
        cameraWorldPosGU,
        getTexture: textureManager.getTexture,
        getEntityWorldPosGU: entityQuery.getEntityWorldPosGU,
        getViewSizeGU: () => cameraSystem.getViewSizeAtHeight(cameraHeight.value),
        getWorldUnitsPerPixel: () => zoom.value,
        getViewSizeAtDepth: (distanceFromCamera: number) => {
            const heightGU =
                2 * distanceFromCamera * Math.tan(THREE.MathUtils.degToRad(camera.fov) / 2)
            return {
                widthGU: heightGU * camera.aspect,
                heightGU,
            }
        },
        options,
        worldSeed,
        toRenderPos: (worldPos: { x: number; y: number }) => ({
            x: worldPos.x - cameraWorldPosGU.x,
            y: worldPos.y - cameraWorldPosGU.y,
        }),
    }

    for (const layer of layerManager.layers.values()) {
        layer.init(ctx)
    }
    gridRenderer.init(ctx)
    hexOutlineRenderer.init(ctx)

    // 创建公共鼠标交互管理器，注册可交互元素喵
    const mouseInteractionManager = new MouseInteractionManager()
    mouseInteractionManager.bindCanvas(canvas)
    const effectsLayer = layerManager.getLayer('entityEffects') as EntityEffectsLayer | null
    const starLabelInteractable = effectsLayer?.getStarLabelInteractable() ?? null
    if (starLabelInteractable) {
        mouseInteractionManager.register(starLabelInteractable)
    }

    const frameBuilder = createFrameStateBuilder(
        container,
        cameraWorldPosGU,
        zoom,
        options.lod,
        visibilityManager,
        ctx.getViewSizeGU,
    )
    const inputSystem = createInputSystem(canvas)

    let currentCullingAabb = { minX: 0, maxX: 0, minY: 0, maxY: 0 }
    let latestLowFreqState: LowFreqWorldState | null = null
    let latestSectorCenters: LowFreqWorldState['sectorCenters'] = []
    let lastFrameTime = 0

    const cameraChangeListeners = new Set<() => void>()
    const onCameraChanged = (cb: () => void) => {
        cameraChangeListeners.add(cb)
        return () => cameraChangeListeners.delete(cb)
    }

    const updateDerivedZoom = () => {
        zoom.value = cameraSystem.getWorldUnitsPerPixelAtHeight(cameraHeight.value)
    }

    const applyCameraTransform = () => {
        cameraSystem.applyTransform(cameraHeight.value, cameraWorldPosGU)
        updateDerivedZoom()
        const frame = frameBuilder.build()
        currentCullingAabb = frame.cullingAabb
        for (const cb of cameraChangeListeners) cb()
    }

    const setZoom = (z: number) => {
        const clampedZoom = Math.max(minZoom, Math.min(maxZoom, z))
        cameraHeight.value = cameraSystem.worldUnitsPerPixelToHeight(clampedZoom)
        zoom.value = clampedZoom
        applyCameraTransform()
    }

    const setCameraHeight = (height: number) => {
        const minHeight = cameraSystem.worldUnitsPerPixelToHeight(minZoom)
        const maxHeight = cameraSystem.worldUnitsPerPixelToHeight(maxZoom)
        cameraHeight.value = Math.max(minHeight, Math.min(maxHeight, height))
        applyCameraTransform()
    }

    // CAMERA_ZOOM_IN / CAMERA_ZOOM_OUT 由 canvas wheel 事件直接处理（以鼠标为中心缩放）喵
    inputSystem.onAction('CAMERA_ZOOM_RESET', () => {
        setZoom(1)
        cameraWorldPosGU.set(0, 0)
        applyCameraTransform()
    })

    // ── 相机动画子系统 ──喵
    const cameraAnim = createCameraAnimationSubsystem()

    // Space 键：无选中时鸟瞰全图，有选中时飞行聚焦到第一个实体喵
    inputSystem.onAction('PAUSE', () => {
        if (currentSelectedIds.length > 0) {
            const firstId = currentSelectedIds[0]!
            const pos = entityQuery.getEntityWorldPosGU(firstId)
            if (pos) {
                const snap = entityQuery.getEntitySnapshot(firstId)
                const details = snap?.details as any
                const radiusGU = details?.radiusGU ?? 0
                if (radiusGU > 0) {
                    const targetWorldSize = radiusGU * 2 * 1.8
                    const viewportPx = Math.max(container.clientHeight, 1)
                    const targetZoomVal = Math.max(minZoom, Math.min(maxZoom, targetWorldSize / viewportPx))
                    cameraAnim.flyTo(pos, targetZoomVal)
                } else {
                    cameraAnim.flyTo(pos, Math.max(minZoom, Math.min(maxZoom, 10)))
                }
            }
        } else {
            cameraAnim.flyToZoom(maxZoom)
        }
    })

    /** 将相机中心移动到指定世界坐标（带动画）喵 */
    const focusOnWorldPos = (worldPos: { x: number; y: number }) => {
        cameraAnim.flyToPosition(worldPos)
    }

    let isDragging = false
    const dragStartClient = new THREE.Vector2(0, 0)
    const dragStartCamera = new THREE.Vector2(0, 0)

    const onPointerDown = (e: PointerEvent) => {
        if (e.button === 2) {
            e.preventDefault()
            canvas.setPointerCapture(e.pointerId)
            return
        }
        if (e.button !== 1) return

        isDragging = true
        cameraAnim.cancel()
        dragStartClient.set(e.clientX, e.clientY)
        dragStartCamera.copy(cameraWorldPosGU)
        canvas.setPointerCapture(e.pointerId)
        e.preventDefault()
    }

    const onPointerMove = (e: PointerEvent) => {
        if (!isDragging) return

        const dxPx = e.clientX - dragStartClient.x
        const dyPx = e.clientY - dragStartClient.y

        cameraWorldPosGU.set(dragStartCamera.x - dxPx * zoom.value, dragStartCamera.y + dyPx * zoom.value)
        applyCameraTransform()
        e.preventDefault()
    }

    const endDrag = (e: PointerEvent) => {
        if (!isDragging) return
        isDragging = false
        try {
            canvas.releasePointerCapture(e.pointerId)
        } catch {
            // Ignore already-released pointers.
        }
        e.preventDefault()
    }

    const onWheel = (e: WheelEvent) => {
        e.preventDefault()
        cameraAnim.cancel()
        const zoomDelta = e.deltaY * 0.001
        const oldZoom = zoom.value
        const newZoom = Math.max(minZoom, Math.min(maxZoom, oldZoom * (1 + zoomDelta)))
        if (newZoom === oldZoom) return

        // 以鼠标位置为缩放中心：保持鼠标下方的世界坐标不变喵
        const rect = canvas.getBoundingClientRect()
        const mx = e.clientX - rect.left   // 鼠标在 canvas 中的 X 像素（左→右）喵
        const my = e.clientY - rect.top    // 鼠标在 canvas 中的 Y 像素（上→下）喵
        const canvasW = rect.width
        const canvasH = rect.height

        // 鼠标相对于视口中心的像素偏移（世界坐标系方向）喵
        const dxPx = mx - canvasW / 2      // 右为正喵
        const dyPx = canvasH / 2 - my      // 上为正喵

        // 调整相机位置，使鼠标下方的世界坐标保持不变喵
        cameraWorldPosGU.x += dxPx * (oldZoom - newZoom)
        cameraWorldPosGU.y += dyPx * (oldZoom - newZoom)

        setZoom(newZoom)
    }

    const onContextMenu = (e: MouseEvent) => {
        e.preventDefault()
    }

    canvas.addEventListener('contextmenu', onContextMenu)
    canvas.addEventListener('pointerdown', onPointerDown)
    canvas.addEventListener('pointermove', onPointerMove)
    canvas.addEventListener('pointerup', endDrag)
    canvas.addEventListener('pointercancel', endDrag)
    canvas.addEventListener('pointerleave', endDrag)
    canvas.addEventListener('wheel', onWheel, { passive: false })

    const resizeObserver = new ResizeObserver(() => {
        cameraSystem.updateFrustum()
        applyCameraTransform()
    })
    resizeObserver.observe(container)

    const renderLoop = createRenderLoop(
        renderer,
        scene,
        camera,
        ctx,
        () => frameBuilder.build(),
        inputSystem,
        cameraWorldPosGU,
        zoom,
        applyCameraTransform,
        {
            layerManager,
            isCameraAnimating: () => cameraAnim.isAnimating(),
            cancelCameraAnimation: () => cameraAnim.cancel(),
            onFrameUpdate: (renderCtx, frame) => {
                // 仅在动画进行时驱动，空闲时零开销喵
                if (cameraAnim.isAnimating()) {
                    const now = performance.now() / 1000
                    const dtSec = lastFrameTime > 0 ? Math.min(now - lastFrameTime, 0.1) : 0
                    lastFrameTime = now
                    cameraAnim.update(dtSec, cameraWorldPosGU, zoom, cameraHeight, cameraSystem.worldUnitsPerPixelToHeight, applyCameraTransform)
                } else {
                    lastFrameTime = 0
                }

                mouseInteractionManager.update()
                gridRenderer.update(renderCtx, frame)
                hexOutlineRenderer.update(renderCtx, frame)
            },
        },
    )

    cameraSystem.updateFrustum()
    applyCameraTransform()
    renderLoop.start()

    let currentSelectedIds: number[] = []

    const setSelectedEntityIds = (ids: number[]) => {
        currentSelectedIds = ids
        frameBuilder.setSelectedIds(ids)
        void frameBuilder.build()
    }

    const updateFromHighFreqSnapshot = (snapshot: SnapshotHighFreqMessage) => {
        if (!snapshot.ok) return

        // 高频快照只含动态实体（舰船），不重新塞入全部1935个实体喵
        const privateTierMap = snapshot.privateEntitiesByIntelLevel ?? {}
        const privateEntities = Object.values(privateTierMap).flatMap((arr) => arr ?? [])
        if (privateEntities.length === 0) return

        // 仅增量更新渲染缓存，不替换恒星/行星基线喵
        frameBuilder.updateEntities(privateEntities)
        entityQuery.addEntities(privateEntities)
    }

    /** 是否已设置恒星/行星基线渲染缓存喵 */
    let lowFreqBaselineSet = false

    const updateLowFreqState = (state: LowFreqWorldState | null) => {
        latestLowFreqState = state
        latestSectorCenters = state?.sectorCenters ?? []
        frameBuilder.replaceSectorCenters(latestSectorCenters)
        frameBuilder.updateSectorOwnerNationIdByCoord(state?.sectorOwnerNationIdByCoord ?? {})
        // 首次低频基线：将恒星/行星注入渲染缓存（后续高频只增量更新舰船）喵
        if (!lowFreqBaselineSet && state?.entities && state.entities.length > 0) {
            lowFreqBaselineSet = true
            frameBuilder.updateEntities(state.entities)
            entityQuery.addEntities(state.entities)
        }
        void frameBuilder.build()
    }

    const updateFromSnapshot = (snapshot: SnapshotMessage) => {
        if (!snapshot.ok || !snapshot.realTimeWorldState) return

        updateLowFreqState({
            simulationTick: snapshot.realTimeWorldState.simulationTick,
            version: snapshot.realTimeWorldState.simulationTick,
            syncMode: 'full',
            baseVersion: null,
            worldRadius: snapshot.realTimeWorldState.worldRadius,
            worldType: snapshot.realTimeWorldState.worldType ?? null,
            gameSecondsPerRealSecond: snapshot.realTimeWorldState.gameSecondsPerRealSecond ?? null,
            timeScale: snapshot.realTimeWorldState.timeScale ?? null,
            year: snapshot.realTimeWorldState.year ?? null,
            month: snapshot.realTimeWorldState.month ?? null,
            day: snapshot.realTimeWorldState.day ?? null,
            hour: snapshot.realTimeWorldState.hour ?? null,
            minute: snapshot.realTimeWorldState.minute ?? null,
            second: snapshot.realTimeWorldState.second ?? null,
            sectorCenters: snapshot.realTimeWorldState.sectorCenters ?? [],
            sectorOwnerNationIdByCoord: snapshot.realTimeWorldState.sectorOwnerNationIdByCoord ?? {},
            dailySettlementState: snapshot.dailySettlementState ?? null,
            playerNationId: snapshot.playerNationId ?? null,
            receivedAtClientMs: Date.now(),
        })

        updateFromHighFreqSnapshot({
            type: 'snapshot_high_freq',
            ok: snapshot.ok,
            error: snapshot.error,
            tickCostMs: snapshot.tickCostMs,
            simulationTick: snapshot.realTimeWorldState.simulationTick,
            totalGameSeconds: snapshot.realTimeWorldState.totalGameSeconds,
            totalGameSecondsExact: snapshot.realTimeWorldState.totalGameSecondsExact ?? snapshot.realTimeWorldState.totalGameSeconds,
            deltaGameSeconds: snapshot.realTimeWorldState.deltaGameSeconds,
            syncMode: 'full',
            entities: snapshot.realTimeWorldState.entities ?? [],
            privateEntitiesByIntelLevel: snapshot.realTimeWorldState.privateEntitiesByIntelLevel ?? {},
            playerNationId: snapshot.playerNationId,
        })
    }

    const removeEntitiesFromCache = (entityIds: number[]) => {
        frameBuilder.removeEntities(entityIds)
    }

    const removeSectorsFromCache = (sectorKeys: string[]) => {
        frameBuilder.removeSectors(sectorKeys)
    }

    const clearAllSectorsFromCache = () => {
        frameBuilder.clearAllSectors()
    }

    const dispose = () => {
        resizeObserver.disconnect()

        canvas.removeEventListener('contextmenu', onContextMenu)
        canvas.removeEventListener('pointerdown', onPointerDown)
        canvas.removeEventListener('pointermove', onPointerMove)
        canvas.removeEventListener('pointerup', endDrag)
        canvas.removeEventListener('pointercancel', endDrag)
        canvas.removeEventListener('pointerleave', endDrag)
        canvas.removeEventListener('wheel', onWheel)

        renderLoop.stop()
        inputSystem.dispose()
        mouseInteractionManager.dispose()
        cameraAnim.dispose()

        layerManager.disposeAll(ctx)
        gridRenderer.dispose(ctx)
        hexOutlineRenderer.dispose(ctx)
        textureManager.dispose()
        cameraSystem.dispose()
    }

    const setCurrentNationId = (nationId: string | null) => {
        visibilityManager.setCurrentNationId(nationId)
        void frameBuilder.build()
    }

    const setGridVisible = (visible: boolean) => {
        gridRenderer.setVisible(visible)
    }

    return {
        zoom,
        cameraHeight,
        cameraWorldPosGU,
        setZoom,
        setCameraHeight,
        applyCameraTransform,
        getCullingAabbGU: () => currentCullingAabb,
        setSelectedEntityIds,
        updateFromHighFreqSnapshot,
        updateLowFreqState,
        updateFromSnapshot,
        removeEntitiesFromCache,
        removeSectorsFromCache,
        clearAllSectorsFromCache,
        getEntityWorldPosGU: entityQuery.getEntityWorldPosGU,
        setCurrentNationId,
        setGridVisible,
        focusOnWorldPos,
        onCameraChanged,
        dispose,
        getLayer: (name: string) => layerManager.getLayer(name),
        setLayerVisible: (name: string, visible: boolean) => {
            const layer = layerManager.getLayer(name)
            if (layer) layer.setVisible(visible)
        },
        setLayerQuality: (name: string, quality: number) => {
            const layer = layerManager.getLayer(name)
            if (layer) layer.setQuality(quality)
        },
    }
}

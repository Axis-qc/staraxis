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
    }

    for (const layer of layerManager.layers.values()) {
        layer.init(ctx)
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
    let latestSectorCenters: LowFreqWorldState['sectorCenters'] = []

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

    inputSystem.onAction('CAMERA_ZOOM_IN', () => setZoom(zoom.value * 0.9))
    inputSystem.onAction('CAMERA_ZOOM_OUT', () => setZoom(zoom.value * 1.1))
    inputSystem.onAction('CAMERA_ZOOM_RESET', () => {
        setZoom(1)
        cameraWorldPosGU.set(0, 0)
        applyCameraTransform()
    })

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
        const zoomDelta = e.deltaY * 0.001
        setZoom(zoom.value * (1 + zoomDelta))
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
        { layerManager },
    )

    cameraSystem.updateFrustum()
    applyCameraTransform()
    renderLoop.start()

    const setSelectedEntityIds = (ids: number[]) => {
        frameBuilder.setSelectedIds(ids)
        void frameBuilder.build()
    }

    const updateFromHighFreqSnapshot = (snapshot: SnapshotHighFreqMessage) => {
        if (!snapshot.ok) return

        const publicEntities = snapshot.entities ?? []
        const privateTierMap = snapshot.privateEntitiesByIntelLevel ?? {}
        const privateEntities = Object.values(privateTierMap).flatMap((arr) => arr ?? [])

        const mergedById = new Map<number, EntitySnapshot>()
        for (const entity of publicEntities) {
            mergedById.set(entity.entityId, entity)
        }
        for (const entity of privateEntities) {
            mergedById.set(entity.entityId, entity)
        }
        const mergedEntities = Array.from(mergedById.values())

        visibilityManager.updateFromSnapshot(
            mergedEntities,
            latestSectorCenters,
            Date.now(),
        )

        frameBuilder.updateEntities(mergedEntities)
        entityQuery.updateEntities(mergedEntities)
        void frameBuilder.build()
    }

    const updateLowFreqState = (state: LowFreqWorldState | null) => {
        latestSectorCenters = state?.sectorCenters ?? []
        frameBuilder.replaceSectorCenters(latestSectorCenters)
        frameBuilder.updateSectorOwnerNationIdByCoord(state?.sectorOwnerNationIdByCoord ?? {})
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

        layerManager.disposeAll(ctx)
        textureManager.dispose()
        cameraSystem.dispose()
    }

    const setCurrentNationId = (nationId: string | null) => {
        visibilityManager.setCurrentNationId(nationId)
        void frameBuilder.build()
    }

    const setGridVisible = (visible: boolean) => {
        console.log(`setGridVisible(${visible}) - grid layer not implemented`)
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

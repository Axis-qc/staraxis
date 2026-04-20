/**
 * @file worldRenderManager.ts
 *
 * @description
 * 世界渲染管理器（World Render Manager）- 分层渲染系统的协调入口。
 *
 * 作用：
 * - 协调相机、帧状态、实体查询、层管理器与渲染循环。
 * - 处理输入事件（通过 InputSystem）。
 * - 提供对外 API（zoom、cameraWorldPosGU、updateFromSnapshot 等）。
 *
 * @important_notes
 * - 渲染职责已经下沉到各 layer / system 模块。
 * - 具体实现见：
 *   - systems/cameraSystem.ts - 相机和渲染器管理
 *   - systems/frameStateBuilder.ts - 帧状态构建
 *   - systems/entityQuerySystem.ts - 实体位置查询
 *   - systems/renderLoop.ts - 渲染循环
 *   - layers/ - 分层渲染实现
 *   - subsystems/lodSystem.ts - LOD系统
 */
import * as THREE from 'three'
import type { SnapshotMessage } from '../net/snapshotWs'

import { createCameraSystem } from './systems/cameraSystem'
import { createFrameStateBuilder } from './systems/frameStateBuilder'
import { createEntityQuerySystem } from './systems/entityQuerySystem'
import { createRenderLoop } from './systems/renderLoop'
import { createTextureManager } from './subsystems/textureManager'
import { SimpleLayerManager } from './layers/layerManager'
import type { RenderLayer } from './layers'
import { CelestialLayer } from './layers/celestial'
import { EntityLayer } from './layers/entity'
import { BackgroundLayer } from './layers/background'
import { EntityEffectsLayer } from './layers/entityEffects'
import type { LodState, LodOptions } from './subsystems/lodSystem'
import { createInputSystem } from '../input/inputSystem'
import { VisibilityStateManager } from './systems/visibilityState'

// 重新导出类型供外部使用
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
    updateFromSnapshot: (snapshot: SnapshotMessage) => void
    removeEntitiesFromCache: (entityIds: number[]) => void
    removeSectorsFromCache: (sectorKeys: string[]) => void
    clearAllSectorsFromCache: () => void
    getEntityWorldPosGU: (entityId: number) => { x: number; y: number } | null
    setCurrentNationId: (nationId: string | null) => void
    setGridVisible: (visible: boolean) => void
    onCameraChanged: (cb: () => void) => () => void
    dispose: () => void
    // 新增层控制API
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
    snapshot: SnapshotMessage | null
    entitiesById: Map<number, import('../net/snapshotWs').EntitySnapshot>
    sectorCenters: { q: number; r: number; x: number; y: number }[]
    selectedIds: Set<number>
    cullingAabb: { minX: number; maxX: number; minY: number; maxY: number }
    lod: LodState
    totalDays: number
    visibilityManager: VisibilityStateManager | null
}

export function createWorldRenderManager(
    container: HTMLDivElement,
    options: WorldRendererOptions = {}
): WorldRenderer {
    const minZoom = options.minZoom ?? 0.1
    const maxZoom = options.maxZoom ?? 2_000_000

    // 状态
    const zoom = { value: 1 }
    const cameraHeight = { value: 1 }
    const cameraWorldPosGU = new THREE.Vector2(0, 0)
    const visibilityManager = new VisibilityStateManager()

    // 应用初始相机状态（若提供）喵
    if (typeof options.initialZoom === 'number' && Number.isFinite(options.initialZoom)) {
        zoom.value = Math.max(minZoom, Math.min(maxZoom, options.initialZoom))
    }
    if (options.initialCameraPos && Number.isFinite(options.initialCameraPos.x) && Number.isFinite(options.initialCameraPos.y)) {
        cameraWorldPosGU.set(options.initialCameraPos.x, options.initialCameraPos.y)
    }

    // 初始化相机系统
    const cameraSystem = createCameraSystem(container)
    const { renderer, scene, camera, worldGroup, entitiesGroup, canvas } = cameraSystem

    cameraHeight.value = cameraSystem.worldUnitsPerPixelToHeight(zoom.value)

    // 初始化层管理器
    const layerManager = new SimpleLayerManager()
    layerManager.registerLayer(new BackgroundLayer())
    layerManager.registerLayer(new CelestialLayer())
    layerManager.registerLayer(new EntityLayer())
    layerManager.registerLayer(new EntityEffectsLayer())

    // 初始化纹理管理器
    const textureManager = createTextureManager()

    // 初始化实体查询系统
    const entityQuery = createEntityQuerySystem()

    // 初始化渲染上下文
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
            const heightGU = 2 * distanceFromCamera * Math.tan(THREE.MathUtils.degToRad(camera.fov) / 2)
            return {
                widthGU: heightGU * camera.aspect,
                heightGU,
            }
        },
        options,
    }

    // 初始化所有渲染层
    for (const layer of layerManager.layers.values()) {
        layer.init(ctx)
    }

    // 初始化帧状态构建器
    const frameBuilder = createFrameStateBuilder(container, cameraWorldPosGU, zoom, options.lod, visibilityManager, ctx.getViewSizeGU)


    // 初始化输入系统
    const inputSystem = createInputSystem(canvas)

    // 缓存当前剔除范围（cullingAabb），供订阅星区与其它系统复用喵
    let currentCullingAabb = { minX: 0, maxX: 0, minY: 0, maxY: 0 }

    // 相机变化监听喵
    const cameraChangeListeners = new Set<() => void>()
    const onCameraChanged = (cb: () => void) => {
        cameraChangeListeners.add(cb)
        return () => cameraChangeListeners.delete(cb)
    }

    const updateDerivedZoom = () => {
        zoom.value = cameraSystem.getWorldUnitsPerPixelAtHeight(cameraHeight.value)
    }

    // 相机控制动作
    const applyCameraTransform = () => {
        cameraSystem.applyTransform(cameraHeight.value, cameraWorldPosGU)
        updateDerivedZoom()
        // 触发重建以更新LOD
        const frame = frameBuilder.build(null)
        currentCullingAabb = frame.cullingAabb
        // 通知外部相机已变化喵
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

    // 注册输入动作
    inputSystem.onAction('CAMERA_ZOOM_IN', () => setZoom(zoom.value * 0.9))
    inputSystem.onAction('CAMERA_ZOOM_OUT', () => setZoom(zoom.value * 1.1))
    inputSystem.onAction('CAMERA_ZOOM_RESET', () => {
        setZoom(1)
        cameraWorldPosGU.set(0, 0)
        applyCameraTransform()
    })

    // 鼠标拖拽状态
    let isDragging = false
    const dragStartClient = new THREE.Vector2(0, 0)
    const dragStartCamera = new THREE.Vector2(0, 0)

    // 保留原有的 pointer 事件监听（与 InputSystem 并存）
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
        } catch { }
        e.preventDefault()
    }

    const onWheel = (e: WheelEvent) => {
        e.preventDefault()
        const zoomDelta = e.deltaY * 0.001
        const next = zoom.value * (1 + zoomDelta)
        setZoom(next)
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

    // ResizeObserver
    const resizeObserver = new ResizeObserver(() => {
        cameraSystem.updateFrustum()
        applyCameraTransform()
    })
    resizeObserver.observe(container)

    // 初始化渲染循环
    const buildFrameState = () => frameBuilder.build(null)
    const renderLoop = createRenderLoop(
        renderer,
        scene,
        camera,
        ctx,
        buildFrameState,
        inputSystem,
        cameraWorldPosGU,
        zoom,
        applyCameraTransform,
        { layerManager } // 新增层管理器参数
    )

    // 启动渲染循环
    cameraSystem.updateFrustum()
    applyCameraTransform()
    renderLoop.start()

    // API
    const setSelectedEntityIds = (ids: number[]) => {
        frameBuilder.setSelectedIds(ids)
        void frameBuilder.build(null) // 触发帧状态更新，供层管理器使用
    }

    const updateFromSnapshot = (snapshot: SnapshotMessage) => {
        if (!snapshot.ok || !snapshot.realTimeWorldState) return

        // 合并公开实体与私有实体（按情报等级分层）喵。
        // 说明：SHIP（舰船实体）通常走 privateEntitiesByIntelLevel（私有实体分层）下发，
        // 若只读取 entities（公开实体）会导致前端“无舰船可渲染”喵。
        const publicEntities = snapshot.realTimeWorldState.entities ?? []
        const privateTierMap = snapshot.realTimeWorldState.privateEntitiesByIntelLevel ?? {}
        const privateEntities = Object.values(privateTierMap).flatMap((arr) => arr ?? [])

        // 去重：同一 entityId 以后出现者覆盖前者（通常 private 精度更高）喵。
        const mergedById = new Map<number, import('../net/snapshotWs').EntitySnapshot>()
        for (const e of publicEntities) {
            mergedById.set(e.entityId, e)
        }
        for (const e of privateEntities) {
            mergedById.set(e.entityId, e)
        }
        const mergedEntities = Array.from(mergedById.values())

        // 更新可见性状态
        const currentTime = Date.now()
        visibilityManager.updateFromSnapshot(
            mergedEntities,
            snapshot.realTimeWorldState.sectorCenters ?? [],
            currentTime
        )

        frameBuilder.updateSectorCenters(snapshot.realTimeWorldState.sectorCenters ?? [])
        frameBuilder.updateEntities(mergedEntities)
        entityQuery.updateSnapshot(snapshot)
        entityQuery.updateEntities(mergedEntities)

        void frameBuilder.build(snapshot) // 触发帧状态更新，供层管理器使用
    }

    const getEntityWorldPosGU = entityQuery.getEntityWorldPosGU

    const removeEntitiesFromCache = (entityIds: number[]) => {
        frameBuilder.removeEntities(entityIds)
        // 实体查询系统不需要 hub，它自己维护了快照状态喵
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

    const getCullingAabbGU = () => currentCullingAabb

    const setCurrentNationId = (nationId: string | null) => {
        visibilityManager.setCurrentNationId(nationId)
        // 国家变更后需要重新构建帧状态
        void frameBuilder.build(null) // 触发帧状态更新，供层管理器使用
    }

    const setGridVisible = (visible: boolean) => {
        // TODO: 实现网格层后重新启用网格可见性控制喵
        console.log(`setGridVisible(${visible}) - 网格层尚未实现`)
    }

    return {
        zoom,
        cameraHeight,
        cameraWorldPosGU,
        setZoom,
        setCameraHeight,
        applyCameraTransform,
        getCullingAabbGU,
        setSelectedEntityIds,
        updateFromSnapshot,
        removeEntitiesFromCache,
        removeSectorsFromCache,
        clearAllSectorsFromCache,
        getEntityWorldPosGU,
        setCurrentNationId,
        setGridVisible,
        onCameraChanged,
        dispose,
        // 层控制API
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

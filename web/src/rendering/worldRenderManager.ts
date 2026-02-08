/**
 * @file worldRenderManager.ts
 *
 * @description
 * 世界渲染管理器（World Render Manager）- 渲染系统的协调入口。
 *
 * 作用：
 * - 协调各子系统（CameraSystem、FrameStateBuilder、EntityQuerySystem、RenderLoop）。
 * - 处理输入事件（通过 InputSystem）。
 * - 提供对外 API（zoom、cameraWorldPosGU、updateFromSnapshot 等）。
 *
 * @important_notes
 * - 本文件为重构后的新入口，功能已下沉到各子系统模块。
 * - 具体实现见：
 *   - systems/cameraSystem.ts - 相机和渲染器管理
 *   - systems/frameStateBuilder.ts - 帧状态构建
 *   - systems/entityQuerySystem.ts - 实体位置查询
 *   - systems/renderLoop.ts - 渲染循环
 *   - subsystems/lodSystem.ts - LOD系统
 */
import * as THREE from 'three'
import type { SnapshotMessage } from '../net/snapshotWs'

import { createCameraSystem } from './systems/cameraSystem'
import { createFrameStateBuilder } from './systems/frameStateBuilder'
import { createEntityQuerySystem } from './systems/entityQuerySystem'
import { createRenderLoop } from './systems/renderLoop'
import { createTextureManager } from './subsystems/textureManager'
import { StarRenderer } from './subsystems/starRenderer'
import { PlanetRenderer } from './subsystems/planetRenderer'
import { SelectionRenderer } from './subsystems/selectionRenderer'
import { GridRenderer } from './subsystems/gridRenderer'
import { HexOutlineRenderer } from './subsystems/hexOutlineRenderer'
import type { LodState, LodOptions } from './subsystems/lodSystem'
import { createInputSystem } from '../input/inputSystem'

// 重新导出类型供外部使用
export type { LodState, LodOptions } from './subsystems/lodSystem'
export { LodLevel } from './subsystems/lodSystem'
export type { FrameState } from './systems/frameStateBuilder'
export type { CameraSystem } from './systems/cameraSystem'
export type { EntityQuerySystem } from './systems/entityQuerySystem'

export type WorldRenderer = {
    zoom: { value: number }
    cameraWorldPosGU: THREE.Vector2
    setZoom: (z: number) => void
    applyCameraTransform: () => void
    setSelectedEntityIds: (ids: number[]) => void
    updateFromSnapshot: (snapshot: SnapshotMessage) => void
    getEntityWorldPosGU: (entityId: number) => { x: number; y: number } | null
    dispose: () => void
}

export type WorldRendererOptions = {
    minZoom?: number
    maxZoom?: number
    getSpritePath?: (typeId: string) => string | undefined
    lod?: LodOptions
}

export type WorldRenderContext = {
    renderer: THREE.WebGLRenderer
    scene: THREE.Scene
    camera: THREE.OrthographicCamera
    worldGroup: THREE.Group
    entitiesGroup: THREE.Group
    zoom: { value: number }
    cameraWorldPosGU: THREE.Vector2
    getTexture: (path: string) => Promise<THREE.Texture>
    getEntityWorldPosGU: (entityId: number) => { x: number; y: number } | null
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
}

export function createWorldRenderManager(
    container: HTMLDivElement,
    options: WorldRendererOptions = {}
): WorldRenderer {
    const minZoom = options.minZoom ?? 0.1
    const maxZoom = options.maxZoom ?? 2_000_000

    // 状态
    const zoom = { value: 1 }
    const cameraWorldPosGU = new THREE.Vector2(0, 0)

    // 初始化相机系统
    const cameraSystem = createCameraSystem(container)
    const { renderer, scene, camera, worldGroup, entitiesGroup, canvas } = cameraSystem

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
        cameraWorldPosGU,
        getTexture: textureManager.getTexture,
        getEntityWorldPosGU: entityQuery.getEntityWorldPosGU,
        options,
    }

    // 初始化帧状态构建器
    const frameBuilder = createFrameStateBuilder(container, cameraWorldPosGU, zoom, options.lod)

    // 初始化渲染子系统
    const starRenderer = new StarRenderer()
    const planetRenderer = new PlanetRenderer()
    const selectionRenderer = new SelectionRenderer(entityQuery.getEntityWorldPosGU)
    const gridRenderer = new GridRenderer()
    const hexOutlineRenderer = new HexOutlineRenderer()

    const subsystems = [starRenderer, planetRenderer, selectionRenderer, gridRenderer, hexOutlineRenderer]
    for (const s of subsystems) s.init(ctx)

    // 初始化输入系统
    const inputSystem = createInputSystem(canvas)

    // 相机控制动作
    const applyCameraTransform = () => {
        cameraSystem.applyTransform(zoom.value, cameraWorldPosGU)
        // 触发重建以更新LOD
        const frame = frameBuilder.build(null)
        for (const s of subsystems) {
            s.update(ctx, frame)
        }
    }

    const setZoom = (z: number) => {
        zoom.value = Math.max(minZoom, Math.min(maxZoom, z))
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
        subsystems,
        buildFrameState,
        inputSystem,
        cameraWorldPosGU,
        zoom,
        applyCameraTransform
    )

    // 启动渲染循环
    cameraSystem.updateFrustum()
    applyCameraTransform()
    renderLoop.start()

    // API
    const setSelectedEntityIds = (ids: number[]) => {
        frameBuilder.setSelectedIds(ids)
        const frame = frameBuilder.build(null)
        for (const s of subsystems) {
            s.update(ctx, frame)
        }
    }

    const updateFromSnapshot = (snapshot: SnapshotMessage) => {
        if (!snapshot.ok || !snapshot.realTimeWorldState) return

        frameBuilder.updateSectorCenters(snapshot.realTimeWorldState.sectorCenters ?? [])
        frameBuilder.updateEntities(snapshot.realTimeWorldState.entities ?? [])
        entityQuery.updateSnapshot(snapshot)
        entityQuery.updateEntities(snapshot.realTimeWorldState.entities ?? [])

        const frame = frameBuilder.build(snapshot)
        for (const s of subsystems) {
            s.update(ctx, frame)
        }
    }

    const getEntityWorldPosGU = entityQuery.getEntityWorldPosGU

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

        for (const s of subsystems) s.dispose(ctx)
        textureManager.dispose()
        cameraSystem.dispose()
    }

    return {
        zoom,
        cameraWorldPosGU,
        setZoom,
        applyCameraTransform,
        setSelectedEntityIds,
        updateFromSnapshot,
        getEntityWorldPosGU,
        dispose,
    }
}

/**
 * @file worldRenderManager.ts
 *
 * @description
 * 世界渲染管理器（World Render Manager）。
 *
 * 作用：
 * - 统一管理 Three.js 渲染上下文（WebGLRenderer、Scene、Camera、RAF、ResizeObserver）。
 * - 提供相机控制（缩放/平移）。
 * - 协调各渲染子系统（star/planet/orbit 等）。
 * - 处理输入事件（拖拽/滚轮/右键）。
 *
 * @usage
 * - 在 InGameView 中调用 `createWorldRenderManager(container, options)`。
 * - 收到快照后调用 `updateFromSnapshot(snapshot)`。
 * - UI 侧选择变化时调用 `setSelectedEntityIds(ids)`。
 *
 * @provides
 * - **渲染实例**：WebGLRenderer 的创建/销毁与渲染循环。
 * - **相机控制**：缩放与平移（GU/像素口径）。
 * - **实体渲染**：通过子系统渲染恒星/行星/轨道等。
 *
 * @api
 * - createWorldRenderManager(container, options): WorldRenderer
 * - zoom: { value: number }
 * - cameraWorldPosGU: THREE.Vector2
 * - setZoom(z): void
 * - applyCameraTransform(): void
 * - setSelectedEntityIds(ids): void
 * - updateFromSnapshot(snapshot): void
 * - getEntityWorldPosGU(entityId): { x: number; y: number } | null
 * - dispose(): void
 *
 * @important_notes
 * - 本文件为重构后的新入口，不再兼容/依赖 threeWorldRenderer.ts。
 * - 第一阶段目标：渲染能跑起来 + 子系统编排稳定；后续再补剔除/LOD/调试网格等。
 */
import * as THREE from 'three'
import type { EntitySnapshot, PlanetDetails, SnapshotMessage } from '../net/snapshotWs'

import { createTextureManager } from './subsystems/textureManager'
import { StarRenderer } from './subsystems/starRenderer'
import { PlanetRenderer } from './subsystems/planetRenderer'
import { OrbitRenderer } from './subsystems/orbitRenderer'
import { SelectionRenderer } from './subsystems/selectionRenderer'
import { GridRenderer } from './subsystems/gridRenderer'
import { HexOutlineRenderer } from './subsystems/hexOutlineRenderer'

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
    options: WorldRendererOptions
}

export type WorldFrameState = {
    snapshot: SnapshotMessage | null
    entitiesById: Map<number, EntitySnapshot>
    sectorCenters: { q: number; r: number; x: number; y: number }[]
    selectedIds: Set<number>
    cullingAabb: { minX: number; maxX: number; minY: number; maxY: number }
    showPlanets: boolean
    farZoomStars: boolean
    totalDays: number
}

function computePlanetPosWorldGU(args: {
    planet: EntitySnapshot
    details: PlanetDetails
    orbitCenter: EntitySnapshot
    totalDays: number
}): { x: number; y: number } | null {
    const { details, orbitCenter, totalDays } = args
    if (!details || !orbitCenter.posWorldGU) return null

    const meanAnomaly = (Number(details.meanAnomalyDegAtEpoch ?? 0) * Math.PI) / 180
    const periodDays = Number(details.orbitalPeriodDays ?? 0)
    if (!Number.isFinite(periodDays) || periodDays <= 0) return null

    const angle = meanAnomaly + (totalDays / periodDays) * 2 * Math.PI

    const a = Number(details.semiMajorAxisGU ?? 0)
    const e = Number(details.eccentricity ?? 0)
    const b = a * Math.sqrt(Math.max(0, 1 - e ** 2))

    const periapsisArgRad = (Number(details.periapsisArgDeg ?? 0) * Math.PI) / 180

    const localX = a * Math.cos(angle)
    const localY = b * Math.sin(angle)

    const cosW = Math.cos(periapsisArgRad)
    const sinW = Math.sin(periapsisArgRad)
    const rotatedX = localX * cosW - localY * sinW
    const rotatedY = localX * sinW + localY * cosW

    return {
        x: orbitCenter.posWorldGU.x + rotatedX,
        y: orbitCenter.posWorldGU.y + rotatedY,
    }
}

export function createWorldRenderManager(container: HTMLDivElement, options: WorldRendererOptions = {}): WorldRenderer {
    const minZoom = options.minZoom ?? 0.1
    const maxZoom = options.maxZoom ?? 2_000_000

    const zoom = { value: 1 }
    const cameraWorldPosGU = new THREE.Vector2(0, 0)

    const renderer = new THREE.WebGLRenderer({ antialias: true, alpha: true })
    renderer.setPixelRatio(Math.min(window.devicePixelRatio || 1, 2))
    renderer.setClearColor(0x000000, 0)

    const scene = new THREE.Scene()

    const width = container.clientWidth
    const height = container.clientHeight
    const halfW = width / 2
    const halfH = height / 2
    const camera = new THREE.OrthographicCamera(-halfW, halfW, halfH, -halfH, -1_000_000, 1_000_000)
    camera.position.set(0, 0, 10)
    camera.lookAt(0, 0, 0)

    const worldGroup = new THREE.Group()
    worldGroup.frustumCulled = false
    scene.add(worldGroup)

    const entitiesGroup = new THREE.Group()
    entitiesGroup.frustumCulled = false
    worldGroup.add(entitiesGroup)

    const textureManager = createTextureManager()

    container.appendChild(renderer.domElement)

    let worldSectorCenters: { q: number; r: number; x: number; y: number }[] = []
    const entitiesById = new Map<number, EntitySnapshot>()
    let lastSnapshot: SnapshotMessage | null = null
    let selectedIds = new Set<number>()

    const ctx: WorldRenderContext = {
        renderer,
        scene,
        camera,
        worldGroup,
        entitiesGroup,
        zoom,
        cameraWorldPosGU,
        getTexture: textureManager.getTexture,
        options,
    }

    const starRenderer = new StarRenderer()
    const planetRenderer = new PlanetRenderer()
    const orbitRenderer = new OrbitRenderer()

    const getEntityWorldPosGUForSelection = (entityId: number): { x: number; y: number } | null => {
        const e = entitiesById.get(entityId)
        if (!e) return null

        if (e.entityType === 'STAR') {
            return e.posWorldGU ?? null
        }

        if (e.entityType === 'PLANET') {
            const details = e.details as PlanetDetails
            if (!details) return null

            const center = entitiesById.get(details.orbitCenterEntityId)
            if (!center) return null

            const totalDays =
                (lastSnapshot?.realTimeWorldState?.gameDatetimeDay ?? 0) +
                (lastSnapshot?.realTimeWorldState?.accGameHoursInDay ?? 0) / 24

            return computePlanetPosWorldGU({ planet: e, details, orbitCenter: center, totalDays })
        }

        return null
    }

    const selectionRenderer = new SelectionRenderer(getEntityWorldPosGUForSelection)
    const gridRenderer = new GridRenderer()
    const hexOutlineRenderer = new HexOutlineRenderer()

    const subsystems = [starRenderer, orbitRenderer, planetRenderer, selectionRenderer, gridRenderer, hexOutlineRenderer]
    for (const s of subsystems) s.init(ctx)

    const updateCameraFrustum = () => {
        const w = container.clientWidth
        const h = container.clientHeight
        renderer.setSize(w, h, true)

        const hw = w / 2
        const hh = h / 2
        camera.left = -hw
        camera.right = hw
        camera.top = hh
        camera.bottom = -hh
        camera.updateProjectionMatrix()
    }

    const applyCameraTransform = () => {
        camera.position.set(0, 0, 10)
        camera.lookAt(0, 0, 0)

        camera.zoom = 1 / zoom.value
        camera.updateProjectionMatrix()

        if (lastSnapshot) {
            rebuild(lastSnapshot)
        }
    }

    const setZoom = (z: number) => {
        zoom.value = Math.max(minZoom, Math.min(maxZoom, z))
        applyCameraTransform()
    }

    const STAR_SPRITE_DISABLE_ZOOM_THRESHOLD = 100_000
    const PLANET_SPRITE_DISABLE_ZOOM_THRESHOLD = 100_000

    const buildFrameState = (snapshot: SnapshotMessage | null): WorldFrameState => {
        const viewWidthGU = (camera.right - camera.left) / camera.zoom
        const viewHeightGU = (camera.top - camera.bottom) / camera.zoom

        const cullingScale = zoom.value <= 100_000 ? 3.0 : 1.5
        const cullingAabb = {
            minX: cameraWorldPosGU.x - (viewWidthGU * cullingScale) / 2,
            maxX: cameraWorldPosGU.x + (viewWidthGU * cullingScale) / 2,
            minY: cameraWorldPosGU.y - (viewHeightGU * cullingScale) / 2,
            maxY: cameraWorldPosGU.y + (viewHeightGU * cullingScale) / 2,
        }

        const totalDays =
            (snapshot?.realTimeWorldState?.gameDatetimeDay ?? 0) + (snapshot?.realTimeWorldState?.accGameHoursInDay ?? 0) / 24

        return {
            snapshot,
            entitiesById,
            sectorCenters: worldSectorCenters,
            selectedIds,
            cullingAabb,
            showPlanets: zoom.value <= PLANET_SPRITE_DISABLE_ZOOM_THRESHOLD,
            farZoomStars: zoom.value > STAR_SPRITE_DISABLE_ZOOM_THRESHOLD,
            totalDays,
        }
    }

    const rebuild = (snapshot: SnapshotMessage | null) => {
        const frame = buildFrameState(snapshot)
        for (const s of subsystems) {
            s.update(ctx, frame)
        }
    }

    // --- Input ---
    let isDragging = false
    const dragStartClient = new THREE.Vector2(0, 0)
    const dragStartCamera = new THREE.Vector2(0, 0)

    const canvas = renderer.domElement

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
        }
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

    const resizeObserver = new ResizeObserver(() => {
        updateCameraFrustum()
        applyCameraTransform()
    })
    resizeObserver.observe(container)

    updateCameraFrustum()
    applyCameraTransform()

    let rafId = 0
    const renderLoop = () => {
        rafId = requestAnimationFrame(renderLoop)
        renderer.render(scene, camera)
    }
    rafId = requestAnimationFrame(renderLoop)

    const setSelectedEntityIds = (ids: number[]) => {
        selectedIds = new Set(ids)
        if (lastSnapshot) rebuild(lastSnapshot)
    }

    const updateFromSnapshot = (snapshot: SnapshotMessage) => {
        if (!snapshot.ok || !snapshot.realTimeWorldState) return

        worldSectorCenters = snapshot.realTimeWorldState.sectorCenters ?? []

        entitiesById.clear()
        for (const e of snapshot.realTimeWorldState.entities ?? []) {
            entitiesById.set(e.entityId, e)
        }

        lastSnapshot = snapshot
        rebuild(snapshot)
    }

    const getEntityWorldPosGU = (entityId: number): { x: number; y: number } | null => {
        const e = entitiesById.get(entityId)
        if (!e) return null

        if (e.entityType === 'STAR') {
            return e.posWorldGU ?? null
        }

        if (e.entityType === 'PLANET') {
            const details = e.details as PlanetDetails
            if (!details) return null

            const center = entitiesById.get(details.orbitCenterEntityId)
            if (!center) return null

            const totalDays =
                (lastSnapshot?.realTimeWorldState?.gameDatetimeDay ?? 0) +
                (lastSnapshot?.realTimeWorldState?.accGameHoursInDay ?? 0) / 24

            return computePlanetPosWorldGU({ planet: e, details, orbitCenter: center, totalDays })
        }

        return null
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

        cancelAnimationFrame(rafId)

        for (const s of subsystems) s.dispose(ctx)
        textureManager.dispose()

        renderer.dispose()
        const gl = renderer.getContext()
        gl.getExtension('WEBGL_lose_context')?.loseContext()

        if (renderer.domElement.parentElement === container) {
            container.removeChild(renderer.domElement)
        }
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

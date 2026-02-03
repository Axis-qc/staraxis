/**
 * @file threeWorldRenderer.ts
 *
 * @description
 * Three 世界渲染器（World Renderer）。
 *
 * 职责：
 * - 创建并维护 three.js 的 `Scene`/`OrthographicCamera`/`WebGLRenderer`。
 * - 按后端快照（`SnapshotMessage`）重建可见对象：恒星/行星/轨道/选中环/网格等。
 * - 提供镜头缩放/平移能力，并维护相机中心（`cameraWorldPosGU`）与缩放倍率（`zoom.value`）。
 * - 渲染剔除使用自定义 AABB（`cullingAabb`）与对象池回收机制；为避免 three.js 自动视锥裁剪带来的不稳定，
 *   本文件对渲染对象统一关闭 `frustumCulled`。
 *
 * @usage
 * - 在视图层（如 `InGameView.vue`）中调用 `createThreeWorldRenderer(container, options)`。
 * - 收到 WS 快照后调用 `updateFromSnapshot(snapshot)`。
 * - UI 侧选择变化时调用 `setSelectedEntityIds(ids)`。
 *
 * @provides
 * - **渲染实例**：`WebGLRenderer` 的创建/销毁与渲染循环。
 * - **实体渲染**：STAR/PLANET sprite、轨道线、选择环、网格等。
 * - **相机控制**：缩放与平移（GU/像素口径）。
 */
import * as THREE from 'three'
import type { EntitySnapshot, SnapshotMessage, StarDetails, PlanetDetails } from '../net/snapshotWs'
import { buildHexSegmentPositions, SECTOR_SIZE_GU } from './hexSectorGeometry'

export type ThreeWorldRenderer = {
    zoom: { value: number }
    cameraWorldPosGU: THREE.Vector2
    setZoom: (z: number) => void
    applyCameraTransform: () => void
    setSelectedEntityIds: (ids: number[]) => void
    updateFromSnapshot: (snapshot: SnapshotMessage) => void
    getEntityWorldPosGU: (entityId: number) => { x: number; y: number } | null
    dispose: () => void
}

export type ThreeWorldRendererOptions = {
    minZoom?: number
    maxZoom?: number
    getSpritePath?: (typeId: string) => string | undefined
}

function createDynamicGrid() {
    const gridMaterial = new THREE.LineBasicMaterial({
        color: 0x2f3a4a,
        transparent: true,
        opacity: 0.5,
    })
    const gridGeometry = new THREE.BufferGeometry()
    const grid = new THREE.LineSegments(gridGeometry, gridMaterial)
    grid.frustumCulled = false

    const update = (camera: THREE.OrthographicCamera, zoom: number) => {
        const viewWidthGU = (camera.right - camera.left) / camera.zoom
        const viewHeightGU = (camera.top - camera.bottom) / camera.zoom

        const viewMinX = -viewWidthGU / 2
        const viewMaxX = viewWidthGU / 2
        const viewMinY = -viewHeightGU / 2
        const viewMaxY = viewHeightGU / 2

        const minStepGU = 10 * zoom
        const powerOf10 = 10 ** Math.floor(Math.log10(minStepGU))
        let stepGU = powerOf10
        if (stepGU / zoom < 10) stepGU *= 2
        if (stepGU / zoom < 10) stepGU *= 2.5
        if (stepGU / zoom < 10) stepGU *= 2

        const vertices: number[] = []

        const startX = Math.floor(viewMinX / stepGU) * stepGU
        const endX = Math.ceil(viewMaxX / stepGU) * stepGU
        for (let x = startX; x <= endX; x += stepGU) {
            vertices.push(x, viewMinY, 0, x, viewMaxY, 0)
        }

        const startY = Math.floor(viewMinY / stepGU) * stepGU
        const endY = Math.ceil(viewMaxY / stepGU) * stepGU
        for (let y = startY; y <= endY; y += stepGU) {
            vertices.push(viewMinX, y, 0, viewMaxX, y, 0)
        }

        gridGeometry.setAttribute('position', new THREE.Float32BufferAttribute(vertices, 3))
    }

    const dispose = () => {
        gridGeometry.dispose()
        gridMaterial.dispose()
    }

    return { grid, update, dispose }
}

function isPointInAabb(point: { x: number; y: number }, aabb: { minX: number; maxX: number; minY: number; maxY: number }): boolean {
    return point.x >= aabb.minX && point.x <= aabb.maxX && point.y >= aabb.minY && point.y <= aabb.maxY
}

export function createThreeWorldRenderer(
    container: HTMLDivElement,
    options: ThreeWorldRendererOptions = {},
): ThreeWorldRenderer {
    const minZoom = options.minZoom ?? 0.1
    const maxZoom = options.maxZoom ?? 2_000_000
    const getSpritePath = options.getSpritePath ?? (() => undefined)

    const zoom = { value: 1 }

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

    const dynamicGrid = createDynamicGrid()
    scene.add(dynamicGrid.grid)

    let axes: THREE.AxesHelper | null = new THREE.AxesHelper(2_000)
    axes.frustumCulled = false
    scene.add(axes)

    const textureLoader = new THREE.TextureLoader()
    const textureCache = new Map<string, THREE.Texture>()
    const getTexture = (path: string) => {
        let texture = textureCache.get(path)
        if (!texture) {
            texture = textureLoader.load(`/assets/${path}`)
            texture.anisotropy = renderer.capabilities.getMaxAnisotropy()
            textureCache.set(path, texture)
        }
        return texture
    }

    let debugVisible = true

    container.appendChild(renderer.domElement)

    const cameraWorldPosGU = new THREE.Vector2(0, 0)

    let worldSectorCenters: { q: number; r: number; x: number; y: number }[] = []
    let entities = new Map<number, EntitySnapshot>()
    let lastSnapshot: SnapshotMessage | null = null

    const entitiesGroup = new THREE.Group()
    entitiesGroup.frustumCulled = false
    worldGroup.add(entitiesGroup)

    // --- Object pools / caches ---

    const starDotPool: THREE.Mesh[] = []
    const activeStarDotByEntityId = new Map<number, THREE.Mesh>()

    const starSpritePoolByPath = new Map<string, THREE.Sprite[]>()
    const activeStarSpriteByEntityId = new Map<number, THREE.Sprite>()

    const planetSpritePoolByPath = new Map<string, THREE.Sprite[]>()
    const activePlanetSpriteByEntityId = new Map<number, THREE.Sprite>()

    const orbitLinePool: THREE.Line[] = []
    const activeOrbitLineByEntityId = new Map<number, THREE.Line>()

    let selectedEntityIds = new Set<number>()

    const selectionRingPool: THREE.Mesh[] = []
    const activeSelectionRingByEntityId = new Map<number, THREE.Mesh>()

    const acquireSelectionRing = () => {
        const ring = selectionRingPool.pop()
        if (ring) {
            ring.visible = true
            return ring
        }

        const geo = new THREE.RingGeometry(0.85, 1.0, 48)
        const mat = new THREE.MeshBasicMaterial({
            color: 0xffe04d,
            transparent: true,
            opacity: 0.95,
            depthWrite: false,
        })
        const m = new THREE.Mesh(geo, mat)
        m.visible = true
        m.frustumCulled = false
        return m
    }

    const releaseSelectionRing = (ring: THREE.Mesh) => {
        ring.visible = false
        entitiesGroup.remove(ring)
        selectionRingPool.push(ring)
    }

    const acquireStarDot = () => {
        const dot = starDotPool.pop()
        if (dot) {
            dot.visible = true
            return dot
        }

        const geo = new THREE.CircleGeometry(1, 12)
        const mat = new THREE.MeshBasicMaterial({ color: 0xffffff, transparent: true, opacity: 0.9, depthWrite: false })
        const m = new THREE.Mesh(geo, mat)
        m.visible = true
        m.frustumCulled = false
        return m
    }

    const releaseStarDot = (dot: THREE.Mesh) => {
        dot.visible = false
        entitiesGroup.remove(dot)
        starDotPool.push(dot)
    }

    const acquireSprite = (poolByPath: Map<string, THREE.Sprite[]>, spritePath: string, texture: THREE.Texture) => {
        const pool = poolByPath.get(spritePath)
        if (pool && pool.length > 0) {
            const s = pool.pop()!
            s.visible = true
            return s
        }
        const material = new THREE.SpriteMaterial({ map: texture, sizeAttenuation: false, depthWrite: false })
        const sprite = new THREE.Sprite(material)
        sprite.visible = true
        sprite.frustumCulled = false
        return sprite
    }

    const releaseSprite = (poolByPath: Map<string, THREE.Sprite[]>, spritePath: string, sprite: THREE.Sprite) => {
        sprite.visible = false
        entitiesGroup.remove(sprite)
        const pool = poolByPath.get(spritePath) ?? []
        pool.push(sprite)
        poolByPath.set(spritePath, pool)
    }

    const acquireOrbitLine = () => {
        const line = orbitLinePool.pop()
        if (line) {
            line.visible = true
            return line
        }
        const geometry = new THREE.BufferGeometry()
        const material = new THREE.LineBasicMaterial({
            color: 0xaaaaaa,
            transparent: true,
            opacity: 0.5,
            depthTest: false,
            depthWrite: false,
        })
        const l = new THREE.Line(geometry, material)
        l.visible = true
        l.frustumCulled = false
        return l
    }

    const releaseOrbitLine = (line: THREE.Line) => {
        line.visible = false
        entitiesGroup.remove(line)
        orbitLinePool.push(line)
    }

    let hexOutlines: THREE.LineSegments | undefined

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

        dynamicGrid.update(camera, zoom.value)

        if (worldSectorCenters.length > 0) {
            rebuildVisibleObjects(lastSnapshot)
        }
    }

    const setZoom = (z: number) => {
        zoom.value = Math.max(minZoom, Math.min(maxZoom, z))
        applyCameraTransform()
    }

    const STAR_SPRITE_DISABLE_ZOOM_THRESHOLD = 100_000
    const PLANET_SPRITE_DISABLE_ZOOM_THRESHOLD = 100_000

    const rebuildVisibleObjects = (snapshot: SnapshotMessage | null) => {
        if (hexOutlines) {
            worldGroup.remove(hexOutlines)
            hexOutlines.geometry.dispose();
            (hexOutlines.material as THREE.Material).dispose()
            hexOutlines = undefined
        }

        const viewWidthGU = (camera.right - camera.left) / camera.zoom
        const viewHeightGU = (camera.top - camera.bottom) / camera.zoom
        const cullingScale = zoom.value <= 100_000 ? 3.0 : 1.5
        const cullingAabb = {
            minX: cameraWorldPosGU.x - (viewWidthGU * cullingScale) / 2,
            maxX: cameraWorldPosGU.x + (viewWidthGU * cullingScale) / 2,
            minY: cameraWorldPosGU.y - (viewHeightGU * cullingScale) / 2,
            maxY: cameraWorldPosGU.y + (viewHeightGU * cullingScale) / 2,
        }

        const visibleCentersCameraLocal: { x: number; y: number }[] = []
        for (const s of worldSectorCenters) {
            if (isPointInAabb(s, cullingAabb)) {
                visibleCentersCameraLocal.push({ x: s.x - cameraWorldPosGU.x, y: s.y - cameraWorldPosGU.y })
            }
        }

        const segPositions = buildHexSegmentPositions(visibleCentersCameraLocal)
        const geometry = new THREE.BufferGeometry()
        geometry.setAttribute('position', new THREE.BufferAttribute(segPositions, 3))
        const material = new THREE.LineBasicMaterial({ color: 0x7fd3ff, transparent: true, opacity: 0.55 })
        hexOutlines = new THREE.LineSegments(geometry, material)
        hexOutlines.frustumCulled = false
        worldGroup.add(hexOutlines)

        const farZoomStars = zoom.value > STAR_SPRITE_DISABLE_ZOOM_THRESHOLD
        const showPlanets = zoom.value <= PLANET_SPRITE_DISABLE_ZOOM_THRESHOLD

        const nextStarDotIds = new Set<number>()
        const nextStarSpriteIds = new Set<number>()
        const nextPlanetSpriteIds = new Set<number>()
        const nextOrbitIds = new Set<number>()
        const nextSelectionRingIds = new Set<number>()

        for (const entity of entities.values()) {
            switch (entity.entityType) {
                case 'STAR': {
                    const isSelected = selectedEntityIds.has(entity.entityId)

                    if (!entity.posWorldGU || (!isSelected && !isPointInAabb(entity.posWorldGU, cullingAabb))) {
                        continue
                    }

                    const details = entity.details as StarDetails

                    if (farZoomStars) {
                        nextStarDotIds.add(entity.entityId)
                        const radiusGU = details.radiusGU
                        const size = Math.max(zoom.value * 2, radiusGU * 2)

                        let dot = activeStarDotByEntityId.get(entity.entityId)
                        if (!dot) {
                            dot = acquireStarDot()
                            activeStarDotByEntityId.set(entity.entityId, dot)
                            entitiesGroup.add(dot)
                        }
                        dot.scale.set(size, size, 1)
                        dot.position.set(entity.posWorldGU.x - cameraWorldPosGU.x, entity.posWorldGU.y - cameraWorldPosGU.y, 0)
                        dot.visible = true

                        const oldSprite = activeStarSpriteByEntityId.get(entity.entityId)
                        if (oldSprite) {
                            oldSprite.visible = false
                        }

                    } else {
                        const spritePath = getSpritePath(details.starTypeId)
                        if (!spritePath) {
                            continue
                        }

                        nextStarSpriteIds.add(entity.entityId)

                        const texture = getTexture(spritePath)
                        const radiusGU = details.radiusGU
                        const size = radiusGU * 2

                        let sprite = activeStarSpriteByEntityId.get(entity.entityId)
                        if (!sprite) {
                            sprite = acquireSprite(starSpritePoolByPath, spritePath, texture)
                            activeStarSpriteByEntityId.set(entity.entityId, sprite)
                            entitiesGroup.add(sprite)
                        } else {
                            const mat = sprite.material as THREE.SpriteMaterial
                            if (mat.map !== texture) {
                                mat.map = texture
                                mat.needsUpdate = true
                            }
                        }

                        sprite.scale.set(size, size, 1)
                        sprite.position.set(entity.posWorldGU.x - cameraWorldPosGU.x, entity.posWorldGU.y - cameraWorldPosGU.y, 0)
                        sprite.visible = true

                        const oldDot = activeStarDotByEntityId.get(entity.entityId)
                        if (oldDot) {
                            oldDot.visible = false
                        }
                    }

                    if (isSelected) {
                        nextSelectionRingIds.add(entity.entityId)
                        let ring = activeSelectionRingByEntityId.get(entity.entityId)
                        if (!ring) {
                            ring = acquireSelectionRing()
                            activeSelectionRingByEntityId.set(entity.entityId, ring)
                            entitiesGroup.add(ring)
                        }
                        const size = Math.max(details.radiusGU * 2.4, zoom.value * 4)
                        ring.scale.set(size, size, 1)
                        ring.position.set(entity.posWorldGU.x - cameraWorldPosGU.x, entity.posWorldGU.y - cameraWorldPosGU.y, 1) // z=1 to be on top
                        ring.visible = true
                    }

                    break
                }

                case 'PLANET': {
                    const isSelected = selectedEntityIds.has(entity.entityId)
                    if (!showPlanets && !isSelected) continue
                    const details = entity.details as PlanetDetails
                    const orbit = details.orbit
                    if (!orbit) continue

                    const orbitCenter = entities.get(orbit.orbitCenterEntityId)
                    if (!orbitCenter || !orbitCenter.posWorldGU) continue

                    const semiMajorAxisGU = orbit.semiMajorAxisGU

                    const totalDays =
                        (snapshot?.realTimeWorldState?.gameDatetimeDay ?? 0) +
                        (snapshot?.realTimeWorldState?.accGameHoursInDay ?? 0) / 24
                    const meanAnomaly = (orbit.meanAnomalyDegAtEpoch * Math.PI) / 180
                    const angle = meanAnomaly + (totalDays / orbit.orbitalPeriodDays) * 2 * Math.PI

                    const a = semiMajorAxisGU
                    const b = semiMajorAxisGU * Math.sqrt(1 - orbit.eccentricity ** 2)

                    const periapsisArgRad = ((orbit as any).periapsisArgDeg ?? 0) * (Math.PI / 180)
                    const localX = a * Math.cos(angle)
                    const localY = b * Math.sin(angle)
                    const cosW = Math.cos(periapsisArgRad)
                    const sinW = Math.sin(periapsisArgRad)
                    const rotatedX = localX * cosW - localY * sinW
                    const rotatedY = localX * sinW + localY * cosW

                    const planetX = orbitCenter.posWorldGU.x + rotatedX
                    const planetY = orbitCenter.posWorldGU.y + rotatedY

                    if (!isSelected && !isPointInAabb({ x: planetX, y: planetY }, cullingAabb)) {
                        continue
                    }

                    const spritePath = getSpritePath(details.planetTypeId)
                    if (!spritePath) {
                        continue
                    }

                    nextPlanetSpriteIds.add(entity.entityId)
                    const texture = getTexture(spritePath)

                    let sprite = activePlanetSpriteByEntityId.get(entity.entityId)
                    if (!sprite) {
                        sprite = acquireSprite(planetSpritePoolByPath, spritePath, texture)
                        activePlanetSpriteByEntityId.set(entity.entityId, sprite)
                        entitiesGroup.add(sprite)
                    } else {
                        const mat = sprite.material as THREE.SpriteMaterial
                        if (mat.map !== texture) {
                            mat.map = texture
                            mat.needsUpdate = true
                        }
                    }

                    const radiusGU = details.radiusGU
                    const size = radiusGU * 2
                    sprite.scale.set(size, size, 1)
                    sprite.position.set(planetX - cameraWorldPosGU.x, planetY - cameraWorldPosGU.y, 0)
                    sprite.visible = true

                    {
                        nextOrbitIds.add(entity.entityId)
                        let ellipse = activeOrbitLineByEntityId.get(entity.entityId)
                        if (!ellipse) {
                            ellipse = acquireOrbitLine()
                            activeOrbitLineByEntityId.set(entity.entityId, ellipse)
                            entitiesGroup.add(ellipse)
                        }

                        const curve = new THREE.EllipseCurve(
                            0,
                            0,
                            semiMajorAxisGU,
                            semiMajorAxisGU * Math.sqrt(1 - orbit.eccentricity ** 2),
                            0,
                            2 * Math.PI,
                            false,
                            ((orbit as any).periapsisArgDeg ?? 0) * (Math.PI / 180),
                        )
                        const points = curve.getPoints(128)
                        for (const p of points) {
                            p.x += orbitCenter.posWorldGU.x - cameraWorldPosGU.x
                            p.y += orbitCenter.posWorldGU.y - cameraWorldPosGU.y
                        }
                        ellipse.geometry.setFromPoints(points)
                        ellipse.visible = true
                    }

                    if (isSelected) {
                        nextSelectionRingIds.add(entity.entityId)
                        let ring = activeSelectionRingByEntityId.get(entity.entityId)
                        if (!ring) {
                            ring = acquireSelectionRing()
                            activeSelectionRingByEntityId.set(entity.entityId, ring)
                            entitiesGroup.add(ring)
                        }
                        const size = Math.max(details.radiusGU * 2.4, zoom.value * 4)
                        ring.scale.set(size, size, 1)
                        ring.position.set(planetX - cameraWorldPosGU.x, planetY - cameraWorldPosGU.y, 1) // z=1 to be on top
                        ring.visible = true
                    }

                    break
                }
            }
        }

        for (const [id, dot] of activeStarDotByEntityId.entries()) {
            if (!nextStarDotIds.has(id)) {
                activeStarDotByEntityId.delete(id)
                releaseStarDot(dot)
            }
        }

        for (const [id, sprite] of activeStarSpriteByEntityId.entries()) {
            if (!nextStarSpriteIds.has(id)) {
                activeStarSpriteByEntityId.delete(id)
                const details = entities.get(id)?.details as StarDetails | undefined
                const spritePath = details ? getSpritePath(details.starTypeId) : undefined
                if (spritePath) {
                    releaseSprite(starSpritePoolByPath, spritePath, sprite)
                } else {
                    sprite.visible = false
                    entitiesGroup.remove(sprite)
                }
            }
        }

        for (const [id, sprite] of activePlanetSpriteByEntityId.entries()) {
            if (!nextPlanetSpriteIds.has(id)) {
                activePlanetSpriteByEntityId.delete(id)
                const details = entities.get(id)?.details as PlanetDetails | undefined
                const spritePath = details ? getSpritePath(details.planetTypeId) : undefined
                if (spritePath) {
                    releaseSprite(planetSpritePoolByPath, spritePath, sprite)
                } else {
                    sprite.visible = false
                    entitiesGroup.remove(sprite)
                }
            }
        }

        for (const [id, ellipse] of activeOrbitLineByEntityId.entries()) {
            if (!nextOrbitIds.has(id)) {
                activeOrbitLineByEntityId.delete(id)
                releaseOrbitLine(ellipse)
            }
        }

        for (const [id, ring] of activeSelectionRingByEntityId.entries()) {
            if (!nextSelectionRingIds.has(id)) {
                activeSelectionRingByEntityId.delete(id)
                releaseSelectionRing(ring)
            }
        }
    }

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

    const onKeyDown = (e: KeyboardEvent) => {
        if (e.key !== 'p' && e.key !== 'P') return
        debugVisible = !debugVisible
        dynamicGrid.grid.visible = debugVisible
        if (axes) axes.visible = debugVisible
    }

    canvas.addEventListener('contextmenu', onContextMenu)
    canvas.addEventListener('pointerdown', onPointerDown)
    canvas.addEventListener('pointermove', onPointerMove)
    canvas.addEventListener('pointerup', endDrag)
    canvas.addEventListener('pointercancel', endDrag)
    canvas.addEventListener('pointerleave', endDrag)
    canvas.addEventListener('wheel', onWheel, { passive: false })
    window.addEventListener('keydown', onKeyDown)

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
        selectedEntityIds = new Set(ids)
        rebuildVisibleObjects(lastSnapshot)
    }

    const updateFromSnapshot = (snapshot: SnapshotMessage) => {
        if (!snapshot.ok || !snapshot.realTimeWorldState) return

        worldSectorCenters = snapshot.realTimeWorldState.sectorCenters ?? []
        entities.clear()
        for (const entity of snapshot.realTimeWorldState.entities ?? []) {
            entities.set(entity.entityId, entity)
        }

        if (axes) {
            const axisSize = Math.max(2_000, SECTOR_SIZE_GU * 0.25)
            axes.scale.set(axisSize / 2_000, axisSize / 2_000, axisSize / 2_000)
        }

        lastSnapshot = snapshot
        rebuildVisibleObjects(snapshot)
        applyCameraTransform()
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
        window.removeEventListener('keydown', onKeyDown)

        cancelAnimationFrame(rafId)

        if (hexOutlines) {
            worldGroup.remove(hexOutlines)
            hexOutlines.geometry.dispose();
            (hexOutlines.material as THREE.Material).dispose()
            hexOutlines = undefined
        }

        dynamicGrid.dispose()

        for (const dot of starDotPool) {
            dot.geometry.dispose()
            if (Array.isArray(dot.material)) {
                dot.material.forEach((m) => m.dispose())
            } else {
                ; (dot.material as THREE.Material).dispose()
            }
        }
        for (const dot of activeStarDotByEntityId.values()) {
            dot.geometry.dispose()
            if (Array.isArray(dot.material)) {
                dot.material.forEach((m) => m.dispose())
            } else {
                ; (dot.material as THREE.Material).dispose()
            }
        }

        const disposeSprite = (sprite: THREE.Sprite) => {
            ; (sprite.material as THREE.SpriteMaterial).dispose()
        }
        for (const pool of starSpritePoolByPath.values()) {
            pool.forEach(disposeSprite)
        }
        for (const pool of planetSpritePoolByPath.values()) {
            pool.forEach(disposeSprite)
        }
        for (const sprite of activeStarSpriteByEntityId.values()) {
            disposeSprite(sprite)
        }
        for (const sprite of activePlanetSpriteByEntityId.values()) {
            disposeSprite(sprite)
        }

        for (const line of orbitLinePool) {
            line.geometry.dispose()
                ; (line.material as THREE.Material).dispose()
        }
        for (const line of activeOrbitLineByEntityId.values()) {
            line.geometry.dispose()
                ; (line.material as THREE.Material).dispose()
        }

        for (const ring of selectionRingPool) {
            ring.geometry.dispose()
                ; (ring.material as THREE.Material).dispose()
        }
        for (const ring of activeSelectionRingByEntityId.values()) {
            ring.geometry.dispose()
                ; (ring.material as THREE.Material).dispose()
        }

        if (axes) {
            scene.remove(axes);
            (axes.geometry as any)?.dispose?.();
            (axes.material as any)?.dispose?.()
            axes = null
        }

        renderer.dispose()
        const gl = renderer.getContext()
        gl.getExtension('WEBGL_lose_context')?.loseContext()

        if (renderer.domElement.parentElement === container) {
            container.removeChild(renderer.domElement)
        }
    }

    const getEntityWorldPosGU = (entityId: number): { x: number; y: number } | null => {
        const e = entities.get(entityId)
        if (!e) return null

        if (e.entityType === 'STAR') {
            return e.posWorldGU ?? null
        }

        if (e.entityType === 'PLANET') {
            const details = e.details as PlanetDetails
            const orbit = details.orbit
            if (!orbit) return null

            const orbitCenter = entities.get(orbit.orbitCenterEntityId)
            if (!orbitCenter?.posWorldGU) return null

            const totalDays =
                (lastSnapshot?.realTimeWorldState?.gameDatetimeDay ?? 0) +
                (lastSnapshot?.realTimeWorldState?.accGameHoursInDay ?? 0) / 24
            const meanAnomaly = (orbit.meanAnomalyDegAtEpoch * Math.PI) / 180
            const angle = meanAnomaly + (totalDays / orbit.orbitalPeriodDays) * 2 * Math.PI

            const a = orbit.semiMajorAxisGU
            const b = orbit.semiMajorAxisGU * Math.sqrt(1 - orbit.eccentricity ** 2)

            const periapsisArgRad = ((orbit as any).periapsisArgDeg ?? 0) * (Math.PI / 180)
            const localX = a * Math.cos(angle)
            const localY = b * Math.sin(angle)
            const cosW = Math.cos(periapsisArgRad)
            const sinW = Math.sin(periapsisArgRad)
            const rotatedX = localX * cosW - localY * sinW
            const rotatedY = localX * sinW + localY * cosW

            const x = orbitCenter.posWorldGU.x + rotatedX
            const y = orbitCenter.posWorldGU.y + rotatedY
            return { x, y }
        }

        return null
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

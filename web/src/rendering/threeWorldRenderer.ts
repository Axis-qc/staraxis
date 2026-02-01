import * as THREE from 'three'
import type { SnapshotMessage } from '../net/snapshotWs'
import { buildHexSegmentPositions, SECTOR_SIZE_GU } from './hexSectorGeometry'

export type ThreeWorldRenderer = {
    zoom: { value: number }
    cameraWorldPosGU: THREE.Vector2
    setZoom: (z: number) => void
    applyCameraTransform: () => void
    updateFromSnapshot: (snapshot: SnapshotMessage) => void
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

        // 视野范围以“相机局部坐标”(0,0)为中心
        const viewMinX = -viewWidthGU / 2
        const viewMaxX = viewWidthGU / 2
        const viewMinY = -viewHeightGU / 2
        const viewMaxY = viewHeightGU / 2

        // 目标：每格约 10px。权威口径：1px = zoom GU => 10px = 10*zoom GU
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

function worldAabbIntersects(a: { minX: number; maxX: number; minY: number; maxY: number }, b: {
    minX: number
    maxX: number
    minY: number
    maxY: number
}): boolean {
    return !(a.maxX < b.minX || a.minX > b.maxX || a.maxY < b.minY || a.minY > b.maxY)
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
    scene.add(worldGroup)

    const dynamicGrid = createDynamicGrid()
    scene.add(dynamicGrid.grid)

    let axes: THREE.AxesHelper | null = new THREE.AxesHelper(2_000)
    scene.add(axes)

    const textureLoader = new THREE.TextureLoader()
    const textureCache = new Map<string, THREE.Texture>()
    const getTexture = (path: string) => {
        let texture = textureCache.get(path)
        if (!texture) {
            // All assets are served from the root /assets path
            texture = textureLoader.load(`/assets/${path}`)
            texture.anisotropy = renderer.capabilities.getMaxAnisotropy()
            textureCache.set(path, texture)
        }
        return texture
    }

    let debugVisible = true

    container.appendChild(renderer.domElement)

    // 权威相机世界坐标（GU，float64 语义）
    const cameraWorldPosGU = new THREE.Vector2(0, 0)

    // world 数据缓存（权威坐标，仅用于逻辑判断，不进 GPU）
    let worldSectorCenters: { q: number; r: number; x: number; y: number }[] = []
    let worldStarSystems: any[] = []
    let lastSnapshot: SnapshotMessage | null = null

    const starSystemsGroup = new THREE.Group()
    worldGroup.add(starSystemsGroup)

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
    const ORBIT_DISABLE_ZOOM_THRESHOLD = 200_000

    const rebuildVisibleObjects = (snapshot: SnapshotMessage | null) => {
        if (worldSectorCenters.length === 0) {
            return
        }

        // 当前视野的 world AABB（用于逻辑裁剪）
        const viewWidthGU = (camera.right - camera.left) / camera.zoom
        const viewHeightGU = (camera.top - camera.bottom) / camera.zoom
        const viewAabb = {
            minX: cameraWorldPosGU.x - viewWidthGU / 2,
            maxX: cameraWorldPosGU.x + viewWidthGU / 2,
            minY: cameraWorldPosGU.y - viewHeightGU / 2,
            maxY: cameraWorldPosGU.y + viewHeightGU / 2,
        }

        // 星区包围盒：中心点 + 半径近似
        const radius = SECTOR_SIZE_GU

        // GPU 只吃 CameraLocal：cameraLocal = world - cameraWorldPosGU
        const visibleCentersCameraLocal: { x: number; y: number }[] = []
        for (const s of worldSectorCenters) {
            const aabb = {
                minX: s.x - radius,
                maxX: s.x + radius,
                minY: s.y - radius,
                maxY: s.y + radius,
            }
            if (worldAabbIntersects(aabb, viewAabb)) {
                visibleCentersCameraLocal.push({
                    x: s.x - cameraWorldPosGU.x,
                    y: s.y - cameraWorldPosGU.y,
                })
            }
        }

        if (hexOutlines) {
            worldGroup.remove(hexOutlines)
            hexOutlines.geometry.dispose()
            if (Array.isArray(hexOutlines.material)) {
                hexOutlines.material.forEach((m) => m.dispose())
            } else {
                hexOutlines.material.dispose()
            }
            hexOutlines = undefined
        }

        const segPositions = buildHexSegmentPositions(visibleCentersCameraLocal)
        const geometry = new THREE.BufferGeometry()
        geometry.setAttribute('position', new THREE.BufferAttribute(segPositions, 3))

        const material = new THREE.LineBasicMaterial({
            color: 0x7fd3ff,
            transparent: true,
            opacity: 0.55,
        })

        hexOutlines = new THREE.LineSegments(geometry, material)
        worldGroup.add(hexOutlines)

        // --- Star Systems ---
        // Naive rebuild: clear and add all visible stars. For performance, use object pooling and instancing.
        while (starSystemsGroup.children.length > 0) {
            const obj = starSystemsGroup.children[0] as any
            starSystemsGroup.remove(obj)
            obj.geometry?.dispose?.()
            if (Array.isArray(obj.material)) {
                obj.material.forEach((m: any) => m.dispose?.())
            } else {
                obj.material?.dispose?.()
            }
        }

        const GAME_UNIT_IN_METERS = 100_000
        const AU_TO_GU = 149597870700 / GAME_UNIT_IN_METERS
        const GAME_HOUR_PER_TICK = 0.25 // From SimulationClock.java

        const starSystemAabb = { minX: 0, maxX: 0, minY: 0, maxY: 0 }

        for (const system of worldStarSystems) {
            if (!system.centerWorldGU) continue

            const center = system.centerWorldGU

            // Calculate a bounding radius for the system for culling
            let maxOrbitAU = 0
            for (const planet of system.planets) {
                if (planet.orbit && planet.orbit.semiMajorAxisAU > maxOrbitAU) {
                    maxOrbitAU = planet.orbit.semiMajorAxisAU
                }
            }
            let maxStarRadiusKm = 0
            for (const star of system.stars) {
                if (star.radiusKm > maxStarRadiusKm) {
                    maxStarRadiusKm = star.radiusKm
                }
            }
            const maxStarRadiusGU = (maxStarRadiusKm * 1000) / GAME_UNIT_IN_METERS
            const systemRadiusGU = maxOrbitAU * AU_TO_GU + maxStarRadiusGU

            starSystemAabb.minX = center.x - systemRadiusGU
            starSystemAabb.maxX = center.x + systemRadiusGU
            starSystemAabb.minY = center.y - systemRadiusGU
            starSystemAabb.maxY = center.y + systemRadiusGU

            if (worldAabbIntersects(starSystemAabb, viewAabb)) {
                const farZoom = zoom.value > STAR_SPRITE_DISABLE_ZOOM_THRESHOLD

                for (const star of system.stars) {
                    if (farZoom) {
                        // 远距离：禁用纹理精灵，改为轻量点（Circle）
                        const radiusGU = (star.radiusKm * 1000) / GAME_UNIT_IN_METERS
                        const size = Math.max(zoom.value * 2, radiusGU * 2)

                        const geo = new THREE.CircleGeometry(size / 2, 12)
                        const mat = new THREE.MeshBasicMaterial({ color: 0xffffff, transparent: true, opacity: 0.9, depthWrite: false })
                        const dot = new THREE.Mesh(geo, mat)
                        dot.position.set(center.x - cameraWorldPosGU.x, center.y - cameraWorldPosGU.y, 0)
                        starSystemsGroup.add(dot)
                        continue
                    }

                    const spritePath = getSpritePath(star.typeId)
                    if (spritePath) {
                        const texture = getTexture(spritePath)
                        const material = new THREE.SpriteMaterial({ map: texture, sizeAttenuation: false, depthWrite: false })
                        const sprite = new THREE.Sprite(material)
                        const radiusGU = (star.radiusKm * 1000) / GAME_UNIT_IN_METERS
                        const size = radiusGU * 2
                        sprite.scale.set(size, size, 1)
                        sprite.position.set(center.x - cameraWorldPosGU.x, center.y - cameraWorldPosGU.y, 0)
                        starSystemsGroup.add(sprite)
                    }
                }

                const showPlanets = zoom.value <= PLANET_SPRITE_DISABLE_ZOOM_THRESHOLD
                const showOrbits = zoom.value <= ORBIT_DISABLE_ZOOM_THRESHOLD

                for (const planet of system.planets) {
                    if (!planet.orbit) continue

                    const orbit = planet.orbit
                    const semiMajorAxisGU = orbit.semiMajorAxisAU * AU_TO_GU

                    if (showOrbits) {
                        // Draw orbit
                        const curve = new THREE.EllipseCurve(
                            center.x - cameraWorldPosGU.x,
                            center.y - cameraWorldPosGU.y,
                            semiMajorAxisGU,
                            semiMajorAxisGU * Math.sqrt(1 - orbit.eccentricity ** 2),
                            0,
                            2 * Math.PI,
                            false,
                            0
                        )
                        const points = curve.getPoints(128)
                        const geometry = new THREE.BufferGeometry().setFromPoints(points)
                        const material = new THREE.LineBasicMaterial({ color: 0xaaaaaa, transparent: true, opacity: 0.5 })
                        const ellipse = new THREE.Line(geometry, material)
                        starSystemsGroup.add(ellipse)
                    }

                    if (!showPlanets) {
                        continue
                    }

                    // Draw planet
                    const simulationTick = snapshot?.realTimeWorldState?.simulationTick ?? 0
                    const totalHours = simulationTick * GAME_HOUR_PER_TICK
                    const totalDays = totalHours / 24
                    const meanAnomaly = (orbit.meanAnomalyDegAtEpoch * Math.PI) / 180
                    const angle = meanAnomaly + (totalDays / orbit.orbitalPeriodDays) * 2 * Math.PI

                    const planetX = center.x + semiMajorAxisGU * Math.cos(angle)
                    const planetY = center.y + semiMajorAxisGU * Math.sin(angle)

                    const spritePath = getSpritePath(planet.typeId)
                    if (spritePath) {
                        const texture = getTexture(spritePath)
                        const spriteMaterial = new THREE.SpriteMaterial({ map: texture, sizeAttenuation: false, depthWrite: false })
                        const sprite = new THREE.Sprite(spriteMaterial)
                        const radiusGU = (planet.radiusKm * 1000) / GAME_UNIT_IN_METERS
                        const size = radiusGU * 2
                        sprite.scale.set(size, size, 1)
                        sprite.position.set(planetX - cameraWorldPosGU.x, planetY - cameraWorldPosGU.y, 0)
                        starSystemsGroup.add(sprite)
                    }
                }
            }
        }
    }

    // input: drag pan
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

        // worldDeltaGU = screenDeltaPx * zoom
        cameraWorldPosGU.set(dragStartCamera.x - dxPx * zoom.value, dragStartCamera.y + dyPx * zoom.value)

        // 这里不再平移 worldGroup、不再引入 renderOrigin：保证逻辑裁剪与 GPU 坐标一致
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

    const updateFromSnapshot = (snapshot: SnapshotMessage) => {
        if (!snapshot.ok || !snapshot.realTimeWorldState?.sectorCenters) return

        worldSectorCenters = snapshot.realTimeWorldState.sectorCenters
        worldStarSystems = snapshot.realTimeWorldState.starSystems ?? []

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
            hexOutlines.geometry.dispose()
            if (Array.isArray(hexOutlines.material)) {
                hexOutlines.material.forEach((m) => m.dispose())
            } else {
                hexOutlines.material.dispose()
            }
            hexOutlines = undefined
        }

        dynamicGrid.dispose()

        if (axes) {
            scene.remove(axes)
                ; (axes.geometry as any)?.dispose?.()
                ; (axes.material as any)?.dispose?.()
            axes = null
        }

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
        updateFromSnapshot,
        dispose,
    }
}

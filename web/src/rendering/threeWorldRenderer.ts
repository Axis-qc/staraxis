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

    let debugVisible = true

    container.appendChild(renderer.domElement)

    // 权威相机世界坐标（GU，float64 语义）
    const cameraWorldPosGU = new THREE.Vector2(0, 0)

    // world 数据缓存（权威坐标，仅用于逻辑判断，不进 GPU）
    let worldSectorCenters: { q: number; r: number; x: number; y: number }[] = []

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
            rebuildVisibleHexOutlines()
        }
    }

    const setZoom = (z: number) => {
        zoom.value = Math.max(minZoom, Math.min(maxZoom, z))
        applyCameraTransform()
    }

    const rebuildVisibleHexOutlines = () => {
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
        if (e.button !== 0) return

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

        if (axes) {
            const axisSize = Math.max(2_000, SECTOR_SIZE_GU * 0.25)
            axes.scale.set(axisSize / 2_000, axisSize / 2_000, axisSize / 2_000)
        }

        rebuildVisibleHexOutlines()
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

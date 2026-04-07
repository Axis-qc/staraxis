/**
 * @file cameraSystem.ts
 *
 * @description
 * 相机系统 - 管理 Three.js 相机、渲染器和投影范围。
 *
 * 作用：
 * - 创建和配置 Three.js WebGLRenderer、Scene、OrthographicCamera。
 * - 管理相机投影范围更新（响应容器尺寸变化）。
 * - 应用相机变换（缩放、位置）。
 * - 提供渲染上下文。
 *
 * @usage
 * - 在 WorldRenderManager 中创建 CameraSystem 实例。
 * - 调用 updateFrustum() 更新相机投影范围。
 * - 调用 applyTransform(zoom, position) 更新相机状态。
 */
import * as THREE from 'three'

export type CameraSystemOptions = {
    minZoom?: number
    maxZoom?: number
    pixelRatio?: number
}

export type CameraSystem = {
    renderer: THREE.WebGLRenderer
    scene: THREE.Scene
    camera: THREE.OrthographicCamera
    worldGroup: THREE.Group
    entitiesGroup: THREE.Group
    canvas: HTMLCanvasElement
    updateFrustum: () => void
    applyTransform: (zoom: number, cameraWorldPosGU: THREE.Vector2) => void
    dispose: () => void
}

export function createCameraSystem(
    container: HTMLDivElement,
    options: CameraSystemOptions = {}
): CameraSystem {
    const pixelRatio = Math.min(options.pixelRatio ?? window.devicePixelRatio ?? 1, 2)

    // 创建渲染器
    const renderer = new THREE.WebGLRenderer({ antialias: true, alpha: true })
    renderer.setPixelRatio(pixelRatio)
    renderer.setClearColor(0x000000, 0)

    // 创建场景
    const scene = new THREE.Scene()

    // 初始化相机
    const width = container.clientWidth
    const height = container.clientHeight
    const halfW = width / 2
    const halfH = height / 2
    const camera = new THREE.OrthographicCamera(-halfW, halfW, halfH, -halfH, -1_000_000, 1_000_000)
    camera.position.set(0, 0, 10)
    camera.lookAt(0, 0, 0)

    // 创建场景层级
    const worldGroup = new THREE.Group()
    worldGroup.frustumCulled = false
    scene.add(worldGroup)

    const entitiesGroup = new THREE.Group()
    entitiesGroup.frustumCulled = false
    worldGroup.add(entitiesGroup)

    // 添加 canvas 到容器
    container.appendChild(renderer.domElement)

    const canvas = renderer.domElement

    const updateFrustum = () => {
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

    const applyTransform = (zoom: number) => {
        camera.position.set(0, 0, 10)
        camera.lookAt(0, 0, 0)
        camera.zoom = 1 / zoom
        camera.updateProjectionMatrix()
    }

    const dispose = () => {
        renderer.dispose()
        const gl = renderer.getContext()
        gl.getExtension('WEBGL_lose_context')?.loseContext()

        if (renderer.domElement.parentElement === container) {
            container.removeChild(renderer.domElement)
        }
    }

    // 初始更新
    updateFrustum()

    return {
        renderer,
        scene,
        camera,
        worldGroup,
        entitiesGroup,
        canvas,
        updateFrustum,
        applyTransform,
        dispose,
    }
}

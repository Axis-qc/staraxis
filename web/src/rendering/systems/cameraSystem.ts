/**
 * @file cameraSystem.ts
 *
 * @description
 * 相机系统 - 管理 Three.js 相机、渲染器和投影范围。
 *
 * 作用：
 * - 创建和配置 Three.js WebGLRenderer、Scene、PerspectiveCamera（透视相机）喵。
 * - 管理相机投影范围更新（响应容器尺寸变化）喵。
 * - 应用俯视相机变换（高度、位置）喵。
 * - 提供渲染上下文。
 *
 * @usage
 * - 在 WorldRenderManager 中创建 CameraSystem 实例。
 * - 调用 updateFrustum() 更新相机投影范围。
 * - 调用 applyTransform(zoom, position) 更新相机状态。
 */
import * as THREE from 'three'

export type CameraSystemOptions = {
    pixelRatio?: number
    fovDeg?: number
    near?: number
    far?: number
}

export type CameraSystem = {
    renderer: THREE.WebGLRenderer
    scene: THREE.Scene
    camera: THREE.PerspectiveCamera
    worldGroup: THREE.Group
    entitiesGroup: THREE.Group
    canvas: HTMLCanvasElement
    updateFrustum: () => void
    applyTransform: (cameraHeight: number, cameraWorldPosGU: THREE.Vector2) => void
    getViewSizeAtHeight: (cameraHeight: number) => { widthGU: number; heightGU: number }
    getWorldUnitsPerPixelAtHeight: (cameraHeight: number) => number
    worldUnitsPerPixelToHeight: (worldUnitsPerPixel: number) => number
    dispose: () => void
}

export function createCameraSystem(
    container: HTMLDivElement,
    options: CameraSystemOptions = {}
): CameraSystem {
    const pixelRatio = Math.min(options.pixelRatio ?? window.devicePixelRatio ?? 1, 2)
    const fovDeg = options.fovDeg ?? 50
    const near = options.near ?? 0.1
    const far = options.far ?? 10_000_000

    // 创建渲染器
    const renderer = new THREE.WebGLRenderer({ antialias: true, alpha: true })
    renderer.setPixelRatio(pixelRatio)
    renderer.setClearColor(0x000000, 0)

    // 创建场景
    const scene = new THREE.Scene()

    // 初始化相机
    const width = container.clientWidth
    const height = container.clientHeight
    const aspect = width > 0 && height > 0 ? width / height : 1
    const camera = new THREE.PerspectiveCamera(fovDeg, aspect, near, far)
    camera.position.set(0, 0, 1000)
    camera.lookAt(0, 0, 0)
    camera.up.set(0, 1, 0)

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
        camera.aspect = w > 0 && h > 0 ? w / h : 1
        camera.updateProjectionMatrix()
    }

    const getViewSizeAtHeight = (cameraHeight: number) => {
        const heightGU = 2 * cameraHeight * Math.tan(THREE.MathUtils.degToRad(fovDeg) / 2)
        const widthGU = heightGU * camera.aspect
        return { widthGU, heightGU }
    }

    const getWorldUnitsPerPixelAtHeight = (cameraHeight: number) => {
        const viewportHeightPx = Math.max(container.clientHeight, 1)
        const { heightGU } = getViewSizeAtHeight(cameraHeight)
        return heightGU / viewportHeightPx
    }

    const worldUnitsPerPixelToHeight = (worldUnitsPerPixel: number) => {
        const viewportHeightPx = Math.max(container.clientHeight, 1)
        const targetHeightGU = worldUnitsPerPixel * viewportHeightPx
        return targetHeightGU / (2 * Math.tan(THREE.MathUtils.degToRad(fovDeg) / 2))
    }

    const applyTransform = (cameraHeight: number, _cameraWorldPosGU: THREE.Vector2) => {
        // 相机固定在渲染原点 (0, 0, cameraHeight)喵。
        // 所有世界物体通过 toRenderPos() 转换为相机相对坐标后再写入 mesh.position 喵。
        // worldGroup 不做偏移，保持 (0,0,0) 喵。
        camera.position.set(0, 0, cameraHeight)
        camera.lookAt(0, 0, 0)
        camera.updateMatrixWorld()
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
        getViewSizeAtHeight,
        getWorldUnitsPerPixelAtHeight,
        worldUnitsPerPixelToHeight,
        dispose,
    }
}

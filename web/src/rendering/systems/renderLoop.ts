/**
 * @file renderLoop.ts
 *
 * @description
 * 渲染循环管理器 - 管理 RAF 渲染循环和持续输入处理。
 *
 * 作用：
 * - 管理 requestAnimationFrame 渲染循环。
 * - 处理键盘持续按键（WASD/方向键平移）。
 * - 协调渲染层更新和场景渲染。
 *
 * @usage
 * - 调用 start() 启动渲染循环。
 * - 调用 stop() 停止渲染循环。
 * - 在循环回调中执行分层更新和场景渲染。
 */
import * as THREE from 'three'
import type { WorldRenderContext } from '../worldRenderManager'
import type { FrameState } from './frameStateBuilder'
import type { InputSystem } from '../../input/inputSystem'
import type { LayerManager } from '../layers'
import { beginRenderFrame } from '@/game/world'

export type RenderLoopOptions = {
    keyboardPanSpeed?: number
    layerManager?: LayerManager
    /** 每帧 layerManager.updateAll 之后调用的子系统回调喵 */
    onFrameUpdate?: (ctx: WorldRenderContext, frame: FrameState) => void
}

export type RenderLoop = {
    start: () => void
    stop: () => void
    isRunning: () => boolean
}

export function createRenderLoop(
    renderer: THREE.WebGLRenderer,
    scene: THREE.Scene,
    camera: THREE.PerspectiveCamera,
    ctx: WorldRenderContext,
    buildFrameState: () => FrameState,
    inputSystem: InputSystem,
    cameraWorldPosGU: THREE.Vector2,
    zoom: { value: number },
    applyCameraTransform: () => void,
    options: RenderLoopOptions = {}
): RenderLoop {
    const KEYBOARD_PAN_SPEED = options.keyboardPanSpeed ?? 20
    const layerManager = options.layerManager
    let rafId = 0
    let isRunning = false

    const tick = (timestampMs: number) => {
        if (!isRunning) return

        rafId = requestAnimationFrame(tick)
        beginRenderFrame(timestampMs)

        // 处理键盘持续平移（WASD/方向键）
        const inputState = inputSystem.getState()
        let panX = 0
        let panY = 0

        if (inputState.pressedKeys.has('KeyW') || inputState.pressedKeys.has('ArrowUp')) {
            panY += KEYBOARD_PAN_SPEED
        }
        if (inputState.pressedKeys.has('KeyS') || inputState.pressedKeys.has('ArrowDown')) {
            panY -= KEYBOARD_PAN_SPEED
        }
        if (inputState.pressedKeys.has('KeyA') || inputState.pressedKeys.has('ArrowLeft')) {
            panX -= KEYBOARD_PAN_SPEED
        }
        if (inputState.pressedKeys.has('KeyD') || inputState.pressedKeys.has('ArrowRight')) {
            panX += KEYBOARD_PAN_SPEED
        }

        // 如果有键盘平移输入，更新相机位置
        if (panX !== 0 || panY !== 0) {
            cameraWorldPosGU.x += panX * zoom.value
            cameraWorldPosGU.y += panY * zoom.value
            applyCameraTransform()
        }

        // 构建帧状态并更新渲染层
        const frame = buildFrameState()

        if (layerManager) {
            layerManager.updateAll(ctx, frame)
        }

        // 更新遗留子系统（网格、六边形轮廓等）喵
        if (options.onFrameUpdate) {
            options.onFrameUpdate(ctx, frame)
        }

        // 渲染场景
        renderer.render(scene, camera)
    }

    const start = () => {
        if (isRunning) return
        isRunning = true
        tick()
    }

    const stop = () => {
        isRunning = false
        if (rafId) {
            cancelAnimationFrame(rafId)
            rafId = 0
        }
    }

    const isRunningFn = () => isRunning

    return {
        start,
        stop,
        isRunning: isRunningFn,
    }
}

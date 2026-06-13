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

/** 渲染性能统计全局开关，按 F9 切换喵 */
let _perfEnabled = false
export const isPerfEnabled = () => _perfEnabled

export type RenderLoopOptions = {
    keyboardPanSpeed?: number
    layerManager?: LayerManager
    /** 每帧 layerManager.updateAll 之后调用的子系统回调喵 */
    onFrameUpdate?: (ctx: WorldRenderContext, frame: FrameState) => void
    /** 相机动画是否正在进行中（WASD 平移应跳过）喵 */
    isCameraAnimating?: () => boolean
    /** 取消相机动画（WASD 按下时调用）喵 */
    cancelCameraAnimation?: () => void
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

    // ── 渲染性能统计 ──喵
    let perfFrameCount = 0
    let perfWindowStart = 0
    const perfAccum = {
        snapshot: 0,
        input: 0,
        buildFrame: 0,
        layers: 0,
        subsystems: 0,
        webgl: 0,
        total: 0,
    }
    const perfKeys = Object.keys(perfAccum) as (keyof typeof perfAccum)[]
    /** 各渲染层逐层耗时累积喵 */
    const layerAccum = new Map<string, number>()

    /** 按 F9 切换控制台性能统计喵 */
    const togglePerf = (e: KeyboardEvent) => {
        if (e.key === 'F9') {
            _perfEnabled = !_perfEnabled
            perfFrameCount = 0
            perfWindowStart = 0
            for (const k of perfKeys) perfAccum[k] = 0
            layerAccum.clear()
            console.log(`[RenderPerf] ${_perfEnabled ? '已开启 — 每秒在控制台打印各阶段耗时' : '已关闭'}喵`)
        }
    }
    globalThis.addEventListener('keydown', togglePerf)

    const tick = (timestampMs: number) => {
        if (!isRunning) return

        rafId = requestAnimationFrame(tick)

        // 非统计模式走快速路径，零额外开销喵
        if (!_perfEnabled) {
            beginRenderFrame(timestampMs)
            if (!options.isCameraAnimating?.()) {
                const inputState = inputSystem.getState()
                let panX = 0
                let panY = 0
                if (inputState.pressedKeys.has('KeyW') || inputState.pressedKeys.has('ArrowUp')) panY += KEYBOARD_PAN_SPEED
                if (inputState.pressedKeys.has('KeyS') || inputState.pressedKeys.has('ArrowDown')) panY -= KEYBOARD_PAN_SPEED
                if (inputState.pressedKeys.has('KeyA') || inputState.pressedKeys.has('ArrowLeft')) panX -= KEYBOARD_PAN_SPEED
                if (inputState.pressedKeys.has('KeyD') || inputState.pressedKeys.has('ArrowRight')) panX += KEYBOARD_PAN_SPEED
                if (panX !== 0 || panY !== 0) {
                    options.cancelCameraAnimation?.()
                    cameraWorldPosGU.x += panX * zoom.value
                    cameraWorldPosGU.y += panY * zoom.value
                    applyCameraTransform()
                }
            }
            const frame = buildFrameState()
            if (layerManager) layerManager.updateAll(ctx, frame)
            if (options.onFrameUpdate) options.onFrameUpdate(ctx, frame)
            renderer.render(scene, camera)
            return
        }

        // ── 统计模式：逐段计时 ──喵
        let t0: number
        const frameStart = performance.now()

        // 快照插值喵
        t0 = performance.now()
        beginRenderFrame(timestampMs)
        perfAccum.snapshot += performance.now() - t0

        // 键盘输入喵
        t0 = performance.now()
        if (!options.isCameraAnimating?.()) {
            const inputState = inputSystem.getState()
            let panX = 0
            let panY = 0
            if (inputState.pressedKeys.has('KeyW') || inputState.pressedKeys.has('ArrowUp')) panY += KEYBOARD_PAN_SPEED
            if (inputState.pressedKeys.has('KeyS') || inputState.pressedKeys.has('ArrowDown')) panY -= KEYBOARD_PAN_SPEED
            if (inputState.pressedKeys.has('KeyA') || inputState.pressedKeys.has('ArrowLeft')) panX -= KEYBOARD_PAN_SPEED
            if (inputState.pressedKeys.has('KeyD') || inputState.pressedKeys.has('ArrowRight')) panX += KEYBOARD_PAN_SPEED
            if (panX !== 0 || panY !== 0) {
                options.cancelCameraAnimation?.()
                cameraWorldPosGU.x += panX * zoom.value
                cameraWorldPosGU.y += panY * zoom.value
                applyCameraTransform()
            }
        }
        perfAccum.input += performance.now() - t0

        // 构建帧状态喵
        t0 = performance.now()
        const frame = buildFrameState()
        perfAccum.buildFrame += performance.now() - t0

        // 渲染层更新喵
        t0 = performance.now()
        if (layerManager) layerManager.updateAll(ctx, frame)
        perfAccum.layers += performance.now() - t0

        // 累积各层耗时喵
        if (layerManager) {
            for (const [name, ms] of layerManager.lastLayerTimings) {
                layerAccum.set(name, (layerAccum.get(name) ?? 0) + ms)
            }
        }

        // 遗留子系统喵
        t0 = performance.now()
        if (options.onFrameUpdate) options.onFrameUpdate(ctx, frame)
        perfAccum.subsystems += performance.now() - t0

        // Three.js 渲染喵
        t0 = performance.now()
        renderer.render(scene, camera)
        perfAccum.webgl += performance.now() - t0

        perfAccum.total += performance.now() - frameStart
        perfFrameCount++

        // 每秒输出一次汇总喵
        if (perfWindowStart === 0) perfWindowStart = performance.now()
        const elapsed = performance.now() - perfWindowStart
        if (elapsed >= 1000) {
            const fps = Math.round((perfFrameCount * 1000) / elapsed)
            const rows: Record<string, string> = {}
            for (const k of perfKeys) {
                const avgMs = perfAccum[k] / perfFrameCount
                const pct = ((perfAccum[k] / perfAccum.total) * 100).toFixed(1)
                rows[k === 'total' ? '🔴 total' : k] = `${avgMs.toFixed(2)}ms  (${pct}%)`
            }
            // 逐层耗时明细（缩进显示在 layers 下方）喵
            for (const [name, ms] of layerAccum) {
                const avgMs = ms / perfFrameCount
                const pct = ((ms / perfAccum.layers) * 100).toFixed(1)
                rows[`  └ ${name}`] = `${avgMs.toFixed(2)}ms  (${pct}%)`
            }
            console.log(`[RenderPerf] FPS: ${fps}  帧数: ${perfFrameCount}`)
            console.table(rows)

            // 重置统计窗口喵
            perfFrameCount = 0
            perfWindowStart = performance.now()
            for (const k of perfKeys) perfAccum[k] = 0
            layerAccum.clear()
        }
    }

    const start = () => {
        if (isRunning) return
        isRunning = true
        tick(performance.now())
    }

    const stop = () => {
        isRunning = false
        if (rafId) {
            cancelAnimationFrame(rafId)
            rafId = 0
        }
        globalThis.removeEventListener('keydown', togglePerf)
    }

    const isRunningFn = () => isRunning

    return {
        start,
        stop,
        isRunning: isRunningFn,
    }
}

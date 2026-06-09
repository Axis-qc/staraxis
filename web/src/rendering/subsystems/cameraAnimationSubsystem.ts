/**
 * @file cameraAnimationSubsystem.ts
 * @description 相机动画子系统 - 管理相机平滑飞行（聚焦/缩放）喵。
 *
 * @设计说明
 * - 使用指数衰减插值（lerp），起始快结束慢，手感自然喵。
 * - 位置和缩放独立插值，可单独或同时动画喵。
 * - 每帧由 renderLoop 调用 update()，驱动相机状态更新喵。
 * - 新的飞行请求会覆盖当前动画（不会排队）喵。
 */

import * as THREE from 'three'

/** 动画配置 */
const POSITION_LERP_SPEED = 8    // 位置插值速度（越大越快）喵
const ZOOM_LERP_SPEED = 8        // 缩放插值速度喵
const STOP_THRESHOLD_POS = 0.01  // 位置停止阈值（世界单位）喵
const STOP_THRESHOLD_ZOOM = 0.001 // 缩放停止阈值喵

export type CameraAnimationSubsystem = {
    /**
     * 请求相机飞到指定世界坐标喵。
     * 会打断当前飞行动画喵。
     */
    flyToPosition: (worldPos: { x: number; y: number }) => void

    /**
     * 请求相机飞行到指定世界坐标并缩放到目标级别喵。
     * 会打断当前飞行动画喵。
     */
    flyTo: (worldPos: { x: number; y: number }, targetZoom: number) => void

    /**
     * 请求相机缩放到目标级别（保持当前位置）喵。
     */
    flyToZoom: (targetZoom: number) => void

    /** 当前是否有动画在进行喵 */
    isAnimating: () => boolean

    /**
     * 每帧调用，更新相机状态喵。
     * @param dtSec 距上一帧的时间（秒）
     * @param cameraWorldPosGU 相机世界位置（会被直接修改）喵
     * @param zoom 当前缩放值（会被直接修改）喵
     * @param cameraHeight 当前相机高度（需同步更新，否则 applyCameraTransform 会覆盖 zoom）喵
     * @param worldUnitsPerPixelToHeight zoom → cameraHeight 转换函数喵
     * @param applyCameraTransform 应用相机变换的回调喵
     */
    update: (
        dtSec: number,
        cameraWorldPosGU: THREE.Vector2,
        zoom: { value: number },
        cameraHeight: { value: number },
        worldUnitsPerPixelToHeight: (zoom: number) => number,
        applyCameraTransform: () => void,
    ) => void

    /** 取消当前动画喵 */
    cancel: () => void

    dispose: () => void
}

export function createCameraAnimationSubsystem(): CameraAnimationSubsystem {
    // 动画目标状态（null 表示不需要动画）喵
    let targetPos: { x: number; y: number } | null = null
    let targetZoom: number | null = null

    const isAnimating = () => targetPos !== null || targetZoom !== null

    const flyToPosition = (worldPos: { x: number; y: number }) => {
        targetPos = { x: worldPos.x, y: worldPos.y }
        // 缩放保持不变喵
    }

    const flyTo = (worldPos: { x: number; y: number }, zoom: number) => {
        targetPos = { x: worldPos.x, y: worldPos.y }
        targetZoom = zoom
    }

    const flyToZoom = (zoom: number) => {
        targetZoom = zoom
        // 位置保持不变喵
    }

    const cancel = () => {
        targetPos = null
        targetZoom = null
    }

    const update = (
        dtSec: number,
        cameraWorldPosGU: THREE.Vector2,
        zoom: { value: number },
        cameraHeight: { value: number },
        worldUnitsPerPixelToHeight: (zoom: number) => number,
        applyCameraTransform: () => void,
    ) => {
        if (!isAnimating()) return

        let changed = false

        // 位置插值喵
        if (targetPos) {
            const speed = 1 - Math.exp(-POSITION_LERP_SPEED * dtSec)
            const dx = targetPos.x - cameraWorldPosGU.x
            const dy = targetPos.y - cameraWorldPosGU.y

            if (Math.abs(dx) < STOP_THRESHOLD_POS && Math.abs(dy) < STOP_THRESHOLD_POS) {
                cameraWorldPosGU.set(targetPos.x, targetPos.y)
                targetPos = null
                changed = true
            } else {
                cameraWorldPosGU.x += dx * speed
                cameraWorldPosGU.y += dy * speed
                changed = true
            }
        }

        // 缩放插值（同步更新 cameraHeight，防止 applyCameraTransform 内 updateDerivedZoom 覆盖）喵
        if (targetZoom !== null) {
            const speed = 1 - Math.exp(-ZOOM_LERP_SPEED * dtSec)
            const dz = targetZoom - zoom.value

            if (Math.abs(dz) < STOP_THRESHOLD_ZOOM) {
                zoom.value = targetZoom
                cameraHeight.value = worldUnitsPerPixelToHeight(targetZoom)
                targetZoom = null
                changed = true
            } else {
                zoom.value += dz * speed
                cameraHeight.value = worldUnitsPerPixelToHeight(zoom.value)
                changed = true
            }
        }

        if (changed) {
            applyCameraTransform()
        }
    }

    const dispose = () => {
        targetPos = null
        targetZoom = null
    }

    return {
        flyToPosition,
        flyTo,
        flyToZoom,
        isAnimating,
        update,
        cancel,
        dispose,
    }
}

/**
 * @file gridRenderer.ts
 *
 * @description
 * 动态网格渲染器。
 *
 * 作用：
 * - 根据相机缩放与视口尺寸动态生成背景网格。
 * - 网格密度随 zoom 自适应，保证视觉可读性。
 *
 * @usage
 * - 在 WorldRenderManager 中注册：subsystems.push(new GridRenderer())
 * - 在 update 中调用，基于 ctx.camera 与 ctx.zoom 更新网格 geometry。
 *
 * @provides
 * - **动态背景网格**：LineSegments 形式渲染。
 *
 * @api
 * - init(ctx): void
 * - update(ctx, frame): void
 * - dispose(ctx): void
 *
 * @important_notes
 * - 网格使用相机局部坐标（不需要随 cameraWorldPosGU 平移），与旧实现保持一致。
 */
import * as THREE from 'three'
import type { WorldRenderContext, WorldFrameState } from '../worldRenderManager'
import type { WorldRenderSubsystem } from './worldRenderSubsystem'

export class GridRenderer implements WorldRenderSubsystem {
    private grid: THREE.LineSegments | null = null
    private geometry: THREE.BufferGeometry | null = null
    private material: THREE.LineBasicMaterial | null = null

    init(ctx: WorldRenderContext): void {
        this.material = new THREE.LineBasicMaterial({
            color: 0x2f3a4a,
            transparent: true,
            opacity: 0.5,
        })
        this.geometry = new THREE.BufferGeometry()
        this.grid = new THREE.LineSegments(this.geometry, this.material)
        this.grid.frustumCulled = false
        // 设置网格的 z 位置在背景，位于实体（恒星/行星）下方
        this.grid.position.z = -1
        this.grid.renderOrder = -1

        // 添加到 worldGroup 而不是 scene，确保在实体后面渲染
        ctx.worldGroup.add(this.grid)
    }

    update(ctx: WorldRenderContext, frame: WorldFrameState): void {
        void frame

        if (!this.geometry) return

        const camera = ctx.camera
        const zoom = ctx.zoom.value

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

        this.geometry.setAttribute('position', new THREE.Float32BufferAttribute(vertices, 3))
    }

    dispose(ctx: WorldRenderContext): void {
        if (this.grid) {
            ctx.worldGroup.remove(this.grid)
            this.grid = null
        }
        if (this.geometry) {
            this.geometry.dispose()
            this.geometry = null
        }
        if (this.material) {
            this.material.dispose()
            this.material = null
        }
    }
}

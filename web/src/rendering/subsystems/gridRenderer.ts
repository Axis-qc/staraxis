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
 * - 网格现在直接使用世界坐标，并围绕 cameraWorldPosGU（相机世界坐标）生成当前视口范围喵。
 * - 当前文件保留是因为网格层尚未迁移到 layer 架构。
 */
import * as THREE from 'three'
import type { WorldRenderContext, WorldFrameState } from '../worldRenderManager'
import type { WorldRenderSubsystem } from './worldRenderSubsystem'

export class GridRenderer implements WorldRenderSubsystem {
    private grid: THREE.LineSegments | null = null
    private geometry: THREE.BufferGeometry | null = null
    private material: THREE.LineBasicMaterial | null = null
    private visible: boolean = true

    init(ctx: WorldRenderContext): void {
        this.material = new THREE.LineBasicMaterial({
            color: 0x2f3a4a,
            transparent: true,
            opacity: 0.5,
        })
        this.geometry = new THREE.BufferGeometry()
        this.grid = new THREE.LineSegments(this.geometry, this.material)
        this.grid.frustumCulled = false
        this.grid.position.z = -1
        this.grid.renderOrder = -1
        ctx.worldGroup.add(this.grid)
    }

    setVisible(visible: boolean): void {
        this.visible = visible
        if (this.grid) {
            this.grid.visible = visible
        }
    }

    update(ctx: WorldRenderContext, frame: WorldFrameState): void {
        void frame

        if (!this.geometry || !this.grid) return

        this.grid.visible = this.visible

        const zoom = ctx.zoom.value
        const { widthGU: viewWidthGU, heightGU: viewHeightGU } = ctx.getViewSizeGU()

        const viewMinX = ctx.cameraWorldPosGU.x - viewWidthGU / 2
        const viewMaxX = ctx.cameraWorldPosGU.x + viewWidthGU / 2
        const viewMinY = ctx.cameraWorldPosGU.y - viewHeightGU / 2
        const viewMaxY = ctx.cameraWorldPosGU.y + viewHeightGU / 2

        const minStepGU = 10 * zoom
        const powerOf10 = 10 ** Math.floor(Math.log10(minStepGU))
        let stepGU = powerOf10
        if (stepGU / zoom < 10) stepGU *= 2
        if (stepGU / zoom < 10) stepGU *= 2.5
        if (stepGU / zoom < 10) stepGU *= 2

        if (this.material) {
            this.material.opacity = 0.5
        }

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

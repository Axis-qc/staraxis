/**
 * @file screenSpaceLayer.ts
 *
 * @description
 * 屏幕空间渲染层抽象基类，将group添加为相机子对象实现屏幕固定喵。
 *
 * 核心设计：
 * 1. 继承自BaseLayer，实现RenderLayer接口
 * 2. 将group添加为相机子对象，自动保持屏幕位置
 * 3. 提供屏幕空间坐标转换工具方法
 *
 * 使用方式：
 * - 具体屏幕空间层继承此类
 * - 在init()中调用super.init(ctx)将group添加到相机
 * - 在update()中调用super.update(ctx, frame)保持屏幕位置
 * - 在dispose()中调用super.dispose(ctx)从相机移除group喵。
 */

import * as THREE from 'three'
import type { WorldRenderContext, WorldFrameState } from '../worldRenderManager'
import { BaseLayer } from './baseLayer'
import type { RenderOrder } from './index'

export abstract class ScreenSpaceLayer extends BaseLayer {
    protected depth: number = -100 // 屏幕空间层在相机后面的深度

    constructor(name: string, renderOrder: RenderOrder) {
        super(name, renderOrder)
    }

    async init(ctx: WorldRenderContext): Promise<void> {
        // 确保相机在场景中，否则其子对象不会被渲染喵。
        if (!ctx.camera.parent) {
            console.log(`${this.name}Layer: Adding camera to scene for screen-space rendering`)
            ctx.scene.add(ctx.camera)
        }

        // 将group添加到相机作为子对象，实现屏幕空间固定喵。
        ctx.camera.add(this.group)
        this.group.position.set(0, 0, this.depth)
        this.group.visible = true // 确保组可见喵。

        // 初始化缩放补偿，确保屏幕空间层不受相机缩放影响喵。
        const initialZoom = ctx.camera.zoom || 1.0
        const inverseZoom = 1.0 / initialZoom
        this.group.scale.set(inverseZoom, inverseZoom, inverseZoom)

        // 验证group已添加到相机喵。
        const isChildOfCamera = this.group.parent === ctx.camera
        const isInScene = this.group.parent?.parent === ctx.scene

        console.log(`[${this.name}Layer] 屏幕空间层已初始化`, {
            相机类型: ctx.camera.type,
            相机缩放: ctx.camera.zoom,
            组缩放: this.group.scale.toArray(),
            组深度: this.depth,
            组可见: this.group.visible,
            渲染顺序: this.group.renderOrder
        })
    }

    update(ctx: WorldRenderContext, frame: WorldFrameState): void {
        if (!this.visible) return

        // 相机缩放补偿：确保屏幕空间层不受相机缩放影响喵。
        // 当相机缩放时，组以相反比例缩放以保持屏幕大小不变喵。
        const zoom = ctx.camera.zoom || 1.0
        const inverseZoom = 1.0 / zoom

        // 设置缩放补偿，同时保持位置和旋转不变喵。
        this.group.scale.set(inverseZoom, inverseZoom, inverseZoom)
        this.group.updateMatrix()
        this.group.updateMatrixWorld(true)

        this.updateTimestamp()
    }

    dispose(ctx: WorldRenderContext): void {
        // 从相机中移除group喵。
        ctx.camera.remove(this.group)
        super.setVisible(false)
    }

    /**
     * 将像素坐标转换为屏幕空间坐标喵。
     * @param pixelX 像素X坐标（0到容器宽度）
     * @param pixelY 像素Y坐标（0到容器高度）
     * @param ctx 世界渲染上下文
     */
    protected pixelToScreenSpace(pixelX: number, pixelY: number, ctx: WorldRenderContext): THREE.Vector3 {
        const screenWidth = ctx.camera.right - ctx.camera.left
        const screenHeight = ctx.camera.top - ctx.camera.bottom

        const x = THREE.MathUtils.mapLinear(pixelX, 0, ctx.camera.right * 2, ctx.camera.left, ctx.camera.right)
        const y = THREE.MathUtils.mapLinear(pixelY, 0, ctx.camera.top * 2, ctx.camera.bottom, ctx.camera.top)
        return new THREE.Vector3(x, y, this.depth)
    }
}
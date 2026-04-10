/**
 * @file baseLayer.ts
 *
 * @description
 * 基础渲染层抽象类，提供RenderLayer接口的默认实现喵。
 *
 * @usage
 * - 具体层继承此类并实现抽象方法喵。
 * - 自动处理可见性、Group创建等通用逻辑喵。
 */

import * as THREE from 'three'
import type { WorldRenderContext, WorldFrameState } from '../worldRenderManager'
import type { RenderLayer, LayerStats, RenderOrder } from './index'

export abstract class BaseLayer implements RenderLayer {
    readonly name: string
    readonly renderOrder: number
    readonly group: THREE.Group

    protected visible: boolean = true
    protected quality: number = 1.0
    protected lastUpdateTime: number = 0

    constructor(name: string, renderOrder: RenderOrder) {
        this.name = name
        this.renderOrder = renderOrder
        this.group = new THREE.Group()
        this.group.renderOrder = renderOrder
        this.group.frustumCulled = false
    }

    // 抽象方法 - 必须由子类实现
    abstract init(ctx: WorldRenderContext): Promise<void>
    abstract update(ctx: WorldRenderContext, frame: WorldFrameState): void
    abstract dispose(ctx: WorldRenderContext): void

    // 默认实现的方法
    setVisible(visible: boolean): void {
        this.visible = visible
        this.group.visible = visible
    }

    isVisible(): boolean {
        return this.visible
    }

    setQuality(quality: number): void {
        this.quality = Math.max(0, Math.min(1, quality))
    }

    getStats(): LayerStats {
        return {
            visibleObjects: this.countVisibleObjects(),
            totalObjects: this.group.children.length,
            memoryUsageMB: this.estimateMemoryUsage(),
            lastUpdateTimeMs: this.lastUpdateTime,
        }
    }

    // 辅助方法
    protected countVisibleObjects(): number {
        let count = 0
        this.group.traverse((child) => {
            if (child.visible) count++
        })
        return count
    }

    protected estimateMemoryUsage(): number {
        // 简化估算：每个对象约0.1MB
        return this.group.children.length * 0.1
    }

    protected updateTimestamp(): void {
        this.lastUpdateTime = Date.now()
    }
}
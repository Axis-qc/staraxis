/**
 * @file layerManager.ts
 *
 * @description
 * 简单层管理器实现，管理多个渲染层的注册和批量操作喵。
 *
 * @usage
 * - 在WorldRenderManager中创建实例喵。
 * - 通过renderOrder排序确保正确的渲染顺序喵。
 */

import type { WorldRenderContext, WorldFrameState } from '../worldRenderManager'
import type { RenderLayer, LayerManager } from './index'
import { isPerfEnabled } from '../systems/renderLoop'

/** 具有子渲染器计时能力的层接口（如 CelestialLayer）喵 */
interface SubTimingLayer extends RenderLayer {
    lastStarMs?: number
    lastPlanetMs?: number
}

export class SimpleLayerManager implements LayerManager {
    readonly layers = new Map<string, RenderLayer>()

    /** 各层最近一次 update 的耗时（ms），由 updateAll 自动填充喵 */
    readonly lastLayerTimings = new Map<string, number>()

    registerLayer(layer: RenderLayer): void {
        if (this.layers.has(layer.name)) {
            throw new Error(`Layer with name '${layer.name}' already registered`)
        }
        this.layers.set(layer.name, layer)
    }

    unregisterLayer(name: string): void {
        this.layers.delete(name)
    }

    getLayer(name: string): RenderLayer | null {
        return this.layers.get(name) || null
    }

    updateAll(ctx: WorldRenderContext, frame: WorldFrameState): void {
        // 按renderOrder顺序更新，确保正确的渲染顺序
        const sortedLayers = Array.from(this.layers.values())
            .sort((a, b) => a.renderOrder - b.renderOrder)

        if (isPerfEnabled()) {
            for (const layer of sortedLayers) {
                if (layer.isVisible()) {
                    const t0 = performance.now()
                    layer.update(ctx, frame)
                    this.lastLayerTimings.set(layer.name, performance.now() - t0)

                    const sub = layer as SubTimingLayer
                    if (sub.lastStarMs !== undefined) {
                        this.lastLayerTimings.set(`${layer.name}/star`, sub.lastStarMs)
                    }
                    if (sub.lastPlanetMs !== undefined) {
                        this.lastLayerTimings.set(`${layer.name}/planet`, sub.lastPlanetMs)
                    }
                } else {
                    this.lastLayerTimings.set(layer.name, 0)
                }
            }
        } else {
            for (const layer of sortedLayers) {
                if (layer.isVisible()) layer.update(ctx, frame)
            }
        }
    }

    setAllVisible(visible: boolean): void {
        for (const layer of this.layers.values()) {
            layer.setVisible(visible)
        }
    }

    disposeAll(ctx: WorldRenderContext): void {
        for (const layer of this.layers.values()) {
            layer.dispose(ctx)
        }
        this.layers.clear()
    }
}
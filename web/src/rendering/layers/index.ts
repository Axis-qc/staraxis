/**
 * @file index.ts
 *
 * @description
 * 渲染层核心接口定义和常量导出喵。
 *
 * @important_notes
 * - 所有渲染层必须实现RenderLayer接口喵。
 * - 渲染顺序值越小越先渲染喵。
 * - 通过Three.js的renderOrder属性实现喵。
 */

import type * as THREE from 'three'
import type { WorldRenderContext, WorldFrameState } from '../worldRenderManager'

// 渲染顺序值（越小越先渲染）
export const RenderOrder = {
    BACKGROUND: 0,    // 背景层：星空、星云等
    CELESTIAL: 1,     // 星体层：恒星、行星等
    ENTITY: 2,        // 实体层：舰船、建筑、空间站
    EFFECT: 3,        // 实体特效：武器光束、爆炸等
    SELECTION: 4,     // 选择框：实体选择标记
    UI_OVERLAY: 5,    // Three.js UI覆盖层
} as const

export type RenderOrder = typeof RenderOrder[keyof typeof RenderOrder]

/**
 * 层性能统计信息
 */
export interface LayerStats {
    visibleObjects: number
    totalObjects: number
    memoryUsageMB: number
    lastUpdateTimeMs: number
}

/**
 * 渲染层接口定义
 * 所有渲染层必须实现此接口喵。
 */
export interface RenderLayer {
    readonly name: string
    readonly renderOrder: number
    readonly group: THREE.Group

    // 生命周期方法
    init(ctx: WorldRenderContext): Promise<void>
    update(ctx: WorldRenderContext, frame: WorldFrameState): void
    dispose(ctx: WorldRenderContext): void

    // 层控制方法
    setVisible(visible: boolean): void
    isVisible(): boolean
    setQuality(quality: number): void  // 0.0-1.0，控制渲染质量
    getStats(): LayerStats             // 获取层性能统计
}

/**
 * 层管理器接口
 */
export interface LayerManager {
    readonly layers: Map<string, RenderLayer>

    // 层管理
    registerLayer(layer: RenderLayer): void
    unregisterLayer(name: string): void
    getLayer(name: string): RenderLayer | null

    // 批量操作
    updateAll(ctx: WorldRenderContext, frame: WorldFrameState): void
    setAllVisible(visible: boolean): void
    disposeAll(ctx: WorldRenderContext): void
}

export { ScreenSpaceLayer } from './screenSpaceLayer'
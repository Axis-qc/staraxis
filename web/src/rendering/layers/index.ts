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
// 设计约定：
// - EFFECT 用于纯视觉特效（爆炸、光束、拖尾等）喵。
// - ENTITY_EFFECT 用于附着在实体上的交互/指示/UI效果（选中环、状态标记、实体旁UI、引导箭头等）喵。
// - UI_OVERLAY 用于纯屏幕空间覆盖UI，不直接锚定实体喵。
export const RenderOrder = {
    BACKGROUND: 0,    // 背景层：星空、星云等
    CELESTIAL: 1,     // 星体层：恒星、行星等
    ENTITY: 2,        // 实体层：舰船、建筑、空间站
    EFFECT: 3,        // 纯视觉特效：武器光束、爆炸、尾迹等
    ENTITY_EFFECT: 4, // 实体附着效果：选择框、状态标记、跟随实体的UI与交互指示
    SELECTION: 4,     // 兼容别名：旧的选择层顺序，后续统一归入 ENTITY_EFFECT
    UI_OVERLAY: 5,    // Three.js UI覆盖层
} as const

export type RenderOrder = typeof RenderOrder[keyof typeof RenderOrder]

/**
 * Z 轴层级基准常量（远离摄像机方向为负）喵。
 *
 * 设计约定：
 * - 每个物体的实际 z = -(层基准 + 物体渲染半径)，前表面对齐在层基准线上喵。
 * - 实体层（舰船/空间站/建筑）固定在 z = 0 平面喵。
 * - 层级之间留出间隔，避免浮点精度导致的 z-fighting 喵。
 */
export const ZLayer = {
    /** 恒星层前表面基准深度 */
    CELESTIAL_STAR_BASE: 6,
    /** 行星层前表面基准深度 */
    CELESTIAL_PLANET_BASE: 3,
    /** 背景层深度 */
    BACKGROUND: 100,
} as const

/**
 * 行星类型 → 默认表面颜色映射喵。
 *
 * 用途：3D 球体渲染阶段的纯色 fallback，后续替换为程序化着色器喵。
 * 颜色基于 `planet-types.json` 中的 typeId 喵。
 */
export const PLANET_TYPE_COLORS: Record<string, number> = {
    TERRESTRIAL: 0x4a90d9,  // 海洋蓝
    ROCKY_BARREN: 0x8b7355,  // 岩石棕
    GAS_GIANT: 0xd4a054,  // 气态金
    ICE_GIANT: 0x7ec8e3,  // 冰蓝
    OCEAN_WORLD: 0x2e86c1,  // 深海蓝
    LAVA_WORLD: 0xd45d00,  // 岩浆橙
    FROZEN_WORLD: 0xc5e0f0,  // 冰雪白蓝
}

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
    init(ctx: WorldRenderContext): void | Promise<void>
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

    /** 各层最近一次 update 的耗时（ms），由 updateAll 自动填充喵 */
    readonly lastLayerTimings: Map<string, number>

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

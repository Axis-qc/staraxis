/**
 * @file celestialLayer.ts
 *
 * @description
 * 星体层实现，负责渲染恒星、行星等天体喵。
 *
 * @important_notes
 * - 继承BaseLayer，实现RenderLayer接口喵。
 * - 管理星体渲染器（StarRenderer、PlanetRenderer）喵。
 * - 处理星体LOD和可见性喵。
 */

import type { WorldRenderContext, WorldFrameState } from '../../worldRenderManager'
import { BaseLayer } from '../baseLayer'
import { RenderOrder } from '../index'
import { LayerStarRenderer } from './renderers/starRenderer'
import { LayerPlanetRenderer } from './renderers/planetRenderer'
import { isPerfEnabled } from '../../systems/renderLoop'

export class CelestialLayer extends BaseLayer {
    private _starRenderer: LayerStarRenderer | null = null
    private _planetRenderer: LayerPlanetRenderer | null = null

    /** 子渲染器最近一次 update 耗时（ms），仅在 isPerfEnabled() 时有效喵 */
    lastStarMs = 0
    lastPlanetMs = 0

    constructor() {
        super('celestial', RenderOrder.CELESTIAL)
    }

    init(ctx: WorldRenderContext): void {
        // 初始化时将group添加到世界组
        ctx.worldGroup.add(this.group)

        // 初始化恒星渲染器
        this._starRenderer = new LayerStarRenderer(this.group)
        this._starRenderer.init()

        // 初始化行星渲染器
        this._planetRenderer = new LayerPlanetRenderer(this.group)
        this._planetRenderer.init(ctx)
        console.log('CelestialLayer initialized with star and planet renderers')
    }

    update(ctx: WorldRenderContext, frame: WorldFrameState): void {
        if (!this.visible) return

        const perfOn = isPerfEnabled()

        // 更新恒星渲染器
        if (this._starRenderer) {
            if (perfOn) { const t0 = performance.now(); this._starRenderer.update(ctx, frame); this.lastStarMs = performance.now() - t0 }
            else { this._starRenderer.update(ctx, frame) }
        }

        // 更新行星渲染器
        if (this._planetRenderer) {
            if (perfOn) { const t0 = performance.now(); this._planetRenderer.update(ctx, frame); this.lastPlanetMs = performance.now() - t0 }
            else { this._planetRenderer.update(ctx, frame) }
        }

        this.updateTimestamp()
    }

    dispose(ctx: WorldRenderContext): void {
        // 清理渲染器
        if (this._starRenderer) {
            this._starRenderer.dispose()
            this._starRenderer = null
        }

        // 清理行星渲染器
        if (this._planetRenderer) {
            this._planetRenderer.dispose()
            this._planetRenderer = null
        }

        ctx.worldGroup.remove(this.group)
        super.setVisible(false)
    }
}
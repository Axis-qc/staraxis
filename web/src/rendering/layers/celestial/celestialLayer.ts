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

export class CelestialLayer extends BaseLayer {
    private _starRenderer: LayerStarRenderer | null = null
    private _planetRenderer: any = null // TODO: 后续添加

    constructor() {
        super('celestial', RenderOrder.CELESTIAL)
    }

    async init(ctx: WorldRenderContext): Promise<void> {
        // 初始化时将group添加到世界组
        ctx.worldGroup.add(this.group)

        // 初始化恒星渲染器
        this._starRenderer = new LayerStarRenderer(this.group)
        this._starRenderer.init()

        // TODO: 初始化行星渲染器
        console.log('CelestialLayer initialized with star renderer')
        void this._planetRenderer // 避免未使用变量警告
    }

    update(ctx: WorldRenderContext, frame: WorldFrameState): void {
        if (!this.visible) return

        // 更新恒星渲染器
        if (this._starRenderer) {
            this._starRenderer.update(ctx, frame)
        }

        // TODO: 更新行星渲染器

        this.updateTimestamp()
    }

    dispose(ctx: WorldRenderContext): void {
        // 清理渲染器
        if (this._starRenderer) {
            this._starRenderer.dispose()
            this._starRenderer = null
        }

        // TODO: 清理行星渲染器

        ctx.worldGroup.remove(this.group)
        super.setVisible(false)
    }
}
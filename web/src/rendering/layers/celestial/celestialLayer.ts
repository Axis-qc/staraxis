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

export class CelestialLayer extends BaseLayer {
    private _starRenderer: any = null
    private _planetRenderer: any = null

    constructor() {
        super('celestial', RenderOrder.CELESTIAL)
    }

    async init(ctx: WorldRenderContext): Promise<void> {
        // 初始化时将group添加到世界组
        ctx.worldGroup.add(this.group)

        // TODO: 初始化具体渲染器（后续任务实现）
        console.log('CelestialLayer initialized')

        // 避免未使用变量警告
        void this._starRenderer
        void this._planetRenderer
    }

    update(_ctx: WorldRenderContext, _frame: WorldFrameState): void {
        if (!this.visible) return

        // TODO: 更新星体渲染器（后续任务实现）
        this.updateTimestamp()
    }

    dispose(ctx: WorldRenderContext): void {
        // TODO: 清理资源（后续任务实现）
        ctx.worldGroup.remove(this.group)
        super.setVisible(false)
    }
}
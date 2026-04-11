/**
 * @file backgroundLayer.ts
 *
 * @description
 * 背景渲染层实现，负责星空背景等背景元素的渲染喵。
 *
 * 作用喵：
 * - 渲染星空背景（通过StarfieldRenderer）喵。
 * - 提供视差滚动效果增强空间感喵。
 * - 作为最底层的渲染层（RenderOrder.BACKGROUND）喵。
 */

import type { WorldRenderContext, WorldFrameState } from '@/rendering/worldRenderManager'
import { BaseLayer } from '../baseLayer'
import { RenderOrder } from '../index'
import { StarfieldRenderer } from './renderers/starfieldRenderer'

export class BackgroundLayer extends BaseLayer {
    private starfieldRenderer: StarfieldRenderer | null = null

    constructor() {
        super('background', RenderOrder.BACKGROUND)
    }

    async init(ctx: WorldRenderContext): Promise<void> {
        ctx.worldGroup.add(this.group)

        // 初始化星空渲染器
        this.starfieldRenderer = new StarfieldRenderer(this.group)
        await this.starfieldRenderer.init(ctx)

        console.log('BackgroundLayer initialized')
    }

    update(ctx: WorldRenderContext, frame: WorldFrameState): void {
        if (!this.visible) return

        // 更新星空渲染器
        if (this.starfieldRenderer) {
            this.starfieldRenderer.update(ctx, frame)
        }

        this.updateTimestamp()
    }

    dispose(ctx: WorldRenderContext): void {
        if (this.starfieldRenderer) {
            this.starfieldRenderer.dispose()
            this.starfieldRenderer = null
        }

        ctx.worldGroup.remove(this.group)
        super.setVisible(false)
    }
}
/**
 * @file backgroundLayer.ts
 *
 * @description
 * 背景渲染层实现，负责星空背景等背景元素的渲染喵。
 *
 * 作用喵：
 * - 渲染星空背景（通过ParticleStarfieldRenderer）喵。
 * - 将背景绑定到相机，完全跟随相机移动（天空盒性质）喵。
 * - 作为最底层的渲染层（RenderOrder.BACKGROUND）喵。
 */

import type { WorldRenderContext, WorldFrameState } from '@/rendering/worldRenderManager'
import { ScreenSpaceLayer } from '../screenSpaceLayer'
import { RenderOrder } from '../index'
import { ParticleStarfieldRenderer } from './renderers/particleStarfieldRenderer'

export class BackgroundLayer extends ScreenSpaceLayer {
    private starfieldRenderer: ParticleStarfieldRenderer | null = null

    constructor() {
        super('background', RenderOrder.BACKGROUND)
        this.depth = -1500 // 背景层在相机后面的深度喵。
    }

    async init(ctx: WorldRenderContext): Promise<void> {
        // 调用父类初始化，将group添加到相机作为子对象喵。
        await super.init(ctx)

        // 初始化星空渲染器（使用粒子系统）喵。
        this.starfieldRenderer = new ParticleStarfieldRenderer(this.group)
        this.starfieldRenderer.init(ctx)

        console.log('[背景层] 屏幕空间背景层已初始化')
    }

    update(ctx: WorldRenderContext, frame: WorldFrameState): void {
        if (!this.visible) return

        // 调用父类更新，保持相机子对象位置喵。
        super.update(ctx, frame)

        // 更新星空渲染器喵。
        if (this.starfieldRenderer) {
            this.starfieldRenderer.update(ctx, frame)
        }
    }


    dispose(ctx: WorldRenderContext): void {
        if (this.starfieldRenderer) {
            this.starfieldRenderer.dispose()
            this.starfieldRenderer = null
        }

        // 调用父类dispose，从相机中移除group喵。
        super.dispose(ctx)
    }
}
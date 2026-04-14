import type { WorldRenderContext, WorldFrameState } from '../../worldRenderManager'
import { BaseLayer } from '../baseLayer'
import { RenderOrder } from '../index'
import { LayerShipRenderer } from './renderers/shipRenderer'

export class EntityLayer extends BaseLayer {
    private _shipRenderer: LayerShipRenderer | null = null

    constructor() {
        super('entity', RenderOrder.ENTITY)
    }

    init(ctx: WorldRenderContext): void {
        ctx.worldGroup.add(this.group)

        // 初始化舰船渲染器
        this._shipRenderer = new LayerShipRenderer(this.group)
        this._shipRenderer.init()

        console.log('EntityLayer initialized with ship renderer')
    }

    update(ctx: WorldRenderContext, frame: WorldFrameState): void {
        if (!this.visible) return

        // 更新舰船渲染器
        if (this._shipRenderer) {
            this._shipRenderer.update(ctx, frame)
        }

        this.updateTimestamp()
    }

    dispose(ctx: WorldRenderContext): void {
        // 清理舰船渲染器
        if (this._shipRenderer) {
            this._shipRenderer.dispose()
            this._shipRenderer = null
        }

        ctx.worldGroup.remove(this.group)
        super.setVisible(false)
    }
}
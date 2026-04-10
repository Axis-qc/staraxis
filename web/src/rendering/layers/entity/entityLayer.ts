import type { WorldRenderContext, WorldFrameState } from '../../worldRenderManager'
import { BaseLayer } from '../baseLayer'
import { RenderOrder } from '../index'

export class EntityLayer extends BaseLayer {
    private _shipRenderer: any = null

    constructor() {
        super('entity', RenderOrder.ENTITY)
    }

    async init(ctx: WorldRenderContext): Promise<void> {
        ctx.worldGroup.add(this.group)
        console.log('EntityLayer initialized')
        void this._shipRenderer // 避免未使用变量警告
    }

    update(_ctx: WorldRenderContext, _frame: WorldFrameState): void {
        if (!this.visible) return
        this.updateTimestamp()
    }

    dispose(ctx: WorldRenderContext): void {
        ctx.worldGroup.remove(this.group)
        super.setVisible(false)
    }
}
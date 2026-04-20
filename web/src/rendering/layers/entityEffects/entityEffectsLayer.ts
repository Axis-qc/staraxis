/**
 * @file entityEffectsLayer.ts
 * @description 实体附着效果层，统一承载实体相关的交互指示与跟随式 UI 喵
 * @important_notes
 * - 当前第一批转移进来的功能是选中环喵。
 * - 后续实体旁 UI、状态徽标、引导箭头等，也应挂载在这一层喵。
 */

import type { WorldRenderContext, WorldFrameState } from '../../worldRenderManager'
import { BaseLayer } from '../baseLayer'
import { RenderOrder } from '../index'
import { SelectionEffectRenderer } from './renderers/selectionEffectRenderer'

export class EntityEffectsLayer extends BaseLayer {
  private _selectionEffectRenderer: SelectionEffectRenderer | null = null

  constructor() {
    super('entityEffects', RenderOrder.ENTITY_EFFECT)
  }

  init(ctx: WorldRenderContext): void {
    ctx.worldGroup.add(this.group)

    this._selectionEffectRenderer = new SelectionEffectRenderer(this.group)
    this._selectionEffectRenderer.init(ctx)
  }

  update(ctx: WorldRenderContext, frame: WorldFrameState): void {
    if (!this.visible) return

    if (this._selectionEffectRenderer) {
      this._selectionEffectRenderer.update(ctx, frame)
    }

    this.updateTimestamp()
  }

  dispose(ctx: WorldRenderContext): void {
    if (this._selectionEffectRenderer) {
      this._selectionEffectRenderer.dispose()
      this._selectionEffectRenderer = null
    }

    ctx.worldGroup.remove(this.group)
    super.setVisible(false)
  }
}

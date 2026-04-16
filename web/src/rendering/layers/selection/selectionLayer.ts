/**
 * @file selectionLayer.ts
 * @description 选择层 - 处理所有实体类型的选中状态指示喵
 * @important_notes 使用绿色虚线环表示选中实体喵
 */

import type { WorldRenderContext, WorldFrameState } from '../../worldRenderManager'
import { BaseLayer } from '../baseLayer'
import { RenderOrder } from '../index'
import { LayerSelectionRenderer } from './renderers/selectionRenderer'

export class SelectionLayer extends BaseLayer {
  private _selectionRenderer: LayerSelectionRenderer | null = null

  constructor() {
    super('selection', RenderOrder.SELECTION)
  }

  init(ctx: WorldRenderContext): void {
    ctx.worldGroup.add(this.group)

    this._selectionRenderer = new LayerSelectionRenderer(this.group)
    this._selectionRenderer.init(ctx)

    console.log('SelectionLayer initialized')
  }

  update(ctx: WorldRenderContext, frame: WorldFrameState): void {
    if (!this.visible) return

    if (this._selectionRenderer) {
      this._selectionRenderer.update(ctx, frame)
    }

    this.updateTimestamp()
  }

  dispose(ctx: WorldRenderContext): void {
    if (this._selectionRenderer) {
      this._selectionRenderer.dispose()
      this._selectionRenderer = null
    }

    ctx.worldGroup.remove(this.group)
    super.setVisible(false)
  }
}
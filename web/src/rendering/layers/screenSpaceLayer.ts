import * as THREE from 'three'
import type { WorldRenderContext, WorldFrameState } from '../worldRenderManager'
import { BaseLayer } from './baseLayer'
import type { RenderOrder } from './index'

export abstract class ScreenSpaceLayer extends BaseLayer {
    protected depth = -100

    constructor(name: string, renderOrder: RenderOrder) {
        super(name, renderOrder)
    }

    async init(ctx: WorldRenderContext): Promise<void> {
        if (!ctx.camera.parent) {
            ctx.scene.add(ctx.camera)
        }

        ctx.camera.add(this.group)
        this.group.position.set(0, 0, this.depth)
        this.group.scale.set(1, 1, 1)
        this.group.visible = true
    }

    update(ctx: WorldRenderContext, frame: WorldFrameState): void {
        void ctx
        void frame

        if (!this.visible) return

        this.group.scale.set(1, 1, 1)
        this.group.updateMatrix()
        this.group.updateMatrixWorld(true)
        this.updateTimestamp()
    }

    dispose(ctx: WorldRenderContext): void {
        ctx.camera.remove(this.group)
        super.setVisible(false)
    }

    protected pixelToScreenSpace(pixelX: number, pixelY: number, ctx: WorldRenderContext): THREE.Vector3 {
        const viewportWidthPx = Math.max(ctx.renderer.domElement.clientWidth, 1)
        const viewportHeightPx = Math.max(ctx.renderer.domElement.clientHeight, 1)
        const { widthGU, heightGU } = ctx.getViewSizeAtDepth(Math.abs(this.depth))

        const x = THREE.MathUtils.mapLinear(pixelX, 0, viewportWidthPx, -widthGU / 2, widthGU / 2)
        const y = THREE.MathUtils.mapLinear(pixelY, 0, viewportHeightPx, -heightGU / 2, heightGU / 2)
        return new THREE.Vector3(x, y, this.depth)
    }
}

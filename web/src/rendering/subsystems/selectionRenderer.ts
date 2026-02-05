/**
 * @file selectionRenderer.ts
 *
 * @description
 * 选择环渲染器。
 *
 * 作用：
 * - 为选中实体绘制一个高亮选择环（Ring）。
 * - 通过对象池复用 Mesh，避免频繁创建/销毁。
 *
 * @usage
 * - 在 WorldRenderManager 中注册：subsystems.push(new SelectionRenderer())
 * - update 时读取 frame.selectedIds，并为对应实体绘制选择环。
 *
 * @provides
 * - **选择高亮**：选中实体周围的黄色环。
 *
 * @api
 * - init(ctx): void
 * - update(ctx, frame): void
 * - dispose(ctx): void
 *
 * @important_notes
 * - 选择环尺寸依赖实体半径（若无半径则使用 zoom 兜底）。
 * - 选择环位置使用 entity.posWorldGU 或管理器提供的 getEntityWorldPosGU。
 */
import * as THREE from 'three'
import type { WorldRenderContext, WorldFrameState } from '../worldRenderManager'
import type { WorldRenderSubsystem } from './worldRenderSubsystem'

export type SelectionPositionResolver = (entityId: number) => { x: number; y: number } | null

export class SelectionRenderer implements WorldRenderSubsystem {
    private ringPool: THREE.Mesh[] = []
    private activeRingByEntityId = new Map<number, THREE.Mesh>()

    private resolvePos: SelectionPositionResolver

    constructor(resolvePos: SelectionPositionResolver) {
        this.resolvePos = resolvePos
    }

    init(ctx: WorldRenderContext): void {
        void ctx
    }

    update(ctx: WorldRenderContext, frame: WorldFrameState): void {
        const nextIds = frame.selectedIds

        for (const entityId of nextIds) {
            const posWorld = this.resolvePos(entityId)
            if (!posWorld) continue

            let ring = this.activeRingByEntityId.get(entityId)
            if (!ring) {
                ring = this.acquireRing()
                this.activeRingByEntityId.set(entityId, ring)
                ctx.entitiesGroup.add(ring)
            }

            const size = Math.max(ctx.zoom.value * 4, 10)
            ring.scale.set(size, size, 1)
            ring.position.set(posWorld.x - ctx.cameraWorldPosGU.x, posWorld.y - ctx.cameraWorldPosGU.y, 1)
            ring.visible = true
        }

        for (const [id, ring] of this.activeRingByEntityId.entries()) {
            if (!nextIds.has(id)) {
                this.activeRingByEntityId.delete(id)
                this.releaseRing(ring)
            }
        }
    }

    dispose(ctx: WorldRenderContext): void {
        void ctx

        const disposeRing = (ring: THREE.Mesh) => {
            ring.geometry.dispose()
            if (Array.isArray(ring.material)) {
                ring.material.forEach((m) => m.dispose())
            } else {
                ; (ring.material as THREE.Material).dispose()
            }
        }

        for (const ring of this.ringPool) disposeRing(ring)
        for (const ring of this.activeRingByEntityId.values()) disposeRing(ring)

        this.ringPool = []
        this.activeRingByEntityId.clear()
    }

    private acquireRing(): THREE.Mesh {
        const ring = this.ringPool.pop()
        if (ring) {
            ring.visible = true
            return ring
        }

        const geo = new THREE.RingGeometry(0.85, 1.0, 48)
        const mat = new THREE.MeshBasicMaterial({
            color: 0xffe04d,
            transparent: true,
            opacity: 0.95,
            depthWrite: false,
        })

        const m = new THREE.Mesh(geo, mat)
        m.visible = true
        m.frustumCulled = false
        return m
    }

    private releaseRing(ring: THREE.Mesh): void {
        ring.visible = false
        ring.parent?.remove(ring)
        this.ringPool.push(ring)
    }
}

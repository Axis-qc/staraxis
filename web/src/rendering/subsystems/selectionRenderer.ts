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
import type { StarDetails, PlanetDetails } from '../../net/snapshotWs'
import { shouldRender, getLodSize } from './lodSystem'

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
        const selectionLod = frame.lod.selection

        // 使用LOD系统检查是否应该在当前zoom级别显示选择环
        // 注意：选择环是特殊实体，即使在高zoom下也应该显示（除非达到极高zoom）
        if (!shouldRender(selectionLod, true)) {
            // 隐藏所有选择环
            for (const ring of this.activeRingByEntityId.values()) {
                ring.visible = false
            }
            return
        }

        for (const entityId of nextIds) {
            const posWorld = this.resolvePos(entityId)
            if (!posWorld) continue

            // 获取实体数据以计算大小
            const entity = frame.entitiesById.get(entityId)
            let entityRadius = 0
            if (entity) {
                if (entity.entityType === 'STAR') {
                    const details = entity.details as StarDetails
                    entityRadius = details?.radiusGU ?? 0
                } else if (entity.entityType === 'PLANET') {
                    const details = entity.details as PlanetDetails
                    entityRadius = details?.radiusGU ?? 0
                }
            }

            let ring = this.activeRingByEntityId.get(entityId)
            if (!ring) {
                ring = this.acquireRing()
                this.activeRingByEntityId.set(entityId, ring)
                ctx.entitiesGroup.add(ring)
            }

            // 计算选择环大小：基于实体半径 + 额外边距
            // 如果实体没有半径，使用兜底值
            let baseSize: number
            if (entityRadius > 0) {
                // 实体半径 * 2（直径） + 40% 边距
                baseSize = entityRadius * 2 * 1.4
            } else {
                // 兜底：使用 zoom 相关的大小
                baseSize = Math.max(ctx.zoom.value * 4, 10)
            }

            const size = getLodSize(selectionLod, true, baseSize)

            // 确保最小显示像素（至少 10 像素可见）
            // 将最小像素转换为世界单位：像素 * zoom = 世界单位
            const MIN_PIXEL_SIZE = 10
            const minWorldSize = MIN_PIXEL_SIZE * ctx.zoom.value
            const finalSize = Math.max(size, minWorldSize)

            ring.scale.set(finalSize, finalSize, 1)
            ring.position.set(posWorld.x - ctx.cameraWorldPosGU.x, posWorld.y - ctx.cameraWorldPosGU.y, 1)

            // 根据LOD调整透明度
            const material = ring.material as THREE.MeshBasicMaterial
            material.opacity = 0.95 * selectionLod.params.textureQuality

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

/**
 * @file starRenderer.ts
 *
 * @description
 * 恒星渲染器。
 *
 * 作用：
 * - 根据快照数据渲染恒星实体。
 * - 当前阶段先提供最小可用：远距离点模式（star dot）。
 * - 后续再扩展精灵/材质/模型等。
 *
 * @usage
 * - 在管理器中注册：subsystems.push(new StarRenderer())
 * - 管理器在 update 时传入 context 和 frameState。
 *
 * @provides
 * - **恒星渲染**：根据半径与缩放渲染恒星点。
 * - **对象池**：复用 Three.js 对象，减少 GC 压力。
 *
 * @api
 * - init(ctx: WorldRenderContext): void
 * - update(ctx: WorldRenderContext, frame: WorldFrameState): void
 * - dispose(ctx: WorldRenderContext): void
 *
 * @important_notes
 * - 只渲染可见范围内的恒星，提高渲染效率。
 * - 选择环/高亮暂不在此处理（后续下沉到 selectionRenderer）。
 */
import * as THREE from 'three'
import type { WorldRenderContext, WorldFrameState } from '../worldRenderManager'
import type { WorldRenderSubsystem } from './worldRenderSubsystem'
import type { StarDetails } from '../../net/snapshotWs'

export class StarRenderer implements WorldRenderSubsystem {
    private starDotPool: THREE.Mesh[] = []
    private activeStarDotsByEntityId = new Map<number, THREE.Mesh>()

    init(ctx: WorldRenderContext): void {
        void ctx

        for (let i = 0; i < 100; i++) {
            const geo = new THREE.CircleGeometry(1, 12)
            const mat = new THREE.MeshBasicMaterial({ color: 0xffffff, transparent: true, opacity: 0.9, depthWrite: false })
            const m = new THREE.Mesh(geo, mat)
            m.visible = false
            m.frustumCulled = false
            this.starDotPool.push(m)
        }
    }

    update(ctx: WorldRenderContext, frame: WorldFrameState): void {
        const { entitiesGroup } = ctx
        const { entitiesById, selectedIds, cullingAabb } = frame

        const nextStarDotIds = new Set<number>()

        for (const entity of entitiesById.values()) {
            if (entity.entityType !== 'STAR') continue

            const isSelected = selectedIds.has(entity.entityId)
            if (!entity.posWorldGU || (!isSelected && !isPointInAabb(entity.posWorldGU, cullingAabb))) {
                continue
            }

            const details = entity.details as StarDetails

            nextStarDotIds.add(entity.entityId)

            const radiusGU = Number(details?.radiusGU ?? 1)
            const size = Math.max(ctx.zoom.value * 2, radiusGU * 2)

            let dot = this.activeStarDotsByEntityId.get(entity.entityId)
            if (!dot) {
                dot = this.acquireStarDot()
                this.activeStarDotsByEntityId.set(entity.entityId, dot)
                entitiesGroup.add(dot)
            }

            dot.scale.set(size, size, 1)
            dot.position.set(entity.posWorldGU.x - ctx.cameraWorldPosGU.x, entity.posWorldGU.y - ctx.cameraWorldPosGU.y, 0)
            dot.visible = true
        }

        for (const [id, dot] of this.activeStarDotsByEntityId.entries()) {
            if (!nextStarDotIds.has(id)) {
                this.activeStarDotsByEntityId.delete(id)
                this.releaseStarDot(dot)
            }
        }
    }

    dispose(ctx: WorldRenderContext): void {
        void ctx

        const disposeDot = (dot: THREE.Mesh) => {
            dot.geometry.dispose()
            if (Array.isArray(dot.material)) {
                dot.material.forEach((m) => m.dispose())
            } else {
                ; (dot.material as THREE.Material).dispose()
            }
        }

        for (const dot of this.starDotPool) disposeDot(dot)
        for (const dot of this.activeStarDotsByEntityId.values()) disposeDot(dot)

        this.starDotPool = []
        this.activeStarDotsByEntityId.clear()
    }

    private acquireStarDot(): THREE.Mesh {
        const dot = this.starDotPool.pop()
        if (dot) {
            dot.visible = true
            return dot
        }

        const geo = new THREE.CircleGeometry(1, 12)
        const mat = new THREE.MeshBasicMaterial({ color: 0xffffff, transparent: true, opacity: 0.9, depthWrite: false })
        const m = new THREE.Mesh(geo, mat)
        m.visible = true
        m.frustumCulled = false
        return m
    }

    private releaseStarDot(dot: THREE.Mesh): void {
        dot.visible = false
        dot.parent?.remove(dot)
        this.starDotPool.push(dot)
    }
}

function isPointInAabb(
    point: { x: number; y: number },
    aabb: { minX: number; maxX: number; minY: number; maxY: number },
): boolean {
    return point.x >= aabb.minX && point.x <= aabb.maxX && point.y >= aabb.minY && point.y <= aabb.maxY
}

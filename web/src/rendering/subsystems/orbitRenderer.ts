/**
 * @file orbitRenderer.ts
 *
 * @description
 * 轨道渲染器。
 *
 * 作用：
 * - 根据快照数据渲染行星轨道。
 * - 使用椭圆曲线生成轨道线。
 * - 支持轨道参数变化时的动态更新。
 * - 管理对象池，提高性能。
 *
 * @usage
 * - 在管理器中注册：subsystems.push(new OrbitRenderer())
 * - 管理器在 update 时传入 context 和 frameState。
 *
 * @provides
 * - **轨道渲染**：使用椭圆曲线生成轨道线。
 * - **动态更新**：支持轨道参数变化时的重新生成。
 * - **对象池**：复用 Three.js 对象，减少 GC 压力。
 *
 * @api
 * - init(ctx: WorldRenderContext): void
 * - update(ctx: WorldRenderContext, frame: WorldFrameState): void
 * - dispose(ctx: WorldRenderContext): void
 *
 * @important_notes
 * - 使用椭圆曲线生成轨道线，支持椭圆轨道。
 * - 根据轨道参数动态更新曲线。
 * - 只渲染可见范围内的轨道，提高渲染效率。
 * - 使用对象池避免频繁创建/销毁 Three.js 对象。
 */
import * as THREE from 'three'
import type { WorldRenderContext, WorldFrameState } from '../worldRenderManager'
import type { WorldRenderSubsystem } from './worldRenderSubsystem'
import type { PlanetDetails } from '../../net/snapshotWs'

export class OrbitRenderer implements WorldRenderSubsystem {
    private orbitLinePool: THREE.Line[] = []
    private activeOrbitLinesByEntityId = new Map<number, THREE.Line>()

    init(ctx: WorldRenderContext): void {
        void ctx // unused

        // 预创建一些轨道线对象以避免运行时创建
        for (let i = 0; i < 20; i++) {
            const geometry = new THREE.BufferGeometry()
            const material = new THREE.LineBasicMaterial({
                color: 0xaaaaaa,
                transparent: true,
                opacity: 0.5,
                depthTest: false,
                depthWrite: false,
            })
            const line = new THREE.Line(geometry, material)
            line.visible = false
            line.frustumCulled = false
            this.orbitLinePool.push(line)
        }
    }

    update(ctx: WorldRenderContext, frame: WorldFrameState): void {
        const { entitiesGroup } = ctx
        const { entitiesById, selectedIds, cullingAabb } = frame

        const nextOrbitIds = new Set<number>()

        for (const entity of entitiesById.values()) {
            if (entity.entityType !== 'PLANET') continue

            const details = entity.details as PlanetDetails
            const orbit = details.orbit
            if (!orbit) continue

            const orbitCenter = entitiesById.get(orbit.orbitCenterEntityId)
            if (!orbitCenter?.posWorldGU) continue

            const isSelected = selectedIds.has(entity.entityId)
            // 粗略剔除：如果轨道中心在剔除范围外，则不渲染轨道
            if (!isSelected && !isPointInAabb(orbitCenter.posWorldGU, cullingAabb)) {
                continue
            }

            nextOrbitIds.add(entity.entityId)

            let ellipse = this.activeOrbitLinesByEntityId.get(entity.entityId)
            if (!ellipse) {
                ellipse = this.acquireOrbitLine()
                this.activeOrbitLinesByEntityId.set(entity.entityId, ellipse)
                entitiesGroup.add(ellipse)
            }

            const semiMajorAxisGU = Number(orbit.semiMajorAxisGU ?? 0)
            const ecc = Number(orbit.eccentricity ?? 0)

            const curve = new THREE.EllipseCurve(
                0,
                0,
                semiMajorAxisGU,
                semiMajorAxisGU * Math.sqrt(Math.max(0, 1 - ecc ** 2)),
                0,
                2 * Math.PI,
                false,
                (Number((orbit as any).periapsisArgDeg ?? 0) * Math.PI) / 180,
            )
            const points = curve.getPoints(128)
            for (const p of points) {
                p.x += orbitCenter.posWorldGU.x - ctx.cameraWorldPosGU.x
                p.y += orbitCenter.posWorldGU.y - ctx.cameraWorldPosGU.y
            }
            ellipse.geometry.setFromPoints(points)
            ellipse.visible = true
        }

        // 回收不可见的对象
        for (const [id, line] of this.activeOrbitLinesByEntityId.entries()) {
            if (!nextOrbitIds.has(id)) {
                this.activeOrbitLinesByEntityId.delete(id)
                this.releaseOrbitLine(line)
            }
        }
    }

    dispose(ctx: WorldRenderContext): void {
        void ctx // unused

        // 释放所有对象
        for (const line of this.orbitLinePool) {
            line.geometry.dispose()
                ; (line.material as THREE.Material).dispose()
        }
        for (const line of this.activeOrbitLinesByEntityId.values()) {
            line.geometry.dispose()
                ; (line.material as THREE.Material).dispose()
        }

        this.orbitLinePool = []
        this.activeOrbitLinesByEntityId.clear()
    }

    private acquireOrbitLine(): THREE.Line {
        const line = this.orbitLinePool.pop()
        if (line) {
            line.visible = true
            return line
        }

        const geometry = new THREE.BufferGeometry()
        const material = new THREE.LineBasicMaterial({
            color: 0xaaaaaa,
            transparent: true,
            opacity: 0.5,
            depthTest: false,
            depthWrite: false,
        })
        const l = new THREE.Line(geometry, material)
        l.visible = true
        l.frustumCulled = false
        return l
    }

    private releaseOrbitLine(line: THREE.Line): void {
        line.visible = false
        line.parent?.remove(line)
        this.orbitLinePool.push(line)
    }
}

function isPointInAabb(
    point: { x: number; y: number },
    aabb: { minX: number; maxX: number; minY: number; maxY: number },
): boolean {
    return point.x >= aabb.minX && point.x <= aabb.maxX && point.y >= aabb.minY && point.y <= aabb.maxY
}

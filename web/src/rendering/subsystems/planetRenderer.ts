/**
 * @file planetRenderer.ts
 *
 * @description
 * 行星渲染器。
 *
 * 作用：
 * - 根据快照数据渲染行星实体。
 * - 使用球体几何体（白模）渲染 3D 行星。
 * - 计算行星轨道位置（基于轨道参数）。
 * - 管理对象池，提高性能。
 *
 * @usage
 * - 在管理器中注册：subsystems.push(new PlanetRenderer())
 * - 管理器在 update 时传入 context 和 frameState。
 *
 * @provides
 * - **3D 行星渲染**：使用球体几何体（白模）。
 * - **轨道位置计算**：基于轨道参数计算行星位置。
 * - **对象池**：复用 Three.js 对象，减少 GC 压力。
 *
 * @api
 * - init(ctx: WorldRenderContext): void
 * - update(ctx: WorldRenderContext, frame: WorldFrameState): void
 * - dispose(ctx: WorldRenderContext): void
 *
 * @important_notes
 * - 使用球体几何体 + PBR 贴图渲染 3D 行星。
 * - 根据轨道参数计算行星位置，支持椭圆轨道。
 * - 只渲染可见范围内的行星，提高渲染效率。
 * - 使用对象池避免频繁创建/销毁 Three.js 对象。
 */
import * as THREE from 'three'
import type { WorldRenderContext, WorldFrameState } from '../worldRenderManager'
import type { WorldRenderSubsystem } from './worldRenderSubsystem'
import type { PlanetDetails } from '../../net/snapshotWs'

export class PlanetRenderer implements WorldRenderSubsystem {
    private planetMeshPool: THREE.Mesh[] = []
    private activePlanetMeshesByEntityId = new Map<number, THREE.Mesh>()

    init(ctx: WorldRenderContext): void {
        void ctx // unused

        // 预创建一些球体对象以避免运行时创建
        for (let i = 0; i < 20; i++) {
            const geometry = new THREE.SphereGeometry(1, 32, 16)
            const material = new THREE.MeshStandardMaterial({
                color: 0xcccccc, // 白模
                transparent: false,
                depthWrite: true,
            })
            const mesh = new THREE.Mesh(geometry, material)
            mesh.visible = false
            mesh.frustumCulled = false
            this.planetMeshPool.push(mesh)
        }
    }

    update(ctx: WorldRenderContext, frame: WorldFrameState): void {
        const { entitiesGroup } = ctx
        const { entitiesById, selectedIds, cullingAabb, showPlanets, totalDays } = frame

        const nextPlanetMeshIds = new Set<number>()

        for (const entity of entitiesById.values()) {
            if (entity.entityType !== 'PLANET') continue

            const isSelected = selectedIds.has(entity.entityId)
            if (!showPlanets && !isSelected) continue

            const details = entity.details as PlanetDetails
            const orbit = details.orbit
            if (!orbit) continue

            const orbitCenter = entitiesById.get(orbit.orbitCenterEntityId)
            if (!orbitCenter?.posWorldGU) continue

            const planetPos = computePlanetPosWorldGU({ orbit, orbitCenter, totalDays })
            if (!planetPos) continue

            if (!isSelected && !isPointInAabb(planetPos, cullingAabb)) {
                continue
            }

            nextPlanetMeshIds.add(entity.entityId)

            // 3D 球体渲染（白模）
            const radiusGU = details.radiusGU
            const size = radiusGU * 2

            let mesh = this.activePlanetMeshesByEntityId.get(entity.entityId)
            if (!mesh) {
                mesh = this.acquirePlanetMesh()
                this.activePlanetMeshesByEntityId.set(entity.entityId, mesh)
                entitiesGroup.add(mesh)
            }

            mesh.scale.set(size, size, size)
            mesh.position.set(planetPos.x - ctx.cameraWorldPosGU.x, planetPos.y - ctx.cameraWorldPosGU.y, 0)
            mesh.visible = true
        }

        // 回收不可见的对象
        for (const [id, mesh] of this.activePlanetMeshesByEntityId.entries()) {
            if (!nextPlanetMeshIds.has(id)) {
                this.activePlanetMeshesByEntityId.delete(id)
                this.releasePlanetMesh(mesh)
            }
        }
    }

    dispose(ctx: WorldRenderContext): void {
        void ctx // unused

        // 释放所有对象
        for (const mesh of this.planetMeshPool) {
            mesh.geometry.dispose()
                ; (mesh.material as THREE.Material).dispose()
        }
        for (const mesh of this.activePlanetMeshesByEntityId.values()) {
            mesh.geometry.dispose()
                ; (mesh.material as THREE.Material).dispose()
        }

        this.planetMeshPool = []
        this.activePlanetMeshesByEntityId.clear()
    }

    private acquirePlanetMesh(): THREE.Mesh {
        const mesh = this.planetMeshPool.pop()
        if (mesh) {
            mesh.visible = true
            return mesh
        }

        const geometry = new THREE.SphereGeometry(1, 32, 16)
        const material = new THREE.MeshStandardMaterial({
            color: 0xcccccc,
            transparent: false,
            depthWrite: true,
        })
        const m = new THREE.Mesh(geometry, material)
        m.visible = true
        m.frustumCulled = false
        return m
    }

    private releasePlanetMesh(mesh: THREE.Mesh): void {
        mesh.visible = false
        mesh.parent?.remove(mesh)
        this.planetMeshPool.push(mesh)
    }
}

function isPointInAabb(
    point: { x: number; y: number },
    aabb: { minX: number; maxX: number; minY: number; maxY: number },
): boolean {
    return point.x >= aabb.minX && point.x <= aabb.maxX && point.y >= aabb.minY && point.y <= aabb.maxY
}

function computePlanetPosWorldGU(args: {
    orbit: any
    orbitCenter: any
    totalDays: number
}): { x: number; y: number } | null {
    const { orbit, orbitCenter, totalDays } = args
    if (!orbit || !orbitCenter.posWorldGU) return null

    const meanAnomaly = (Number(orbit.meanAnomalyDegAtEpoch ?? 0) * Math.PI) / 180
    const periodDays = Number(orbit.orbitalPeriodDays ?? 0)
    if (!Number.isFinite(periodDays) || periodDays <= 0) return null

    const angle = meanAnomaly + (totalDays / periodDays) * 2 * Math.PI

    const a = Number(orbit.semiMajorAxisGU ?? 0)
    const e = Number(orbit.eccentricity ?? 0)
    const b = a * Math.sqrt(Math.max(0, 1 - e ** 2))

    const periapsisArgRad = (Number(orbit.periapsisArgDeg ?? 0) * Math.PI) / 180

    const localX = a * Math.cos(angle)
    const localY = b * Math.sin(angle)

    const cosW = Math.cos(periapsisArgRad)
    const sinW = Math.sin(periapsisArgRad)
    const rotatedX = localX * cosW - localY * sinW
    const rotatedY = localX * sinW + localY * cosW

    return {
        x: orbitCenter.posWorldGU.x + rotatedX,
        y: orbitCenter.posWorldGU.y + rotatedY,
    }
}

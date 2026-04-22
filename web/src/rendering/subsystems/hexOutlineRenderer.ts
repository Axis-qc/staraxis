/**
 * @file hexOutlineRenderer.ts
 *
 * @description
 * 六边形星区轮廓渲染器。
 *
 * 作用：
 * - 根据低频缓存中的 `sectorCenters`（星区中心）构建可见星区的六边形边界线段喵。
 * - 使用 buildHexSegmentPositions 生成线段点集。
 *
 * @usage
 * - 在 WorldRenderManager 中注册：subsystems.push(new HexOutlineRenderer())
 * - update 时根据 frame.sectorCenters 和 frame.cullingAabb 生成可见轮廓。
 *
 * @provides
 * - **星区轮廓**：LineSegments 形式渲染。
 *
 * @api
 * - init(ctx): void
 * - update(ctx, frame): void
 * - dispose(ctx): void
 *
 * @important_notes
 * - 轮廓线现在直接使用世界坐标，不再减去 cameraWorldPosGU（相机世界坐标）喵。
 * - 当前实现为每次 update 重建 geometry（最小可用）；后续可做增量更新/缓存。
 * - 当前文件保留是因为六边形轮廓层尚未迁移到 layer 架构。
 */
import * as THREE from 'three'
import type { WorldRenderContext, WorldFrameState } from '../worldRenderManager'
import type { WorldRenderSubsystem } from './worldRenderSubsystem'
import { buildHexSegmentPositions } from '../hexSectorGeometry'
import { shouldRender } from './lodSystem'

export class HexOutlineRenderer implements WorldRenderSubsystem {
    private line: THREE.LineSegments | null = null
    private geometry: THREE.BufferGeometry | null = null
    private material: THREE.LineBasicMaterial | null = null

    private readonly COLORS = {
        OWNED_BY_SELF: 0x7fd3ff,
        OWNED_BY_OTHERS: 0xff4d4d,
        UNOCCUPIED: 0x444444,
    }

    private getNationColor(ownerId: string | null, selfNationId: string | null): number {
        if (!ownerId) return this.COLORS.UNOCCUPIED
        if (ownerId === selfNationId) return this.COLORS.OWNED_BY_SELF
        return this.COLORS.OWNED_BY_OTHERS
    }

    init(ctx: WorldRenderContext): void {
        this.geometry = new THREE.BufferGeometry()
        this.material = new THREE.LineBasicMaterial({
            vertexColors: true,
            transparent: true,
            opacity: 0.55,
        })
        this.line = new THREE.LineSegments(this.geometry, this.material)
        this.line.frustumCulled = false
        ctx.worldGroup.add(this.line)
    }

    update(ctx: WorldRenderContext, frame: WorldFrameState): void {
        if (!this.geometry || !this.line) return

        const hexLod = frame.lod.hexOutline
        if (!shouldRender(hexLod, false)) {
            this.line.visible = false
            return
        }
        this.line.visible = true

        const positions: number[] = []
        const colors: number[] = []
        const ownerMap = frame.sectorOwnerNationIdByCoord
        const selfNationId = frame.visibilityManager?.getCurrentNationId() || null

        for (const sector of frame.sectorCenters) {
            if (!isPointInAabb(sector, frame.cullingAabb)) continue

            const sectorKey = `${sector.q},${sector.r}`
            const ownerId = ownerMap[sectorKey] ?? null
            const color = new THREE.Color(this.getNationColor(ownerId, selfNationId))
            const hexSegments = buildHexSegmentPositions([{ x: sector.x, y: sector.y }])

            for (let i = 0; i < hexSegments.length; i += 3) {
                positions.push(hexSegments[i] ?? 0, hexSegments[i + 1] ?? 0, hexSegments[i + 2] ?? 0)
                colors.push(color.r, color.g, color.b)
            }
        }

        this.geometry.setAttribute('position', new THREE.Float32BufferAttribute(positions, 3))
        this.geometry.setAttribute('color', new THREE.Float32BufferAttribute(colors, 3))

        if (this.material) {
            this.material.opacity = 0.55 * hexLod.params.textureQuality
        }
    }

    dispose(ctx: WorldRenderContext): void {
        if (this.line) {
            ctx.worldGroup.remove(this.line)
            this.line = null
        }
        if (this.geometry) {
            this.geometry.dispose()
            this.geometry = null
        }
        if (this.material) {
            this.material.dispose()
            this.material = null
        }
    }
}

function isPointInAabb(
    point: { x: number; y: number },
    aabb: { minX: number; maxX: number; minY: number; maxY: number },
): boolean {
    return point.x >= aabb.minX && point.x <= aabb.maxX && point.y >= aabb.minY && point.y <= aabb.maxY
}

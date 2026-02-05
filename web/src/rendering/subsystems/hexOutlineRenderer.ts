/**
 * @file hexOutlineRenderer.ts
 *
 * @description
 * 六边形星区轮廓渲染器。
 *
 * 作用：
 * - 根据快照中的 sectorCenters，构建可见星区的六边形边界线段。
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
 * - 轮廓线使用相机局部坐标（减去 cameraWorldPosGU）。
 * - 当前实现为每次 update 重建 geometry（最小可用）；后续可做增量更新/缓存。
 */
import * as THREE from 'three'
import type { WorldRenderContext, WorldFrameState } from '../worldRenderManager'
import type { WorldRenderSubsystem } from './worldRenderSubsystem'
import { buildHexSegmentPositions } from '../hexSectorGeometry'

export class HexOutlineRenderer implements WorldRenderSubsystem {
    private line: THREE.LineSegments | null = null
    private geometry: THREE.BufferGeometry | null = null
    private material: THREE.LineBasicMaterial | null = null

    init(ctx: WorldRenderContext): void {
        this.geometry = new THREE.BufferGeometry()
        this.material = new THREE.LineBasicMaterial({ color: 0x7fd3ff, transparent: true, opacity: 0.55 })
        this.line = new THREE.LineSegments(this.geometry, this.material)
        this.line.frustumCulled = false
        ctx.worldGroup.add(this.line)
    }

    update(ctx: WorldRenderContext, frame: WorldFrameState): void {
        if (!this.geometry) return

        const visibleCentersCameraLocal: { x: number; y: number }[] = []

        for (const s of frame.sectorCenters) {
            if (isPointInAabb(s, frame.cullingAabb)) {
                visibleCentersCameraLocal.push({ x: s.x - ctx.cameraWorldPosGU.x, y: s.y - ctx.cameraWorldPosGU.y })
            }
        }

        const segPositions = buildHexSegmentPositions(visibleCentersCameraLocal)
        this.geometry.setAttribute('position', new THREE.BufferAttribute(segPositions, 3))
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

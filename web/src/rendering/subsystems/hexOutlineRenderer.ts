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
import { shouldRender } from './lodSystem'

export class HexOutlineRenderer implements WorldRenderSubsystem {
    private line: THREE.LineSegments | null = null
    private geometry: THREE.BufferGeometry | null = null
    private material: THREE.LineBasicMaterial | null = null

    // 默认颜色配置（后续可移入全局配置系统）喵
    private readonly COLORS = {
        OWNED_BY_SELF: 0x7fd3ff, // 本国：亮蓝色
        OWNED_BY_OTHERS: 0xff4d4d, // 他国：红色
        UNOCCUPIED: 0x444444      // 无人占领：深灰色
    }

    private getNationColor(ownerId: string | null, selfNationId: string | null): number {
        if (!ownerId) return this.COLORS.UNOCCUPIED
        if (ownerId === selfNationId) return this.COLORS.OWNED_BY_SELF
        return this.COLORS.OWNED_BY_OTHERS
    }

    init(ctx: WorldRenderContext): void {
        this.geometry = new THREE.BufferGeometry()
        this.material = new THREE.LineBasicMaterial({
            vertexColors: true, // 启用顶点着色以支持不同星区不同颜色喵
            transparent: true,
            opacity: 0.55
        })
        this.line = new THREE.LineSegments(this.geometry, this.material)
        this.line.frustumCulled = false
        ctx.worldGroup.add(this.line)
    }

    update(ctx: WorldRenderContext, frame: WorldFrameState): void {
        if (!this.geometry || !this.line) return

        const { lod } = frame
        const hexLod = lod.hexOutline

        if (!shouldRender(hexLod, false)) {
            this.line.visible = false
            return
        }
        this.line.visible = true

        const positions: number[] = []
        const colors: number[] = []
        const ownerMap = frame.snapshot?.realTimeWorldState?.sectorOwnerNationIdByCoord || {}

        // 获取当前玩家国家ID，用于颜色判定喵
        const selfNationId = frame.visibilityManager?.getCurrentNationId() || null

        for (const s of frame.sectorCenters) {
            if (isPointInAabb(s, frame.cullingAabb)) {
                const cameraLocalX = s.x - ctx.cameraWorldPosGU.x
                const cameraLocalY = s.y - ctx.cameraWorldPosGU.y

                // 获取该星区的颜色喵
                const sectorKey = `${s.q},${s.r}`
                const ownerId = ownerMap[sectorKey] ?? null
                const colorHex = this.getNationColor(ownerId, selfNationId)
                const color = new THREE.Color(colorHex)

                const hexSegs = buildHexSegmentPositions([{ x: cameraLocalX, y: cameraLocalY }])

                if (hexSegs) {
                    for (let i = 0; i < hexSegs.length; i += 3) {
                        positions.push(hexSegs[i] ?? 0, hexSegs[i + 1] ?? 0, hexSegs[i + 2] ?? 0)
                        colors.push(color.r, color.g, color.b)
                    }
                }
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

/**
 * @file planetRenderer.ts
 *
 * @description
 * 行星渲染器。
 *
 * 作用：
 * - 根据快照数据渲染行星实体。
 * - 使用 2D 精灵（sprite）渲染行星，展示表面纹理。
 * - 计算行星轨道位置（基于轨道参数）。
 * - 管理对象池，提高性能。
 *
 * @usage
 * - 在管理器中注册：subsystems.push(new PlanetRenderer())
 * - 管理器在 update 时传入 context 和 frameState。
 *
 * @provides
 * - **2D 行星精灵渲染**：使用表面贴图渲染平面精灵。
 * - **轨道位置计算**：基于轨道参数计算行星位置。
 * - **对象池**：复用 Three.js 对象，减少 GC 压力。
 *
 * @api
 * - init(ctx: WorldRenderContext): void
 * - update(ctx: WorldRenderContext, frame: WorldFrameState): void
 * - dispose(ctx: WorldRenderContext): void
 *
 * @important_notes
 * - 使用 2D 平面精灵 + 纹理渲染行星。
 * - 根据轨道参数计算行星位置，支持椭圆轨道。
 * - 只渲染可见范围内的行星，提高渲染效率。
 * - 使用对象池避免频繁创建/销毁 Three.js 对象。
 */
import * as THREE from 'three'
import type { WorldRenderContext, WorldFrameState } from '../worldRenderManager'
import type { WorldRenderSubsystem } from './worldRenderSubsystem'
import type { PlanetDetails } from '../../net/snapshotWs'

export class PlanetRenderer implements WorldRenderSubsystem {
    private planetSpritePool: THREE.Sprite[] = []
    private activePlanetSpritesByEntityId = new Map<number, THREE.Sprite>()
    private textureCache = new Map<string, THREE.Texture>()

    init(ctx: WorldRenderContext): void {
        void ctx // unused

        // 预创建一些精灵对象以避免运行时创建
        for (let i = 0; i < 20; i++) {
            const material = new THREE.SpriteMaterial({
                color: 0xffffff,
                sizeAttenuation: true,
            })
            const sprite = new THREE.Sprite(material)
            sprite.visible = false
            this.planetSpritePool.push(sprite)
        }
    }

    update(ctx: WorldRenderContext, frame: WorldFrameState): void {
        const { entitiesGroup } = ctx
        const { entitiesById, selectedIds, cullingAabb, showPlanets, totalDays } = frame

        const nextPlanetSpriteIds = new Set<number>()

        for (const entity of entitiesById.values()) {
            if (entity.entityType !== 'PLANET') continue

            const isSelected = selectedIds.has(entity.entityId)
            if (!showPlanets && !isSelected) continue

            const details = entity.details as PlanetDetails

            const orbitCenter = entitiesById.get(details.orbitCenterEntityId)
            if (!orbitCenter?.posWorldGU) continue

            const planetPos = computePlanetPosWorldGU({ details, orbitCenter, totalDays })
            if (!planetPos) continue

            if (!isSelected && !isPointInAabb(planetPos, cullingAabb)) {
                continue
            }

            nextPlanetSpriteIds.add(entity.entityId)

            // 2D 精灵渲染
            const radiusGU = details.radiusGU
            const size = radiusGU * 2

            let sprite = this.activePlanetSpritesByEntityId.get(entity.entityId)
            if (!sprite) {
                sprite = this.acquirePlanetSprite()
                this.activePlanetSpritesByEntityId.set(entity.entityId, sprite)
                entitiesGroup.add(sprite)
            }

            // 纹理加载
            const material = sprite.material as THREE.SpriteMaterial
            if (details.surfaceTexturePath && !material.map) {
                this.loadAndApplyTexture(material, details.surfaceTexturePath)
            }

            sprite.scale.set(size, size, 1)
            sprite.position.set(planetPos.x - ctx.cameraWorldPosGU.x, planetPos.y - ctx.cameraWorldPosGU.y, 0)
            sprite.visible = true
        }

        // 回收不可见的对象
        for (const [id, sprite] of this.activePlanetSpritesByEntityId.entries()) {
            if (!nextPlanetSpriteIds.has(id)) {
                this.activePlanetSpritesByEntityId.delete(id)
                this.releasePlanetSprite(sprite)
            }
        }
    }

    dispose(ctx: WorldRenderContext): void {
        void ctx // unused

        // 释放所有对象
        for (const sprite of this.planetSpritePool) {
            (sprite.material as THREE.Material).dispose()
        }
        for (const sprite of this.activePlanetSpritesByEntityId.values()) {
            (sprite.material as THREE.Material).dispose()
        }

        this.planetSpritePool = []
        this.activePlanetSpritesByEntityId.clear()
    }

    private acquirePlanetSprite(): THREE.Sprite {
        const sprite = this.planetSpritePool.pop()
        if (sprite) {
            sprite.visible = true
            return sprite
        }

        const material = new THREE.SpriteMaterial({
            color: 0xffffff,
            sizeAttenuation: true,
        })
        const s = new THREE.Sprite(material)
        s.visible = true
        return s
    }

    private releasePlanetSprite(sprite: THREE.Sprite): void {
        const material = sprite.material as THREE.SpriteMaterial
        material.map = null
        material.needsUpdate = true

        sprite.visible = false
        sprite.parent?.remove(sprite)
        this.planetSpritePool.push(sprite)
    }

    private loadAndApplyTexture(material: THREE.SpriteMaterial, surfaceTexturePath: string): void {
        if (this.textureCache.has(surfaceTexturePath)) {
            material.map = this.textureCache.get(surfaceTexturePath)!
            material.needsUpdate = true
            return
        }

        const loader = new THREE.TextureLoader()
        loader.load(
            `/assets/${surfaceTexturePath}`,
            (texture) => {
                texture.anisotropy = 16
                this.textureCache.set(surfaceTexturePath, texture)
                material.map = texture
                material.needsUpdate = true
            },
            undefined,
            () => {
                // 加载失败则保持纯白
            },
        )
    }
}

function isPointInAabb(
    point: { x: number; y: number },
    aabb: { minX: number; maxX: number; minY: number; maxY: number },
): boolean {
    return point.x >= aabb.minX && point.x <= aabb.maxX && point.y >= aabb.minY && point.y <= aabb.maxY
}

function computePlanetPosWorldGU(args: {
    details: PlanetDetails
    orbitCenter: any
    totalDays: number
}): { x: number; y: number } | null {
    const { details, orbitCenter, totalDays } = args
    if (!orbitCenter.posWorldGU) return null

    const meanAnomaly = (Number(details.meanAnomalyDegAtEpoch ?? 0) * Math.PI) / 180
    const periodDays = Number(details.orbitalPeriodDays ?? 0)
    if (!Number.isFinite(periodDays) || periodDays <= 0) return null

    const angle = meanAnomaly + (totalDays / periodDays) * 2 * Math.PI

    const a = Number(details.semiMajorAxisGU ?? 0)
    const e = Number(details.eccentricity ?? 0)
    const b = a * Math.sqrt(Math.max(0, 1 - e ** 2))

    const periapsisArgRad = (Number(details.periapsisArgDeg ?? 0) * Math.PI) / 180

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

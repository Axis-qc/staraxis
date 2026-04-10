/**
 * @file starRenderer.ts
 *
 * @description
 * 恒星渲染器适配层版本，实现层渲染器接口喵。
 *
 * @important_notes
 * - 基于现有StarRenderer重构，适配层架构喵。
 * - 不直接操作entitiesGroup，而是通过层group喵。
 */

import * as THREE from 'three'
import type { WorldRenderContext, WorldFrameState } from '../../../worldRenderManager'
import type { StarDetails } from '../../../../net/snapshotWs'
import { shouldRender, getLodSize, shouldShowEffects } from '../../../subsystems/lodSystem'

export class LayerStarRenderer {
    private starSpritePool: THREE.Sprite[] = []
    private activeStarSpritesByEntityId = new Map<number, THREE.Sprite>()
    private fallbackCircleTexture: THREE.CanvasTexture | null = null

    private layerGroup: THREE.Group

    constructor(layerGroup: THREE.Group) {
        this.layerGroup = layerGroup
    }

    init(): void {
        this.fallbackCircleTexture = this.createCircleTexture()

        // 预创建精灵对象池
        for (let i = 0; i < 50; i++) {
            const material = new THREE.SpriteMaterial({
                color: 0xffffff,
                sizeAttenuation: true,
            })
            const sprite = new THREE.Sprite(material)
            sprite.visible = false
            this.starSpritePool.push(sprite)
        }
    }

    update(ctx: WorldRenderContext, frame: WorldFrameState): void {
        const { entitiesById, selectedIds, cullingAabb, lod } = frame
        const starLod = lod.star

        // 快速路径：LOD完全隐藏
        if (!starLod.visible) {
            this.releaseAllSprites()
            return
        }

        // 第一遍：检查哪些实体需要渲染
        const visibleEntityIds = new Set<number>()

        for (const entity of entitiesById.values()) {
            if (entity.entityType !== 'STAR') continue

            const isSelected = selectedIds.has(entity.entityId)

            const shouldBeVisible = shouldRender(starLod, isSelected) &&
                entity.posWorldGU &&
                (isSelected || this.isPointInAabb(entity.posWorldGU, cullingAabb))

            if (shouldBeVisible) {
                const details = entity.details as StarDetails | null
                if (details) {
                    visibleEntityIds.add(entity.entityId)
                }
            }
        }

        // 回收不在可见列表中的对象
        this.releaseInvisibleSprites(visibleEntityIds)

        // 第二遍：更新可见实体的渲染数据
        for (const entityId of visibleEntityIds) {
            const entity = entitiesById.get(entityId)!
            const details = entity.details as StarDetails
            this.updateStarSprite(entityId, entity, details, ctx, starLod, selectedIds.has(entityId))
        }
    }

    private updateStarSprite(
        entityId: number,
        entity: any,
        details: StarDetails,
        ctx: WorldRenderContext,
        starLod: any,
        isSelected: boolean
    ): void {
        // 计算实体在屏幕上的实际像素大小
        const radiusGU = details.radiusGU
        const diameterPx = (radiusGU * 2) / ctx.zoom.value
        const MIN_TEXTURE_PIXEL_SIZE = 10

        const useRealTexture = diameterPx >= MIN_TEXTURE_PIXEL_SIZE &&
                               starLod.params.textureQuality >= 0.5

        let size: number
        if (useRealTexture) {
            size = getLodSize(starLod, isSelected, radiusGU * 2)
        } else {
            size = MIN_TEXTURE_PIXEL_SIZE * ctx.zoom.value
        }

        let sprite = this.activeStarSpritesByEntityId.get(entityId)
        if (!sprite) {
            sprite = this.acquireStarSprite()
            this.activeStarSpritesByEntityId.set(entityId, sprite)
            this.layerGroup.add(sprite)
        }

        const material = sprite.material as THREE.SpriteMaterial

        if (useRealTexture) {
            if (material.map === this.fallbackCircleTexture) {
                material.map = null
                material.needsUpdate = true
            }
            if (details.surfaceTexturePath && (!material.map || !material.map.image)) {
                this.loadAndApplyTexture(sprite, material, details.surfaceTexturePath, ctx)
            }
            material.sizeAttenuation = true
        } else {
            if (this.fallbackCircleTexture && material.map !== this.fallbackCircleTexture) {
                material.map = this.fallbackCircleTexture
                material.needsUpdate = true
            }
            material.sizeAttenuation = false
        }

        // 根据表面温度设置颜色
        const temperatureK = details.temperatureK
        material.color.set(this.getStarColorByTemperature(temperatureK))

        const showEffects = shouldShowEffects(starLod, isSelected)
        material.opacity = showEffects ? 1.0 : 0.8

        sprite.scale.set(size, size, 1)
        sprite.position.set(
            entity.posWorldGU!.x - ctx.cameraWorldPosGU.x,
            entity.posWorldGU!.y - ctx.cameraWorldPosGU.y,
            0
        )
        sprite.visible = true
    }

    private createCircleTexture(): THREE.CanvasTexture {
        const canvas = document.createElement('canvas')
        canvas.width = 64
        canvas.height = 64
        const ctx = canvas.getContext('2d')!

        ctx.clearRect(0, 0, 64, 64)

        const centerX = 32
        const centerY = 32
        const radius = 30

        const gradient = ctx.createRadialGradient(centerX, centerY, 0, centerX, centerY, radius)
        gradient.addColorStop(0, 'rgba(255, 255, 255, 1)')
        gradient.addColorStop(0.8, 'rgba(255, 255, 255, 1)')
        gradient.addColorStop(1, 'rgba(255, 255, 255, 0.8)')

        ctx.beginPath()
        ctx.arc(centerX, centerY, radius, 0, Math.PI * 2)
        ctx.fillStyle = gradient
        ctx.fill()

        const texture = new THREE.CanvasTexture(canvas)
        texture.needsUpdate = true
        return texture
    }

    private acquireStarSprite(): THREE.Sprite {
        const sprite = this.starSpritePool.pop()
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

    private releaseStarSprite(sprite: THREE.Sprite): void {
        const material = sprite.material as THREE.SpriteMaterial
        material.map = null
        material.needsUpdate = true

        sprite.visible = false
        sprite.parent?.remove(sprite)
        this.starSpritePool.push(sprite)
    }

    private releaseAllSprites(): void {
        for (const [id, sprite] of this.activeStarSpritesByEntityId.entries()) {
            this.activeStarSpritesByEntityId.delete(id)
            this.releaseStarSprite(sprite)
        }
    }

    private releaseInvisibleSprites(visibleEntityIds: Set<number>): void {
        for (const [id, sprite] of this.activeStarSpritesByEntityId.entries()) {
            if (!visibleEntityIds.has(id)) {
                this.activeStarSpritesByEntityId.delete(id)
                this.releaseStarSprite(sprite)
            }
        }
    }

    private loadAndApplyTexture(
        sprite: THREE.Sprite,
        material: THREE.SpriteMaterial,
        surfaceTexturePath: string,
        ctx: WorldRenderContext
    ): void {
        if (material.map && material.map.image) return

        ctx.getTexture(surfaceTexturePath)
            .then((texture) => {
                material.map = texture
                material.needsUpdate = true
                sprite.visible = true
            })
            .catch((error) => {
                console.error(`Failed to load star texture: ${surfaceTexturePath}`, error)
                sprite.visible = true
            })
    }

    private getStarColorByTemperature(temperatureK: number): THREE.Color {
        if (temperatureK >= 30000) return new THREE.Color(0x9bb0ff)
        else if (temperatureK >= 10000) return new THREE.Color(0xa6c5ff)
        else if (temperatureK >= 7500) return new THREE.Color(0xcad7ff)
        else if (temperatureK >= 6000) return new THREE.Color(0xf8f7ff)
        else if (temperatureK >= 5200) return new THREE.Color(0xfff4ea)
        else if (temperatureK >= 3700) return new THREE.Color(0xffd2a1)
        else if (temperatureK >= 2400) return new THREE.Color(0xffb347)
        else return new THREE.Color(0xff6b3d)
    }

    private isPointInAabb(
        point: { x: number; y: number },
        aabb: { minX: number; maxX: number; minY: number; maxY: number },
    ): boolean {
        return point.x >= aabb.minX && point.x <= aabb.maxX && point.y >= aabb.minY && point.y <= aabb.maxY
    }

    dispose(): void {
        if (this.fallbackCircleTexture) {
            this.fallbackCircleTexture.dispose()
            this.fallbackCircleTexture = null
        }

        for (const sprite of this.starSpritePool) {
            (sprite.material as THREE.Material).dispose()
        }
        for (const sprite of this.activeStarSpritesByEntityId.values()) {
            (sprite.material as THREE.Material).dispose()
        }

        this.starSpritePool = []
        this.activeStarSpritesByEntityId.clear()
    }
}
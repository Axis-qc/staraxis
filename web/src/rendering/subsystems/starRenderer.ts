/**
 * @deprecated 将迁移到分层架构中的CelestialLayer喵。
 * 新代码请使用 src/rendering/layers/celestial/renderers/starRenderer.ts 喵。
 */

/**
 * @file starRenderer.ts
 *
 * @description
 * 恒星渲染器。
 *
 * 作用：
 * - 根据快照数据渲染恒星实体。
 * - 使用 2D 精灵（sprite）渲染恒星，展示表面纹理。
 * - 使用快照中的表面温度决定恒星颜色。
 * - 管理对象池，提高性能。
 *
 * @usage
 * - 在管理器中注册：subsystems.push(new StarRenderer())
 * - 管理器在 update 时传入 context 和 frameState。
 *
 * @provides
 * - **2D 恒星精灵渲染**：使用表面贴图渲染平面精灵。
 * - **对象池**：复用 Three.js 对象，减少 GC 压力。
 *
 * @api
 * - init(ctx: WorldRenderContext): void
 * - update(ctx: WorldRenderContext, frame: WorldFrameState): void
 * - dispose(ctx: WorldRenderContext): void
 *
 * @important_notes
 * - 使用 2D 平面精灵 + 纹理渲染恒星。
 * - 根据表面温度显示不同颜色。
 * - 只渲染可见范围内的恒星，提高渲染效率。
 * - 使用对象池避免频繁创建/销毁 Three.js 对象。
 */
import * as THREE from 'three'
import type { WorldRenderContext, WorldFrameState } from '../worldRenderManager'
import type { WorldRenderSubsystem } from './worldRenderSubsystem'
import type { StarDetails } from '../../net/snapshotWs'
import { shouldRender, getLodSize, shouldShowEffects } from './lodSystem'

export class StarRenderer implements WorldRenderSubsystem {
    private starSpritePool: THREE.Sprite[] = []
    private activeStarSpritesByEntityId = new Map<number, THREE.Sprite>()
    private context: WorldRenderContext | null = null
    private fallbackCircleTexture: THREE.CanvasTexture | null = null

    init(ctx: WorldRenderContext): void {
        this.context = ctx

        // 创建远距离显示用的圆形后备纹理
        this.fallbackCircleTexture = this.createCircleTexture()

        // 预创建一些精灵对象以避免运行时创建
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

    /**
     * 创建圆形纹理用于远距离显示
     */
    private createCircleTexture(): THREE.CanvasTexture {
        const canvas = document.createElement('canvas')
        canvas.width = 64
        canvas.height = 64
        const ctx = canvas.getContext('2d')!
        
        // 清除画布（透明）
        ctx.clearRect(0, 0, 64, 64)
        
        // 绘制白色圆形带轻微边缘羽化
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

    update(ctx: WorldRenderContext, frame: WorldFrameState): void {
        const { entitiesGroup } = ctx
        const { entitiesById, selectedIds, cullingAabb, lod } = frame

        const starLod = lod.star

        // 快速路径：如果LOD完全隐藏（zoom超过阈值且非选中状态），直接回收所有对象并返回
        // 不进行任何实体遍历
        if (!starLod.visible) {
            for (const [id, sprite] of this.activeStarSpritesByEntityId.entries()) {
                this.activeStarSpritesByEntityId.delete(id)
                this.releaseStarSprite(sprite)
            }
            return
        }

        // 第一遍：检查哪些实体需要渲染
        const visibleEntityIds = new Set<number>()
        
        for (const entity of entitiesById.values()) {
            if (entity.entityType !== 'STAR') continue

            const isSelected = selectedIds.has(entity.entityId)
            
            // 快速剔除检查：LOD不可见或不在镜头内
            const shouldBeVisible = shouldRender(starLod, isSelected) && 
                entity.posWorldGU && 
                (isSelected || isPointInAabb(entity.posWorldGU, cullingAabb))
            
            if (shouldBeVisible) {
                const details = entity.details as StarDetails | null
                if (details) {
                    visibleEntityIds.add(entity.entityId)
                }
            }
        }

        // 回收不在可见列表中的对象（立即释放回对象池）
        for (const [id, sprite] of this.activeStarSpritesByEntityId.entries()) {
            if (!visibleEntityIds.has(id)) {
                this.activeStarSpritesByEntityId.delete(id)
                this.releaseStarSprite(sprite)
            }
        }

        // 第二遍：只更新可见实体的渲染数据
        for (const entityId of visibleEntityIds) {
            const entity = entitiesById.get(entityId)!
            const details = entity.details as StarDetails
            const isSelected = selectedIds.has(entityId)

            // 计算实体在屏幕上的实际像素大小
            // 实体直径(GU) / zoom = 屏幕像素
            const radiusGU = details.radiusGU
            const diameterPx = (radiusGU * 2) / ctx.zoom.value
            const MIN_TEXTURE_PIXEL_SIZE = 10
            
            // 动态决定是否使用真实纹理：
            // - 屏幕像素 >= 10px：使用真实纹理
            // - 屏幕像素 < 10px：使用圆形纹理（避免加载大尺寸纹理显示成小点）
            const useRealTexture = diameterPx >= MIN_TEXTURE_PIXEL_SIZE && 
                                   starLod.params.textureQuality >= 0.5
            
            let size: number
            if (useRealTexture) {
                // 使用真实纹理，世界单位大小
                size = getLodSize(starLod, isSelected, radiusGU * 2)
            } else {
                // 使用圆形纹理，固定屏幕像素大小
                void isSelected
                size = MIN_TEXTURE_PIXEL_SIZE * ctx.zoom.value
            }

            let sprite = this.activeStarSpritesByEntityId.get(entityId)
            if (!sprite) {
                sprite = this.acquireStarSprite()
                this.activeStarSpritesByEntityId.set(entityId, sprite)
                entitiesGroup.add(sprite)
            }

            const material = sprite.material as THREE.SpriteMaterial
            
            if (useRealTexture) {
                // 加载真实纹理
                // 如果之前使用的是圆形纹理，先清理（回收）
                if (material.map === this.fallbackCircleTexture) {
                    material.map = null
                    material.needsUpdate = true
                }
                if (details.surfaceTexturePath && (!material.map || !material.map.image)) {
                    this.loadAndApplyTexture(sprite, material, details.surfaceTexturePath, true)
                }
                material.sizeAttenuation = true
            } else {
                // 使用圆形纹理
                if (this.fallbackCircleTexture && material.map !== this.fallbackCircleTexture) {
                    material.map = this.fallbackCircleTexture
                    material.needsUpdate = true
                }
                material.sizeAttenuation = false
            }

            // 根据表面温度设置颜色
            const temperatureK = details.temperatureK
            material.color.set(this.getStarColorByTemperature(temperatureK))

            // 根据LOD决定是否显示特效（光晕等）
            const showEffects = shouldShowEffects(starLod, isSelected)
            material.opacity = showEffects ? 1.0 : 0.8

            sprite.scale.set(size, size, 1)
            sprite.position.set(entity.posWorldGU!.x - ctx.cameraWorldPosGU.x, entity.posWorldGU!.y - ctx.cameraWorldPosGU.y, 0)

            // 显示逻辑：远距离使用圆形纹理，始终可见
            sprite.visible = true
        }
    }

    dispose(ctx: WorldRenderContext): void {
        void ctx // unused

        // 释放后备纹理
        if (this.fallbackCircleTexture) {
            this.fallbackCircleTexture.dispose()
            this.fallbackCircleTexture = null
        }

        // 释放所有对象
        for (const sprite of this.starSpritePool) {
            (sprite.material as THREE.Material).dispose()
        }
        for (const sprite of this.activeStarSpritesByEntityId.values()) {
            (sprite.material as THREE.Material).dispose()
        }

        this.starSpritePool = []
        this.activeStarSpritesByEntityId.clear()
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

    private loadAndApplyTexture(
        sprite: THREE.Sprite,
        material: THREE.SpriteMaterial,
        surfaceTexturePath: string,
        highQuality: boolean = true,
    ): void {
        // 如果材质已经有纹理且质量满足要求，不再加载
        if (material.map && (highQuality || !material.map.image)) return

        if (!this.context) return

        // 使用纹理管理器统一加载纹理
        // TODO: 未来可以支持加载不同质量的纹理
        this.context.getTexture(surfaceTexturePath)
            .then((texture) => {
                material.map = texture
                material.needsUpdate = true
                // 纹理加载完成后显示 sprite
                sprite.visible = true
            })
            .catch((error) => {
                console.error(`Failed to load star texture: ${surfaceTexturePath}`, error)
                // 加载失败则保持纯白，但仍然显示（避免完全不可见）
                sprite.visible = true
            })
    }

    private getStarColorByTemperature(temperatureK: number): THREE.Color {
        // 基于温度的颜色映射
        // 参考：http://www.vendian.org/mncharity/dir3/starcolor/
        if (temperatureK >= 30000) {
            return new THREE.Color(0x9bb0ff) // 蓝白色
        } else if (temperatureK >= 10000) {
            return new THREE.Color(0xa6c5ff) // 蓝白色
        } else if (temperatureK >= 7500) {
            return new THREE.Color(0xcad7ff) // 白色
        } else if (temperatureK >= 6000) {
            return new THREE.Color(0xf8f7ff) // 淡黄色
        } else if (temperatureK >= 5200) {
            return new THREE.Color(0xfff4ea) // 黄色
        } else if (temperatureK >= 3700) {
            return new THREE.Color(0xffd2a1) // 橙黄色
        } else if (temperatureK >= 2400) {
            return new THREE.Color(0xffb347) // 橙色
        } else {
            return new THREE.Color(0xff6b3d) // 红色
        }
    }
}

function isPointInAabb(
    point: { x: number; y: number },
    aabb: { minX: number; maxX: number; minY: number; maxY: number },
): boolean {
    return point.x >= aabb.minX && point.x <= aabb.maxX && point.y >= aabb.minY && point.y <= aabb.maxY
}

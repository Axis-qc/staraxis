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
import { shouldRender, getLodSize, shouldShowEffects } from './lodSystem'

export class PlanetRenderer implements WorldRenderSubsystem {
    private planetSpritePool: THREE.Sprite[] = []
    private activePlanetSpritesByEntityId = new Map<number, THREE.Sprite>()
    private context: WorldRenderContext | null = null
    private fallbackCircleTexture: THREE.CanvasTexture | null = null
    
    // 移动轨迹（拖尾）相关
    private trailLinePool: THREE.Line[] = []
    private activeTrailsByEntityId = new Map<number, THREE.Line>()
    private positionHistory = new Map<number, Array<{ x: number; y: number }>>()

    init(ctx: WorldRenderContext): void {
        this.context = ctx

        // 创建远距离显示用的圆形后备纹理
        this.fallbackCircleTexture = this.createCircleTexture()

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
        const { entitiesById, selectedIds, cullingAabb, lod, totalDays } = frame

        const planetLod = lod.planet

        // 快速路径：如果LOD完全隐藏（zoom超过阈值且非选中状态），直接回收所有对象并返回
        // 不进行任何实体遍历或轨道计算
        if (!planetLod.visible) {
            // 回收所有活跃对象
            for (const [id, sprite] of this.activePlanetSpritesByEntityId.entries()) {
                this.activePlanetSpritesByEntityId.delete(id)
                this.releasePlanetSprite(sprite)
            }
            return
        }

        // 第一遍：检查哪些实体需要渲染
        const visibleEntityIds = new Set<number>()

        for (const entity of entitiesById.values()) {
            if (entity.entityType !== 'PLANET') continue

            const isSelected = selectedIds.has(entity.entityId)
            
            // 快速检查：LOD不可见（未选中且超过隐藏阈值）
            if (!shouldRender(planetLod, isSelected)) continue

            const details = entity.details as PlanetDetails
            const orbitCenter = entitiesById.get(details.orbitCenterEntityId)
            if (!orbitCenter?.posWorldGU) continue

            const planetPos = computePlanetPosWorldGU({ details, orbitCenter, totalDays })
            if (!planetPos) continue

            // 剔除检查
            if (!isSelected && !isPointInAabb(planetPos, cullingAabb)) {
                continue
            }

            visibleEntityIds.add(entity.entityId)
        }

        // 回收不在可见列表中的对象（立即释放回对象池）
        for (const [id, sprite] of this.activePlanetSpritesByEntityId.entries()) {
            if (!visibleEntityIds.has(id)) {
                this.activePlanetSpritesByEntityId.delete(id)
                this.releasePlanetSprite(sprite)
            }
        }

        // 第二遍：只更新可见实体的渲染数据
        for (const entityId of visibleEntityIds) {
            const entity = entitiesById.get(entityId)
            if (!entity) continue
            
            const details = entity.details as PlanetDetails
            const orbitCenter = entitiesById.get(details.orbitCenterEntityId)
            if (!orbitCenter?.posWorldGU) continue
            
            // 重新计算当前位置（确保使用最新的 totalDays）
            const planetPos = computePlanetPosWorldGU({ details, orbitCenter, totalDays })
            if (!planetPos) continue
            
            const isSelected = selectedIds.has(entityId)

            // 计算实体在屏幕上的实际像素大小
            // 实体直径(GU) / zoom = 屏幕像素
            const radiusGU = details.radiusGU
            const diameterPx = (radiusGU * 2) / ctx.zoom.value
            const MIN_TEXTURE_PIXEL_SIZE = 10
            
            // 动态决定是否使用真实纹理：
            // - 屏幕像素 >= 10px：使用真实纹理
            // - 屏幕像素 < 10px：使用圆形纹理（避免加载大尺寸纹理显示成小点）
            const useRealTexture = diameterPx >= MIN_TEXTURE_PIXEL_SIZE
            
            let size: number
            if (useRealTexture) {
                // 使用真实纹理，世界单位大小
                size = getLodSize(planetLod, isSelected, radiusGU * 2)
            } else {
                // 使用圆形纹理，固定屏幕像素大小
                void isSelected
                size = MIN_TEXTURE_PIXEL_SIZE * ctx.zoom.value
            }

            let sprite = this.activePlanetSpritesByEntityId.get(entityId)
            if (!sprite) {
                sprite = this.acquirePlanetSprite()
                this.activePlanetSpritesByEntityId.set(entityId, sprite)
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
                    this.loadAndApplyTexture(material, details.surfaceTexturePath, true)
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

            // 透明度计算：
            // - 使用真实纹理时：根据LOD params
            // - 使用圆形纹理时：根据zoom计算淡出，1,000开始变淡，100,000完全透明
            let opacity: number
            if (useRealTexture) {
                const showEffects = shouldShowEffects(planetLod, isSelected)
                opacity = showEffects ? 1.0 : 0.8
            } else {
                // 圆形纹理淡出：1,000->1.0, 100,000->0.0
                const FADE_START = 1_000
                const FADE_END = 100_000
                const zoom = ctx.zoom.value
                if (zoom >= FADE_END) {
                    opacity = 0
                } else if (zoom <= FADE_START) {
                    opacity = 1
                } else {
                    // 线性插值
                    opacity = 1 - (zoom - FADE_START) / (FADE_END - FADE_START)
                }
            }
            material.opacity = opacity

            sprite.scale.set(size, size, 1)
            sprite.position.set(planetPos.x - ctx.cameraWorldPosGU.x, planetPos.y - ctx.cameraWorldPosGU.y, 0)
            sprite.visible = true
            
            // 更新并渲染移动轨迹（拖尾）
            this.updateTrail(entityId, planetPos, ctx, planetLod, isSelected)
        }
        
        // 回收不可见实体的轨迹
        for (const [id, line] of this.activeTrailsByEntityId.entries()) {
            if (!visibleEntityIds.has(id)) {
                this.activeTrailsByEntityId.delete(id)
                this.positionHistory.delete(id)
                this.releaseTrailLine(line)
            }
        }
    }
    
    /**
     * 更新并渲染行星移动轨迹（拖尾）
     */
    private updateTrail(
        entityId: number,
        currentPos: { x: number; y: number },
        ctx: WorldRenderContext,
        planetLod: import('./lodSystem').EntityLodState,
        isSelected: boolean
    ): void {
        const { entitiesGroup } = ctx
        const TRAIL_LENGTH = 15  // 拖尾长度（历史帧数）
        const TRAIL_OPACITY_BASE = 0.4  // 基础透明度
        
        // 更新位置历史
        let history = this.positionHistory.get(entityId)
        if (!history) {
            history = []
            this.positionHistory.set(entityId, history)
        }
        
        // 添加当前位置到历史
        history.push({ x: currentPos.x, y: currentPos.y })
        
        // 限制历史长度
        if (history.length > TRAIL_LENGTH) {
            history.shift()
        }
        
        // 历史点太少时不渲染
        if (history.length < 2) return
        
        // 获取或创建轨迹线
        let trailLine = this.activeTrailsByEntityId.get(entityId)
        if (!trailLine) {
            trailLine = this.acquireTrailLine()
            this.activeTrailsByEntityId.set(entityId, trailLine)
            entitiesGroup.add(trailLine)
        }
        
        // 计算轨迹透明度 - 基于 zoom 值而非行星LOD
        // zoom < 1,000: 正常显示
        // zoom > 1,000: 开始淡出
        // zoom > 100,000: 完全隐藏
        const zoom = ctx.zoom.value
        const FADE_START = 1_000
        const FADE_END = 100_000
        
        let trailOpacity: number
        if (zoom < FADE_START) {
            // zoom < 1,000 不消失
            trailOpacity = TRAIL_OPACITY_BASE
        } else if (zoom >= FADE_END) {
            // zoom >= 100,000 完全隐藏
            trailOpacity = 0
        } else {
            // 1,000 - 100,000 之间线性淡出
            trailOpacity = TRAIL_OPACITY_BASE * (1 - (zoom - FADE_START) / (FADE_END - FADE_START))
        }
        
        // 选中时增加透明度
        if (isSelected && trailOpacity > 0) {
            trailOpacity = Math.min(1.0, trailOpacity * 1.5)
        }
        
        // 完全透明时隐藏
        if (trailOpacity <= 0.01) {
            trailLine.visible = false
            return
        }
        
        // 更新轨迹几何体
        const points: THREE.Vector3[] = []
        for (const pos of history) {
            points.push(new THREE.Vector3(
                pos.x - ctx.cameraWorldPosGU.x,
                pos.y - ctx.cameraWorldPosGU.y,
                -0.1  // 稍微在行星后面
            ))
        }
        
        trailLine.geometry.setFromPoints(points)
        
        // 更新材质透明度
        const material = trailLine.material as THREE.LineBasicMaterial
        material.opacity = trailOpacity
        material.transparent = true
        
        trailLine.visible = true
    }

    dispose(ctx: WorldRenderContext): void {
        void ctx // unused

        // 释放后备纹理
        if (this.fallbackCircleTexture) {
            this.fallbackCircleTexture.dispose()
            this.fallbackCircleTexture = null
        }

        // 释放所有行星精灵
        for (const sprite of this.planetSpritePool) {
            (sprite.material as THREE.Material).dispose()
        }
        for (const sprite of this.activePlanetSpritesByEntityId.values()) {
            (sprite.material as THREE.Material).dispose()
        }

        // 释放所有轨迹线
        for (const line of this.trailLinePool) {
            line.geometry.dispose()
            ;(line.material as THREE.Material).dispose()
        }
        for (const line of this.activeTrailsByEntityId.values()) {
            line.geometry.dispose()
            ;(line.material as THREE.Material).dispose()
        }

        this.planetSpritePool = []
        this.activePlanetSpritesByEntityId.clear()
        this.trailLinePool = []
        this.activeTrailsByEntityId.clear()
        this.positionHistory.clear()
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
    
    private acquireTrailLine(): THREE.Line {
        const line = this.trailLinePool.pop()
        if (line) {
            line.visible = true
            return line
        }
        
        const geometry = new THREE.BufferGeometry()
        const material = new THREE.LineBasicMaterial({
            color: 0x888888,  // 灰色轨迹
            transparent: true,
            opacity: 0.4,
            depthTest: false,
            depthWrite: false,
            linewidth: 3,  // 线宽 3 像素（注意：在某些平台可能被限制为 1）
        })
        const l = new THREE.Line(geometry, material)
        l.visible = true
        l.frustumCulled = false
        return l
    }
    
    private releaseTrailLine(line: THREE.Line): void {
        line.visible = false
        line.parent?.remove(line)
        this.trailLinePool.push(line)
    }

    private loadAndApplyTexture(
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
            })
            .catch((error) => {
                console.error(`Failed to load planet texture: ${surfaceTexturePath}`, error)
                // 加载失败则保持纯白
            })
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

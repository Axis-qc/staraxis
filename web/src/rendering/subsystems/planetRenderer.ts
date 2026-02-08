/**
 * @file planetRenderer.ts
 *
 * @description
 * 行星渲染器。
 *
 * 作用：
 * - 根据快照数据渲染行星实体。
 * - 使用 2D 精灵（sprite）渲染行星，展示表面纹理。
 * - 计算行星轨道位置（通过 ctx.getEntityWorldPosGU）。
 * - 使用基于 Mesh 的拖尾（Trail）效果，支持固定屏幕像素宽度与渐变。
 * - 管理对象池，提高性能。
 *
 * @usage
 * - 在管理器中注册：subsystems.push(new PlanetRenderer())
 * - 管理器在 update 时传入 context 和 frameState.
 *
 * @provides
 * - **2D 行星精灵渲染**：使用表面贴图渲染平面精灵。
 * - **Mesh 拖尾**：高性能 quad strip 拖尾，支持渐变透明。
 * - **对象池**：复用 Three.js 对象，减少 GC 压力。
 *
 * @api
 * - init(ctx: WorldRenderContext): void
 * - update(ctx: WorldRenderContext, frame: WorldFrameState): void
 * - dispose(ctx: WorldRenderContext): void
 *
 * @important_notes
 * - 使用 2D 平面精灵 + 纹理渲染行星。
 * - 拖尾使用基于 Mesh 的三角形带实现，确保在不同缩放级别下保持 3px 宽度。
 * - 轨道位置完全由 ctx.getEntityWorldPosGU 提供，以确保与选择框等系统绝对同步。
 * - 拖尾逻辑：每游戏分钟采样一次，最多保留 1000 点；进入视野开始绘制，离开视野立即清空。
 * - 使用对象池避免频繁创建/销毁 Three.js 对象。
 */
import * as THREE from 'three'
import type { WorldRenderContext, WorldFrameState } from '../worldRenderManager'
import type { WorldRenderSubsystem } from './worldRenderSubsystem'
import type { PlanetDetails } from '../../net/snapshotWs'
import { shouldRender, getLodSize, shouldShowEffects } from './lodSystem'

const TRAIL_BASE_COLOR = new THREE.Color(0xffffff)

// 拖尾 Shader 定义
const TRAIL_VERTEX_SHADER = `
    attribute float aAlpha;
    varying float vAlpha;
    void main() {
        vAlpha = aAlpha;
        gl_Position = projectionMatrix * modelViewMatrix * vec4(position, 1.0);
    }
`
const TRAIL_FRAGMENT_SHADER = `
    uniform vec3 uColor;
    uniform float uOpacity;
    varying float vAlpha;
    void main() {
        gl_FragColor = vec4(uColor, vAlpha * uOpacity);
    }
`

export class PlanetRenderer implements WorldRenderSubsystem {
    private planetSpritePool: THREE.Sprite[] = []
    private activePlanetSpritesByEntityId = new Map<number, THREE.Sprite>()
    private context: WorldRenderContext | null = null
    private fallbackCircleTexture: THREE.CanvasTexture | null = null

    // 移动轨迹（拖尾）相关 - 使用单个 Mesh (三角形带) + Shader 实现真正的 Alpha 渐隐
    private trailMeshPool: THREE.Mesh[] = []
    private activeTrailsByEntityId = new Map<number, THREE.Mesh>()

    // 拖尾相关常量
    private static readonly MAX_TRAIL_POINTS = 1000 // 最大采样点数
    private positionHistory = new Map<number, Array<{ x: number; y: number }>>()
    private lastSampleMinuteByEntityId = new Map<number, number>() // 记录每个行星上次采样的分钟数 (totalDays * 1440)

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
        if (!planetLod.visible) {
            // 回收所有活跃对象并清空状态
            this.clearAllTrails()
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

            // 统一通过 context 获取世界坐标位置，确保与选框等系统绝对同步
            const planetPos = ctx.getEntityWorldPosGU(entity.entityId)
            if (!planetPos) continue

            // 剔除检查（视口外不渲染）
            if (!isSelected && !isPointInAabb(planetPos, cullingAabb)) {
                continue
            }

            visibleEntityIds.add(entity.entityId)
        }

        // 回收不在可见列表中的对象
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
            const isSelected = selectedIds.has(entityId)
            const planetPos = ctx.getEntityWorldPosGU(entityId)
            if (!planetPos) continue

            // 计算实体在屏幕上的实际像素大小
            const radiusGU = details.radiusGU
            const diameterPx = (radiusGU * 2) / ctx.zoom.value
            const MIN_TEXTURE_PIXEL_SIZE = 10

            // 动态决定是否使用真实纹理
            const useRealTexture = diameterPx >= MIN_TEXTURE_PIXEL_SIZE

            let size: number
            if (useRealTexture) {
                // 使用真实纹理，世界单位大小
                size = getLodSize(planetLod, isSelected, radiusGU * 2)
            } else {
                // 使用圆形纹理，固定屏幕像素大小
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
                if (material.map === this.fallbackCircleTexture) {
                    material.map = null
                    material.needsUpdate = true
                }
                if (details.surfaceTexturePath && (!material.map || !material.map.image)) {
                    this.loadAndApplyTexture(material, details.surfaceTexturePath)
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

            // 透明度计算：根据缩放淡出
            let opacity: number
            if (useRealTexture) {
                opacity = shouldShowEffects(planetLod, isSelected) ? 1.0 : 0.8
            } else {
                // 圆形纹理淡出：1,000->1.0, 100,000->0.0
                const zoomValue = ctx.zoom.value
                const FADE_START = 1_000
                const FADE_END = 100_000
                if (zoomValue >= FADE_END) {
                    opacity = 0
                } else if (zoomValue <= FADE_START) {
                    opacity = 1
                } else {
                    opacity = 1 - (zoomValue - FADE_START) / (FADE_END - FADE_START)
                }
            }
            material.opacity = opacity

            sprite.scale.set(size, size, 1)
            sprite.position.set(planetPos.x - ctx.cameraWorldPosGU.x, planetPos.y - ctx.cameraWorldPosGU.y, 0)
            sprite.visible = true

            // 更新并渲染移动轨迹（拖尾）
            this.updateTrail(entityId, planetPos, ctx, isSelected, totalDays)
        }

        // 回收不可见实体的轨迹
        for (const [id, trail] of this.activeTrailsByEntityId.entries()) {
            if (!visibleEntityIds.has(id)) {
                this.activeTrailsByEntityId.delete(id)
                this.positionHistory.delete(id)
                this.lastSampleMinuteByEntityId.delete(id)
                this.releaseTrailMesh(trail)
            }
        }
    }

    /**
     * 清空所有轨迹数据
     */
    private clearAllTrails(): void {
        for (const [id, trail] of this.activeTrailsByEntityId.entries()) {
            this.activeTrailsByEntityId.delete(id)
            this.releaseTrailMesh(trail)
        }
        this.positionHistory.clear()
        this.lastSampleMinuteByEntityId.clear()
    }

    /**
     * 更新并渲染行星移动轨迹（拖尾）
     * 使用 Mesh (三角形带) + Shader 实现，支持固定 3px 宽度与真正的线性 Alpha 渐隐
     */
    private updateTrail(
        entityId: number,
        currentPos: { x: number; y: number },
        ctx: WorldRenderContext,
        isSelected: boolean,
        totalDays: number
    ): void {
        const { entitiesGroup } = ctx
        const TRAIL_LENGTH = PlanetRenderer.MAX_TRAIL_POINTS  // 1000 点
        const TRAIL_OPACITY_BASE = 0.8  // 基础透明度
        const PIXEL_WIDTH = 3 // 屏幕像素宽度

        // 更新位置历史
        let history = this.positionHistory.get(entityId)
        if (!history) {
            history = []
            this.positionHistory.set(entityId, history)
        }

        // 按游戏分钟采样 (1天 = 1440分钟)
        const currentMinute = Math.floor(totalDays * 1440)
        const lastMinute = this.lastSampleMinuteByEntityId.get(entityId)

        if (lastMinute !== currentMinute) {
            this.lastSampleMinuteByEntityId.set(entityId, currentMinute)
            history.push({ x: currentPos.x, y: currentPos.y })

            if (history.length > TRAIL_LENGTH) {
                history.shift()
            }
        }

        // 历史点不足时不渲染
        if (history.length < 2) return

        // 获取或创建轨迹 Mesh
        let trailMesh = this.activeTrailsByEntityId.get(entityId)
        if (!trailMesh) {
            trailMesh = this.acquireTrailMesh()
            this.activeTrailsByEntityId.set(entityId, trailMesh)
            entitiesGroup.add(trailMesh)
        }

        // 计算轨迹整体透明度 (按 zoom 规则)
        const zoom = ctx.zoom.value
        const FADE_START = 1_000
        const FADE_END = 100_000
        let trailOpacity = zoom < FADE_START ? TRAIL_OPACITY_BASE :
            (zoom >= FADE_END ? 0 : TRAIL_OPACITY_BASE * (1 - (zoom - FADE_START) / (FADE_END - FADE_START)))

        if (isSelected && trailOpacity > 0) {
            trailOpacity = Math.min(1.0, trailOpacity * 1.5)
        }

        if (trailOpacity <= 0.01) {
            trailMesh.visible = false
            return
        }

        // 将像素宽度转换为世界单位
        const worldWidth = PIXEL_WIDTH * zoom
        const geometry = trailMesh.geometry
        const posAttr = geometry.getAttribute('position') as THREE.BufferAttribute
        const positions = posAttr.array as Float32Array
        const alphaAttr = geometry.getAttribute('aAlpha') as THREE.BufferAttribute
        const alphas = alphaAttr.array as Float32Array

        // 构建三角形带顶点
        for (let i = 0; i < history.length; i++) {
            const p = history[i]
            if (!p) break

            let dx = 0
            let dy = 0

            // 使用平滑切线计算法线 (避免转角处三角形感)
            if (i < history.length - 1) {
                const nextP = history[i + 1]
                if (nextP) {
                    dx = nextP.x - p.x
                    dy = nextP.y - p.y
                }
            } else if (i > 0) {
                const prevP = history[i - 1]
                if (prevP) {
                    dx = p.x - prevP.x
                    dy = p.y - prevP.y
                }
            }

            const len = Math.sqrt(dx * dx + dy * dy) || 1
            const nx = (-dy / len) * (worldWidth / 2)
            const ny = (dx / len) * (worldWidth / 2)

            const vIdx = i * 6
            // 左顶点
            positions[vIdx] = p.x - nx
            positions[vIdx + 1] = p.y - ny
            positions[vIdx + 2] = -0.2
            // 右顶点
            positions[vIdx + 3] = p.x + nx
            positions[vIdx + 4] = p.y + ny
            positions[vIdx + 5] = -0.2

            // 设置顶点 Alpha 渐隐效果 (线性消失)
            const vertexAlpha = i / (history.length - 1 || 1)
            alphas[i * 2] = vertexAlpha
            alphas[i * 2 + 1] = vertexAlpha
        }

        posAttr.needsUpdate = true
        alphaAttr.needsUpdate = true
        geometry.setDrawRange(0, (history.length - 1) * 6)

        // 更新 Shader 全局属性
        const material = trailMesh.material as THREE.ShaderMaterial
        if (material.uniforms && material.uniforms.uOpacity) {
            material.uniforms.uOpacity.value = trailOpacity
        }
        if (material.uniforms && material.uniforms.uColor) {
            material.uniforms.uColor.value.copy(TRAIL_BASE_COLOR)
        }

        // 相机偏移
        trailMesh.position.set(-ctx.cameraWorldPosGU.x, -ctx.cameraWorldPosGU.y, 0)
        trailMesh.visible = true
    }

    dispose(ctx: WorldRenderContext): void {
        void ctx
        if (this.fallbackCircleTexture) this.fallbackCircleTexture.dispose()
        for (const s of this.planetSpritePool) (s.material as THREE.Material).dispose()
        for (const s of this.activePlanetSpritesByEntityId.values()) (s.material as THREE.Material).dispose()
        for (const m of this.trailMeshPool) {
            m.geometry.dispose()
                ; (m.material as THREE.Material).dispose()
        }
        for (const m of this.activeTrailsByEntityId.values()) {
            m.geometry.dispose()
                ; (m.material as THREE.Material).dispose()
        }
        this.activePlanetSpritesByEntityId.clear()
        this.activeTrailsByEntityId.clear()
        this.positionHistory.clear()
        this.lastSampleMinuteByEntityId.clear()
    }

    private acquirePlanetSprite(): THREE.Sprite {
        const sprite = this.planetSpritePool.pop()
        if (sprite) {
            sprite.visible = true
            return sprite
        }
        return new THREE.Sprite(new THREE.SpriteMaterial({ color: 0xffffff, sizeAttenuation: true }))
    }

    private releasePlanetSprite(sprite: THREE.Sprite): void {
        (sprite.material as THREE.SpriteMaterial).map = null
        sprite.visible = false
        sprite.parent?.remove(sprite)
        this.planetSpritePool.push(sprite)
    }

    private acquireTrailMesh(): THREE.Mesh {
        const mesh = this.trailMeshPool.pop()
        if (mesh) {
            mesh.visible = true
            return mesh
        }

        // 创建基于 Shader 的三角形带 Mesh
        const geometry = new THREE.BufferGeometry()
        const maxVertices = PlanetRenderer.MAX_TRAIL_POINTS * 2
        geometry.setAttribute('position', new THREE.BufferAttribute(new Float32Array(maxVertices * 3), 3))
        geometry.setAttribute('aAlpha', new THREE.BufferAttribute(new Float32Array(maxVertices), 1))

        // 预填充索引以形成三角形带
        const indices = new Uint16Array((PlanetRenderer.MAX_TRAIL_POINTS - 1) * 6)
        for (let i = 0; i < PlanetRenderer.MAX_TRAIL_POINTS - 1; i++) {
            const v = i * 2
            const idx = i * 6
            indices[idx] = v; indices[idx + 1] = v + 1; indices[idx + 2] = v + 2
            indices[idx + 3] = v + 2; indices[idx + 4] = v + 1; indices[idx + 5] = v + 3
        }
        geometry.setIndex(new THREE.BufferAttribute(indices, 1))

        const material = new THREE.ShaderMaterial({
            uniforms: {
                uColor: { value: new THREE.Color(0xffffff) },
                uOpacity: { value: 1.0 }
            },
            vertexShader: TRAIL_VERTEX_SHADER,
            fragmentShader: TRAIL_FRAGMENT_SHADER,
            transparent: true,
            depthWrite: false,
            side: THREE.DoubleSide,
            blending: THREE.AdditiveBlending
        })

        const m = new THREE.Mesh(geometry, material)
        m.frustumCulled = false
        m.renderOrder = -1
        return m
    }

    private releaseTrailMesh(mesh: THREE.Mesh): void {
        mesh.visible = false
        mesh.parent?.remove(mesh)
        this.trailMeshPool.push(mesh)
    }

    private loadAndApplyTexture(material: THREE.SpriteMaterial, path: string): void {
        if (!this.context) return
        this.context.getTexture(path).then(t => {
            material.map = t
            material.needsUpdate = true
        })
    }
}

function isPointInAabb(p: { x: number, y: number }, a: { minX: number, maxX: number, minY: number, maxY: number }): boolean {
    return p.x >= a.minX && p.x <= a.maxX && p.y >= a.minY && p.y <= a.maxY
}

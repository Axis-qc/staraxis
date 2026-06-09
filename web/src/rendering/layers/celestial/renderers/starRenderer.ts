/**
 * @file starRenderer.ts
 *
 * @description
 * 恒星渲染器分层适配实现喵。
 *
 * 作用：
 * - 将恒星从 `SpriteMaterial`（Three.js 精灵贴图材质）切换为程序化 `ShaderMaterial`（Three.js 自定义着色材质）喵。
 * - 通过共享 `PlaneGeometry`（平面几何）和对象池减少频繁分配喵。
 * - 按 `lodSystem`（细节层级系统）结果更新可见性、尺寸和特效强度喵。
 */

import * as THREE from 'three'
import type { WorldRenderContext, WorldFrameState } from '../../../worldRenderManager'
import type { EntitySnapshot, StarDetails } from '../../../../net/snapshotWs'
import type { EntityLodState } from '../../../subsystems/lodSystem'
import { getLodSize, LodLevel, shouldRender, shouldShowEffects } from '../../../subsystems/lodSystem'
import { ZLayer } from '../../index'
import { getStarProfile } from './starProfile'
import {
    applyStarProfileToMaterial,
    createProceduralStarMaterial,
    updateStarMaterialOpacity,
    updateStarMaterialTime,
    type ProceduralStarMaterial,
} from './starMaterial'

const DEFAULT_POOL_SIZE = 50
const MIN_PROCEDURAL_PIXEL_SIZE = 10
const MIN_FALLBACK_PIXEL_SIZE = 10

type StarMesh = THREE.Mesh<THREE.SphereGeometry, ProceduralStarMaterial>

/**
 * LayerStarRenderer（恒星层渲染器）负责维护恒星网格对象池与逐帧更新喵。
 */
export class LayerStarRenderer {
    private readonly starMeshPool: StarMesh[] = []
    private readonly activeStarMeshesByEntityId = new Map<number, StarMesh>()
    private readonly starSpritePool: THREE.Sprite[] = []
    private readonly activeStarSpritesByEntityId = new Map<number, THREE.Sprite>()
    private readonly layerGroup: THREE.Group
    private fallbackCircleTexture: THREE.CanvasTexture | null = null
    private sharedGeometry: THREE.SphereGeometry | null = null
    private disposed = false

    constructor(layerGroup: THREE.Group) {
        this.layerGroup = layerGroup
    }

    init(): void {
        this.sharedGeometry = new THREE.SphereGeometry(1, 32, 16)
        this.fallbackCircleTexture = this.createCircleTexture()

        // 预热对象池，避免初次缩放进入恒星密集区域时集中分配喵。
        for (let i = 0; i < DEFAULT_POOL_SIZE; i++) {
            this.starMeshPool.push(this.createStarMesh())
            this.starSpritePool.push(this.createStarSprite())
        }
    }

    update(ctx: WorldRenderContext, frame: WorldFrameState): void {
        if (this.disposed) return

        const { entitiesById, selectedIds, cullingAabb, lod } = frame
        const starLod = lod.star

        if (!starLod.visible) {
            this.releaseAllVisuals()
            return
        }

        const visibleEntityIds = new Set<number>()

        for (const entity of entitiesById.values()) {
            if (entity.entityType !== 'STAR') continue

            const isSelected = selectedIds.has(entity.entityId)
            const details = entity.details as StarDetails | null
            const shouldBeVisible = shouldRender(starLod, isSelected) &&
                entity.posWorldGU !== null &&
                details !== null &&
                (isSelected || this.isStarQuadInAabb(entity.posWorldGU, details, ctx, starLod, isSelected, cullingAabb))

            if (!shouldBeVisible) continue

            visibleEntityIds.add(entity.entityId)
        }

        this.releaseInvisibleMeshes(visibleEntityIds)

        const elapsedSeconds = performance.now() / 1000

        for (const entityId of visibleEntityIds) {
            const entity = entitiesById.get(entityId)
            if (!entity) continue

            const details = entity.details as StarDetails
            this.updateStarVisual(
                entityId,
                entity,
                details,
                ctx,
                starLod,
                selectedIds.has(entityId),
                elapsedSeconds,
            )
        }
    }

    private updateStarVisual(
        entityId: number,
        entity: EntitySnapshot,
        details: StarDetails,
        ctx: WorldRenderContext,
        starLod: EntityLodState,
        isSelected: boolean,
        elapsedSeconds: number,
    ): void {
        const radiusGU = Math.max(details.radiusGU, 1)
        const diameterGU = radiusGU * 2
        const diameterPx = diameterGU / ctx.zoom.value
        const useFallbackSprite = starLod.level >= LodLevel.Minimal || diameterPx < MIN_PROCEDURAL_PIXEL_SIZE

        if (useFallbackSprite) {
            this.updateFallbackSprite(entityId, entity, details, ctx, starLod, isSelected, diameterGU)
            return
        }

        this.releaseActiveSprite(entityId)

        const clampedDiameterGU = Math.max(diameterGU, MIN_PROCEDURAL_PIXEL_SIZE * ctx.zoom.value)
        const size = getLodSize(starLod, isSelected, clampedDiameterGU)
        const scaledRadiusGU = size * 0.5

        let mesh = this.activeStarMeshesByEntityId.get(entityId)
        if (!mesh) {
            mesh = this.acquireStarMesh()
            this.activeStarMeshesByEntityId.set(entityId, mesh)
            this.layerGroup.add(mesh)
        }

        const material = mesh.material
        const profile = getStarProfile(details)
        const showEffects = shouldShowEffects(starLod, isSelected)

        applyStarProfileToMaterial(material, profile)
        updateStarMaterialTime(material, elapsedSeconds + entityId * 0.013)
        updateStarMaterialOpacity(material, showEffects ? 1.0 : 0.84)

        // 远景小恒星减少条带与辉光，避免屏幕闪烁喵。
        if (diameterPx < MIN_PROCEDURAL_PIXEL_SIZE) {
            material.uniforms.uSurfaceBanding.value *= 0.55
            material.uniforms.uGlowIntensity.value *= 0.78
        } else if (!showEffects) {
            material.uniforms.uGlowIntensity.value *= 0.86
        }

        mesh.scale.set(scaledRadiusGU, scaledRadiusGU, scaledRadiusGU)
        const rp = ctx.toRenderPos(entity.posWorldGU!)
        mesh.position.set(
            rp.x,
            rp.y,
            -(ZLayer.CELESTIAL_STAR_BASE + scaledRadiusGU),
        )
        mesh.visible = true
    }

    private updateFallbackSprite(
        entityId: number,
        entity: EntitySnapshot,
        details: StarDetails,
        ctx: WorldRenderContext,
        starLod: EntityLodState,
        isSelected: boolean,
        diameterGU: number,
    ): void {
        this.releaseActiveMesh(entityId)

        let sprite = this.activeStarSpritesByEntityId.get(entityId)
        if (!sprite) {
            sprite = this.acquireStarSprite()
            this.activeStarSpritesByEntityId.set(entityId, sprite)
            this.layerGroup.add(sprite)
        }

        const material = sprite.material as THREE.SpriteMaterial
        const profile = getStarProfile(details)
        const fallbackDiameterGU = Math.max(
            getLodSize(starLod, isSelected, diameterGU),
            MIN_FALLBACK_PIXEL_SIZE * ctx.zoom.value,
        )

        material.color.copy(profile.baseColor)
        material.opacity = shouldShowEffects(starLod, isSelected) ? 0.95 : 0.82
        material.sizeAttenuation = true
        sprite.scale.set(fallbackDiameterGU, fallbackDiameterGU, 1)
        const rp = ctx.toRenderPos(entity.posWorldGU!)
        sprite.position.set(
            rp.x,
            rp.y,
            -ZLayer.CELESTIAL_STAR_BASE,
        )
        sprite.visible = true
    }

    private createStarMesh(): StarMesh {
        if (!this.sharedGeometry) {
            throw new Error('sharedGeometry is required before creating star mesh')
        }

        const material = createProceduralStarMaterial(getStarProfile({
            starTypeId: 'G_MAIN_SEQUENCE',
            temperatureK: 5800,
            massSolar: 1,
            radiusGU: 7000,
        }))

        const mesh = new THREE.Mesh(this.sharedGeometry, material)
        mesh.frustumCulled = false
        mesh.renderOrder = -1
        mesh.visible = false
        return mesh
    }

    private createStarSprite(): THREE.Sprite {
        const material = new THREE.SpriteMaterial({
            color: 0xffffff,
            sizeAttenuation: true,
            map: this.fallbackCircleTexture ?? undefined,
            transparent: true,
            depthWrite: false,
            depthTest: false,
        })
        const sprite = new THREE.Sprite(material)
        sprite.frustumCulled = false
        sprite.visible = false
        sprite.renderOrder = -1
        return sprite
    }

    private acquireStarMesh(): StarMesh {
        const mesh = this.starMeshPool.pop()
        if (mesh) {
            mesh.visible = true
            return mesh
        }

        return this.createStarMesh()
    }

    private acquireStarSprite(): THREE.Sprite {
        const sprite = this.starSpritePool.pop()
        if (sprite) {
            sprite.visible = true
            return sprite
        }

        return this.createStarSprite()
    }

    private releaseStarMesh(mesh: StarMesh): void {
        mesh.visible = false
        mesh.parent?.remove(mesh)
        this.starMeshPool.push(mesh)
    }

    private releaseStarSprite(sprite: THREE.Sprite): void {
        sprite.visible = false
        sprite.parent?.remove(sprite)
        this.starSpritePool.push(sprite)
    }

    private releaseActiveMesh(entityId: number): void {
        const mesh = this.activeStarMeshesByEntityId.get(entityId)
        if (!mesh) return

        this.activeStarMeshesByEntityId.delete(entityId)
        this.releaseStarMesh(mesh)
    }

    private releaseActiveSprite(entityId: number): void {
        const sprite = this.activeStarSpritesByEntityId.get(entityId)
        if (!sprite) return

        this.activeStarSpritesByEntityId.delete(entityId)
        this.releaseStarSprite(sprite)
    }

    private releaseAllMeshes(): void {
        for (const [entityId, mesh] of this.activeStarMeshesByEntityId.entries()) {
            this.activeStarMeshesByEntityId.delete(entityId)
            this.releaseStarMesh(mesh)
        }
    }

    private releaseAllSprites(): void {
        for (const [entityId, sprite] of this.activeStarSpritesByEntityId.entries()) {
            this.activeStarSpritesByEntityId.delete(entityId)
            this.releaseStarSprite(sprite)
        }
    }

    private releaseAllVisuals(): void {
        this.releaseAllMeshes()
        this.releaseAllSprites()
    }

    private releaseInvisibleMeshes(visibleEntityIds: Set<number>): void {
        for (const [entityId, mesh] of this.activeStarMeshesByEntityId.entries()) {
            if (visibleEntityIds.has(entityId)) continue

            this.activeStarMeshesByEntityId.delete(entityId)
            this.releaseStarMesh(mesh)
        }

        for (const [entityId, sprite] of this.activeStarSpritesByEntityId.entries()) {
            if (visibleEntityIds.has(entityId)) continue

            this.activeStarSpritesByEntityId.delete(entityId)
            this.releaseStarSprite(sprite)
        }
    }

    /**
     * 按恒星实际渲染四边形与镜头剔除范围是否相交来判断是否可见喵。
     *
     * 说明：
     * - 不能只用中心点做剔除喵，否则镜头进入超大恒星内部时会被错误剔除喵。
     * - 这里取程序化网格与远景圆形指示纹理两条路径中的较大尺寸，再额外放大一圈给 halo（光晕）留边喵。
     */
    private isStarQuadInAabb(
        position: { x: number; y: number },
        details: StarDetails,
        ctx: WorldRenderContext,
        starLod: EntityLodState,
        isSelected: boolean,
        aabb: { minX: number; maxX: number; minY: number; maxY: number },
    ): boolean {
        const radiusGU = Math.max(details.radiusGU, 1)
        const diameterGU = radiusGU * 2

        const proceduralDiameterGU = getLodSize(
            starLod,
            isSelected,
            Math.max(diameterGU, MIN_PROCEDURAL_PIXEL_SIZE * ctx.zoom.value),
        )
        const fallbackDiameterGU = Math.max(
            getLodSize(starLod, isSelected, diameterGU),
            MIN_FALLBACK_PIXEL_SIZE * ctx.zoom.value,
        )

        // 给外层 halo（光晕）留出额外边距，避免边缘刚好在屏幕里时被误裁掉喵。
        const quadHalfExtent = Math.max(proceduralDiameterGU, fallbackDiameterGU) * 0.68

        const quadMinX = position.x - quadHalfExtent
        const quadMaxX = position.x + quadHalfExtent
        const quadMinY = position.y - quadHalfExtent
        const quadMaxY = position.y + quadHalfExtent

        return !(quadMaxX < aabb.minX || quadMinX > aabb.maxX || quadMaxY < aabb.minY || quadMinY > aabb.maxY)
    }

    dispose(): void {
        if (this.disposed) return

        this.releaseAllVisuals()

        for (const mesh of this.starMeshPool) {
            mesh.material.dispose()
        }
        this.starMeshPool.length = 0

        for (const sprite of this.starSpritePool) {
            ; (sprite.material as THREE.Material).dispose()
        }
        this.starSpritePool.length = 0

        if (this.sharedGeometry) {
            this.sharedGeometry.dispose()
            this.sharedGeometry = null
        }

        if (this.fallbackCircleTexture) {
            this.fallbackCircleTexture.dispose()
            this.fallbackCircleTexture = null
        }

        this.activeStarMeshesByEntityId.clear()
        this.activeStarSpritesByEntityId.clear()
        this.disposed = true
    }

    private createCircleTexture(): THREE.CanvasTexture {
        const canvas = document.createElement('canvas')
        canvas.width = 64
        canvas.height = 64
        const ctx = canvas.getContext('2d')

        if (!ctx) {
            throw new Error('Canvas 2D context is required for star fallback texture')
        }

        ctx.clearRect(0, 0, 64, 64)

        const centerX = 32
        const centerY = 32
        const radius = 30
        const gradient = ctx.createRadialGradient(centerX, centerY, 0, centerX, centerY, radius)

        gradient.addColorStop(0, 'rgba(255, 255, 255, 1)')
        gradient.addColorStop(0.78, 'rgba(255, 255, 255, 0.95)')
        gradient.addColorStop(1, 'rgba(255, 255, 255, 0.0)')

        ctx.beginPath()
        ctx.arc(centerX, centerY, radius, 0, Math.PI * 2)
        ctx.fillStyle = gradient
        ctx.fill()

        const texture = new THREE.CanvasTexture(canvas)
        texture.needsUpdate = true
        return texture
    }
}

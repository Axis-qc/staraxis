/**
 * @file selectionEffectRenderer.ts
 * @description 实体附着效果层中的选中环渲染器 - 基于屏幕空间纹理 sprite 的稳定实现喵
 * @important_notes 通过缓存圆环纹理避免着色器方案带来的卡死问题喵
 */

import * as THREE from 'three'
import type { WorldRenderContext, WorldFrameState } from '../../../worldRenderManager'
import type { EntitySnapshot, PlanetDetails, StarDetails } from '../../../../net/snapshotWs'

const SELECTION_COLOR = 'rgba(76, 175, 80, 0.96)'
const LINE_WIDTH_PX = 12
const DASH_SIZE_PX = 30
const GAP_SIZE_PX = 15
const MIN_RING_SIZE_PX = 72
const MAX_RING_SIZE_PX = 640
const SHIP_BASE_SIZE_PX = 24
const RING_PADDING_PX = 24
const RING_TEXTURE_BUCKET_PX = 8
const RING_POOL_SIZE = 12
const ROTATION_SPEED_RAD_PER_SEC = 0.35

export class SelectionEffectRenderer {
  private readonly parentGroup: THREE.Group
  private readonly ringPool: THREE.Sprite[] = []
  private readonly activeRingsByEntityId = new Map<number, THREE.Sprite>()
  private readonly ringTextureCache = new Map<number, THREE.CanvasTexture>()

  constructor(parentGroup: THREE.Group) {
    this.parentGroup = parentGroup
  }

  init(_ctx: WorldRenderContext): void {
    for (let i = 0; i < RING_POOL_SIZE; i++) {
      const ring = this.createRingSprite()
      ring.visible = false
      this.parentGroup.add(ring)
      this.ringPool.push(ring)
    }
  }

  update(ctx: WorldRenderContext, frame: WorldFrameState): void {
    const selectionLod = frame.lod.selection
    if (!selectionLod.visible || frame.selectedIds.size === 0) {
      this.releaseInactiveRings(new Set())
      return
    }

    const visibleIds = new Set<number>()
    const nowSeconds = performance.now() / 1000

    for (const entityId of frame.selectedIds) {
      const entity = frame.entitiesById.get(entityId)
      if (!entity) continue

      const entityPos = ctx.getEntityWorldPosGU(entityId) ?? entity.posWorldGU
      if (!entityPos) continue

      visibleIds.add(entityId)

      let ring = this.activeRingsByEntityId.get(entityId)
      if (!ring) {
        ring = this.acquireRing()
        this.activeRingsByEntityId.set(entityId, ring)
      }

      const spriteSizePx = this.getRingSpriteSizePx(entity, ctx)
      const texture = this.getRingTexture(spriteSizePx)
      const material = ring.material as THREE.SpriteMaterial

      if (material.map !== texture) {
        material.map = texture
        material.needsUpdate = true
      }

      material.opacity = 0.95 * Math.max(0.35, selectionLod.params.textureQuality)
      material.rotation = (entityId % 16) * (Math.PI / 8) + nowSeconds * ROTATION_SPEED_RAD_PER_SEC

      const worldSize = spriteSizePx * ctx.zoom.value
      ring.scale.set(worldSize, worldSize, 1)
      ring.position.set(
        entityPos.x,
        entityPos.y,
        0.4
      )
      ring.visible = true
    }

    this.releaseInactiveRings(visibleIds)
  }

  dispose(): void {
    const allRings = new Set([...this.ringPool, ...this.activeRingsByEntityId.values()])
    for (const ring of allRings) {
      const material = ring.material as THREE.SpriteMaterial
      material.map = null
      material.dispose()
      ring.parent?.remove(ring)
    }

    this.ringPool.length = 0
    this.activeRingsByEntityId.clear()

    for (const texture of this.ringTextureCache.values()) {
      texture.dispose()
    }
    this.ringTextureCache.clear()
  }

  private createRingSprite(): THREE.Sprite {
    const material = new THREE.SpriteMaterial({
      color: 0xffffff,
      transparent: true,
      opacity: 0.95,
      depthWrite: false,
      depthTest: false,
      sizeAttenuation: true,
    })
    const sprite = new THREE.Sprite(material)
    sprite.frustumCulled = false
    return sprite
  }

  private acquireRing(): THREE.Sprite {
    const ring = this.ringPool.pop()
    if (ring) {
      ring.visible = true
      return ring
    }

    const ringSprite = this.createRingSprite()
    this.parentGroup.add(ringSprite)
    return ringSprite
  }

  private releaseRing(ring: THREE.Sprite): void {
    ring.visible = false
    this.ringPool.push(ring)
  }

  private releaseInactiveRings(visibleIds: Set<number>): void {
    for (const [entityId, ring] of this.activeRingsByEntityId.entries()) {
      if (!visibleIds.has(entityId)) {
        this.activeRingsByEntityId.delete(entityId)
        this.releaseRing(ring)
      }
    }
  }

  private getRingSpriteSizePx(entity: EntitySnapshot, ctx: WorldRenderContext): number {
    const projectedDiameterPx = this.getProjectedEntityDiameterPx(entity, ctx)
    const requestedSize = projectedDiameterPx + RING_PADDING_PX
    const clampedSize = Math.max(MIN_RING_SIZE_PX, Math.min(MAX_RING_SIZE_PX, requestedSize))
    return Math.ceil(clampedSize / RING_TEXTURE_BUCKET_PX) * RING_TEXTURE_BUCKET_PX
  }

  private getProjectedEntityDiameterPx(entity: EntitySnapshot, ctx: WorldRenderContext): number {
    switch (entity.entityType) {
      case 'STAR':
        return ((entity.details as StarDetails | undefined)?.radiusGU ?? 0) * 2 / ctx.zoom.value
      case 'PLANET':
        return ((entity.details as PlanetDetails | undefined)?.radiusGU ?? 0) * 2 / ctx.zoom.value
      case 'SHIP':
        return SHIP_BASE_SIZE_PX
      default:
        return SHIP_BASE_SIZE_PX
    }
  }

  private getRingTexture(spriteSizePx: number): THREE.CanvasTexture {
    const cached = this.ringTextureCache.get(spriteSizePx)
    if (cached) {
      return cached
    }

    const texture = this.createRingTexture(spriteSizePx)
    this.ringTextureCache.set(spriteSizePx, texture)
    return texture
  }

  private createRingTexture(spriteSizePx: number): THREE.CanvasTexture {
    const devicePixelRatio = Math.min(window.devicePixelRatio || 1, 2)
    const canvas = document.createElement('canvas')
    canvas.width = Math.max(1, Math.round(spriteSizePx * devicePixelRatio))
    canvas.height = Math.max(1, Math.round(spriteSizePx * devicePixelRatio))

    const ctx = canvas.getContext('2d')
    if (!ctx) {
      throw new Error('Failed to create 2D context for selection ring texture')
    }

    ctx.scale(devicePixelRatio, devicePixelRatio)
    ctx.clearRect(0, 0, spriteSizePx, spriteSizePx)

    const radius = spriteSizePx / 2 - LINE_WIDTH_PX / 2 - 2
    ctx.beginPath()
    ctx.arc(spriteSizePx / 2, spriteSizePx / 2, Math.max(radius, LINE_WIDTH_PX), 0, Math.PI * 2)
    ctx.strokeStyle = SELECTION_COLOR
    ctx.lineWidth = LINE_WIDTH_PX
    ctx.lineCap = 'round'
    ctx.setLineDash([DASH_SIZE_PX, GAP_SIZE_PX])
    ctx.stroke()

    const texture = new THREE.CanvasTexture(canvas)
    texture.needsUpdate = true
    texture.generateMipmaps = false
    return texture
  }
}

/**
 * @file selectionEffectRenderer.ts
 * @description 实体附着效果层中的六边形选中指示器 - 科技风格喵
 * @important_notes
 * - 使用正六边形 + 虚线扫描 + 角标accent 实现科技感选中效果喵
 * - 颜色从 CSS 主题变量（--sa-accent）动态读取，跟随主题切换喵
 * - 通过缓存纹理避免每帧重绘喵
 */

import * as THREE from 'three'
import type { WorldRenderContext, WorldFrameState } from '../../../worldRenderManager'
import type { EntitySnapshot, PlanetDetails, StarDetails } from '../../../../net/snapshotWs'

// ── 主题颜色读取 ────────────────────────────────────────────喵

/** 解析 CSS 颜色为 [r, g, b]，支持 rgba() 和 #hex 喵 */
function parseColorRgb(color: string): [number, number, number] {
  const m = color.match(/rgba?\(\s*(\d+)\s*,\s*(\d+)\s*,\s*(\d+)/)
  if (m) return [+m[1]!, +m[2]!, +m[3]!]
  const h = color.match(/^#([0-9a-f]{6})$/i)
  if (h) {
    const s = h[1]!
    return [parseInt(s.slice(0, 2), 16), parseInt(s.slice(2, 4), 16), parseInt(s.slice(4, 6), 16)]
  }
  return [0, 191, 255] // 回退青色喵
}

/** 从 CSS 变量读取当前主题的强调色喵 */
function readAccentRgb(): [number, number, number] {
  const cs = getComputedStyle(document.documentElement)
  const raw = cs.getPropertyValue('--sa-accent').trim() || 'rgba(0, 191, 255, 0.92)'
  return parseColorRgb(raw)
}

// ── 静态配置 ──────────────────────────────────────────────喵

const HEX_LINE_WIDTH_PX = 2           // 主描边线宽喵
const HEX_GLOW_LINE_WIDTH_PX = 6      // 发光线宽喵
const DASH_SIZE_PX = 18               // 虚线段长喵
const GAP_SIZE_PX = 12                // 虚线间隔喵
const ACCENT_LEN_PX = 14             // 角标 accent 长度喵
const ACCENT_GAP_PX = 4              // 角标与六边形顶点的间距喵
const ACCENT_LINE_WIDTH_PX = 2.5     // 角标线宽喵
const MIN_SIZE_PX = 72
const MAX_SIZE_PX = 640
const SHIP_BASE_SIZE_PX = 24
const RING_PADDING_PX = 28
const RING_TEXTURE_BUCKET_PX = 8
const RING_POOL_SIZE = 12
const DASH_ANIM_SPEED_PX_PER_SEC = 40 // 虚线扫描速度喵
const GLOW_ALPHA = 0.25               // 发光层透明度喵
const MAIN_ALPHA = 0.90               // 主描边透明度喵
const ACCENT_ALPHA = 0.85             // 角标透明度喵
const SCAN_ALPHA = 0.35               // 扫描线透明度喵
const SCAN_WIDTH_PX = 3               // 扫描线宽喵

export class SelectionEffectRenderer {
  private readonly parentGroup: THREE.Group
  private readonly ringPool: THREE.Sprite[] = []
  private readonly activeRingsByEntityId = new Map<number, THREE.Sprite>()
  private readonly ringTextureCache = new Map<number, THREE.CanvasTexture>()

  // 主题监听喵
  private themeObserver: MutationObserver | null = null
  private themeVersion = 0
  private cachedAccentRgb: [number, number, number] | null = null

  constructor(parentGroup: THREE.Group) {
    this.parentGroup = parentGroup
  }

  init(_ctx: WorldRenderContext): void {
    // 监听主题变化喵
    this.themeObserver = new MutationObserver(() => {
      this.themeVersion++
      this.cachedAccentRgb = null
      for (const tex of this.ringTextureCache.values()) tex.dispose()
      this.ringTextureCache.clear()
    })
    this.themeObserver.observe(document.documentElement, {
      attributes: true,
      attributeFilter: ['style'],
    })

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
      // 虚线扫描动画：通过 dashOffset 变化实现，不旋转精灵喵
      const dashOffset = -(nowSeconds * DASH_ANIM_SPEED_PX_PER_SEC + entityId * 37)
      const texture = this.getRingTexture(spriteSizePx, dashOffset)
      const material = ring.material as THREE.SpriteMaterial

      if (material.map !== texture) {
        material.map = texture
        material.needsUpdate = true
      }

      material.opacity = Math.max(0.35, selectionLod.params.textureQuality)
      // 六边形不旋转，通过 dashOffset 实现扫描动效喵
      material.rotation = 0

      // 选中环世界大小 = max(像素大小, 实体世界直径)，确保放大后环不缩进实体喵
      const worldSizeFromPx = spriteSizePx * ctx.zoom.value
      const entityDiameterGU = this.getEntityWorldDiameterGU(entity)
      const worldSize = Math.max(worldSizeFromPx, entityDiameterGU + RING_PADDING_PX * ctx.zoom.value)
      ring.scale.set(worldSize, worldSize, 1)
      const rp = ctx.toRenderPos(entityPos)
      ring.position.set(rp.x, rp.y, 0.4)
      ring.visible = true
    }

    this.releaseInactiveRings(visibleIds)
  }

  dispose(): void {
    this.themeObserver?.disconnect()
    this.themeObserver = null
    this.cachedAccentRgb = null

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

  // ── 内部方法 ──────────────────────────────────────────────喵

  private getAccentRgb(): [number, number, number] {
    if (!this.cachedAccentRgb) {
      this.cachedAccentRgb = readAccentRgb()
    }
    return this.cachedAccentRgb
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
    const clampedSize = Math.max(MIN_SIZE_PX, Math.min(MAX_SIZE_PX, requestedSize))
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

  /** 获取实体在世界坐标系中的直径（GU），用于放大时撑大选中环喵 */
  private getEntityWorldDiameterGU(entity: EntitySnapshot): number {
    switch (entity.entityType) {
      case 'STAR':
        return ((entity.details as StarDetails | undefined)?.radiusGU ?? 0) * 2
      case 'PLANET':
        return ((entity.details as PlanetDetails | undefined)?.radiusGU ?? 0) * 2
      default:
        return 0
    }
  }

  /**
   * 获取纹理缓存（键包含主题版本 + dashOffset 量化值）喵
   */
  private getRingTexture(spriteSizePx: number, dashOffset: number): THREE.CanvasTexture {
    // 量化 dashOffset 到 4px 精度，减少纹理重建频率喵
    const quantizedOffset = Math.round(dashOffset / 4) * 4
    const cacheKey = spriteSizePx * 10000 + Math.abs(quantizedOffset % 10000) + this.themeVersion * 100000000

    const cached = this.ringTextureCache.get(cacheKey)
    if (cached) return cached

    const texture = this.createHexTexture(spriteSizePx, dashOffset)
    this.ringTextureCache.set(cacheKey, texture)
    return texture
  }

  /**
   * 计算正六边形的 6 个顶点坐标（平顶六边形，第一个顶点在右上）喵
   */
  private getHexVertices(cx: number, cy: number, radius: number): [number, number][] {
    const verts: [number, number][] = []
    for (let i = 0; i < 6; i++) {
      // 平顶六边形：起始角度 -30°（即从右上方开始）喵
      const angle = (Math.PI / 6) + (i * Math.PI / 3)
      verts.push([
        cx + radius * Math.cos(angle),
        cy + radius * Math.sin(angle),
      ])
    }
    return verts
  }

  /**
   * 绘制六边形路径喵
   */
  private traceHexPath(ctx: CanvasRenderingContext2D, verts: [number, number][]): void {
    ctx.beginPath()
    ctx.moveTo(verts[0]![0], verts[0]![1])
    for (let i = 1; i < 6; i++) {
      ctx.lineTo(verts[i]![0], verts[i]![1])
    }
    ctx.closePath()
  }

  /**
   * 创建六边形选中纹理喵
   * 包含三层：发光层（blur）+ 主描边层（虚线）+ 角标 accent 层喵
   */
  private createHexTexture(spriteSizePx: number, dashOffset: number): THREE.CanvasTexture {
    const devicePixelRatio = Math.min(window.devicePixelRatio || 1, 2)
    const size = Math.max(1, Math.round(spriteSizePx * devicePixelRatio))

    const canvas = document.createElement('canvas')
    canvas.width = size
    canvas.height = size

    const ctx = canvas.getContext('2d')
    if (!ctx) {
      throw new Error('Failed to create 2D context for hex selection texture')
    }

    ctx.scale(devicePixelRatio, devicePixelRatio)
    ctx.clearRect(0, 0, spriteSizePx, spriteSizePx)

    const accent = this.getAccentRgb()
    const accentStr = `rgb(${accent[0]}, ${accent[1]}, ${accent[2]})`

    const cx = spriteSizePx / 2
    const cy = spriteSizePx / 2
    const hexRadius = (spriteSizePx / 2) - HEX_GLOW_LINE_WIDTH_PX - 2
    const verts = this.getHexVertices(cx, cy, Math.max(hexRadius, HEX_LINE_WIDTH_PX))

    // ── 第1层：发光层（外发光 + 柔和扩散）喵 ──
    ctx.save()
    ctx.shadowColor = accentStr
    ctx.shadowBlur = 12
    ctx.strokeStyle = `rgba(${accent[0]}, ${accent[1]}, ${accent[2]}, ${GLOW_ALPHA})`
    ctx.lineWidth = HEX_GLOW_LINE_WIDTH_PX
    ctx.lineJoin = 'round'
    this.traceHexPath(ctx, verts)
    ctx.stroke()
    ctx.restore()

    // ── 第2层：主描边（虚线扫描）喵 ──
    ctx.strokeStyle = `rgba(${accent[0]}, ${accent[1]}, ${accent[2]}, ${MAIN_ALPHA})`
    ctx.lineWidth = HEX_LINE_WIDTH_PX
    ctx.lineJoin = 'round'
    ctx.lineCap = 'round'
    ctx.setLineDash([DASH_SIZE_PX, GAP_SIZE_PX])
    ctx.lineDashOffset = dashOffset
    this.traceHexPath(ctx, verts)
    ctx.stroke()
    ctx.setLineDash([])

    // ── 第3层：角标 accent（每个顶点外侧的小短线）喵 ──
    ctx.strokeStyle = `rgba(${accent[0]}, ${accent[1]}, ${accent[2]}, ${ACCENT_ALPHA})`
    ctx.lineWidth = ACCENT_LINE_WIDTH_PX
    ctx.lineCap = 'round'

    for (let i = 0; i < 6; i++) {
      const [vx, vy] = verts[i]!
      // 从顶点向外延伸的方向喵
      const outAngle = (Math.PI / 6) + (i * Math.PI / 3)
      const dx = Math.cos(outAngle)
      const dy = Math.sin(outAngle)

      // 角标起点：顶点外侧留间距喵
      const sx = vx + dx * ACCENT_GAP_PX
      const sy = vy + dy * ACCENT_GAP_PX
      // 角标终点喵
      const ex = vx + dx * (ACCENT_GAP_PX + ACCENT_LEN_PX)
      const ey = vy + dy * (ACCENT_GAP_PX + ACCENT_LEN_PX)

      ctx.beginPath()
      ctx.moveTo(sx, sy)
      ctx.lineTo(ex, ey)
      ctx.stroke()
    }

    // ── 第4层：扫描高亮弧段（一条较亮的短弧沿六边形边缘运动）喵 ──
    const scanArcLen = Math.PI * 0.4 // 扫描弧的弧度长度喵
    const scanAngle = (-dashOffset / (DASH_SIZE_PX + GAP_SIZE_PX)) * (Math.PI / 3) // 基于 dashOffset 推算角度喵
    ctx.strokeStyle = `rgba(${accent[0]}, ${accent[1]}, ${accent[2]}, ${SCAN_ALPHA})`
    ctx.lineWidth = SCAN_WIDTH_PX
    ctx.lineCap = 'round'
    ctx.setLineDash([])

    // 沿六边形边缘绘制一段连续的扫描弧喵
    ctx.beginPath()
    const totalPerimeter = hexRadius * 6 // 近似喵
    const startT = ((scanAngle % (Math.PI * 2)) + Math.PI * 2) % (Math.PI * 2)
    const segmentsToDraw = 2 // 绘制跨越 2 条边的弧喵

    for (let seg = 0; seg < segmentsToDraw; seg++) {
      const edgeStartAngle = startT + seg * (Math.PI / 3)
      const edgeIdx = Math.floor(((edgeStartAngle % (Math.PI * 2)) + Math.PI * 2) % (Math.PI * 2) / (Math.PI / 3)) % 6
      const nextIdx = (edgeIdx + 1) % 6
      const [x1, y1] = verts[edgeIdx]!
      const [x2, y2] = verts[nextIdx]!

      if (seg === 0) {
        ctx.moveTo(x1, y1)
      }
      ctx.lineTo(x2, y2)
    }
    ctx.stroke()

    const texture = new THREE.CanvasTexture(canvas)
    texture.needsUpdate = true
    texture.generateMipmaps = false
    return texture
  }
}

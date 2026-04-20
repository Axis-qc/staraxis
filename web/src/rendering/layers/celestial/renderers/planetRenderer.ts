/**
 * @file planetRenderer.ts
 * @description 分层行星渲染器 - 适配现有PlanetRenderer逻辑到分层架构喵
 * @usage 在CelestialLayer中初始化并调用update方法喵
 */

import * as THREE from 'three'
import type { WorldRenderContext, WorldFrameState } from '../../../worldRenderManager'
import type { PlanetDetails } from '../../../../net/snapshotWs'
import { shouldRender, getLodSize, shouldShowEffects } from '../../../subsystems/lodSystem'

const TRAIL_BASE_COLOR = new THREE.Color(0xffffff)

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

export class LayerPlanetRenderer {
  private parentGroup: THREE.Group
  private planetSpritePool: THREE.Sprite[] = []
  private activePlanetSpritesByEntityId = new Map<number, THREE.Sprite>()
  private fallbackCircleTexture: THREE.CanvasTexture | null = null
  private context: WorldRenderContext | null = null
  private poolSize: number
  private disposed = false

  // 移动轨迹相关（复用现有逻辑）
  private trailMeshPool: THREE.Mesh[] = []
  private activeTrailsByEntityId = new Map<number, THREE.Mesh>()
  private positionHistory = new Map<number, Array<{ x: number; y: number }>>()
  private lastSampleMinuteByEntityId = new Map<number, number>()
  private static readonly MAX_TRAIL_POINTS = 1000
  private static readonly DEFAULT_POOL_SIZE = 20

  constructor(parentGroup: THREE.Group, poolSize: number = LayerPlanetRenderer.DEFAULT_POOL_SIZE) {
    if (!parentGroup) {
      throw new Error('parentGroup is required for LayerPlanetRenderer')
    }
    if (poolSize <= 0) {
      throw new Error('poolSize must be positive')
    }
    this.parentGroup = parentGroup
    this.poolSize = poolSize
  }

  private createCircleTexture(): THREE.CanvasTexture {
    const canvas = document.createElement('canvas')
    canvas.width = 64
    canvas.height = 64
    const ctx = canvas.getContext('2d')
    if (!ctx) {
      // 提供降级方案或抛出明确错误喵
      console.error('Failed to get 2D context for planet fallback texture')
      // 创建简单的纯色纹理作为降级喵
      const fallbackCanvas = document.createElement('canvas')
      fallbackCanvas.width = 64
      fallbackCanvas.height = 64
      const fallbackCtx = fallbackCanvas.getContext('2d')
      if (fallbackCtx) {
        fallbackCtx.fillStyle = '#ffffff'
        fallbackCtx.fillRect(0, 0, 64, 64)
        const texture = new THREE.CanvasTexture(fallbackCanvas)
        texture.needsUpdate = true
        return texture
      }
      throw new Error('Canvas 2D context not supported in this environment')
    }

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

  init(ctx: WorldRenderContext): void {
    this.context = ctx
    this.fallbackCircleTexture = this.createCircleTexture()

    // 预创建精灵对象池喵
    for (let i = 0; i < this.poolSize; i++) {
      const material = new THREE.SpriteMaterial({
        color: 0xffffff,
        sizeAttenuation: true,
        map: this.fallbackCircleTexture || undefined  // 使用后备纹理喵
      })
      const sprite = new THREE.Sprite(material)
      sprite.visible = false
      this.parentGroup.add(sprite)
      this.planetSpritePool.push(sprite)
    }

    console.log('LayerPlanetRenderer initialized')
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
      map: this.fallbackCircleTexture || undefined  // 使用后备纹理喵
    })
    const newSprite = new THREE.Sprite(material)
    this.parentGroup.add(newSprite)
    return newSprite
  }

  private releasePlanetSprite(sprite: THREE.Sprite): void {
    // 安全的类型检查喵
    if (sprite.material instanceof THREE.SpriteMaterial) {
      const material = sprite.material
      material.map = null
    } else {
      console.warn('Unexpected material type in planet sprite', sprite.material)
    }
    sprite.visible = false
    this.planetSpritePool.push(sprite)
  }

  private releaseTrailMesh(mesh: THREE.Mesh): void {
    mesh.visible = false
    mesh.parent?.remove(mesh)
    this.trailMeshPool.push(mesh)
  }

  private clearAllTrails(): void {
    for (const [id, trail] of this.activeTrailsByEntityId.entries()) {
      this.activeTrailsByEntityId.delete(id)
      this.releaseTrailMesh(trail)
    }
    this.positionHistory.clear()
    this.lastSampleMinuteByEntityId.clear()
  }

  private isPointInAabb(p: { x: number, y: number }, a: { minX: number, maxX: number, minY: number, maxY: number }): boolean {
    return p.x >= a.minX && p.x <= a.maxX && p.y >= a.minY && p.y <= a.maxY
  }

  private loadAndApplyTexture(material: THREE.SpriteMaterial, path: string): void {
    if (!this.context) return
    this.context.getTexture(path).then(t => {
      material.map = t
      material.needsUpdate = true
    }).catch(err => {
      console.warn(`Failed to load planet texture: ${path}`, err)
    })
  }

  private updateTrail(
    entityId: number,
    currentPos: { x: number; y: number },
    ctx: WorldRenderContext,
    isSelected: boolean,
    totalDays: number
  ): void {
    const TRAIL_OPACITY_BASE = 0.8
    const PIXEL_WIDTH = 3

    let history = this.positionHistory.get(entityId)
    if (!history) {
      history = []
      this.positionHistory.set(entityId, history)
    }

    const currentMinute = Math.floor(totalDays * 1440)
    const lastMinute = this.lastSampleMinuteByEntityId.get(entityId)

    if (lastMinute !== currentMinute) {
      this.lastSampleMinuteByEntityId.set(entityId, currentMinute)
      history.push({ x: currentPos.x, y: currentPos.y })

      if (history.length > LayerPlanetRenderer.MAX_TRAIL_POINTS) {
        history.shift()
      }
    }

    if (history.length < 2) {
      return
    }

    let trailMesh = this.activeTrailsByEntityId.get(entityId)
    if (!trailMesh) {
      trailMesh = this.acquireTrailMesh()
      this.activeTrailsByEntityId.set(entityId, trailMesh)
      this.parentGroup.add(trailMesh)
    }

    const zoom = ctx.zoom.value
    const FADE_START = 1_000
    const FADE_END = 100_000
    let trailOpacity = zoom < FADE_START
      ? TRAIL_OPACITY_BASE
      : zoom >= FADE_END
        ? 0
        : TRAIL_OPACITY_BASE * (1 - (zoom - FADE_START) / (FADE_END - FADE_START))

    if (isSelected && trailOpacity > 0) {
      trailOpacity = Math.min(1.0, trailOpacity * 1.5)
    }

    if (trailOpacity <= 0.01) {
      trailMesh.visible = false
      return
    }

    const worldWidth = PIXEL_WIDTH * zoom
    const geometry = trailMesh.geometry
    const posAttr = geometry.getAttribute('position') as THREE.BufferAttribute
    const positions = posAttr.array as Float32Array
    const alphaAttr = geometry.getAttribute('aAlpha') as THREE.BufferAttribute
    const alphas = alphaAttr.array as Float32Array

    for (let i = 0; i < history.length; i++) {
      const point = history[i]
      if (!point) break

      let dx = 0
      let dy = 0

      if (i < history.length - 1) {
        const nextPoint = history[i + 1]
        if (nextPoint) {
          dx = nextPoint.x - point.x
          dy = nextPoint.y - point.y
        }
      } else if (i > 0) {
        const previousPoint = history[i - 1]
        if (previousPoint) {
          dx = point.x - previousPoint.x
          dy = point.y - previousPoint.y
        }
      }

      const length = Math.sqrt(dx * dx + dy * dy) || 1
      const nx = (-dy / length) * (worldWidth / 2)
      const ny = (dx / length) * (worldWidth / 2)

      const vertexOffset = i * 6
      positions[vertexOffset] = point.x - nx
      positions[vertexOffset + 1] = point.y - ny
      positions[vertexOffset + 2] = -0.2
      positions[vertexOffset + 3] = point.x + nx
      positions[vertexOffset + 4] = point.y + ny
      positions[vertexOffset + 5] = -0.2

      const vertexAlpha = i / (history.length - 1 || 1)
      alphas[i * 2] = vertexAlpha
      alphas[i * 2 + 1] = vertexAlpha
    }

    posAttr.needsUpdate = true
    alphaAttr.needsUpdate = true
    geometry.setDrawRange(0, (history.length - 1) * 6)

    const material = trailMesh.material as THREE.ShaderMaterial & {
      uniforms: {
        uOpacity: { value: number }
        uColor: { value: THREE.Color }
      }
    }
    material.uniforms.uOpacity.value = trailOpacity
    material.uniforms.uColor.value.copy(TRAIL_BASE_COLOR)

    trailMesh.position.set(-ctx.cameraWorldPosGU.x, -ctx.cameraWorldPosGU.y, 0)
    trailMesh.visible = true
  }

  private acquireTrailMesh(): THREE.Mesh {
    const mesh = this.trailMeshPool.pop()
    if (mesh) {
      mesh.visible = true
      return mesh
    }

    const geometry = new THREE.BufferGeometry()
    const maxVertices = LayerPlanetRenderer.MAX_TRAIL_POINTS * 2
    geometry.setAttribute('position', new THREE.BufferAttribute(new Float32Array(maxVertices * 3), 3))
    geometry.setAttribute('aAlpha', new THREE.BufferAttribute(new Float32Array(maxVertices), 1))

    const indices = new Uint16Array((LayerPlanetRenderer.MAX_TRAIL_POINTS - 1) * 6)
    for (let i = 0; i < LayerPlanetRenderer.MAX_TRAIL_POINTS - 1; i++) {
      const vertex = i * 2
      const indexOffset = i * 6
      indices[indexOffset] = vertex
      indices[indexOffset + 1] = vertex + 1
      indices[indexOffset + 2] = vertex + 2
      indices[indexOffset + 3] = vertex + 2
      indices[indexOffset + 4] = vertex + 1
      indices[indexOffset + 5] = vertex + 3
    }
    geometry.setIndex(new THREE.BufferAttribute(indices, 1))

    const material = new THREE.ShaderMaterial({
      uniforms: {
        uColor: { value: TRAIL_BASE_COLOR.clone() },
        uOpacity: { value: 1.0 },
      },
      vertexShader: TRAIL_VERTEX_SHADER,
      fragmentShader: TRAIL_FRAGMENT_SHADER,
      transparent: true,
      depthWrite: false,
      side: THREE.DoubleSide,
      blending: THREE.AdditiveBlending,
    })

    const trailMesh = new THREE.Mesh(geometry, material)
    trailMesh.frustumCulled = false
    trailMesh.renderOrder = -1
    return trailMesh
  }

  update(ctx: WorldRenderContext, frame: WorldFrameState): void {
    const { entitiesById, selectedIds, cullingAabb, lod, totalDays } = frame
    const planetLod = lod.planet

    // LOD完全隐藏时回收所有对象
    if (!planetLod.visible) {
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
      if (!shouldRender(planetLod, isSelected)) continue

      const planetPos = ctx.getEntityWorldPosGU(entity.entityId)
      if (!planetPos) continue

      // 剔除检查（选中实体始终显示）
      if (!isSelected && !this.isPointInAabb(planetPos, cullingAabb)) {
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

    // 第二遍：更新可见实体的渲染数据
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
        size = getLodSize(planetLod, isSelected, radiusGU * 2)
      } else {
        size = MIN_TEXTURE_PIXEL_SIZE * ctx.zoom.value
      }

      let sprite = this.activePlanetSpritesByEntityId.get(entityId)
      if (!sprite) {
        sprite = this.acquirePlanetSprite()
        this.activePlanetSpritesByEntityId.set(entityId, sprite)
      }

      const material = sprite.material as THREE.SpriteMaterial

      if (useRealTexture) {
        if (material.map === this.fallbackCircleTexture) {
          material.map = null
          material.needsUpdate = true
        }
        if (details.surfaceTexturePath && (!material.map || !material.map.image)) {
          this.loadAndApplyTexture(material, details.surfaceTexturePath)
        }
        material.sizeAttenuation = true
      } else {
        if (this.fallbackCircleTexture && material.map !== this.fallbackCircleTexture) {
          material.map = this.fallbackCircleTexture
          material.needsUpdate = true
        }
        material.sizeAttenuation = false
      }

      // 透明度计算
      let opacity: number
      if (useRealTexture) {
        opacity = shouldShowEffects(planetLod, isSelected) ? 1.0 : 0.8
      } else {
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

      this.updateTrail(entityId, planetPos, ctx, isSelected, totalDays)
    }

    for (const [id, trail] of this.activeTrailsByEntityId.entries()) {
      if (!visibleEntityIds.has(id)) {
        this.activeTrailsByEntityId.delete(id)
        this.positionHistory.delete(id)
        this.lastSampleMinuteByEntityId.delete(id)
        this.releaseTrailMesh(trail)
      }
    }
  }

  private disposeSprite(sprite: THREE.Sprite): void {
    ;(sprite.material as THREE.Material).dispose()
    this.parentGroup.remove(sprite)
  }

  private disposeTrailMesh(mesh: THREE.Mesh): void {
    const materials = Array.isArray(mesh.material) ? mesh.material : [mesh.material]
    for (const material of materials) {
      material.dispose()
    }
    mesh.geometry.dispose()
    this.parentGroup.remove(mesh)
  }

  dispose(): void {
    // 防止重复清理喵
    if (this.disposed) {
      return
    }

    // 首先解除所有材质的纹理引用，防止双重释放喵
    const allSprites = [...this.planetSpritePool, ...this.activePlanetSpritesByEntityId.values()]
    for (const sprite of allSprites) {
      if (sprite.material instanceof THREE.SpriteMaterial) {
        const material = sprite.material
        material.map = null
      }
    }

    // 清理后备纹理喵
    if (this.fallbackCircleTexture) {
      this.fallbackCircleTexture.dispose()
      this.fallbackCircleTexture = null
    }

    // 清理对象池中的精灵喵
    for (const sprite of this.planetSpritePool) {
      this.disposeSprite(sprite)
    }
    this.planetSpritePool = []

    // 清理活跃精灵喵
    for (const sprite of this.activePlanetSpritesByEntityId.values()) {
      this.disposeSprite(sprite)
    }
    this.activePlanetSpritesByEntityId.clear()

    // 清理轨迹网格池喵
    for (const mesh of this.trailMeshPool) {
      this.disposeTrailMesh(mesh)
    }
    this.trailMeshPool = []

    // 清理活跃轨迹喵
    for (const mesh of this.activeTrailsByEntityId.values()) {
      this.disposeTrailMesh(mesh)
    }
    this.activeTrailsByEntityId.clear()

    // 清理历史数据喵
    this.positionHistory.clear()
    this.lastSampleMinuteByEntityId.clear()

    console.log('LayerPlanetRenderer disposed')
    this.disposed = true
  }
}

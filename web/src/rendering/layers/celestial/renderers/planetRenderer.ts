/**
 * @file planetRenderer.ts
 * @description 分层行星渲染器 - 使用 3D 球体渲染行星喵。
 *
 * @设计说明
 * - 使用 `SphereGeometry`（球体几何）+ `MeshBasicMaterial`（基础网格材质）渲染行星喵。
 * - 纹理由 `generatePlanetCanvas` 程序化生成，基于 (worldSeed, entityId) 确定性固定喵。
 * - z 轴位置 = -(ZLayer.CELESTIAL_PLANET_BASE + scaledRadius)，确保行星层与实体层分离喵。
 * - 后续可替换为 ShaderMaterial 实现实时动态效果喵。
 */

import * as THREE from 'three'
import type { WorldRenderContext, WorldFrameState } from '../../../worldRenderManager'
import type { PlanetDetails } from '../../../../net/snapshotWs'
import { shouldRender, getLodSize } from '../../../subsystems/lodSystem'
import { ZLayer } from '../../index'
import { getPlanetProfile } from './planetProfile'
import { generatePlanetCanvas } from './planetTextureGenerator'

const TRAIL_BASE_COLOR = new THREE.Color(0xffffff)

/** 球体细分段数（固定俯视下不需要太高） */
const PLANET_SPHERE_SEGMENTS = 24

/** 行星球体网格类型 */
type PlanetMesh = THREE.Mesh<THREE.SphereGeometry, THREE.MeshBasicMaterial>

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

  // 行星球体对象池
  private planetMeshPool: PlanetMesh[] = []
  private activePlanetMeshesByEntityId = new Map<number, PlanetMesh>()
  private sharedGeometry: THREE.SphereGeometry | null = null

  /** 程序化纹理缓存（entityId → texture），保证同一行星纹理不重复生成喵 */
  private textureCache = new Map<number, THREE.CanvasTexture>()

  private poolSize: number
  private disposed = false

  // 移动轨迹相关
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

  // ─── 初始化 ──────────────────────────────────────────────────────

  init(_ctx: WorldRenderContext): void {
    // 共享球体几何体，所有行星复用同一个喵
    this.sharedGeometry = new THREE.SphereGeometry(1, PLANET_SPHERE_SEGMENTS, PLANET_SPHERE_SEGMENTS / 2)

    // 预热对象池喵
    for (let i = 0; i < this.poolSize; i++) {
      this.planetMeshPool.push(this.createPlanetMesh())
    }

    console.log('LayerPlanetRenderer initialized (3D sphere mode)')
  }

  // ─── 对象池管理 ──────────────────────────────────────────────────

  /**
   * 创建一个新的行星球体网格喵。
   * 使用 MeshBasicMaterial + 顶点颜色实现渐变光照效果喵。
   */
  private createPlanetMesh(): PlanetMesh {
    if (!this.sharedGeometry) {
      throw new Error('sharedGeometry must be initialized before creating planet mesh')
    }

    const material = new THREE.MeshBasicMaterial({
      transparent: true,
      depthWrite: true,
    })

    const mesh = new THREE.Mesh(this.sharedGeometry, material)
    mesh.frustumCulled = false
    mesh.renderOrder = -1
    mesh.visible = false
    return mesh
  }

  private acquirePlanetMesh(): PlanetMesh {
    const mesh = this.planetMeshPool.pop()
    if (mesh) {
      mesh.visible = true
      return mesh
    }
    return this.createPlanetMesh()
  }

  private releasePlanetMesh(mesh: PlanetMesh): void {
    // 释放纹理引用（不 dispose，纹理由 textureCache 管理）喵
    if (mesh.material.map) {
      mesh.material.map = null
    }
    mesh.visible = false
    this.planetMeshPool.push(mesh)
  }

  // ─── 纹理管理 ──────────────────────────────────────────────────

  /**
   * 获取或生成行星纹理喵。
   * 纹理按 entityId 缓存，相同 (worldSeed, entityId) 永远返回同一纹理喵。
   */
  private getOrGenerateTexture(entityId: number, details: PlanetDetails, worldSeed: number): THREE.CanvasTexture {
    let texture = this.textureCache.get(entityId)
    if (texture) {
      return texture
    }

    const profile = getPlanetProfile(details)
    const canvas = generatePlanetCanvas(worldSeed, entityId, profile)
    texture = new THREE.CanvasTexture(canvas)
    texture.colorSpace = THREE.SRGBColorSpace
    texture.needsUpdate = true

    this.textureCache.set(entityId, texture)
    return texture
  }

  private isPointInAabb(p: { x: number, y: number }, a: { minX: number, maxX: number, minY: number, maxY: number }): boolean {
    return p.x >= a.minX && p.x <= a.maxX && p.y >= a.minY && p.y <= a.maxY
  }

  // ─── 轨迹管理（保持不变） ────────────────────────────────────────

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

      // 轨迹顶点使用相机相对坐标，避免 float32 精度问题喵
      const rp = ctx.toRenderPos(point)

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
      positions[vertexOffset] = rp.x - nx
      positions[vertexOffset + 1] = rp.y - ny
      positions[vertexOffset + 2] = -0.2
      positions[vertexOffset + 3] = rp.x + nx
      positions[vertexOffset + 4] = rp.y + ny
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

    // 轨迹跟随行星层 z 基准喵
    trailMesh.position.set(0, 0, -ZLayer.CELESTIAL_PLANET_BASE)
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

  // ─── 主更新逻辑 ──────────────────────────────────────────────────

  update(ctx: WorldRenderContext, frame: WorldFrameState): void {
    const { entitiesById, selectedIds, cullingAabb, lod, totalDays } = frame
    const planetLod = lod.planet

    // LOD 完全隐藏时回收所有对象喵
    if (!planetLod.visible) {
      this.clearAllTrails()
      for (const [id, mesh] of this.activePlanetMeshesByEntityId.entries()) {
        this.activePlanetMeshesByEntityId.delete(id)
        this.releasePlanetMesh(mesh)
      }
      return
    }

    // 第一遍：检查哪些实体需要渲染喵
    const visibleEntityIds = new Set<number>()

    for (const entity of entitiesById.values()) {
      if (entity.entityType !== 'PLANET') continue

      const isSelected = selectedIds.has(entity.entityId)
      if (!shouldRender(planetLod, isSelected)) continue

      const planetPos = ctx.getEntityWorldPosGU(entity.entityId)
      if (!planetPos) continue

      // 剔除检查（选中实体始终显示）喵
      if (!isSelected && !this.isPointInAabb(planetPos, cullingAabb)) {
        continue
      }

      visibleEntityIds.add(entity.entityId)
    }

    // 回收不在可见列表中的对象喵
    for (const [id, mesh] of this.activePlanetMeshesByEntityId.entries()) {
      if (!visibleEntityIds.has(id)) {
        this.activePlanetMeshesByEntityId.delete(id)
        this.releasePlanetMesh(mesh)
      }
    }

    // 第二遍：更新可见实体的渲染数据喵
    for (const entityId of visibleEntityIds) {
      const entity = entitiesById.get(entityId)
      if (!entity) continue

      const details = entity.details as PlanetDetails
      const isSelected = selectedIds.has(entityId)
      const planetPos = ctx.getEntityWorldPosGU(entityId)
      if (!planetPos) continue

      const radiusGU = details.radiusGU

      // 计算 LOD 缩放后的渲染直径喵
      const clampedDiameterGU = Math.max(radiusGU * 2, 10 * ctx.zoom.value)
      const size = getLodSize(planetLod, isSelected, clampedDiameterGU)
      const scaledRadius = size * 0.5

      // 获取或创建球体网格喵
      let mesh = this.activePlanetMeshesByEntityId.get(entityId)
      if (!mesh) {
        mesh = this.acquirePlanetMesh()
        this.activePlanetMeshesByEntityId.set(entityId, mesh)
        this.parentGroup.add(mesh)
      }

      // 应用程序化纹理（首次生成，后续从缓存读取）喵
      const texture = this.getOrGenerateTexture(entityId, details, ctx.worldSeed)
      if (mesh.material.map !== texture) {
        mesh.material.map = texture
        mesh.material.needsUpdate = true
      }

      // 透明度：远景行星淡出喵
      const diameterPx = size / ctx.zoom.value
      let opacity = 1.0
      if (diameterPx < 5) {
        const FADE_START = 1_000
        const FADE_END = 100_000
        const zoomValue = ctx.zoom.value
        if (zoomValue >= FADE_END) {
          opacity = 0
        } else if (zoomValue <= FADE_START) {
          opacity = 1
        } else {
          opacity = 1 - (zoomValue - FADE_START) / (FADE_END - FADE_START)
        }
      }
      mesh.material.opacity = opacity

      // 缩放球体到渲染尺寸喵
      mesh.scale.set(scaledRadius, scaledRadius, scaledRadius)

      // z 轴位置 = -(层基准 + 半径)，前表面对齐在层基准线喵
      const rp = ctx.toRenderPos(planetPos)
      mesh.position.set(
        rp.x,
        rp.y,
        -(ZLayer.CELESTIAL_PLANET_BASE + scaledRadius),
      )
      mesh.visible = true

      // 更新移动轨迹（历史点存储世界坐标，渲染时转相机相对）喵
      this.updateTrail(entityId, planetPos, ctx, isSelected, totalDays)
    }

    // 回收不可见实体的轨迹喵
    for (const [id, trail] of this.activeTrailsByEntityId.entries()) {
      if (!visibleEntityIds.has(id)) {
        this.activeTrailsByEntityId.delete(id)
        this.positionHistory.delete(id)
        this.lastSampleMinuteByEntityId.delete(id)
        this.releaseTrailMesh(trail)
      }
    }
  }

  // ─── 清理 ────────────────────────────────────────────────────────

  private disposePlanetMesh(mesh: PlanetMesh): void {
    // 不 dispose 共享几何体喵
    mesh.material.dispose()
    this.parentGroup.remove(mesh)
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

    // 清理对象池中的行星网格喵
    for (const mesh of this.planetMeshPool) {
      this.disposePlanetMesh(mesh)
    }
    this.planetMeshPool = []

    // 清理活跃行星网格喵
    for (const mesh of this.activePlanetMeshesByEntityId.values()) {
      this.disposePlanetMesh(mesh)
    }
    this.activePlanetMeshesByEntityId.clear()

    // 清理纹理缓存喵
    for (const texture of this.textureCache.values()) {
      texture.dispose()
    }
    this.textureCache.clear()

    // 清理共享几何体喵
    if (this.sharedGeometry) {
      this.sharedGeometry.dispose()
      this.sharedGeometry = null
    }

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

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
import { SECTOR_SIZE_GU } from '../../../hexSectorGeometry'
import { defaultGameTimeManager } from '../../../../game/time/GameTimeManager'

/** 轨道环采样点数（椭圆平滑度） */
const ORBIT_SEGMENTS = 512
/** 轨道环基础透明度 */
const ORBIT_BASE_OPACITY = 0.45
/** 轨道环淡出起始 zoom（超过此值开始淡出） */
const ORBIT_FADE_START_ZOOM = 1_000
/** 轨道环淡出结束 zoom（超过此值完全隐藏） */
const ORBIT_FADE_END_ZOOM = 100_000
/** 判定行星"在星区内"的距离阈值倍数（× SECTOR_SIZE_GU） */
const SECTOR_IN_RANGE_FACTOR = 1.5

/** 球体细分段数（固定俯视下不需要太高） */
const PLANET_SPHERE_SEGMENTS = 24

/** 行星球体网格类型 */
type PlanetMesh = THREE.Mesh<THREE.SphereGeometry, THREE.MeshBasicMaterial>

/** 轨道环类型（LineLoop 自动闭合首尾） */
type OrbitRing = THREE.LineLoop<THREE.BufferGeometry, THREE.LineBasicMaterial>

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

  // 轨道环相关
  private orbitRingPool: OrbitRing[] = []
  private activeOrbitRingsByEntityId = new Map<number, OrbitRing>()

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

  // ─── 轨道环管理 ────────────────────────────────────────────────

  /** 判断轨道中心是否处于某个星区范围内喵 */
  private isInSector(orbitCenter: { x: number; y: number }, sectorCenters: { x: number; y: number }[]): boolean {
    const threshold = SECTOR_SIZE_GU * SECTOR_IN_RANGE_FACTOR
    const thresholdSq = threshold * threshold
    for (const sc of sectorCenters) {
      const dx = orbitCenter.x - sc.x
      const dy = orbitCenter.y - sc.y
      if (dx * dx + dy * dy <= thresholdSq) return true
    }
    return false
  }

  private releaseOrbitRing(ring: OrbitRing): void {
    ring.visible = false
    this.orbitRingPool.push(ring)
  }

  private clearAllOrbitRings(): void {
    for (const [id, ring] of this.activeOrbitRingsByEntityId.entries()) {
      this.activeOrbitRingsByEntityId.delete(id)
      this.releaseOrbitRing(ring)
    }
  }

  private acquireOrbitRing(): OrbitRing {
    const ring = this.orbitRingPool.pop()
    if (ring) {
      ring.visible = true
      return ring
    }

    const positions = new Float32Array(ORBIT_SEGMENTS * 3)
    const geometry = new THREE.BufferGeometry()
    geometry.setAttribute('position', new THREE.BufferAttribute(positions, 3))

    const material = new THREE.LineBasicMaterial({
      color: 0xffffff,
      transparent: true,
      opacity: ORBIT_BASE_OPACITY,
      depthWrite: false,
      depthTest: false,
    })

    const line = new THREE.LineLoop(geometry, material)
    line.frustumCulled = false
    line.renderOrder = -2
    return line
  }

  /**
   * 更新行星轨道环喵。
   * 根据开普勒轨道参数生成椭圆轨道，仅在星区内显示喵。
   */
  private updateOrbitRing(
    entityId: number,
    details: PlanetDetails,
    ctx: WorldRenderContext,
    frame: WorldFrameState,
  ): void {
    const orbitLod = frame.lod.orbit
    // LOD 完全隐藏时回收喵
    if (!orbitLod.visible) {
      const existing = this.activeOrbitRingsByEntityId.get(entityId)
      if (existing) {
        this.activeOrbitRingsByEntityId.delete(entityId)
        this.releaseOrbitRing(existing)
      }
      return
    }

    // 获取轨道中心（恒星）位置喵
    const centerEntity = frame.entitiesById.get(details.orbitCenterEntityId)
    const centerPos = centerEntity?.posWorldGU
    if (!centerPos) return

    // 检查是否在星区内喵
    if (!this.isInSector(centerPos, frame.sectorCenters)) {
      const existing = this.activeOrbitRingsByEntityId.get(entityId)
      if (existing) {
        this.activeOrbitRingsByEntityId.delete(entityId)
        this.releaseOrbitRing(existing)
      }
      return
    }

    let ring = this.activeOrbitRingsByEntityId.get(entityId)
    if (!ring) {
      ring = this.acquireOrbitRing()
      this.parentGroup.add(ring)
      this.activeOrbitRingsByEntityId.set(entityId, ring)
    }

    // 计算轨道参数喵
    const a = details.semiMajorAxisGU
    const ecc = details.eccentricity
    const b = a * Math.sqrt(Math.max(0, 1 - ecc * ecc))
    const periapsisArgRad = (details.periapsisArgDeg * Math.PI) / 180

    // 生成椭圆轨道点（相机相对坐标）喵
    const posAttr = ring.geometry.getAttribute('position') as THREE.BufferAttribute
    const positions = posAttr.array as Float32Array
    const cosW = Math.cos(periapsisArgRad)
    const sinW = Math.sin(periapsisArgRad)

    // 先生成均匀分布的顶点喵
    for (let i = 0; i < ORBIT_SEGMENTS; i++) {
      const theta = (i / ORBIT_SEGMENTS) * Math.PI * 2
      const localX = a * Math.cos(theta)
      const localY = b * Math.sin(theta)
      const wx = localX * cosW - localY * sinW
      const wy = localX * sinW + localY * cosW
      const rp = ctx.toRenderPos({ x: centerPos.x + wx, y: centerPos.y + wy })
      positions[i * 3] = rp.x
      positions[i * 3 + 1] = rp.y
      positions[i * 3 + 2] = 0
    }

    // 计算行星当前精确位置，修正最近的顶点使其穿过行星中心喵
    defaultGameTimeManager.update()
    const totalDays = defaultGameTimeManager.getCurrentGameSeconds() / 86400
    const meanAnomalyRad = (details.meanAnomalyDegAtEpoch * Math.PI) / 180
    const periodDays = details.orbitalPeriodDays
    if (periodDays > 0) {
      const planetAngle = meanAnomalyRad + (totalDays / periodDays) * 2 * Math.PI
      const planetLocalX = a * Math.cos(planetAngle)
      const planetLocalY = b * Math.sin(planetAngle)
      const planetWx = planetLocalX * cosW - planetLocalY * sinW
      const planetWy = planetLocalX * sinW + planetLocalY * cosW
      const planetRp = ctx.toRenderPos({ x: centerPos.x + planetWx, y: centerPos.y + planetWy })

      // 找最近的顶点索引喵
      const segAngle = (2 * Math.PI) / ORBIT_SEGMENTS
      const normalizedAngle = ((planetAngle % (2 * Math.PI)) + 2 * Math.PI) % (2 * Math.PI)
      const nearestIdx = Math.round(normalizedAngle / segAngle) % ORBIT_SEGMENTS

      // 修正该顶点为行星精确位置喵
      positions[nearestIdx * 3] = planetRp.x
      positions[nearestIdx * 3 + 1] = planetRp.y
    }

    posAttr.needsUpdate = true

    // 轨道 z = 行星中心 z，让轨道穿过行星中心喵
    const planetRadiusGU = details.radiusGU
    const planetClampedDiamGU = Math.max(planetRadiusGU * 2, 10 * ctx.zoom.value)
    const planetSize = getLodSize(frame.lod.planet, false, planetClampedDiamGU)
    const planetScaledRadius = planetSize * 0.5
    ring.position.set(0, 0, -(ZLayer.CELESTIAL_PLANET_BASE + planetScaledRadius))

    // 透明度：随 zoom 淡出喵
    const zoom = ctx.zoom.value
    let opacity = ORBIT_BASE_OPACITY
    if (zoom > ORBIT_FADE_START_ZOOM) {
      opacity = zoom >= ORBIT_FADE_END_ZOOM
        ? 0
        : ORBIT_BASE_OPACITY * (1 - (zoom - ORBIT_FADE_START_ZOOM) / (ORBIT_FADE_END_ZOOM - ORBIT_FADE_START_ZOOM))
    }

    const material = ring.material
    material.opacity = opacity
    material.color.set(0xffffff)

    ring.visible = opacity > 0.01
  }

  // ─── 主更新逻辑 ──────────────────────────────────────────────────

  update(ctx: WorldRenderContext, frame: WorldFrameState): void {
    const { entitiesById, selectedIds, cullingAabb, lod } = frame
    const planetLod = lod.planet

    // LOD 完全隐藏时回收所有对象喵
    if (!planetLod.visible) {
      this.clearAllOrbitRings()
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
    }

    // ── 独立更新轨道环（不受行星剔除影响，遍历所有行星）──喵
    const orbitVisibleIds = new Set<number>()
    for (const entity of entitiesById.values()) {
      if (entity.entityType !== 'PLANET') continue
      orbitVisibleIds.add(entity.entityId)
      this.updateOrbitRing(entity.entityId, entity.details as PlanetDetails, ctx, frame)
    }

    // 回收不再需要的轨道环喵
    for (const [id, ring] of this.activeOrbitRingsByEntityId.entries()) {
      if (!orbitVisibleIds.has(id)) {
        this.activeOrbitRingsByEntityId.delete(id)
        this.releaseOrbitRing(ring)
      }
    }
  }

  // ─── 清理 ────────────────────────────────────────────────────────

  private disposePlanetMesh(mesh: PlanetMesh): void {
    // 不 dispose 共享几何体喵
    mesh.material.dispose()
    this.parentGroup.remove(mesh)
  }

  private disposeOrbitRing(ring: OrbitRing): void {
    ring.material.dispose()
    ring.geometry.dispose()
    this.parentGroup.remove(ring)
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

    // 清理轨道环池喵
    for (const ring of this.orbitRingPool) {
      this.disposeOrbitRing(ring)
    }
    this.orbitRingPool = []

    // 清理活跃轨道环喵
    for (const ring of this.activeOrbitRingsByEntityId.values()) {
      this.disposeOrbitRing(ring)
    }
    this.activeOrbitRingsByEntityId.clear()

    console.log('LayerPlanetRenderer disposed')
    this.disposed = true
  }
}

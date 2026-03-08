/**
 * @file shipRenderer.ts
 *
 * @description
 * 舰船渲染器（ShipRenderer）喵。
 *
 * 作用喵：
 * - 渲染实体快照中的 SHIP（舰船实体）喵。
 * - 对初始舰船（customFlags 包含 INITIAL_SPAWN_SHIP）使用三角形占位渲染喵。
 * - 提供对象池复用，降低频繁创建/销毁 Mesh 的开销喵。
 */
import * as THREE from 'three'
import type { WorldRenderContext, WorldFrameState } from '../worldRenderManager'
import type { WorldRenderSubsystem } from './worldRenderSubsystem'
import { shouldRender } from './lodSystem'

/**
 * 初始舰船标记常量（与后端固定 flag 一致）喵。
 */
const INITIAL_SPAWN_SHIP_FLAG = 'INITIAL_SPAWN_SHIP'

/**
 * ShipRenderer（舰船渲染器）喵。
 */
export class ShipRenderer implements WorldRenderSubsystem {
  private readonly shipPool: THREE.Mesh[] = []
  private readonly activeByEntityId = new Map<number, THREE.Mesh>()

  /**
   * 舰船渲染共用三角形几何（朝上箭头形）喵。
   */
  private shipTriangleGeometry: THREE.BufferGeometry | null = null

  init(_ctx: WorldRenderContext): void {
    // 以世界单位构建一个等腰三角形，占位表示舰船朝向喵。
    const geometry = new THREE.BufferGeometry()
    const vertices = new Float32Array([
      0, 9, 0,
      -6, -6, 0,
      6, -6, 0,
    ])
    geometry.setAttribute('position', new THREE.BufferAttribute(vertices, 3))
    geometry.computeVertexNormals()
    this.shipTriangleGeometry = geometry

    // 预热少量对象池喵。
    for (let i = 0; i < 16; i++) {
      this.shipPool.push(this.createShipMesh())
    }
  }

  update(ctx: WorldRenderContext, frame: WorldFrameState): void {
    const { entitiesById, selectedIds, cullingAabb, lod } = frame

    // 复用行星 LOD（缩放级别）可见性门槛作为临时舰船门槛喵。
    const shipLod = lod.planet
    const visibleIds = new Set<number>()

    for (const entity of entitiesById.values()) {
      if (entity.entityType !== 'SHIP') {
        continue
      }

      const isSelected = selectedIds.has(entity.entityId)
      if (!shouldRender(shipLod, isSelected)) {
        continue
      }

      const pos = ctx.getEntityWorldPosGU(entity.entityId)
      if (!pos) {
        continue
      }

      if (!isSelected && !isPointInAabb(pos, cullingAabb)) {
        continue
      }

      visibleIds.add(entity.entityId)

      let mesh = this.activeByEntityId.get(entity.entityId)
      if (!mesh) {
        mesh = this.acquireShipMesh()
        this.activeByEntityId.set(entity.entityId, mesh)
        ctx.entitiesGroup.add(mesh)
      }

      const detailAny: any = entity.details
      const flags: string[] = Array.isArray(detailAny?.customFlags) ? detailAny.customFlags : []
      const isInitialShip = flags.includes(INITIAL_SPAWN_SHIP_FLAG)

      // 初始舰船：青蓝色；其他舰船：浅灰色喵。
      const material = mesh.material as THREE.MeshBasicMaterial
      material.color.set(isInitialShip ? 0x56d7ff : 0xc8d0d8)

      // 固定尺度：不因选中状态变化，保持沉浸感喵。
      const baseScale = isInitialShip ? 1.2 : 1.0
      mesh.scale.set(baseScale, baseScale, 1)

      // 按后端下发朝向角（headingDeg）旋转：几何默认朝 +Y，需减 90 度对齐 +X 基准喵。
      const headingDeg = Number(detailAny?.headingDeg ?? 0)
      mesh.rotation.z = THREE.MathUtils.degToRad(headingDeg - 90)

      mesh.position.set(pos.x - ctx.cameraWorldPosGU.x, pos.y - ctx.cameraWorldPosGU.y, 0.15)
      mesh.visible = true
    }

    // 回收不可见舰船喵。
    for (const [id, mesh] of this.activeByEntityId.entries()) {
      if (!visibleIds.has(id)) {
        this.activeByEntityId.delete(id)
        this.releaseShipMesh(mesh)
      }
    }
  }

  dispose(_ctx: WorldRenderContext): void {
    for (const mesh of this.shipPool) {
      ;(mesh.material as THREE.Material).dispose()
    }
    for (const mesh of this.activeByEntityId.values()) {
      ;(mesh.material as THREE.Material).dispose()
      mesh.parent?.remove(mesh)
    }
    this.activeByEntityId.clear()

    if (this.shipTriangleGeometry) {
      this.shipTriangleGeometry.dispose()
      this.shipTriangleGeometry = null
    }
  }

  /**
   * 从对象池获取舰船 Mesh（网格）喵。
   */
  private acquireShipMesh(): THREE.Mesh {
    const mesh = this.shipPool.pop()
    if (mesh) {
      mesh.visible = true
      return mesh
    }
    return this.createShipMesh()
  }

  /**
   * 归还舰船 Mesh 到对象池喵。
   */
  private releaseShipMesh(mesh: THREE.Mesh): void {
    mesh.visible = false
    mesh.parent?.remove(mesh)
    this.shipPool.push(mesh)
  }

  /**
   * 创建舰船三角形占位 Mesh 喵。
   */
  private createShipMesh(): THREE.Mesh {
    const geometry = this.shipTriangleGeometry ?? new THREE.BufferGeometry()
    const material = new THREE.MeshBasicMaterial({
      color: 0x56d7ff,
      transparent: true,
      opacity: 0.95,
      depthWrite: false,
      side: THREE.DoubleSide,
    })
    const mesh = new THREE.Mesh(geometry, material)
    mesh.frustumCulled = false
    mesh.visible = false
    return mesh
  }
}

/**
 * 点是否在 AABB（轴对齐包围盒）中喵。
 */
function isPointInAabb(
  p: { x: number; y: number },
  a: { minX: number; maxX: number; minY: number; maxY: number },
): boolean {
  return p.x >= a.minX && p.x <= a.maxX && p.y >= a.minY && p.y <= a.maxY
}

import * as THREE from 'three'
import type { WorldRenderContext, WorldFrameState } from '../../../worldRenderManager'
import { shouldRender } from '@/rendering/subsystems/lodSystem'
import { getInterpolatedEntityDisplayPosition, recordRenderedEntityPose } from '@/game/world'

const INITIAL_SPAWN_SHIP_FLAG = 'INITIAL_SPAWN_SHIP'

function isPointInAabb(
  point: { x: number; y: number },
  aabb: { minX: number; maxX: number; minY: number; maxY: number },
): boolean {
  return (
    point.x >= aabb.minX &&
    point.x <= aabb.maxX &&
    point.y >= aabb.minY &&
    point.y <= aabb.maxY
  )
}

export class LayerShipRenderer {
  private readonly shipPool: THREE.Mesh[] = []
  private readonly activeByEntityId = new Map<number, THREE.Mesh>()
  private readonly pathLinePool: THREE.Line[] = []
  private readonly activePathByEntityId = new Map<number, THREE.Line>()
  private shipTriangleGeometry: THREE.BufferGeometry | null = null
  private pathLineMaterial: THREE.LineBasicMaterial | null = null
  private readonly layerGroup: THREE.Group

  constructor(layerGroup: THREE.Group) {
    this.layerGroup = layerGroup
  }

  enablePrediction(_enabled: boolean): void { }

  init(): void {
    const geometry = new THREE.BufferGeometry()
    geometry.setAttribute(
      'position',
      new THREE.BufferAttribute(
        new Float32Array([
          0, 9, 0,
          -6, -6, 0,
          6, -6, 0,
        ]),
        3,
      ),
    )
    geometry.computeVertexNormals()
    this.shipTriangleGeometry = geometry

    this.pathLineMaterial = new THREE.LineBasicMaterial({
      color: 0x56d7ff,
      transparent: true,
      opacity: 0.6,
      depthWrite: false,
    })

    for (let i = 0; i < 16; i++) {
      this.shipPool.push(this.createShipMesh())
    }
  }

  update(ctx: WorldRenderContext, frame: WorldFrameState): void {
    const { entitiesById, selectedIds, cullingAabb } = frame
    const visibleIds = new Set<number>()

    const shipLod: import('@/rendering/subsystems/lodSystem').EntityLodState = {
      level: 0,
      visible: true,
      params: {
        sizeScale: 1,
        textureQuality: 1,
        showLabel: true,
        showEffects: true,
        showDetails: true,
      },
    }

    for (const entity of entitiesById.values()) {
      if (entity.entityType !== 'SHIP') {
        continue
      }

      const isSelected = selectedIds.has(entity.entityId)
      if (!shouldRender(shipLod, isSelected)) {
        continue
      }

      const displayPose = getInterpolatedEntityDisplayPosition(entity.entityId)
      const shipPos = displayPose?.position ?? entity.posWorldGU
      if (!shipPos) {
        continue
      }

      if (!isSelected && !isPointInAabb(shipPos, cullingAabb)) {
        continue
      }

      const details = entity.details as any
      const headingDeg = Number(displayPose?.headingDeg ?? details?.headingDeg ?? 0)
      const isMoving = displayPose?.isMoving ?? (details?.isMoving === true)
      const movementTarget = displayPose?.movementTarget ?? details?.movementTarget

      visibleIds.add(entity.entityId)

      let mesh = this.activeByEntityId.get(entity.entityId)
      if (!mesh) {
        mesh = this.acquireShipMesh()
        this.activeByEntityId.set(entity.entityId, mesh)
        this.layerGroup.add(mesh)
      }

      const flags: string[] = Array.isArray(details?.customFlags) ? details.customFlags : []
      const isInitialShip = flags.includes(INITIAL_SPAWN_SHIP_FLAG)
      const material = mesh.material as THREE.MeshBasicMaterial
      material.color.set(isInitialShip ? 0x56d7ff : 0xc8d0d8)
      mesh.scale.set(isInitialShip ? 1.2 : 1, isInitialShip ? 1.2 : 1, 1)
      mesh.rotation.z = THREE.MathUtils.degToRad(headingDeg - 90)
      const rp = ctx.toRenderPos(shipPos)
      mesh.position.set(rp.x, rp.y, 0)
      mesh.visible = true
      recordRenderedEntityPose(entity.entityId, shipPos, headingDeg)

      if (isSelected && isMoving && movementTarget) {
        this.updatePathLine(ctx, entity.entityId, shipPos, movementTarget)
      } else {
        this.removePathLine(ctx, entity.entityId)
      }
    }

    for (const [entityId, line] of this.activePathByEntityId.entries()) {
      if (!visibleIds.has(entityId) || !selectedIds.has(entityId)) {
        this.activePathByEntityId.delete(entityId)
        this.releasePathLine(line)
        line.parent?.remove(line)
      }
    }

    for (const [entityId, mesh] of this.activeByEntityId.entries()) {
      if (visibleIds.has(entityId)) {
        continue
      }
      this.activeByEntityId.delete(entityId)
      this.releaseShipMesh(mesh)
    }
  }

  dispose(): void {
    for (const mesh of this.shipPool) {
      ; (mesh.material as THREE.Material).dispose()
    }
    for (const mesh of this.activeByEntityId.values()) {
      ; (mesh.material as THREE.Material).dispose()
      mesh.parent?.remove(mesh)
    }
    this.activeByEntityId.clear()

    for (const line of this.pathLinePool) {
      line.geometry.dispose()
    }
    for (const line of this.activePathByEntityId.values()) {
      line.geometry.dispose()
      line.parent?.remove(line)
    }
    this.activePathByEntityId.clear()

    this.shipTriangleGeometry?.dispose()
    this.shipTriangleGeometry = null
    this.pathLineMaterial?.dispose()
    this.pathLineMaterial = null
  }

  private acquireShipMesh(): THREE.Mesh {
    const mesh = this.shipPool.pop()
    if (mesh) {
      mesh.visible = true
      return mesh
    }
    return this.createShipMesh()
  }

  private releaseShipMesh(mesh: THREE.Mesh): void {
    mesh.visible = false
    mesh.parent?.remove(mesh)
    this.shipPool.push(mesh)
  }

  private createShipMesh(): THREE.Mesh {
    const material = new THREE.MeshBasicMaterial({
      color: 0x56d7ff,
      transparent: true,
      opacity: 0.95,
      depthWrite: false,
      side: THREE.DoubleSide,
    })
    const mesh = new THREE.Mesh(this.shipTriangleGeometry ?? new THREE.BufferGeometry(), material)
    mesh.frustumCulled = false
    mesh.visible = false
    return mesh
  }

  private updatePathLine(
    ctx: WorldRenderContext,
    entityId: number,
    shipPos: { x: number; y: number },
    targetPos: { x: number; y: number },
  ): void {
    let line = this.activePathByEntityId.get(entityId)
    if (!line) {
      line = this.acquirePathLine()
      this.activePathByEntityId.set(entityId, line)
      this.layerGroup.add(line)
    }

    // 路径线端点使用相机相对坐标喵
    const rp = ctx.toRenderPos(shipPos)
    const tp = ctx.toRenderPos(targetPos)
    const geometry = line.geometry as THREE.BufferGeometry
    geometry.setAttribute(
      'position',
      new THREE.BufferAttribute(
        new Float32Array([
          rp.x, rp.y, 0.1,
          tp.x, tp.y, 0.1,
        ]),
        3,
      ),
    )
    geometry.attributes.position!.needsUpdate = true
    line.visible = true
  }

  private removePathLine(_ctx: WorldRenderContext, entityId: number): void {
    const line = this.activePathByEntityId.get(entityId)
    if (!line) {
      return
    }
    this.activePathByEntityId.delete(entityId)
    this.releasePathLine(line)
    line.parent?.remove(line)
  }

  private acquirePathLine(): THREE.Line {
    const line = this.pathLinePool.pop()
    if (line) {
      line.visible = true
      return line
    }
    return this.createPathLine()
  }

  private releasePathLine(line: THREE.Line): void {
    line.visible = false
    line.parent?.remove(line)
    this.pathLinePool.push(line)
  }

  private createPathLine(): THREE.Line {
    const line = new THREE.Line(
      new THREE.BufferGeometry(),
      this.pathLineMaterial ??
      new THREE.LineBasicMaterial({
        color: 0x56d7ff,
        transparent: true,
        opacity: 0.6,
        depthWrite: false,
      }),
    )
    line.frustumCulled = false
    line.visible = false
    return line
  }
}

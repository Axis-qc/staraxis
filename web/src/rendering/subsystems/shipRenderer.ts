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
import { logger } from '../../utils/logger'

/**
 * 初始舰船标记常量（与后端固定 flag 一致）喵。
 */
const INITIAL_SPAWN_SHIP_FLAG = 'INITIAL_SPAWN_SHIP'

/**
 * 舰船渲染状态（用于平滑移动和位置同步）喵。
 *
 * 策略：插值而非预测喵。
 * - targetPos: 最新权威位置（快照位置）喵。
 * - displayPos: 当前显示位置，每帧向 targetPos 插值移动喵。
 * - 这样保证显示位置不会偏离权威位置太远，同时保持平滑喵。
 */
interface ShipRenderState {
  /** 目标位置（最新权威位置）喵。 */
  targetPos: { x: number; y: number }
  /** 当前显示位置（向 targetPos 插值）喵。 */
  displayPos: { x: number; y: number }
  /** 是否正在移动喵。 */
  isMoving: boolean
  /** 上次收到权威坐标的时间戳（performance.now()）喵。 */
  lastAuthTime: number
  /** 上次记录的权威isMoving状态（用于调试日志去重）喵。 */
  lastLoggedAuthIsMoving?: boolean
}

/**
 * ShipRenderer（舰船渲染器）喵。
 *
 * 渲染策略喵：
 * - 使用"插值"而非"预测"，避免前端后端计算偏差喵。
 * - 保存最新权威位置作为 targetPos，当前显示位置 displayPos 向其平滑插值喵。
 * - 每帧 displayPos 以固定速度向 targetPos 移动，保证平滑且不会偏离太远喵。
 * - 收到新快照时更新 targetPos，displayPos 继续向新目标平滑移动喵。
 */
export class ShipRenderer implements WorldRenderSubsystem {
  private readonly shipPool: THREE.Mesh[] = []
  private readonly activeByEntityId = new Map<number, THREE.Mesh>()

  /**
   * 路径线条对象池喵。
   */
  private readonly pathLinePool: THREE.Line[] = []
  private readonly activePathByEntityId = new Map<number, THREE.Line>()

  /**
   * 舰船渲染状态映射（entityId -> renderState）喵。
   */
  private readonly renderStateById = new Map<number, ShipRenderState>()

  /**
   * 舰船渲染共用三角形几何（朝上箭头形）喵。
   */
  private shipTriangleGeometry: THREE.BufferGeometry | null = null

  /**
   * 路径线条共用材质喵。
   */
  private pathLineMaterial: THREE.LineBasicMaterial | null = null

  /**
   * 插值速度（GU/毫秒）：displayPos 向 targetPos 移动的速度喵。
   * 较高的值 = 更快跟上权威位置，但可能不够平滑喵。
   * 较低的值 = 更平滑，但延迟更大喵。
   */
  private readonly INTERPOLATION_SPEED_GU_PER_MS = 0.05

  /**
   * 最大插值距离（GU）：当差异超过此值时直接跳到目标位置喵。
   */
  private readonly MAX_INTERPOLATION_DISTANCE_GU = 100.0

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

    // 创建路径线条共用材质喵
    this.pathLineMaterial = new THREE.LineBasicMaterial({
      color: 0x56d7ff,
      transparent: true,
      opacity: 0.6,
      depthWrite: false,
    })

    // 预热少量对象池喵。
    for (let i = 0; i < 16; i++) {
      this.shipPool.push(this.createShipMesh())
    }
  }

  update(ctx: WorldRenderContext, frame: WorldFrameState): void {
    const { entitiesById, selectedIds, cullingAabb } = frame

    // 当前渲染时间戳喵
    const now = performance.now()

    // 舰船 LOD：始终可见喵
    const shipLod: import('./lodSystem').EntityLodState = {
      level: 0,
      visible: true,
      params: {
        sizeScale: 1.0,
        textureQuality: 1.0,
        showLabel: true,
        showEffects: true,
        showDetails: true,
      },
    }
    const visibleIds = new Set<number>()

    // 调试：统计舰船数量喵
    let shipCount = 0
    let renderedCount = 0

    for (const entity of entitiesById.values()) {
      if (entity.entityType !== 'SHIP') {
        continue
      }
      shipCount++

      const isSelected = selectedIds.has(entity.entityId)
      if (!shouldRender(shipLod, isSelected)) {
        continue
      }

      // 获取快照中的权威位置喵
      const authPos = entity.posWorldGU
      if (!authPos) {
        console.log(`[ShipRenderer] Ship ${entity.entityId} has no position`)
        continue
      }

      const detailAny: any = entity.details
      const authIsMoving = detailAny?.isMoving === true
      const headingDeg = Number(detailAny?.headingDeg ?? 0)

      // 获取或创建渲染状态喵
      let renderState = this.renderStateById.get(entity.entityId)

      if (!renderState) {
        // 首次渲染该舰船，初始化渲染状态喵
        // displayPos 和 targetPos 都初始化为权威位置喵
        renderState = {
          targetPos: { x: authPos.x, y: authPos.y },
          displayPos: { x: authPos.x, y: authPos.y },
          isMoving: authIsMoving,
          lastAuthTime: now,
        }
        this.renderStateById.set(entity.entityId, renderState)
      } else {
        // 更新目标位置为最新权威位置喵
        renderState.targetPos = { x: authPos.x, y: authPos.y }
        renderState.isMoving = authIsMoving
        renderState.lastAuthTime = now

        // 计算当前显示位置与目标位置的距离喵
        const dx = renderState.targetPos.x - renderState.displayPos.x
        const dy = renderState.targetPos.y - renderState.displayPos.y
        const distance = Math.sqrt(dx * dx + dy * dy)

        // 如果距离超过阈值，直接跳到目标位置（防止累积误差）喵
        if (distance > this.MAX_INTERPOLATION_DISTANCE_GU) {
          renderState.displayPos = { x: renderState.targetPos.x, y: renderState.targetPos.y }
        } else if (distance > 0.01) {
          // 否则平滑插值向目标位置移动喵
          // 计算每帧移动的距离（基于 INTERPOLATION_SPEED_GU_PER_MS）喵
          const frameTimeMs = 16.67 // 假设 60 FPS，每帧约 16.67ms
          const maxMoveDistance = this.INTERPOLATION_SPEED_GU_PER_MS * frameTimeMs

          // 如果距离很小，直接到达；否则按比例移动喵
          if (distance <= maxMoveDistance) {
            renderState.displayPos = { x: renderState.targetPos.x, y: renderState.targetPos.y }
          } else {
            const ratio = maxMoveDistance / distance
            renderState.displayPos.x += dx * ratio
            renderState.displayPos.y += dy * ratio
          }
        }
      }

      // 使用显示位置进行视锥剔除检查喵
      if (!isSelected && !isPointInAabb(renderState.displayPos, cullingAabb)) {
        continue
      }

      visibleIds.add(entity.entityId)

      let mesh = this.activeByEntityId.get(entity.entityId)
      if (!mesh) {
        mesh = this.acquireShipMesh()
        this.activeByEntityId.set(entity.entityId, mesh)
        ctx.entitiesGroup.add(mesh)
      }

      const flags: string[] = Array.isArray(detailAny?.customFlags) ? detailAny.customFlags : []
      const isInitialShip = flags.includes(INITIAL_SPAWN_SHIP_FLAG)

      // 初始舰船：青蓝色；其他舰船：浅灰色喵。
      const material = mesh.material as THREE.MeshBasicMaterial
      material.color.set(isInitialShip ? 0x56d7ff : 0xc8d0d8)

      // 固定尺度：不因选中状态变化，保持沉浸感喵。
      const baseScale = isInitialShip ? 1.2 : 1.0
      mesh.scale.set(baseScale, baseScale, 1)

      // 按后端下发朝向角（headingDeg）旋转：几何默认朝 +Y，需减 90 度对齐 +X 基准喵。
      mesh.rotation.z = THREE.MathUtils.degToRad(headingDeg - 90)

      // 使用显示位置设置 Mesh 位置喵
      mesh.position.set(
        renderState.displayPos.x - ctx.cameraWorldPosGU.x,
        renderState.displayPos.y - ctx.cameraWorldPosGU.y,
        0.15
      )
      mesh.visible = true
      renderedCount++

      // 处理移动路径显示（仅当选中且正在移动时）喵
      const movementTarget = detailAny?.movementTarget

      // 调试：只在选中且isMoving状态变化时打印日志喵
      if (isSelected && renderState.lastLoggedAuthIsMoving !== authIsMoving) {
        renderState.lastLoggedAuthIsMoving = authIsMoving
        logger.info('ShipRenderer-Path', `ship=${entity.entityId} isMoving状态变化: ${!authIsMoving ? 'false->' : ''}true hasTarget=${!!movementTarget}`)
        if (authIsMoving && movementTarget) {
          logger.info('MoveShip-Trace', `前端渲染路径 ship=${entity.entityId} 目标=(${movementTarget.x.toFixed(0)},${movementTarget.y.toFixed(0)}) 时间=${performance.now().toFixed(0)}ms`)
        }
      }

      if (isSelected && renderState.isMoving && movementTarget) {
        this.updatePathLine(ctx, entity.entityId, renderState.displayPos, movementTarget)
      } else {
        this.removePathLine(ctx, entity.entityId)
      }
    }

    // 调试：每60帧输出一次统计喵
    if (shipCount > 0 && Math.random() < 0.01) {
      console.log(`[ShipRenderer] Ships in frame: ${shipCount}, Rendered: ${renderedCount}`)
    }

    // 回收不可见舰船及其路径喵
    for (const [id, line] of this.activePathByEntityId.entries()) {
      if (!visibleIds.has(id) || !selectedIds.has(id)) {
        this.activePathByEntityId.delete(id)
        this.releasePathLine(line)
        line.parent?.remove(line)
      }
    }
    for (const [id, mesh] of this.activeByEntityId.entries()) {
      if (!visibleIds.has(id)) {
        this.activeByEntityId.delete(id)
        this.releaseShipMesh(mesh)
        // 清理渲染状态喵
        this.renderStateById.delete(id)
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

    // 清理路径线条对象池喵
    for (const line of this.pathLinePool) {
      line.geometry.dispose()
    }
    for (const line of this.activePathByEntityId.values()) {
      line.geometry.dispose()
      line.parent?.remove(line)
    }
    this.activePathByEntityId.clear()

    if (this.shipTriangleGeometry) {
      this.shipTriangleGeometry.dispose()
      this.shipTriangleGeometry = null
    }

    if (this.pathLineMaterial) {
      this.pathLineMaterial.dispose()
      this.pathLineMaterial = null
    }

    // 清理渲染状态映射喵
    this.renderStateById.clear()
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

  /**
   * 更新或创建舰船的移动路径线条喵。
   */
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
      ctx.entitiesGroup.add(line)
    }

    // 更新路径几何体（从舰船位置到目标位置）喵
    const positions = new Float32Array([
      shipPos.x - ctx.cameraWorldPosGU.x, shipPos.y - ctx.cameraWorldPosGU.y, 0.1,
      targetPos.x - ctx.cameraWorldPosGU.x, targetPos.y - ctx.cameraWorldPosGU.y, 0.1,
    ])
    const geometry = line.geometry as THREE.BufferGeometry
    geometry.setAttribute('position', new THREE.BufferAttribute(positions, 3))
    geometry.attributes.position.needsUpdate = true

    line.visible = true
  }

  /**
   * 移除舰船的移动路径线条喵。
   */
  private removePathLine(ctx: WorldRenderContext, entityId: number): void {
    const line = this.activePathByEntityId.get(entityId)
    if (line) {
      this.activePathByEntityId.delete(entityId)
      this.releasePathLine(line)
      line.parent?.remove(line)
    }
  }

  /**
   * 从对象池获取路径线条喵。
   */
  private acquirePathLine(): THREE.Line {
    const line = this.pathLinePool.pop()
    if (line) {
      line.visible = true
      return line
    }
    return this.createPathLine()
  }

  /**
   * 归还路径线条到对象池喵。
   */
  private releasePathLine(line: THREE.Line): void {
    line.visible = false
    line.parent?.remove(line)
    this.pathLinePool.push(line)
  }

  /**
   * 创建路径线条喵。
   */
  private createPathLine(): THREE.Line {
    const geometry = new THREE.BufferGeometry()
    const material = this.pathLineMaterial ?? new THREE.LineBasicMaterial({
      color: 0x56d7ff,
      transparent: true,
      opacity: 0.6,
      depthWrite: false,
    })
    const line = new THREE.Line(geometry, material)
    line.frustumCulled = false
    line.visible = false
    return line
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

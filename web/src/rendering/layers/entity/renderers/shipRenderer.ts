/**
 * @file shipRenderer.ts
 *
 * @description
 * 舰船渲染器适配层版本（LayerShipRenderer）喵。
 * 基于原有ShipRenderer重构，适配分层架构中的EntityLayer喵。
 *
 * 作用喵：
 * - 渲染实体快照中的 SHIP（舰船实体）喵。
 * - 对初始舰船（customFlags 包含 INITIAL_SPAWN_SHIP）使用三角形占位渲染喵。
 * - 提供对象池复用，降低频繁创建/销毁 Mesh 的开销喵。
 * - 支持预测模式和插值平滑移动喵。
 */

import * as THREE from 'three'
import type { WorldRenderContext, WorldFrameState } from '../../../worldRenderManager'
import { shouldRender } from '@/rendering/subsystems/lodSystem'
import { logger } from '@/utils/logger'
import { ShipMovementSystemFrontend, type ShipState } from '@/game/systems/ShipMovementSystemFrontend'
import { defaultGameTimeManager } from '@/game/time/GameTimeManager'
import { defaultPredictionCorrector } from '@/game/prediction/PredictionCorrector'

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
  /** 预测状态（前端移动计算）喵。 */
  predictionState?: ShipState
  /** 预测位置（前端计算）喵。 */
  predictedPosition?: { x: number; y: number }
  /** 预测速度（前端计算）喵。 */
  predictedVelocity?: { x: number; y: number } | null
  /** 最后预测更新时间戳（performance.now()）喵。 */
  lastPredictionTimeMs?: number
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

/**
 * LayerShipRenderer（分层架构舰船渲染器）喵。
 *
 * 渲染策略喵：
 * - 使用"插值"而非"预测"，避免前端后端计算偏差喵。
 * - 保存最新权威位置作为 targetPos，当前显示位置 displayPos 向其平滑插值喵。
 * - 每帧 displayPos 以固定速度向 targetPos 移动，保证平滑且不会偏离太远喵。
 * - 收到新快照时更新 targetPos，displayPos 继续向新目标平滑移动喵。
 */
export class LayerShipRenderer {
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
   * 前端舰船移动系统喵。
   */
  private readonly movementSystem = new ShipMovementSystemFrontend()

  /**
   * 游戏时间管理器喵。
   */
  private readonly timeManager = defaultGameTimeManager

  /**
   * 预测纠正器喵。
   */
  private readonly predictionCorrector = defaultPredictionCorrector

  /**
   * 是否启用预测模式（默认 true，前端视觉计算迁移方案已启用）喵。
   */
  private predictionEnabled = true

  /**
   * 最后处理的模拟 tick，用于避免重复更新时间快照喵。
   */
  private lastProcessedTick = 0

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

  private layerGroup: THREE.Group

  constructor(layerGroup: THREE.Group) {
    this.layerGroup = layerGroup
  }

  /**
   * 启用或禁用预测模式喵。
   *
   * @param enabled 是否启用预测
   */
  enablePrediction(enabled: boolean): void {
    this.predictionEnabled = enabled
    if (enabled) {
      console.debug(`[LayerShipRenderer] 预测模式已启用喵。`)
    } else {
      console.debug(`[LayerShipRenderer] 预测模式已禁用喵。`)
    }
  }

  /**
   * 更新时间快照喵。
   * 当收到新的后端快照时调用此方法喵。
   *
   * @param snapshot 时间快照数据
   */
  updateTimeSnapshot(snapshot: {
    simulationTick: number
    totalGameSeconds: number
    deltaGameSeconds: number
    timeScale?: number
  }): void {
    this.timeManager.updateSnapshot(snapshot)
  }

  init(): void {
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

    // 更新时间快照（如果快照中有新的时间信息）喵
    const snapshot = frame.snapshot
    if (snapshot?.realTimeWorldState) {
      const rt = snapshot.realTimeWorldState
      if (rt.simulationTick !== this.lastProcessedTick) {
        this.updateTimeSnapshot({
          simulationTick: rt.simulationTick,
          totalGameSeconds: rt.totalGameSeconds,
          deltaGameSeconds: rt.deltaGameSeconds,
          timeScale: rt.timeScale ?? rt.gameSecondsPerRealSecond
        })
        this.lastProcessedTick = rt.simulationTick
      }
    }

    // 当前渲染时间戳喵
    const now = performance.now()

    // 更新时间管理器（用于预测计算）喵
    const timeState = this.timeManager.update()
    const currentGameSeconds = timeState.currentGameSeconds
    const isGamePaused = timeState.isPaused || timeState.timeScale === 0

    // 舰船 LOD：始终可见喵
    const shipLod: import('@/rendering/subsystems/lodSystem').EntityLodState = {
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
    let stateUpdatedCount = 0
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
        console.log(`[LayerShipRenderer] Ship ${entity.entityId} has no position`)
        continue
      }

      // 提前镜头检查：非选中且不在镜头内 -> 跳过状态更新喵
      if (!isSelected && !isPointInAabb(authPos, cullingAabb)) {
        // 非选中且不在镜头内，跳过所有后续计算喵
        continue
      }

      stateUpdatedCount++

      const detailAny: any = entity.details
      const authIsMoving = detailAny?.isMoving === true
      const headingDeg = Number(detailAny?.headingDeg ?? 0)
      const authVelocity = detailAny?.velocity
      const movementTarget = detailAny?.movementTarget
      const maxSpeed = detailAny?.maxSpeed ?? 20.0
      const baseAcceleration = detailAny?.baseAcceleration ?? 5.0
      const bowAccelerationBonus = detailAny?.bowAccelerationBonus ?? 5.0
      const turnRate = detailAny?.turnRate ?? 45.0
      const lateralSpeedPenalty = detailAny?.lateralSpeedPenalty ?? 0.6
      const reverseSpeedPenalty = detailAny?.reverseSpeedPenalty ?? 0.3
      const movementCommand = detailAny?.movementCommand

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
      }

      // 更新预测纠正器的权威状态喵
      this.predictionCorrector.updateEntityState(
        entity.entityId,
        renderState.displayPos, // 预测位置初始化为显示位置喵
        { x: authPos.x, y: authPos.y }, // 权威位置喵
        renderState.predictedVelocity ?? null, // 预测速度喵
        authVelocity ? { x: authVelocity.x, y: authVelocity.y } : null // 权威速度喵
      )

      // 预测模式：初始化或更新预测状态喵
      if (this.predictionEnabled && !isGamePaused) {
        this.updatePredictionState(
          entity.entityId,
          renderState,
          {
            authPos,
            headingDeg,
            authIsMoving,
            movementTarget,
            authVelocity,
            maxSpeed,
            baseAcceleration,
            bowAccelerationBonus,
            turnRate,
            lateralSpeedPenalty,
            reverseSpeedPenalty,
            movementCommand,
          },
          currentGameSeconds,
          now
        )
      }

      // 计算当前显示位置与目标位置的距离喵（仅在非预测模式或游戏暂停时使用）喵
      if (!this.predictionEnabled || isGamePaused) {
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

      // 使用显示位置进行镜头剔除检查喵
      if (!isSelected && !isPointInAabb(renderState.displayPos, cullingAabb)) {
        continue
      }

      visibleIds.add(entity.entityId)

      let mesh = this.activeByEntityId.get(entity.entityId)
      if (!mesh) {
        mesh = this.acquireShipMesh()
        this.activeByEntityId.set(entity.entityId, mesh)
        this.layerGroup.add(mesh)
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
        renderState.displayPos.x,
        renderState.displayPos.y,
        0.15
      )
      mesh.visible = true
      renderedCount++

      // 调试：只在选中且isMoving状态变化时打印日志喵
      if (isSelected && renderState.lastLoggedAuthIsMoving !== authIsMoving) {
        renderState.lastLoggedAuthIsMoving = authIsMoving
        logger.info('LayerShipRenderer-Path', `ship=${entity.entityId} isMoving状态变化: ${!authIsMoving ? 'false->' : ''}true hasTarget=${!!movementTarget}`)
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
      const width = cullingAabb.maxX - cullingAabb.minX
      const height = cullingAabb.maxY - cullingAabb.minY
      console.log(`[LayerShipRenderer] Ships total: ${shipCount}, State-updated: ${stateUpdatedCount}, Rendered: ${renderedCount}, CullingAABB: ${width.toFixed(0)}x${height.toFixed(0)} GU (scale=1.2)`)
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

  /**
   * 更新实体预测状态喵。
   *
   * @param entityId 实体ID
   * @param renderState 渲染状态
   * @param entityData 实体数据
   * @param currentGameSeconds 当前游戏秒数
   * @param now 当前时间戳（performance.now()）
   */
  private updatePredictionState(
    entityId: number,
    renderState: ShipRenderState,
    entityData: {
      authPos: { x: number; y: number }
      headingDeg: number
      authIsMoving: boolean
      movementTarget?: { x: number; y: number }
      authVelocity?: { x: number; y: number }
      maxSpeed: number
      baseAcceleration: number
      bowAccelerationBonus: number
      turnRate: number
      lateralSpeedPenalty: number
      reverseSpeedPenalty: number
      movementCommand?: any
    },
    currentGameSeconds: number,
    now: number
  ): void {
    // 初始化或获取预测状态喵
    let predictionState = renderState.predictionState
    if (!predictionState) {
      predictionState = {
        entityId,
        position: { x: entityData.authPos.x, y: entityData.authPos.y },
        velocity: entityData.authVelocity ? { x: entityData.authVelocity.x, y: entityData.authVelocity.y } : null,
        movementTarget: entityData.movementTarget ? { x: entityData.movementTarget.x, y: entityData.movementTarget.y } : null,
        isMoving: entityData.authIsMoving,
        currentHeadingDeg: entityData.headingDeg,
        targetHeadingDeg: entityData.headingDeg,
        maxSpeed: entityData.maxSpeed,
        baseAcceleration: entityData.baseAcceleration,
        bowAccelerationBonus: entityData.bowAccelerationBonus,
        turnRate: entityData.turnRate,
        lateralSpeedPenalty: entityData.lateralSpeedPenalty,
        reverseSpeedPenalty: entityData.reverseSpeedPenalty,
      }
      renderState.predictionState = predictionState
      renderState.lastPredictionTimeMs = now
    } else {
      // 更新预测状态的基础参数喵
      predictionState.maxSpeed = entityData.maxSpeed
      predictionState.baseAcceleration = entityData.baseAcceleration
      predictionState.bowAccelerationBonus = entityData.bowAccelerationBonus
      predictionState.turnRate = entityData.turnRate
      predictionState.lateralSpeedPenalty = entityData.lateralSpeedPenalty
      predictionState.reverseSpeedPenalty = entityData.reverseSpeedPenalty

      // 更新移动目标和状态喵
      predictionState.movementTarget = entityData.movementTarget
        ? { x: entityData.movementTarget.x, y: entityData.movementTarget.y }
        : null
      predictionState.isMoving = entityData.authIsMoving
    }

    // 计算时间增量（游戏秒）喵
    const lastPredictionTimeMs = renderState.lastPredictionTimeMs ?? now
    const deltaRealMs = now - lastPredictionTimeMs
    const deltaGameSeconds = this.timeManager.realMsToGameSeconds(deltaRealMs)

    // 如果游戏时间有推进，进行预测计算喵
    if (deltaGameSeconds > 0) {
      // 创建临时世界状态（简化）喵
      const worldState = { gameTimeSeconds: currentGameSeconds }

      // 更新预测状态喵
      this.movementSystem.update([predictionState], worldState, deltaGameSeconds)

      // 保存预测结果喵
      renderState.predictedPosition = { x: predictionState.position.x, y: predictionState.position.y }
      renderState.predictedVelocity = predictionState.velocity
        ? { x: predictionState.velocity.x, y: predictionState.velocity.y }
        : null
      renderState.lastPredictionTimeMs = now

      // 更新预测纠正器的预测状态喵
      this.predictionCorrector.updateEntityState(
        entityId,
        { x: predictionState.position.x, y: predictionState.position.y }, // 新的预测位置喵
        entityData.authPos, // 权威位置（不变）喵
        predictionState.velocity ? { x: predictionState.velocity.x, y: predictionState.velocity.y } : null, // 新的预测速度喵
        entityData.authVelocity ? { x: entityData.authVelocity.x, y: entityData.authVelocity.y } : null // 权威速度喵
      )

      // 计算纠正结果喵
      const correctionResult = this.predictionCorrector.calculateCorrection(
        entityId,
        deltaRealMs
      )

      // 应用纠正结果喵
      if (correctionResult.corrected) {
        renderState.displayPos = { ...correctionResult.displayPosition }
        if (correctionResult.displayVelocity) {
          renderState.predictedVelocity = { ...correctionResult.displayVelocity }
          if (predictionState.velocity) {
            predictionState.velocity.x = correctionResult.displayVelocity.x
            predictionState.velocity.y = correctionResult.displayVelocity.y
          }
        }
      } else {
        // 无纠正，使用预测位置喵
        renderState.displayPos = { ...renderState.predictedPosition! }
      }
    }
  }

  dispose(): void {
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
      this.layerGroup.add(line)
    }

    // 更新路径几何体（从舰船位置到目标位置）喵
    const positions = new Float32Array([
      shipPos.x, shipPos.y, 0.1,
      targetPos.x, targetPos.y, 0.1,
    ])
    const geometry = line.geometry as THREE.BufferGeometry
    geometry.setAttribute('position', new THREE.BufferAttribute(positions, 3))
    geometry.attributes.position!.needsUpdate = true

    line.visible = true
  }

  /**
   * 移除舰船的移动路径线条喵。
   */
  private removePathLine(_ctx: WorldRenderContext, entityId: number): void {
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

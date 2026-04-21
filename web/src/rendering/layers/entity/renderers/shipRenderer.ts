import * as THREE from 'three'
import type { WorldRenderContext, WorldFrameState } from '../../../worldRenderManager'
import { shouldRender } from '@/rendering/subsystems/lodSystem'
import { logger } from '@/utils/logger'
import { ShipMovementSystemFrontend, type ShipState } from '@/game/systems/ShipMovementSystemFrontend'
import { defaultGameTimeManager } from '@/game/time/GameTimeManager'
import { defaultPredictionCorrector } from '@/game/prediction/PredictionCorrector'
import { getEstimatedShipPose } from '@/game/shipPositionEstimator'

const INITIAL_SPAWN_SHIP_FLAG = 'INITIAL_SPAWN_SHIP'

interface ShipRenderState {
  targetPos: { x: number; y: number }
  displayPos: { x: number; y: number }
  isMoving: boolean
  lastAuthTime: number
  lastLoggedAuthIsMoving?: boolean
  predictionState?: ShipState
  predictedPosition?: { x: number; y: number }
  predictedVelocity?: { x: number; y: number } | null
  lastPredictionTimeMs?: number
}

function isPointInAabb(
  p: { x: number; y: number },
  a: { minX: number; maxX: number; minY: number; maxY: number },
): boolean {
  return p.x >= a.minX && p.x <= a.maxX && p.y >= a.minY && p.y <= a.maxY
}

export class LayerShipRenderer {
  private readonly shipPool: THREE.Mesh[] = []
  private readonly activeByEntityId = new Map<number, THREE.Mesh>()
  private readonly pathLinePool: THREE.Line[] = []
  private readonly activePathByEntityId = new Map<number, THREE.Line>()
  private readonly renderStateById = new Map<number, ShipRenderState>()
  private readonly movementSystem = new ShipMovementSystemFrontend()
  private readonly timeManager = defaultGameTimeManager
  private readonly predictionCorrector = defaultPredictionCorrector
  private predictionEnabled = true
  private lastProcessedTick = 0
  private shipTriangleGeometry: THREE.BufferGeometry | null = null
  private pathLineMaterial: THREE.LineBasicMaterial | null = null
  private readonly INTERPOLATION_SPEED_GU_PER_MS = 0.05
  private readonly MAX_INTERPOLATION_DISTANCE_GU = 100.0

  constructor(private readonly layerGroup: THREE.Group) {}

  enablePrediction(enabled: boolean): void {
    this.predictionEnabled = enabled
  }

  updateTimeSnapshot(snapshot: {
    simulationTick: number
    totalGameSeconds: number
    deltaGameSeconds: number
    timeScale?: number
  }): void {
    this.timeManager.updateSnapshot(snapshot)
  }

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

    const snapshot = frame.snapshot?.realTimeWorldState
    if (snapshot && snapshot.simulationTick !== this.lastProcessedTick) {
      this.updateTimeSnapshot({
        simulationTick: snapshot.simulationTick,
        totalGameSeconds: snapshot.totalGameSeconds,
        deltaGameSeconds: snapshot.deltaGameSeconds,
        timeScale: snapshot.timeScale ?? snapshot.gameSecondsPerRealSecond,
      })
      this.lastProcessedTick = snapshot.simulationTick
    }

    const now = performance.now()
    const timeState = this.timeManager.update()
    const currentGameSeconds = timeState.currentGameSeconds
    const isGamePaused = timeState.isPaused || timeState.timeScale === 0

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

    const visibleIds = new Set<number>()
    let shipCount = 0
    let stateUpdatedCount = 0
    let renderedCount = 0

    for (const entity of entitiesById.values()) {
      if (entity.entityType !== 'SHIP') continue
      shipCount++

      const isSelected = selectedIds.has(entity.entityId)
      if (!shouldRender(shipLod, isSelected)) continue

      const estimatedPose = getEstimatedShipPose(entity, currentGameSeconds)
      const authPos = estimatedPose?.position ?? entity.posWorldGU
      if (!authPos) continue

      if (!isSelected && !isPointInAabb(authPos, cullingAabb)) continue

      stateUpdatedCount++

      const detailAny: any = entity.details
      const useCommandSeedPose = estimatedPose?.usesCommandSeed === true
      const authIsMoving = estimatedPose?.isMoving ?? (detailAny?.isMoving === true)
      const headingDeg = Number(estimatedPose?.headingDeg ?? detailAny?.headingDeg ?? 0)
      const authVelocity = estimatedPose?.velocity ?? detailAny?.velocity
      const movementTarget = estimatedPose?.movementTarget ?? detailAny?.movementTarget
      const maxSpeed = detailAny?.maxSpeed ?? 20
      const baseAcceleration = detailAny?.baseAcceleration ?? 5
      const bowAccelerationBonus = detailAny?.bowAccelerationBonus ?? 5
      const turnRate = detailAny?.turnRate ?? 45
      const lateralSpeedPenalty = detailAny?.lateralSpeedPenalty ?? 0.6
      const reverseSpeedPenalty = detailAny?.reverseSpeedPenalty ?? 0.3
      const movementCommand = detailAny?.movementCommand

      let renderState = this.renderStateById.get(entity.entityId)
      if (!renderState) {
        renderState = {
          targetPos: { x: authPos.x, y: authPos.y },
          displayPos: { x: authPos.x, y: authPos.y },
          isMoving: authIsMoving,
          lastAuthTime: now,
        }
        this.renderStateById.set(entity.entityId, renderState)
      } else {
        renderState.targetPos = { x: authPos.x, y: authPos.y }
        renderState.isMoving = authIsMoving
        renderState.lastAuthTime = now
      }

      if (useCommandSeedPose) {
        renderState.targetPos = { x: authPos.x, y: authPos.y }
        renderState.displayPos = { x: authPos.x, y: authPos.y }
        renderState.predictionState = undefined
        renderState.predictedPosition = { x: authPos.x, y: authPos.y }
        renderState.predictedVelocity = authVelocity ? { x: authVelocity.x, y: authVelocity.y } : null
        renderState.lastPredictionTimeMs = now
        this.predictionCorrector.removeEntityState(entity.entityId)
      } else {
        this.predictionCorrector.updateEntityState(
          entity.entityId,
          renderState.displayPos,
          { x: authPos.x, y: authPos.y },
          renderState.predictedVelocity ?? null,
          authVelocity ? { x: authVelocity.x, y: authVelocity.y } : null,
        )
      }

      if (!useCommandSeedPose && this.predictionEnabled && !isGamePaused) {
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
          now,
        )
      }

      if (useCommandSeedPose || !this.predictionEnabled || isGamePaused) {
        const dx = renderState.targetPos.x - renderState.displayPos.x
        const dy = renderState.targetPos.y - renderState.displayPos.y
        const distance = Math.sqrt(dx * dx + dy * dy)
        if (distance > this.MAX_INTERPOLATION_DISTANCE_GU) {
          renderState.displayPos = { x: renderState.targetPos.x, y: renderState.targetPos.y }
        } else if (distance > 0.01) {
          const maxMoveDistance = this.INTERPOLATION_SPEED_GU_PER_MS * 16.67
          if (distance <= maxMoveDistance) {
            renderState.displayPos = { x: renderState.targetPos.x, y: renderState.targetPos.y }
          } else {
            const ratio = maxMoveDistance / distance
            renderState.displayPos.x += dx * ratio
            renderState.displayPos.y += dy * ratio
          }
        }
      }

      if (!isSelected && !isPointInAabb(renderState.displayPos, cullingAabb)) continue

      visibleIds.add(entity.entityId)

      let mesh = this.activeByEntityId.get(entity.entityId)
      if (!mesh) {
        mesh = this.acquireShipMesh()
        this.activeByEntityId.set(entity.entityId, mesh)
        this.layerGroup.add(mesh)
      }

      const flags: string[] = Array.isArray(detailAny?.customFlags) ? detailAny.customFlags : []
      const isInitialShip = flags.includes(INITIAL_SPAWN_SHIP_FLAG)
      const material = mesh.material as THREE.MeshBasicMaterial
      material.color.set(isInitialShip ? 0x56d7ff : 0xc8d0d8)
      mesh.scale.set(isInitialShip ? 1.2 : 1, isInitialShip ? 1.2 : 1, 1)
      mesh.rotation.z = THREE.MathUtils.degToRad(headingDeg - 90)
      mesh.position.set(renderState.displayPos.x, renderState.displayPos.y, 0.15)
      mesh.visible = true
      renderedCount++

      if (isSelected && renderState.lastLoggedAuthIsMoving !== authIsMoving) {
        renderState.lastLoggedAuthIsMoving = authIsMoving
        logger.info(
          'LayerShipRenderer-Path',
          `ship=${entity.entityId} isMoving=${authIsMoving} hasTarget=${!!movementTarget}`,
        )
      }

      if (isSelected && renderState.isMoving && movementTarget) {
        this.updatePathLine(ctx, entity.entityId, renderState.displayPos, movementTarget)
      } else {
        this.removePathLine(ctx, entity.entityId)
      }
    }

    if (shipCount > 0 && Math.random() < 0.01) {
      const width = cullingAabb.maxX - cullingAabb.minX
      const height = cullingAabb.maxY - cullingAabb.minY
      console.log(
        `[LayerShipRenderer] Ships total: ${shipCount}, State-updated: ${stateUpdatedCount}, Rendered: ${renderedCount}, CullingAABB: ${width.toFixed(0)}x${height.toFixed(0)} GU`,
      )
    }

    for (const [id, line] of this.activePathByEntityId.entries()) {
      if (!visibleIds.has(id) || !selectedIds.has(id)) {
        this.activePathByEntityId.delete(id)
        this.releasePathLine(line)
        line.parent?.remove(line)
      }
    }

    for (const [id, mesh] of this.activeByEntityId.entries()) {
      if (visibleIds.has(id)) continue
      this.activeByEntityId.delete(id)
      this.releaseShipMesh(mesh)
      this.renderStateById.delete(id)
      this.predictionCorrector.removeEntityState(id)
    }
  }

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
    now: number,
  ): void {
    let predictionState = renderState.predictionState
    if (!predictionState) {
      predictionState = {
        entityId,
        position: { x: entityData.authPos.x, y: entityData.authPos.y },
        velocity: entityData.authVelocity ? { x: entityData.authVelocity.x, y: entityData.authVelocity.y } : null,
        movementTarget: entityData.movementTarget
          ? { x: entityData.movementTarget.x, y: entityData.movementTarget.y }
          : null,
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
      predictionState.maxSpeed = entityData.maxSpeed
      predictionState.baseAcceleration = entityData.baseAcceleration
      predictionState.bowAccelerationBonus = entityData.bowAccelerationBonus
      predictionState.turnRate = entityData.turnRate
      predictionState.lateralSpeedPenalty = entityData.lateralSpeedPenalty
      predictionState.reverseSpeedPenalty = entityData.reverseSpeedPenalty
      predictionState.movementTarget = entityData.movementTarget
        ? { x: entityData.movementTarget.x, y: entityData.movementTarget.y }
        : null
      predictionState.isMoving = entityData.authIsMoving
    }

    const lastPredictionTimeMs = renderState.lastPredictionTimeMs ?? now
    const deltaRealMs = now - lastPredictionTimeMs
    const deltaGameSeconds = this.timeManager.realMsToGameSeconds(deltaRealMs)
    if (deltaGameSeconds <= 0) return

    this.movementSystem.update([predictionState], { gameTimeSeconds: currentGameSeconds }, deltaGameSeconds)
    renderState.predictedPosition = { x: predictionState.position.x, y: predictionState.position.y }
    renderState.predictedVelocity = predictionState.velocity
      ? { x: predictionState.velocity.x, y: predictionState.velocity.y }
      : null
    renderState.lastPredictionTimeMs = now

    this.predictionCorrector.updateEntityState(
      entityId,
      { x: predictionState.position.x, y: predictionState.position.y },
      entityData.authPos,
      predictionState.velocity ? { x: predictionState.velocity.x, y: predictionState.velocity.y } : null,
      entityData.authVelocity ? { x: entityData.authVelocity.x, y: entityData.authVelocity.y } : null,
    )

    const correctionResult = this.predictionCorrector.calculateCorrection(entityId, deltaRealMs)
    if (correctionResult.corrected) {
      renderState.displayPos = { ...correctionResult.displayPosition }
      if (correctionResult.displayVelocity) {
        renderState.predictedVelocity = { ...correctionResult.displayVelocity }
        if (predictionState.velocity) {
          predictionState.velocity.x = correctionResult.displayVelocity.x
          predictionState.velocity.y = correctionResult.displayVelocity.y
        }
      }
      return
    }

    renderState.displayPos = { ...renderState.predictedPosition }
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
    this.renderStateById.clear()
    this.predictionCorrector.clearAllStates()
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
    _ctx: WorldRenderContext,
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

    const geometry = line.geometry as THREE.BufferGeometry
    geometry.setAttribute(
      'position',
      new THREE.BufferAttribute(
        new Float32Array([
          shipPos.x, shipPos.y, 0.1,
          targetPos.x, targetPos.y, 0.1,
        ]),
        3,
      ),
    )
    geometry.attributes.position.needsUpdate = true
    line.visible = true
  }

  private removePathLine(_ctx: WorldRenderContext, entityId: number): void {
    const line = this.activePathByEntityId.get(entityId)
    if (!line) return
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

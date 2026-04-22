import type { EntitySnapshot, SnapshotMessage, ShipDetails } from '../../net/snapshotWs'
import type {
  LocalVisibleWorld,
  AuthoritativeSnapshotMeta,
  WorldSyncResult,
  EntityDisplayPosition,
  PendingCommandRecord,
  PendingCommandStatus,
  PredictedShipState,
} from './localVisibleWorldTypes'
import {
  createPendingCommand,
  updateCommandStatus,
  addPendingCommandToWorld,
  removeCommandFromWorld,
  updateCommandFromSnapshot,
  getEntityCommandStatus,
  hasActiveCommand,
  resolveCommandConflict,
  applyCommandUpdateWithPriority,
  type CommandUpdateSource,
  type CommandUpdateData,
} from './localVisibleWorldCommands'
import { ShipMovementSystemFrontend, type ShipState } from '../systems/ShipMovementSystemFrontend'
import { defaultGameTimeManager } from '../time/GameTimeManager'

type MoveSeed = NonNullable<ShipDetails['movementCommand']>

export class LocalVisibleWorldImpl {
  private readonly state: LocalVisibleWorld
  private readonly movementSystem = new ShipMovementSystemFrontend()

  constructor() {
    this.state = {
      visibleEntitiesById: new Map(),
      predictedShipsById: new Map(),
      pendingCommandsByEntityId: new Map(),
      lastSnapshotMeta: null,
      intelVisibilityState: {
        currentNationId: null,
        lastSyncAtMs: 0,
        visiblePrivateEntitiesByLevel: {},
      },
      focusedEntityIds: new Set(),
      lastAppliedSimulationTick: null,
    }
  }

  getVisibleEntityCount(): number {
    return this.state.visibleEntitiesById.size
  }

  getPredictedShipCount(): number {
    return this.state.predictedShipsById.size
  }

  getPendingCommandCount(): number {
    return this.state.pendingCommandsByEntityId.size
  }

  getLastAppliedTick(): number | null {
    return this.state.lastAppliedSimulationTick
  }

  getEntitySnapshot(entityId: number): EntitySnapshot | null {
    return this.state.visibleEntitiesById.get(entityId) ?? null
  }

  getAllEntitySnapshots(): EntitySnapshot[] {
    return Array.from(this.state.visibleEntitiesById.values())
  }

  getEntityDisplayPosition(entityId: number): EntityDisplayPosition | null {
    const entity = this.getEntitySnapshot(entityId)
    if (!entity) {
      return null
    }

    if (entity.entityType === 'SHIP') {
      return this.getShipDisplayPose(entityId)
    }

    const details = (entity.details ?? {}) as any
    return {
      position: { x: entity.posWorldGU.x, y: entity.posWorldGU.y },
      velocity: this.cloneVec2(details.velocity),
      headingDeg: this.clampNumber(details.headingDeg, 0),
      isMoving: details.isMoving === true,
      movementTarget: this.cloneVec2(details.movementTarget),
      usesCommandSeed: false,
      source: 'authoritative',
    }
  }

  reset(): void {
    this.state.visibleEntitiesById.clear()
    this.state.predictedShipsById.clear()
    this.state.pendingCommandsByEntityId.clear()
    this.state.lastSnapshotMeta = null
    this.state.intelVisibilityState = {
      currentNationId: this.state.intelVisibilityState.currentNationId,
      lastSyncAtMs: 0,
      visiblePrivateEntitiesByLevel: {},
    }
    this.state.focusedEntityIds.clear()
    this.state.lastAppliedSimulationTick = null
  }

  applySnapshot(snapshot: SnapshotMessage): WorldSyncResult {
    const result: WorldSyncResult = {
      success: false,
      addedEntities: 0,
      updatedEntities: 0,
      removedEntities: 0,
      appliedTick: null,
      triggeredCorrection: false,
      correctedEntities: 0,
    }

    if (!snapshot.ok || !snapshot.realTimeWorldState) {
      return result
    }

    const rt = snapshot.realTimeWorldState
    const snapshotTick = rt.simulationTick
    if (
      this.state.lastAppliedSimulationTick !== null &&
      snapshotTick <= this.state.lastAppliedSimulationTick
    ) {
      return result
    }

    result.appliedTick = snapshotTick
    this.state.lastSnapshotMeta = {
      simulationTick: snapshotTick,
      totalGameSeconds: rt.totalGameSeconds,
      totalGameSecondsExact: rt.totalGameSecondsExact ?? null,
      receivedAtClientMs: Date.now(),
    }

    defaultGameTimeManager.updateSnapshot({
      simulationTick: snapshotTick,
      totalGameSeconds: rt.totalGameSeconds,
      totalGameSecondsExact: rt.totalGameSecondsExact,
      deltaGameSeconds: rt.deltaGameSeconds,
      timeScale: rt.timeScale,
      gameSecondsPerRealSecond: rt.gameSecondsPerRealSecond,
    })

    this.syncIntelVisibility(rt.privateEntitiesByIntelLevel ?? {})

    const incomingEntities = this.deduplicateEntities([
      ...(rt.entities ?? []),
      ...Object.values(rt.privateEntitiesByIntelLevel ?? {}).flatMap(level => level ?? []),
    ])
    const incomingIds = new Set<number>()
    const currentIds = new Set(this.state.visibleEntitiesById.keys())

    for (const entity of incomingEntities) {
      incomingIds.add(entity.entityId)
      const existed = this.state.visibleEntitiesById.has(entity.entityId)
      this.state.visibleEntitiesById.set(entity.entityId, entity)
      if (existed) {
        result.updatedEntities++
      } else {
        result.addedEntities++
      }
    }

    for (const entityId of currentIds) {
      if (incomingIds.has(entityId)) {
        continue
      }

      const existingEntity = this.state.visibleEntitiesById.get(entityId)
      if (!existingEntity) {
        continue
      }

      if (this.shouldRetainEntityWithoutSnapshot(existingEntity)) {
        continue
      }

      this.state.visibleEntitiesById.delete(entityId)
      this.state.predictedShipsById.delete(entityId)
      if (!this.hasActiveCommand(entityId)) {
        this.state.pendingCommandsByEntityId.delete(entityId)
      }
      result.removedEntities++
    }

    this.state.lastAppliedSimulationTick = snapshotTick
    result.success = true

    this.updateCommandsFromSnapshot(snapshotTick)
    this.syncPredictedShips(rt.totalGameSecondsExact ?? rt.totalGameSeconds)
    return result
  }

  replaceVisibleEntities(entities: EntitySnapshot[]): void {
    this.state.visibleEntitiesById.clear()
    for (const entity of entities) {
      this.state.visibleEntitiesById.set(entity.entityId, entity)
    }
  }

  setSnapshotMeta(meta: AuthoritativeSnapshotMeta): void {
    this.state.lastSnapshotMeta = meta
  }

  addFocusedEntity(entityId: number): void {
    this.state.focusedEntityIds.add(entityId)
  }

  removeFocusedEntity(entityId: number): void {
    this.state.focusedEntityIds.delete(entityId)
  }

  setFocusedEntities(entityIds: number[]): void {
    this.state.focusedEntityIds = new Set(entityIds)
  }

  isEntityFocused(entityId: number): boolean {
    return this.state.focusedEntityIds.has(entityId)
  }

  getAllFocusedEntityIds(): number[] {
    return Array.from(this.state.focusedEntityIds)
  }

  setCurrentNationId(nationId: string | null): void {
    this.state.intelVisibilityState.currentNationId = nationId
    this.state.intelVisibilityState.lastSyncAtMs = Date.now()
  }

  updatePrivateEntityVisibility(level: string, entityIds: number[]): void {
    this.state.intelVisibilityState.visiblePrivateEntitiesByLevel[level] = new Set(entityIds)
    this.state.intelVisibilityState.lastSyncAtMs = Date.now()
  }

  isEntityVisibleToPlayer(entityId: number): boolean {
    if (this.state.visibleEntitiesById.has(entityId)) {
      return true
    }

    return Object.values(this.state.intelVisibilityState.visiblePrivateEntitiesByLevel).some(levelSet =>
      levelSet.has(entityId),
    )
  }

  syncPredictedShips(snapshotGameSeconds: number): void {
    const retainedIds = new Set<number>()

    for (const entity of this.state.visibleEntitiesById.values()) {
      if (entity.entityType !== 'SHIP') {
        continue
      }

      retainedIds.add(entity.entityId)
      const state = this.getOrCreatePredictedShipState(entity, snapshotGameSeconds)
      const effectiveSeed = this.getEffectiveMovementSeed(entity.entityId, entity)
      const hasLocalOrAuthoritativeMove = this.hasMoveSeed(effectiveSeed)

      if (hasLocalOrAuthoritativeMove) {
        this.reseedPredictedStateIfNeeded(state, entity, effectiveSeed)
        this.advancePredictedShipState(state, snapshotGameSeconds)
        continue
      }

      this.syncPredictedStateFromAuthoritative(state, entity, snapshotGameSeconds)
    }

    for (const entityId of Array.from(this.state.predictedShipsById.keys())) {
      if (retainedIds.has(entityId)) {
        continue
      }
      this.state.predictedShipsById.delete(entityId)
    }
  }

  advancePredictedShips(deltaGameSeconds: number): void {
    if (!Number.isFinite(deltaGameSeconds) || deltaGameSeconds <= 0) {
      return
    }

    for (const predictedState of this.state.predictedShipsById.values()) {
      this.advancePredictedShipState(
        predictedState,
        predictedState.lastSimulatedGameSeconds + deltaGameSeconds,
      )
    }
  }

  getShipDisplayPose(entityId: number): EntityDisplayPosition | null {
    const entity = this.getEntitySnapshot(entityId)
    if (!entity || entity.entityType !== 'SHIP') {
      return null
    }

    const predictedState = this.state.predictedShipsById.get(entityId)
    if (predictedState) {
      return {
        position: { x: predictedState.shipState.position.x, y: predictedState.shipState.position.y },
        velocity: this.cloneVec2(predictedState.shipState.velocity),
        headingDeg: predictedState.shipState.currentHeadingDeg,
        isMoving: predictedState.shipState.isMoving,
        movementTarget: this.cloneVec2(predictedState.shipState.movementTarget),
        usesCommandSeed: predictedState.lastCommandKey !== null,
        source: 'predicted',
      }
    }

    const details = (entity.details ?? {}) as ShipDetails
    return {
      position: { x: entity.posWorldGU.x, y: entity.posWorldGU.y },
      velocity: this.cloneVec2(details.velocity),
      headingDeg: this.clampNumber(details.headingDeg, 0),
      isMoving: details.isMoving === true,
      movementTarget: this.cloneVec2(details.movementTarget),
      usesCommandSeed: false,
      source: 'authoritative',
    }
  }

  clearPredictedShips(): void {
    this.state.predictedShipsById.clear()
  }

  getPendingCommand(entityId: number): PendingCommandRecord | null {
    return getEntityCommandStatus(this.state, entityId)
  }

  hasActiveCommand(entityId: number): boolean {
    return hasActiveCommand(this.state, entityId)
  }

  getReadyMoveCompletionReports(): Array<{
    entityId: number
    clientCommandId: string
    reportedGameSeconds: number
    reportedPosition: { x: number; y: number }
  }> {
    const reports: Array<{
      entityId: number
      clientCommandId: string
      reportedGameSeconds: number
      reportedPosition: { x: number; y: number }
    }> = []

    for (const [entityId, command] of this.state.pendingCommandsByEntityId.entries()) {
      if (command.commandType !== 'MOVE_TO') {
        continue
      }
      if (command.status !== 'predicting' && command.status !== 'confirmed') {
        continue
      }
      if (command.completionReportSentAtClientMs !== null) {
        continue
      }

      const predictedState = this.state.predictedShipsById.get(entityId)
      if (!predictedState || predictedState.shipState.isMoving) {
        continue
      }

      reports.push({
        entityId,
        clientCommandId: command.clientCommandId,
        reportedGameSeconds: predictedState.lastSimulatedGameSeconds,
        reportedPosition: {
          x: predictedState.shipState.position.x,
          y: predictedState.shipState.position.y,
        },
      })
    }

    return reports
  }

  markMoveCompletionReportSent(entityId: number): void {
    const command = this.state.pendingCommandsByEntityId.get(entityId)
    if (!command) {
      return
    }

    this.state.pendingCommandsByEntityId.set(entityId, {
      ...command,
      completionReportSentAtClientMs: Date.now(),
    })
  }

  resetMoveCompletionReport(entityId: number): void {
    const command = this.state.pendingCommandsByEntityId.get(entityId)
    if (!command) {
      return
    }

    this.state.pendingCommandsByEntityId.set(entityId, {
      ...command,
      completionReportSentAtClientMs: null,
    })
  }

  addMoveCommand(
    entityId: number,
    targetPosition: { x: number; y: number },
    movementSeed: ShipDetails['movementCommand'] | null,
    conflictStrategy: 'cancel_previous' | 'queue' | 'reject_new' = 'cancel_previous',
  ): PendingCommandRecord | null {
    const entity = this.state.visibleEntitiesById.get(entityId)
    if (!entity || entity.entityType !== 'SHIP') {
      return null
    }

    const currentGameSeconds = defaultGameTimeManager.getCurrentGameSeconds()
    const liveShipState = this.captureLiveShipState(entity, currentGameSeconds)
    const seed =
      movementSeed ?? this.createLocalMoveSeed(entity, targetPosition, currentGameSeconds, liveShipState)
    const command = createPendingCommand(entityId, 'MOVE_TO', seed)
    if (!resolveCommandConflict(this.state, entityId, command, conflictStrategy)) {
      return null
    }
    if (!addPendingCommandToWorld(this.state, command)) {
      return null
    }

    const commandSeed = this.hasMoveSeed(command.movementSeed) ? command.movementSeed : seed
    this.ensurePredictedStateForCommand(entity, commandSeed, currentGameSeconds, liveShipState)
    return command
  }

  addStopCommand(
    entityId: number,
    conflictStrategy: 'cancel_previous' | 'queue' | 'reject_new' = 'cancel_previous',
  ): PendingCommandRecord | null {
    const command = createPendingCommand(entityId, 'STOP', null)
    if (!resolveCommandConflict(this.state, entityId, command, conflictStrategy)) {
      return null
    }
    if (!addPendingCommandToWorld(this.state, command)) {
      return null
    }

    const predictedState = this.state.predictedShipsById.get(entityId)
    if (predictedState) {
      predictedState.shipState.isMoving = false
      predictedState.shipState.movementTarget = null
      predictedState.lastCommandKey = null
    }

    return command
  }

  updateCommandStatus(
    entityId: number,
    newStatus: PendingCommandStatus,
    options: {
      authoritativeTick?: number | null
      rejectionReason?: string | null
      movementSeed?: ShipDetails['movementCommand'] | null
    } = {},
  ): PendingCommandRecord | null {
    const command = this.state.pendingCommandsByEntityId.get(entityId)
    if (!command) {
      return null
    }

    try {
      const updated = updateCommandStatus(command, newStatus, options)
      this.state.pendingCommandsByEntityId.set(entityId, updated)
      return updated
    } catch {
      return null
    }
  }

  removeCommand(entityId: number, finalStatus: 'completed' | 'rejected'): void {
    removeCommandFromWorld(this.state, entityId, finalStatus)
  }

  applyCommandUpdate(
    entityId: number,
    source: CommandUpdateSource,
    newStatus: PendingCommandStatus,
    options: {
      authoritativeTick?: number | null
      rejectionReason?: string | null
      movementSeed?: ShipDetails['movementCommand'] | null
    } = {},
  ): boolean {
    const update: CommandUpdateData = {
      source,
      clientTimestamp: Date.now(),
      authoritativeTick: options.authoritativeTick,
      data: {
        type: 'status_change',
        newStatus,
        options: {
          rejectionReason: options.rejectionReason,
          movementSeed: options.movementSeed,
        },
      },
    }

    const applied = applyCommandUpdateWithPriority(this.state, entityId, update)
    if (!applied) {
      return false
    }

    const entity = this.state.visibleEntitiesById.get(entityId)
    if (entity?.entityType === 'SHIP' && this.hasMoveSeed(options.movementSeed)) {
      this.ensurePredictedStateForCommand(entity, options.movementSeed)
    }

    if (newStatus === 'correcting') {
      this.resetMoveCompletionReport(entityId)
    }

    if (newStatus === 'rejected' || newStatus === 'completed') {
      this.reconcilePredictedShipToAuthoritative(entityId)
    }

    return true
  }

  applyImmediateCorrection(
    entityId: number,
    correctionData: {
      position: { x: number; y: number }
      velocity?: { x: number; y: number } | null
      headingDeg?: number
      movementCommand?: ShipDetails['movementCommand'] | null
    } | null | undefined,
  ): void {
    if (!correctionData) {
      return
    }

    const entity = this.state.visibleEntitiesById.get(entityId)
    if (!entity || entity.entityType !== 'SHIP') {
      return
    }

    const details = ((entity.details ?? {}) as ShipDetails)
    entity.posWorldGU = {
      x: correctionData.position.x,
      y: correctionData.position.y,
    }
    details.velocity = correctionData.velocity
      ? { x: correctionData.velocity.x, y: correctionData.velocity.y }
      : undefined
    details.headingDeg = correctionData.headingDeg ?? details.headingDeg
    details.movementCommand = correctionData.movementCommand ?? null
    details.isMoving = Boolean(correctionData.movementCommand)
    details.movementTarget = correctionData.movementCommand?.targetPosition ?? undefined

    const currentGameSeconds = defaultGameTimeManager.getCurrentGameSeconds()
    const predictedState = this.getOrCreatePredictedShipState(entity, currentGameSeconds)
    if (this.hasMoveSeed(correctionData.movementCommand)) {
      this.reseedPredictedStateIfNeeded(predictedState, entity, correctionData.movementCommand)
      this.advancePredictedShipState(predictedState, currentGameSeconds)
      return
    }

    this.syncPredictedStateFromAuthoritative(predictedState, entity, currentGameSeconds)
  }

  private deduplicateEntities(entities: EntitySnapshot[]): EntitySnapshot[] {
    const byId = new Map<number, EntitySnapshot>()
    for (const entity of entities) {
      byId.set(entity.entityId, entity)
    }
    return Array.from(byId.values())
  }

  private syncIntelVisibility(levelMap: Record<string, EntitySnapshot[]>): void {
    const nextLevels: Record<string, Set<number>> = {}
    for (const [level, entities] of Object.entries(levelMap)) {
      nextLevels[level] = new Set((entities ?? []).map(entity => entity.entityId))
    }
    this.state.intelVisibilityState.visiblePrivateEntitiesByLevel = nextLevels
    this.state.intelVisibilityState.lastSyncAtMs = Date.now()
  }

  private shouldRetainEntityWithoutSnapshot(entity: EntitySnapshot): boolean {
    if (this.state.focusedEntityIds.has(entity.entityId)) {
      return true
    }
    if (this.hasActiveCommand(entity.entityId)) {
      return true
    }
    if (
      entity.ownerNationId &&
      this.state.intelVisibilityState.currentNationId &&
      entity.ownerNationId === this.state.intelVisibilityState.currentNationId
    ) {
      return true
    }
    return false
  }

  private getEffectiveMovementSeed(
    entityId: number,
    entity: EntitySnapshot,
  ): ShipDetails['movementCommand'] | null {
    const pending = this.state.pendingCommandsByEntityId.get(entityId)
    if (
      pending &&
      pending.status !== 'completed' &&
      pending.status !== 'rejected' &&
      pending.movementSeed
    ) {
      return pending.movementSeed
    }

    const details = (entity.details ?? {}) as ShipDetails
    return details.movementCommand ?? null
  }

  private createShipStateFromEntity(entity: EntitySnapshot): ShipState {
    const details = (entity.details ?? {}) as ShipDetails
    const velocity = this.cloneVec2(details.velocity)
    const movementTarget = this.cloneVec2(details.movementTarget)
    const headingDeg = this.clampNumber(details.headingDeg, 0)

    return {
      entityId: entity.entityId,
      position: { x: entity.posWorldGU.x, y: entity.posWorldGU.y },
      velocity,
      movementTarget,
      isMoving: details.isMoving === true,
      currentHeadingDeg: headingDeg,
      targetHeadingDeg: movementTarget
        ? this.getHeadingToTarget(entity.posWorldGU, movementTarget)
        : headingDeg,
      maxSpeed: this.clampNumber(details.maxSpeed, 20),
      baseAcceleration: this.clampNumber(details.baseAcceleration, 5),
      bowAccelerationBonus: this.clampNumber(details.bowAccelerationBonus, 5),
      turnRate: this.clampNumber(details.turnRate, 45),
      lateralSpeedPenalty: this.clampNumber(details.lateralSpeedPenalty, 0.6),
      reverseSpeedPenalty: this.clampNumber(details.reverseSpeedPenalty, 0.3),
    }
  }

  private cloneShipState(shipState: ShipState): ShipState {
    return {
      entityId: shipState.entityId,
      position: { x: shipState.position.x, y: shipState.position.y },
      velocity: this.cloneVec2(shipState.velocity),
      movementTarget: this.cloneVec2(shipState.movementTarget),
      isMoving: shipState.isMoving,
      currentHeadingDeg: shipState.currentHeadingDeg,
      targetHeadingDeg: shipState.targetHeadingDeg,
      maxSpeed: shipState.maxSpeed,
      baseAcceleration: shipState.baseAcceleration,
      bowAccelerationBonus: shipState.bowAccelerationBonus,
      turnRate: shipState.turnRate,
      lateralSpeedPenalty: shipState.lateralSpeedPenalty,
      reverseSpeedPenalty: shipState.reverseSpeedPenalty,
    }
  }

  private captureLiveShipState(
    entity: EntitySnapshot,
    currentGameSeconds: number,
  ): ShipState {
    const predictedState = this.state.predictedShipsById.get(entity.entityId)
    if (predictedState) {
      this.advancePredictedShipState(predictedState, currentGameSeconds)
      return this.cloneShipState(predictedState.shipState)
    }

    return this.createShipStateFromEntity(entity)
  }

  private createShipStateFromSeed(
    entity: EntitySnapshot,
    movementSeed: MoveSeed,
    fallbackPosition: { x: number; y: number } | null = null,
  ): ShipState {
    const details = (entity.details ?? {}) as ShipDetails
    const startPosition = movementSeed.startPosition ?? fallbackPosition ?? entity.posWorldGU
    const targetPosition = movementSeed.targetPosition ?? details.movementTarget ?? entity.posWorldGU
    const baseHeading = this.clampNumber(
      movementSeed.startHeadingDeg,
      this.clampNumber(details.headingDeg, this.getHeadingToTarget(startPosition, targetPosition)),
    )

    return {
      entityId: entity.entityId,
      position: { x: startPosition.x, y: startPosition.y },
      velocity: this.cloneVec2(movementSeed.startVelocity) ?? this.cloneVec2(details.velocity),
      movementTarget: this.cloneVec2(targetPosition),
      isMoving: true,
      currentHeadingDeg: baseHeading,
      targetHeadingDeg: this.getHeadingToTarget(startPosition, targetPosition),
      maxSpeed: this.clampNumber(movementSeed.maxSpeed, this.clampNumber(details.maxSpeed, 20)),
      baseAcceleration: this.clampNumber(
        movementSeed.baseAcceleration,
        this.clampNumber(details.baseAcceleration, 5),
      ),
      bowAccelerationBonus: this.clampNumber(
        movementSeed.bowAccelerationBonus,
        this.clampNumber(details.bowAccelerationBonus, 5),
      ),
      turnRate: this.clampNumber(movementSeed.turnRate, this.clampNumber(details.turnRate, 45)),
      lateralSpeedPenalty: this.clampNumber(
        movementSeed.lateralSpeedPenalty,
        this.clampNumber(details.lateralSpeedPenalty, 0.6),
      ),
      reverseSpeedPenalty: this.clampNumber(
        movementSeed.reverseSpeedPenalty,
        this.clampNumber(details.reverseSpeedPenalty, 0.3),
      ),
    }
  }

  private hasMoveSeed(command: ShipDetails['movementCommand']): command is MoveSeed {
    return Boolean(
      command &&
        command.commandType === 'MOVE_TO' &&
        command.targetPosition &&
        typeof command.startGameSeconds === 'number' &&
        Number.isFinite(command.startGameSeconds),
    )
  }

  private buildCommandKey(command: MoveSeed): string {
    return JSON.stringify({
      commandType: command.commandType,
      clientCommandId: command.clientCommandId ?? null,
      targetPosition: command.targetPosition ?? null,
      startPosition: command.startPosition ?? null,
      startVelocity: command.startVelocity ?? null,
      startHeadingDeg: command.startHeadingDeg ?? null,
      startGameSeconds: command.startGameSeconds ?? null,
      startSimulationTick: command.startSimulationTick ?? null,
      maxSpeed: command.maxSpeed ?? null,
      baseAcceleration: command.baseAcceleration ?? null,
      bowAccelerationBonus: command.bowAccelerationBonus ?? null,
      turnRate: command.turnRate ?? null,
      lateralSpeedPenalty: command.lateralSpeedPenalty ?? null,
      reverseSpeedPenalty: command.reverseSpeedPenalty ?? null,
    })
  }

  private getHeadingToTarget(from: { x: number; y: number }, to: { x: number; y: number }): number {
    return (Math.atan2(to.y - from.y, to.x - from.x) * 180) / Math.PI
  }

  private getHeadingFromVelocity(
    velocity: { x: number; y: number } | null,
    fallback: number,
  ): number {
    if (!velocity) {
      return fallback
    }
    const speedSq = velocity.x * velocity.x + velocity.y * velocity.y
    if (speedSq <= 0.0001) {
      return fallback
    }
    return (Math.atan2(velocity.y, velocity.x) * 180) / Math.PI
  }

  private clampNumber(value: unknown, fallback: number): number {
    return typeof value === 'number' && Number.isFinite(value) ? value : fallback
  }

  private cloneVec2(
    value: { x: number; y: number } | null | undefined,
  ): { x: number; y: number } | null {
    if (!value) {
      return null
    }
    return { x: Number(value.x), y: Number(value.y) }
  }

  private getOrCreatePredictedShipState(
    entity: EntitySnapshot,
    snapshotGameSeconds: number,
  ): PredictedShipState {
    const existing = this.state.predictedShipsById.get(entity.entityId)
    if (existing) {
      return existing
    }

    const nextState: PredictedShipState = {
      shipState: this.createShipStateFromEntity(entity),
      lastSimulatedGameSeconds: snapshotGameSeconds,
      lastCommandKey: null,
      lastAuthoritativeSnapshotTick: this.state.lastSnapshotMeta?.simulationTick ?? null,
      isCorrecting: false,
      correctionTargetPosition: null,
      correctionStartGameSeconds: null,
      correctionDurationSec: null,
    }
    this.state.predictedShipsById.set(entity.entityId, nextState)
    return nextState
  }

  private reseedPredictedStateIfNeeded(
    predictedState: PredictedShipState,
    entity: EntitySnapshot,
    movementSeed: MoveSeed,
  ): void {
    const commandKey = this.buildCommandKey(movementSeed)
    if (predictedState.lastCommandKey === commandKey) {
      return
    }

    const fallbackPosition = { ...predictedState.shipState.position }
    predictedState.shipState = this.createShipStateFromSeed(entity, movementSeed, fallbackPosition)
    predictedState.lastSimulatedGameSeconds = movementSeed.startGameSeconds
    predictedState.lastCommandKey = commandKey
    predictedState.lastAuthoritativeSnapshotTick = this.state.lastSnapshotMeta?.simulationTick ?? null
    predictedState.isCorrecting = false
    predictedState.correctionTargetPosition = null
    predictedState.correctionStartGameSeconds = null
    predictedState.correctionDurationSec = null
  }

  private syncPredictedStateFromAuthoritative(
    predictedState: PredictedShipState,
    entity: EntitySnapshot,
    snapshotGameSeconds: number,
  ): void {
    const details = (entity.details ?? {}) as ShipDetails
    predictedState.shipState.position = { x: entity.posWorldGU.x, y: entity.posWorldGU.y }
    predictedState.shipState.velocity = this.cloneVec2(details.velocity)
    predictedState.shipState.movementTarget = this.cloneVec2(details.movementTarget)
    predictedState.shipState.isMoving = details.isMoving === true
    predictedState.shipState.currentHeadingDeg = this.clampNumber(
      details.headingDeg,
      this.getHeadingFromVelocity(
        predictedState.shipState.velocity,
        predictedState.shipState.currentHeadingDeg,
      ),
    )
    predictedState.shipState.targetHeadingDeg = predictedState.shipState.movementTarget
      ? this.getHeadingToTarget(
          predictedState.shipState.position,
          predictedState.shipState.movementTarget,
        )
      : predictedState.shipState.currentHeadingDeg
    predictedState.shipState.maxSpeed = this.clampNumber(
      details.maxSpeed,
      predictedState.shipState.maxSpeed,
    )
    predictedState.shipState.baseAcceleration = this.clampNumber(
      details.baseAcceleration,
      predictedState.shipState.baseAcceleration,
    )
    predictedState.shipState.bowAccelerationBonus = this.clampNumber(
      details.bowAccelerationBonus,
      predictedState.shipState.bowAccelerationBonus,
    )
    predictedState.shipState.turnRate = this.clampNumber(details.turnRate, predictedState.shipState.turnRate)
    predictedState.shipState.lateralSpeedPenalty = this.clampNumber(
      details.lateralSpeedPenalty,
      predictedState.shipState.lateralSpeedPenalty,
    )
    predictedState.shipState.reverseSpeedPenalty = this.clampNumber(
      details.reverseSpeedPenalty,
      predictedState.shipState.reverseSpeedPenalty,
    )
    predictedState.lastCommandKey = null
    predictedState.lastSimulatedGameSeconds = snapshotGameSeconds
    predictedState.lastAuthoritativeSnapshotTick = this.state.lastSnapshotMeta?.simulationTick ?? null
    predictedState.isCorrecting = false
    predictedState.correctionTargetPosition = null
    predictedState.correctionStartGameSeconds = null
    predictedState.correctionDurationSec = null
  }

  private advancePredictedShipState(
    predictedState: PredictedShipState,
    targetGameSeconds: number,
  ): void {
    if (
      !Number.isFinite(targetGameSeconds) ||
      targetGameSeconds <= predictedState.lastSimulatedGameSeconds
    ) {
      return
    }

    if (predictedState.shipState.isMoving && predictedState.shipState.movementTarget) {
      predictedState.shipState.targetHeadingDeg = this.getHeadingToTarget(
        predictedState.shipState.position,
        predictedState.shipState.movementTarget,
      )
    } else {
      predictedState.shipState.targetHeadingDeg = this.getHeadingFromVelocity(
        predictedState.shipState.velocity,
        predictedState.shipState.currentHeadingDeg,
      )
    }

    this.movementSystem.update(
      [predictedState.shipState],
      { gameTimeSeconds: targetGameSeconds },
      targetGameSeconds - predictedState.lastSimulatedGameSeconds,
    )

    if (!predictedState.shipState.isMoving) {
      predictedState.shipState.movementTarget = null
    }

    predictedState.lastSimulatedGameSeconds = targetGameSeconds
  }

  private createLocalMoveSeed(
    entity: EntitySnapshot,
    targetPosition: { x: number; y: number },
    currentGameSeconds: number = defaultGameTimeManager.getCurrentGameSeconds(),
    liveShipState: ShipState | null = null,
  ): MoveSeed {
    const details = (entity.details ?? {}) as ShipDetails
    const shipState = liveShipState ?? this.captureLiveShipState(entity, currentGameSeconds)
    const startPosition = shipState.position
    const startVelocity = shipState.velocity
    const startHeadingDeg = shipState.currentHeadingDeg

    return {
      commandType: 'MOVE_TO',
      targetPosition: { x: targetPosition.x, y: targetPosition.y },
      startPosition: { x: startPosition.x, y: startPosition.y },
      startVelocity: startVelocity ? { x: startVelocity.x, y: startVelocity.y } : undefined,
      startHeadingDeg,
      startGameSeconds: currentGameSeconds,
      startSimulationTick: this.state.lastSnapshotMeta?.simulationTick ?? undefined,
      maxSpeed: this.clampNumber(details.maxSpeed, 20),
      baseAcceleration: this.clampNumber(details.baseAcceleration, 5),
      bowAccelerationBonus: this.clampNumber(details.bowAccelerationBonus, 5),
      turnRate: this.clampNumber(details.turnRate, 45),
      lateralSpeedPenalty: this.clampNumber(details.lateralSpeedPenalty, 0.6),
      reverseSpeedPenalty: this.clampNumber(details.reverseSpeedPenalty, 0.3),
    }
  }

  private ensurePredictedStateForCommand(
    entity: EntitySnapshot,
    movementSeed: ShipDetails['movementCommand'] | null,
    currentGameSeconds: number = defaultGameTimeManager.getCurrentGameSeconds(),
    liveShipState: ShipState | null = null,
  ): void {
    if (!this.hasMoveSeed(movementSeed)) {
      return
    }

    const predictedState = this.getOrCreatePredictedShipState(entity, currentGameSeconds)
    if (predictedState.lastSimulatedGameSeconds < currentGameSeconds) {
      this.advancePredictedShipState(predictedState, currentGameSeconds)
    }
    const baseShipState = liveShipState ?? this.cloneShipState(predictedState.shipState)
    predictedState.shipState.position = { x: baseShipState.position.x, y: baseShipState.position.y }
    predictedState.shipState.velocity = this.cloneVec2(baseShipState.velocity)
    predictedState.shipState.currentHeadingDeg = baseShipState.currentHeadingDeg
    predictedState.shipState.targetHeadingDeg = baseShipState.targetHeadingDeg
    predictedState.shipState.isMoving = baseShipState.isMoving
    predictedState.shipState.movementTarget = this.cloneVec2(baseShipState.movementTarget)
    predictedState.lastSimulatedGameSeconds = currentGameSeconds
    this.reseedPredictedStateIfNeeded(predictedState, entity, movementSeed)
  }

  private reconcilePredictedShipToAuthoritative(entityId: number): void {
    const entity = this.state.visibleEntitiesById.get(entityId)
    const predictedState = this.state.predictedShipsById.get(entityId)
    if (!entity || entity.entityType !== 'SHIP' || !predictedState) {
      return
    }

    this.syncPredictedStateFromAuthoritative(
      predictedState,
      entity,
      defaultGameTimeManager.getCurrentGameSeconds(),
    )
  }

  private updateCommandsFromSnapshot(snapshotTick: number): void {
    for (const [entityId, entity] of this.state.visibleEntitiesById.entries()) {
      if (entity.entityType !== 'SHIP') {
        continue
      }

      const details = entity.details as ShipDetails | null
      updateCommandFromSnapshot(
        this.state,
        snapshotTick,
        entityId,
        details?.movementCommand ?? null,
      )
    }
  }
}

let globalInstance: LocalVisibleWorldImpl | null = null

export function getLocalVisibleWorld(): LocalVisibleWorldImpl {
  if (!globalInstance) {
    globalInstance = new LocalVisibleWorldImpl()
  }
  return globalInstance
}

export function resetLocalVisibleWorld(): void {
  globalInstance = null
}

import type {
  EntitySnapshot,
  SnapshotHighFreqMessage,
  SnapshotLowFreqMessage,
  SnapshotMessage,
  ShipDetails,
} from '../../net/snapshotWs'
import type {
  LocalVisibleWorld,
  AuthoritativeSnapshotMeta,
  HighFreqSnapshotFrame,
  LowFreqWorldState,
  WorldSyncResult,
  EntityDisplayPosition,
  InterpolationDebugState,
  EntityInterpolationDebugState,
  EntityInterpolationCaptureState,
  PendingCommandRecord,
  PendingCommandStatus,
  CommandTransportStatus,
} from './localVisibleWorldTypes'
import {
  createPendingCommand,
  updateCommandStatus,
  updateCommandTransport,
  addPendingCommandToWorld,
  removeCommandFromWorld,
  getEntityCommandStatus,
  hasActiveCommand,
  resolveCommandConflict,
  applyCommandUpdateWithPriority,
  type CommandUpdateSource,
  type CommandUpdateData,
} from './localVisibleWorldCommands'
import { defaultGameTimeManager } from '../time/GameTimeManager'
import {
  SnapshotInterpolationBuffer,
  DEFAULT_SNAPSHOT_INTERPOLATION_CONFIG,
  buildAuthoritativeDisplayPosition,
  sampleInterpolatedEntityDisplayPosition,
  type SnapshotInterpolationWindow,
} from './interpolation'

type MoveSeed = NonNullable<ShipDetails['movementCommand']>
const MAX_HIGH_FREQ_FRAME_CACHE_SIZE = DEFAULT_SNAPSHOT_INTERPOLATION_CONFIG.maxBufferedHighFreqTicks
const ENTITY_RETENTION_MS = 5_000

export class LocalVisibleWorldImpl {
  private readonly state: LocalVisibleWorld
  private readonly interpolationBuffer = new SnapshotInterpolationBuffer()
  private activeRenderFrameTimestampMs: number | null = null
  private lastRenderFrameTimestampMs: number | null = null
  private activeRenderFrameDeltaMs = 0
  private activeRenderFrameWindow: SnapshotInterpolationWindow | null = null
  private activeRenderFrameDebugState: InterpolationDebugState | null = null
  private readonly activeRenderFrameDisplayPositionsByEntityId = new Map<number, EntityDisplayPosition | null>()
  private readonly lastPresentedDisplayPositionsByEntityId = new Map<number, EntityDisplayPosition>()
  private readonly entityInterpolationDebugById = new Map<number, EntityInterpolationDebugState>()
  private captureEntityId: number | null = null
  private captureRemainingFrames = 0
  private captureFrames: EntityInterpolationDebugState[] = []

  constructor() {
    this.state = {
      visibleEntitiesById: new Map(),
      highFreqFrames: [],
      latestLowFreqState: null,
      pendingCommandsByEntityId: new Map(),
      lastSnapshotMeta: null,
      intelVisibilityState: {
        currentNationId: null,
        lastSyncAtMs: 0,
        visiblePrivateEntitiesByLevel: {},
      },
      interestEntityIds: new Set(),
      retainedUntilClientMsByEntityId: new Map(),
      focusedEntityIds: new Set(),
      lastAppliedHighFreqTick: null,
      lastAppliedLowFreqVersion: null,
    }
  }

  getVisibleEntityCount(): number {
    return this.state.visibleEntitiesById.size
  }

  getHighFreqFrameCount(): number {
    return this.state.highFreqFrames.length
  }

  getPendingCommandCount(): number {
    return this.state.pendingCommandsByEntityId.size
  }

  getLastAppliedTick(): number | null {
    return this.state.lastAppliedHighFreqTick
  }

  getLastAppliedVersion(): number | null {
    return this.state.lastAppliedLowFreqVersion
  }

  getLatestLowFreqState(): LowFreqWorldState | null {
    return this.state.latestLowFreqState
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

    return this.applyPendingCommandFeedback(
      entityId,
      buildAuthoritativeDisplayPosition(entity),
    )
  }

  getInterpolatedEntityDisplayPosition(entityId: number): EntityDisplayPosition | null {
    this.ensureRenderFramePrepared()

    const cachedDisplayPosition = this.activeRenderFrameDisplayPositionsByEntityId.get(entityId)
    if (cachedDisplayPosition !== undefined) {
      return cachedDisplayPosition
    }

    const authoritativeEntity = this.getEntitySnapshot(entityId)
    const interpolationWindow = this.activeRenderFrameWindow
    const previousDisplayPosition = this.lastPresentedDisplayPositionsByEntityId.get(entityId) ?? null

    if (!interpolationWindow) {
      const fallbackDisplayPosition = authoritativeEntity
        ? this.applyPendingCommandFeedback(
            entityId,
            buildAuthoritativeDisplayPosition(authoritativeEntity),
          )
        : null
      this.activeRenderFrameDisplayPositionsByEntityId.set(entityId, fallbackDisplayPosition)
      if (fallbackDisplayPosition) {
        this.lastPresentedDisplayPositionsByEntityId.set(entityId, fallbackDisplayPosition)
      }
      this.recordEntityInterpolationDebug(
        entityId,
        previousDisplayPosition,
        fallbackDisplayPosition,
        fallbackDisplayPosition,
        fallbackDisplayPosition ? 1 : 0,
        null,
      )
      return fallbackDisplayPosition
    }

    const displayPosition = sampleInterpolatedEntityDisplayPosition({
      entityId,
      fallbackEntity: authoritativeEntity,
      window: interpolationWindow,
      teleportThresholdGU: this.interpolationBuffer.getConfig().teleportThresholdGU,
    })
    const displayPositionWithCommandFeedback = this.applyPendingCommandFeedback(
      entityId,
      displayPosition,
    )
    this.activeRenderFrameDisplayPositionsByEntityId.set(
      entityId,
      displayPositionWithCommandFeedback,
    )
    if (displayPositionWithCommandFeedback) {
      this.lastPresentedDisplayPositionsByEntityId.set(
        entityId,
        displayPositionWithCommandFeedback,
      )
    } else {
      this.lastPresentedDisplayPositionsByEntityId.delete(entityId)
    }
    this.recordEntityInterpolationDebug(
      entityId,
      previousDisplayPosition,
      displayPositionWithCommandFeedback,
      displayPositionWithCommandFeedback,
      interpolationWindow.nextSnapshotBuffer ? interpolationWindow.renderAlpha : 1,
      interpolationWindow,
    )
    return displayPositionWithCommandFeedback
  }

  getInterpolationDebugState(): InterpolationDebugState | null {
    this.ensureRenderFramePrepared()
    return this.activeRenderFrameDebugState
  }

  getEntityInterpolationDebugState(entityId: number): EntityInterpolationDebugState | null {
    this.ensureRenderFramePrepared()
    return this.entityInterpolationDebugById.get(entityId) ?? null
  }

  startEntityInterpolationCapture(entityId: number, frameCount = 30): void {
    if (!Number.isFinite(entityId) || entityId <= 0) {
      return
    }
    this.captureEntityId = entityId
    this.captureRemainingFrames = Math.max(1, Math.floor(frameCount))
    this.captureFrames = []
  }

  getEntityInterpolationCaptureState(): EntityInterpolationCaptureState {
    return {
      entityId: this.captureEntityId,
      remainingFrames: this.captureRemainingFrames,
      frames: this.captureFrames.slice(),
    }
  }

  beginRenderFrame(renderTimestampMs: number): void {
    if (
      this.activeRenderFrameTimestampMs !== null &&
      Math.abs(this.activeRenderFrameTimestampMs - renderTimestampMs) <= 0.001
    ) {
      return
    }

    const currentGameTimeState = defaultGameTimeManager.update()
    this.activeRenderFrameDeltaMs =
      this.lastRenderFrameTimestampMs === null
        ? 0
        : Math.max(0, renderTimestampMs - this.lastRenderFrameTimestampMs)
    this.lastRenderFrameTimestampMs = renderTimestampMs
    this.activeRenderFrameTimestampMs = renderTimestampMs
    this.activeRenderFrameDisplayPositionsByEntityId.clear()
    this.entityInterpolationDebugById.clear()
    this.activeRenderFrameWindow = this.interpolationBuffer.getWindow({
      frames: this.state.highFreqFrames,
      currentGameSeconds: currentGameTimeState.currentGameSeconds,
      nowMs: renderTimestampMs,
      realMsToGameSeconds: realMs => defaultGameTimeManager.realMsToGameSeconds(realMs),
    })
    this.activeRenderFrameDebugState = this.activeRenderFrameWindow
      ? {
          currentTick: this.activeRenderFrameWindow.currentSnapshot.simulationTick,
          nextTick: this.activeRenderFrameWindow.nextSnapshotBuffer?.simulationTick ?? null,
          latestTick: this.activeRenderFrameWindow.latestFrame.simulationTick,
          renderAlpha: this.activeRenderFrameWindow.renderAlpha,
          renderGameSeconds: this.activeRenderFrameWindow.renderGameSeconds,
          mode: this.activeRenderFrameWindow.mode,
          didResetWindow: this.activeRenderFrameWindow.didResetWindow,
        }
      : null
  }

  reset(): void {
    this.state.visibleEntitiesById.clear()
    this.state.highFreqFrames = []
    this.state.latestLowFreqState = null
    this.state.pendingCommandsByEntityId.clear()
    this.state.lastSnapshotMeta = null
    this.state.intelVisibilityState = {
      currentNationId: this.state.intelVisibilityState.currentNationId,
      lastSyncAtMs: 0,
      visiblePrivateEntitiesByLevel: {},
    }
    this.state.interestEntityIds.clear()
    this.state.retainedUntilClientMsByEntityId.clear()
    this.state.focusedEntityIds.clear()
    this.state.lastAppliedHighFreqTick = null
    this.state.lastAppliedLowFreqVersion = null
    this.interpolationBuffer.reset()
    this.resetRenderFrameState()
    this.lastRenderFrameTimestampMs = null
    this.lastPresentedDisplayPositionsByEntityId.clear()
  }

  applySnapshot(snapshot: SnapshotMessage): WorldSyncResult {
    if (!snapshot.ok || !snapshot.realTimeWorldState) {
      return {
        success: false,
        addedEntities: 0,
        updatedEntities: 0,
        removedEntities: 0,
        appliedTick: null,
        triggeredCorrection: false,
        correctedEntities: 0,
      }
    }

    const rt = snapshot.realTimeWorldState
    this.applyLowFreqSnapshot({
      type: 'snapshot_low_freq',
      ok: snapshot.ok,
      error: snapshot.error,
      simulationTick: rt.simulationTick,
      version: rt.simulationTick,
      syncMode: 'full',
      worldRadius: rt.worldRadius,
      worldType: rt.worldType,
      gameSecondsPerRealSecond: rt.gameSecondsPerRealSecond,
      timeScale: rt.timeScale,
      year: rt.year,
      month: rt.month,
      day: rt.day,
      hour: rt.hour,
      minute: rt.minute,
      second: rt.second,
      sectorCenters: rt.sectorCenters,
      sectorOwnerNationIdByCoord: rt.sectorOwnerNationIdByCoord,
      dailySettlementState: snapshot.dailySettlementState,
    })

    return this.applyHighFreqSnapshot({
      type: 'snapshot_high_freq',
      ok: snapshot.ok,
      error: snapshot.error,
      tickCostMs: snapshot.tickCostMs,
      simulationTick: rt.simulationTick,
      totalGameSeconds: rt.totalGameSeconds,
      totalGameSecondsExact: rt.totalGameSecondsExact ?? rt.totalGameSeconds,
      deltaGameSeconds: rt.deltaGameSeconds,
      syncMode: 'full',
      entities: rt.entities,
      privateEntitiesByIntelLevel: rt.privateEntitiesByIntelLevel,
      playerNationId: snapshot.playerNationId,
    })
  }

  applyHighFreqSnapshot(snapshot: SnapshotHighFreqMessage): WorldSyncResult {
    const result: WorldSyncResult = {
      success: false,
      addedEntities: 0,
      updatedEntities: 0,
      removedEntities: 0,
      appliedTick: null,
      triggeredCorrection: false,
      correctedEntities: 0,
    }

    if (!snapshot.ok) {
      return result
    }

    const snapshotTick = snapshot.simulationTick
    if (
      snapshot.syncMode === 'delta' &&
      snapshot.baseTick !== undefined &&
      snapshot.baseTick !== null &&
      this.state.lastAppliedHighFreqTick !== null &&
      snapshotTick > this.state.lastAppliedHighFreqTick &&
      snapshot.baseTick !== this.state.lastAppliedHighFreqTick
    ) {
      return result
    }

    if (
      this.state.lastAppliedHighFreqTick !== null &&
      snapshotTick < this.state.lastAppliedHighFreqTick
    ) {
      return result
    }

    result.appliedTick = snapshotTick
    this.state.lastSnapshotMeta = {
      simulationTick: snapshotTick,
      totalGameSeconds: snapshot.totalGameSeconds,
      totalGameSecondsExact: snapshot.totalGameSecondsExact ?? null,
      receivedAtClientMs: Date.now(),
    }

    defaultGameTimeManager.updateSnapshot({
      simulationTick: snapshotTick,
      totalGameSeconds: snapshot.totalGameSeconds,
      totalGameSecondsExact: snapshot.totalGameSecondsExact,
      deltaGameSeconds: snapshot.deltaGameSeconds,
      timeScale: this.state.latestLowFreqState?.timeScale ?? undefined,
      gameSecondsPerRealSecond: this.state.latestLowFreqState?.gameSecondsPerRealSecond ?? undefined,
    })

    this.syncIntelVisibility(snapshot.privateEntitiesByIntelLevel ?? {})

    const incomingEntities = this.deduplicateEntities([
      ...(snapshot.entities ?? []),
      ...Object.values(snapshot.privateEntitiesByIntelLevel ?? {}).flatMap(level => level ?? []),
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

    this.refreshInterestEntityIds()
    this.markRetainedEntitiesForMissingSnapshots(incomingIds)

    if (snapshot.syncMode === 'full') {
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
        this.state.retainedUntilClientMsByEntityId.delete(entityId)
        if (!this.hasActiveCommand(entityId)) {
          this.state.pendingCommandsByEntityId.delete(entityId)
        }
        result.removedEntities++
      }
    }

    this.state.lastAppliedHighFreqTick = snapshotTick
    this.pushHighFreqFrame(snapshot, incomingEntities)
    result.success = true
    return result
  }

  applyLowFreqSnapshot(snapshot: SnapshotLowFreqMessage): boolean {
    if (!snapshot.ok) {
      return false
    }

    if (
      snapshot.syncMode === 'delta' &&
      snapshot.baseVersion !== undefined &&
      snapshot.baseVersion !== null &&
      this.state.lastAppliedLowFreqVersion !== null &&
      snapshot.baseVersion !== this.state.lastAppliedLowFreqVersion
    ) {
      return false
    }

    if (
      this.state.lastAppliedLowFreqVersion !== null &&
      snapshot.version <= this.state.lastAppliedLowFreqVersion
    ) {
      return false
    }

    const previous = this.state.latestLowFreqState
    this.state.latestLowFreqState = {
      simulationTick: snapshot.simulationTick,
      version: snapshot.version,
      syncMode: snapshot.syncMode,
      baseVersion: snapshot.baseVersion ?? null,
      worldRadius: snapshot.worldRadius ?? previous?.worldRadius ?? null,
      worldType: snapshot.worldType ?? previous?.worldType ?? null,
      gameSecondsPerRealSecond:
        snapshot.gameSecondsPerRealSecond ?? previous?.gameSecondsPerRealSecond ?? null,
      timeScale: snapshot.timeScale ?? previous?.timeScale ?? null,
      year: snapshot.year ?? previous?.year ?? null,
      month: snapshot.month ?? previous?.month ?? null,
      day: snapshot.day ?? previous?.day ?? null,
      hour: snapshot.hour ?? previous?.hour ?? null,
      minute: snapshot.minute ?? previous?.minute ?? null,
      second: snapshot.second ?? previous?.second ?? null,
      sectorCenters: snapshot.sectorCenters ?? previous?.sectorCenters ?? [],
      sectorOwnerNationIdByCoord:
        snapshot.sectorOwnerNationIdByCoord ?? previous?.sectorOwnerNationIdByCoord ?? {},
      dailySettlementState: snapshot.dailySettlementState ?? previous?.dailySettlementState ?? null,
      playerNationId: snapshot.playerNationId ?? previous?.playerNationId ?? null,
      receivedAtClientMs: Date.now(),
    }
    this.state.lastAppliedLowFreqVersion = snapshot.version
    return true
  }

  replaceVisibleEntities(entities: EntitySnapshot[]): void {
    this.state.visibleEntitiesById.clear()
    for (const entity of entities) {
      this.state.visibleEntitiesById.set(entity.entityId, entity)
    }
    this.refreshInterestEntityIds()
  }

  setSnapshotMeta(meta: AuthoritativeSnapshotMeta): void {
    this.state.lastSnapshotMeta = meta
  }

  addFocusedEntity(entityId: number): void {
    this.state.focusedEntityIds.add(entityId)
    this.refreshInterestEntityIds()
  }

  removeFocusedEntity(entityId: number): void {
    this.state.focusedEntityIds.delete(entityId)
    this.refreshInterestEntityIds()
  }

  setFocusedEntities(entityIds: number[]): void {
    this.state.focusedEntityIds = new Set(entityIds)
    this.refreshInterestEntityIds()
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
    this.refreshInterestEntityIds()
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

  getPendingCommand(entityId: number): PendingCommandRecord | null {
    return getEntityCommandStatus(this.state, entityId)
  }

  hasActiveCommand(entityId: number): boolean {
    return hasActiveCommand(this.state, entityId)
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
    const seed =
      movementSeed ?? this.createLocalMoveSeed(entity, targetPosition, currentGameSeconds)
    const command = createPendingCommand(entityId, 'MOVE_TO', seed)
    if (!resolveCommandConflict(this.state, entityId, command, conflictStrategy)) {
      return null
    }
    if (!addPendingCommandToWorld(this.state, command)) {
      return null
    }

    this.refreshInterestEntityIds()
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

    this.refreshInterestEntityIds()
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
      this.refreshInterestEntityIds()
      return updated
    } catch {
      return null
    }
  }

  recordCommandTransportAck(
    entityId: number,
    transportStatus: CommandTransportStatus,
    options: {
      transportError?: string | null
      acknowledgedAtClientMs?: number
    } = {},
  ): PendingCommandRecord | null {
    const command = this.state.pendingCommandsByEntityId.get(entityId)
    if (!command) {
      return null
    }

    const updated = updateCommandTransport(command, transportStatus, options)
    this.state.pendingCommandsByEntityId.set(entityId, updated)
    this.refreshInterestEntityIds()
    return updated
  }

  removeCommand(entityId: number, finalStatus: 'completed' | 'rejected'): void {
    removeCommandFromWorld(this.state, entityId, finalStatus)
    this.refreshInterestEntityIds()
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

    this.refreshInterestEntityIds()
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

  private pushHighFreqFrame(
    snapshot: SnapshotHighFreqMessage,
    incomingEntities: EntitySnapshot[],
  ): void {
    const privateEntityIdsByLevel: Record<string, Set<number>> = {}
    for (const [level, entities] of Object.entries(snapshot.privateEntitiesByIntelLevel ?? {})) {
      privateEntityIdsByLevel[level] = new Set((entities ?? []).map(entity => entity.entityId))
    }

    const frameEntitiesById = new Map<number, EntitySnapshot>()
    for (const entity of incomingEntities) {
      frameEntitiesById.set(entity.entityId, entity)
    }

    const frame: HighFreqSnapshotFrame = {
      simulationTick: snapshot.simulationTick,
      totalGameSeconds: snapshot.totalGameSeconds,
      totalGameSecondsExact: snapshot.totalGameSecondsExact,
      deltaGameSeconds: snapshot.deltaGameSeconds,
      tickCostMs: snapshot.tickCostMs ?? null,
      syncMode: snapshot.syncMode,
      baseTick: snapshot.baseTick ?? null,
      entitiesById: frameEntitiesById,
      privateEntityIdsByLevel,
      receivedAtClientMs: performance.now(),
    }

    const existingFrameIndex = this.state.highFreqFrames.findIndex(
      existingFrame => existingFrame.simulationTick === frame.simulationTick,
    )
    if (existingFrameIndex >= 0) {
      this.state.highFreqFrames[existingFrameIndex] = frame
    } else {
      this.state.highFreqFrames.push(frame)
    }

    this.state.highFreqFrames.sort((left, right) => left.simulationTick - right.simulationTick)
    if (this.state.highFreqFrames.length > MAX_HIGH_FREQ_FRAME_CACHE_SIZE) {
      this.state.highFreqFrames.splice(0, this.state.highFreqFrames.length - MAX_HIGH_FREQ_FRAME_CACHE_SIZE)
    }
  }

  private refreshInterestEntityIds(): void {
    const interestEntityIds = new Set<number>(this.state.focusedEntityIds)

    for (const [entityId, command] of this.state.pendingCommandsByEntityId.entries()) {
      if (command.status === 'completed' || command.status === 'rejected') {
        continue
      }
      interestEntityIds.add(entityId)
    }

    const currentNationId = this.state.intelVisibilityState.currentNationId
    if (currentNationId) {
      for (const entity of this.state.visibleEntitiesById.values()) {
        if (entity.ownerNationId === currentNationId) {
          interestEntityIds.add(entity.entityId)
        }
      }
    }

    this.state.interestEntityIds = interestEntityIds
  }

  private markRetainedEntitiesForMissingSnapshots(incomingIds: Set<number>): void {
    const retainUntil = Date.now() + ENTITY_RETENTION_MS
    for (const entityId of this.state.interestEntityIds) {
      if (incomingIds.has(entityId)) {
        continue
      }
      if (!this.state.visibleEntitiesById.has(entityId)) {
        continue
      }
      this.state.retainedUntilClientMsByEntityId.set(entityId, retainUntil)
    }
  }

  private shouldRetainEntityWithoutSnapshot(entity: EntitySnapshot): boolean {
    const entityId = entity.entityId
    if (this.state.focusedEntityIds.has(entityId)) {
      return true
    }
    if (this.state.interestEntityIds.has(entityId)) {
      return true
    }
    if (this.hasActiveCommand(entityId)) {
      return true
    }

    const retainedUntil = this.state.retainedUntilClientMsByEntityId.get(entityId)
    if (retainedUntil && retainedUntil > Date.now()) {
      return true
    }
    this.state.retainedUntilClientMsByEntityId.delete(entityId)

    if (
      entity.ownerNationId &&
      this.state.intelVisibilityState.currentNationId &&
      entity.ownerNationId === this.state.intelVisibilityState.currentNationId
    ) {
      return true
    }
    return false
  }

  /**
   * 把待确认移动命令的本地目标点叠加到显示姿态里喵。
   *
   * 这里只补即时命令反馈喵。
   * 不改权威位置、不改插值结果，只让右键后的路径线和目标点立即可见喵。
   */
  private applyPendingCommandFeedback(
    entityId: number,
    displayPosition: EntityDisplayPosition | null,
  ): EntityDisplayPosition | null {
    if (!displayPosition) {
      return null
    }

    const command = this.state.pendingCommandsByEntityId.get(entityId)
    if (!command || !this.shouldUsePendingMoveCommandFeedback(command)) {
      return displayPosition
    }

    const targetPosition = command.movementSeed?.targetPosition
    if (!targetPosition) {
      return displayPosition
    }

    return {
      ...displayPosition,
      isMoving: true,
      movementTarget: {
        x: targetPosition.x,
        y: targetPosition.y,
      },
      usesCommandSeed: true,
    }
  }

  /**
   * 判断待确认命令是否仍应提供本地移动反馈喵。
   */
  private shouldUsePendingMoveCommandFeedback(command: PendingCommandRecord): boolean {
    if (command.commandType !== 'MOVE_TO') {
      return false
    }
    if (command.status === 'completed' || command.status === 'rejected') {
      return false
    }
    if (command.transportStatus === 'failed') {
      return false
    }
    return Boolean(command.movementSeed?.targetPosition)
  }

  private createLocalMoveSeed(
    entity: EntitySnapshot,
    targetPosition: { x: number; y: number },
    currentGameSeconds: number = defaultGameTimeManager.getCurrentGameSeconds(),
  ): MoveSeed {
    const details = (entity.details ?? {}) as ShipDetails
    const startPosition = { x: entity.posWorldGU.x, y: entity.posWorldGU.y }
    const startVelocity = this.cloneVec2(details.velocity)
    const startHeadingDeg = this.clampNumber(
      details.headingDeg,
      this.getHeadingToTarget(startPosition, targetPosition),
    )

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

  private getHeadingToTarget(from: { x: number; y: number }, to: { x: number; y: number }): number {
    return (Math.atan2(to.y - from.y, to.x - from.x) * 180) / Math.PI
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

  private ensureRenderFramePrepared(): void {
    if (this.activeRenderFrameTimestampMs !== null) {
      return
    }

    // 非渲染主循环的查询方喵，例如调试面板或属性面板喵，
    // 仍然需要一个当前时刻的插值窗口作为兜底喵。
    this.beginRenderFrame(performance.now())
  }

  private resetRenderFrameState(): void {
    this.activeRenderFrameTimestampMs = null
    this.activeRenderFrameWindow = null
    this.activeRenderFrameDebugState = null
    this.activeRenderFrameDeltaMs = 0
    this.activeRenderFrameDisplayPositionsByEntityId.clear()
    this.entityInterpolationDebugById.clear()
  }

  private recordEntityInterpolationDebug(
    entityId: number,
    previousDisplayPosition: EntityDisplayPosition | null,
    targetDisplayPosition: EntityDisplayPosition | null,
    presentedDisplayPosition: EntityDisplayPosition | null,
    frameBlendAlpha: number,
    window: SnapshotInterpolationWindow | null,
  ): void {
    const debugState: EntityInterpolationDebugState = {
      entityId,
      previousRenderPosition: previousDisplayPosition?.position ?? null,
      targetRenderPosition: targetDisplayPosition?.position ?? null,
      presentedRenderPosition: presentedDisplayPosition?.position ?? null,
      renderedMeshPosition: null,
      previousRenderHeadingDeg: previousDisplayPosition?.headingDeg ?? null,
      targetRenderHeadingDeg: targetDisplayPosition?.headingDeg ?? null,
      presentedRenderHeadingDeg: presentedDisplayPosition?.headingDeg ?? null,
      renderedMeshHeadingDeg: null,
      frameBlendAlpha,
      renderFrameDeltaMs: this.activeRenderFrameDeltaMs,
      currentTick: window?.currentSnapshot.simulationTick ?? -1,
      nextTick: window?.nextSnapshotBuffer?.simulationTick ?? null,
      renderGameSeconds: window?.renderGameSeconds ?? defaultGameTimeManager.getCurrentGameSeconds(),
      targetSource: targetDisplayPosition?.source ?? 'none',
    }

    this.entityInterpolationDebugById.set(entityId, debugState)

    if (this.captureEntityId === entityId && this.captureRemainingFrames > 0) {
      this.captureFrames.push(debugState)
      this.captureRemainingFrames -= 1
      if (this.captureRemainingFrames <= 0) {
        this.captureEntityId = entityId
      }
    }
  }

  /**
   * 记录渲染器最终写入网格的姿态喵。
   *
   * 用于对比“插值显示姿态”和“真正画出去的网格姿态”是否一致喵。
   */
  recordRenderedEntityPose(
    entityId: number,
    renderedMeshPosition: { x: number; y: number },
    renderedMeshHeadingDeg: number,
  ): void {
    const debugState = this.entityInterpolationDebugById.get(entityId)
    if (!debugState) {
      return
    }

    debugState.renderedMeshPosition = {
      x: renderedMeshPosition.x,
      y: renderedMeshPosition.y,
    }
    debugState.renderedMeshHeadingDeg = renderedMeshHeadingDeg
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

export function beginLocalVisibleWorldRenderFrame(renderTimestampMs: number): void {
  getLocalVisibleWorld().beginRenderFrame(renderTimestampMs)
}

export function recordLocalVisibleWorldRenderedEntityPose(
  entityId: number,
  renderedMeshPosition: { x: number; y: number },
  renderedMeshHeadingDeg: number,
): void {
  getLocalVisibleWorld().recordRenderedEntityPose(
    entityId,
    renderedMeshPosition,
    renderedMeshHeadingDeg,
  )
}

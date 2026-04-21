import type { EntitySnapshot, ShipDetails } from '../net/snapshotWs'
import { ShipMovementSystemFrontend, type ShipState } from './systems/ShipMovementSystemFrontend'
import { defaultGameTimeManager } from './time/GameTimeManager'

type MovementCommandSeed = NonNullable<ShipDetails['movementCommand']>

type ShipEstimatorState = {
    shipState: ShipState
    lastSimulatedGameSeconds: number
    lastCommandKey: string | null
}

export type EstimatedShipPose = {
    position: { x: number; y: number }
    velocity: { x: number; y: number } | null
    headingDeg: number
    isMoving: boolean
    movementTarget: { x: number; y: number } | null
    usesCommandSeed: boolean
}

const movementSystem = new ShipMovementSystemFrontend()
const statesByEntityId = new Map<number, ShipEstimatorState>()

function clampNumber(value: unknown, fallback: number): number {
    return typeof value === 'number' && Number.isFinite(value) ? value : fallback
}

function cloneVec2(value: { x: number; y: number } | null | undefined): { x: number; y: number } | null {
    if (!value) return null
    return { x: Number(value.x), y: Number(value.y) }
}

function hasMoveSeed(command: ShipDetails['movementCommand']): command is MovementCommandSeed {
    return Boolean(
        command &&
        command.commandType === 'MOVE_TO' &&
        command.startPosition &&
        command.targetPosition &&
        typeof command.startGameSeconds === 'number' &&
        Number.isFinite(command.startGameSeconds),
    )
}

function buildCommandKey(command: MovementCommandSeed): string {
    return JSON.stringify({
        type: command.commandType,
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

function getHeadingToTarget(from: { x: number; y: number }, to: { x: number; y: number }): number {
    return (Math.atan2(to.y - from.y, to.x - from.x) * 180) / Math.PI
}

function getHeadingFromVelocity(velocity: { x: number; y: number } | null, fallback: number): number {
    if (!velocity) return fallback
    const speedSq = velocity.x * velocity.x + velocity.y * velocity.y
    if (speedSq <= 0.0001) return fallback
    return (Math.atan2(velocity.y, velocity.x) * 180) / Math.PI
}

function createShipStateFromEntity(entity: EntitySnapshot): ShipState {
    const details = (entity.details ?? {}) as ShipDetails
    const velocity = cloneVec2(details.velocity)
    const movementTarget = cloneVec2(details.movementTarget)
    const baseHeading = clampNumber(details.headingDeg, 0)
    return {
        entityId: entity.entityId,
        position: { x: entity.posWorldGU.x, y: entity.posWorldGU.y },
        velocity,
        movementTarget,
        isMoving: details.isMoving === true,
        currentHeadingDeg: baseHeading,
        targetHeadingDeg: movementTarget ? getHeadingToTarget(entity.posWorldGU, movementTarget) : baseHeading,
        maxSpeed: clampNumber(details.maxSpeed, 20),
        baseAcceleration: clampNumber(details.baseAcceleration, 5),
        bowAccelerationBonus: clampNumber(details.bowAccelerationBonus, 5),
        turnRate: clampNumber(details.turnRate, 45),
        lateralSpeedPenalty: clampNumber(details.lateralSpeedPenalty, 0.6),
        reverseSpeedPenalty: clampNumber(details.reverseSpeedPenalty, 0.3),
    }
}

function createShipStateFromCommand(entity: EntitySnapshot, command: MovementCommandSeed): ShipState {
    const details = (entity.details ?? {}) as ShipDetails
    const startPosition = command.startPosition ?? entity.posWorldGU
    const targetPosition = command.targetPosition ?? details.movementTarget ?? entity.posWorldGU
    const startVelocity = cloneVec2(command.startVelocity) ?? cloneVec2(details.velocity)
    const startHeadingDeg = clampNumber(
        command.startHeadingDeg,
        clampNumber(details.headingDeg, getHeadingToTarget(startPosition, targetPosition)),
    )
    return {
        entityId: entity.entityId,
        position: { x: startPosition.x, y: startPosition.y },
        velocity: startVelocity,
        movementTarget: { x: targetPosition.x, y: targetPosition.y },
        isMoving: true,
        currentHeadingDeg: startHeadingDeg,
        targetHeadingDeg: getHeadingToTarget(startPosition, targetPosition),
        maxSpeed: clampNumber(command.maxSpeed, clampNumber(details.maxSpeed, 20)),
        baseAcceleration: clampNumber(command.baseAcceleration, clampNumber(details.baseAcceleration, 5)),
        bowAccelerationBonus: clampNumber(command.bowAccelerationBonus, clampNumber(details.bowAccelerationBonus, 5)),
        turnRate: clampNumber(command.turnRate, clampNumber(details.turnRate, 45)),
        lateralSpeedPenalty: clampNumber(command.lateralSpeedPenalty, clampNumber(details.lateralSpeedPenalty, 0.6)),
        reverseSpeedPenalty: clampNumber(command.reverseSpeedPenalty, clampNumber(details.reverseSpeedPenalty, 0.3)),
    }
}

function advanceEstimatorState(state: ShipEstimatorState, targetGameSeconds: number): void {
    if (!Number.isFinite(targetGameSeconds) || targetGameSeconds <= state.lastSimulatedGameSeconds) {
        return
    }
    if (state.shipState.isMoving && state.shipState.movementTarget) {
        state.shipState.targetHeadingDeg = getHeadingToTarget(state.shipState.position, state.shipState.movementTarget)
    } else {
        state.shipState.targetHeadingDeg = getHeadingFromVelocity(
            state.shipState.velocity,
            state.shipState.currentHeadingDeg,
        )
    }
    movementSystem.update(
        [state.shipState],
        { gameTimeSeconds: targetGameSeconds },
        targetGameSeconds - state.lastSimulatedGameSeconds,
    )
    if (!state.shipState.isMoving) {
        state.shipState.movementTarget = null
    }
    state.lastSimulatedGameSeconds = targetGameSeconds
}

function getOrCreateEstimatorState(
    entity: EntitySnapshot,
    snapshotGameSeconds: number,
): ShipEstimatorState {
    const details = (entity.details ?? {}) as ShipDetails
    const command = details.movementCommand
    const existing = statesByEntityId.get(entity.entityId)

    if (hasMoveSeed(command)) {
        const commandKey = buildCommandKey(command)
        if (existing && existing.lastCommandKey === commandKey) {
            return existing
        }

        const state = {
            shipState: createShipStateFromCommand(entity, command),
            lastSimulatedGameSeconds: command.startGameSeconds,
            lastCommandKey: commandKey,
        }
        statesByEntityId.set(entity.entityId, state)
        return state
    }

    if (existing) {
        return existing
    }

    const state = {
        shipState: createShipStateFromEntity(entity),
        lastSimulatedGameSeconds: snapshotGameSeconds,
        lastCommandKey: null,
    }
    statesByEntityId.set(entity.entityId, state)
    return state
}

export function syncEstimatedShips(entities: EntitySnapshot[], snapshotGameSeconds: number): void {
    const nextIds = new Set<number>()

    for (const entity of entities) {
        if (entity.entityType !== 'SHIP') continue
        nextIds.add(entity.entityId)

        const details = (entity.details ?? {}) as ShipDetails
        const command = details.movementCommand
        const existing = statesByEntityId.get(entity.entityId)
        const state = getOrCreateEstimatorState(entity, snapshotGameSeconds)

        if (hasMoveSeed(command)) {
            const commandKey = buildCommandKey(command)
            const isNewCommandSeed = !existing || existing.lastCommandKey !== commandKey
            if (isNewCommandSeed) {
                advanceEstimatorState(state, snapshotGameSeconds)
            }
            continue
        }

        state.shipState.maxSpeed = clampNumber(details.maxSpeed, state.shipState.maxSpeed)
        state.shipState.baseAcceleration = clampNumber(details.baseAcceleration, state.shipState.baseAcceleration)
        state.shipState.bowAccelerationBonus = clampNumber(
            details.bowAccelerationBonus,
            state.shipState.bowAccelerationBonus,
        )
        state.shipState.turnRate = clampNumber(details.turnRate, state.shipState.turnRate)
        state.shipState.lateralSpeedPenalty = clampNumber(
            details.lateralSpeedPenalty,
            state.shipState.lateralSpeedPenalty,
        )
        state.shipState.reverseSpeedPenalty = clampNumber(
            details.reverseSpeedPenalty,
            state.shipState.reverseSpeedPenalty,
        )
        state.shipState.isMoving = details.isMoving === true
        state.shipState.movementTarget = cloneVec2(details.movementTarget)
        state.shipState.velocity = cloneVec2(details.velocity)
        state.shipState.currentHeadingDeg = clampNumber(
            details.headingDeg,
            getHeadingFromVelocity(state.shipState.velocity, state.shipState.currentHeadingDeg),
        )
        state.shipState.targetHeadingDeg = state.shipState.movementTarget
            ? getHeadingToTarget(state.shipState.position, state.shipState.movementTarget)
            : state.shipState.currentHeadingDeg
        state.lastCommandKey = null

        advanceEstimatorState(state, snapshotGameSeconds)
    }

    for (const entityId of Array.from(statesByEntityId.keys())) {
        if (!nextIds.has(entityId)) {
            statesByEntityId.delete(entityId)
        }
    }
}

export function advanceEstimatedShips(
    entities: Iterable<EntitySnapshot>,
    currentGameSeconds = defaultGameTimeManager.getCurrentGameSeconds(),
): void {
    for (const entity of entities) {
        if (entity.entityType !== 'SHIP') continue
        const state = statesByEntityId.get(entity.entityId)
        if (!state) continue
        advanceEstimatorState(state, currentGameSeconds)
    }
}

export function advanceEstimatedShipsByDelta(
    entities: Iterable<EntitySnapshot>,
    deltaGameSeconds: number,
    currentGameSeconds = defaultGameTimeManager.getCurrentGameSeconds(),
): void {
    if (!Number.isFinite(deltaGameSeconds) || deltaGameSeconds <= 0) {
        return
    }

    for (const entity of entities) {
        if (entity.entityType !== 'SHIP') continue
        const state = getOrCreateEstimatorState(entity, currentGameSeconds - deltaGameSeconds)
        advanceEstimatorState(state, state.lastSimulatedGameSeconds + deltaGameSeconds)
    }
}

export function getEstimatedShipPose(
    entity: EntitySnapshot | null | undefined,
    currentGameSeconds = defaultGameTimeManager.getCurrentGameSeconds(),
): EstimatedShipPose | null {
    if (!entity || entity.entityType !== 'SHIP') return null

    const state = getOrCreateEstimatorState(entity, currentGameSeconds)
    if (!state) {
        const details = (entity.details ?? {}) as ShipDetails
        return entity.posWorldGU
            ? {
                position: { x: entity.posWorldGU.x, y: entity.posWorldGU.y },
                velocity: cloneVec2(details.velocity),
                headingDeg: clampNumber(details.headingDeg, 0),
                isMoving: details.isMoving === true,
                movementTarget: cloneVec2(details.movementTarget),
                usesCommandSeed: false,
            }
            : null
    }

    advanceEstimatorState(state, currentGameSeconds)

    return {
        position: { x: state.shipState.position.x, y: state.shipState.position.y },
        velocity: state.shipState.velocity ? { x: state.shipState.velocity.x, y: state.shipState.velocity.y } : null,
        headingDeg: state.shipState.currentHeadingDeg,
        isMoving: state.shipState.isMoving,
        movementTarget: state.shipState.movementTarget
            ? { x: state.shipState.movementTarget.x, y: state.shipState.movementTarget.y }
            : null,
        usesCommandSeed: state.lastCommandKey !== null,
    }
}

export function clearEstimatedShips(): void {
    statesByEntityId.clear()
}

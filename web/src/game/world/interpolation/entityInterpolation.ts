/**
 * @file entityInterpolation.ts
 *
 * @description
 * 实体插值与短时外推纯函数喵。
 *
 * 这些函数只读取快照帧和权威实体缓存喵。
 * 不会修改 `LocalVisibleWorld`（本地可见世界）里的任何状态喵。
 */

import type { EntitySnapshot, ShipDetails } from '../../../net/snapshotWs'
import type { EntityDisplayPosition, HighFreqSnapshotFrame } from '../localVisibleWorldTypes'
import type { SnapshotInterpolationWindow } from './snapshotInterpolationBuffer'

type EntityMotionState = {
  position: { x: number; y: number }
  velocity: { x: number; y: number } | null
  headingDeg: number
  isMoving: boolean
  movementTarget: { x: number; y: number } | null
}

/**
 * 从权威实体快照构造显示姿态喵。
 */
export function buildAuthoritativeDisplayPosition(
  entity: EntitySnapshot,
  source: EntityDisplayPosition['source'] = 'authoritative',
): EntityDisplayPosition {
  const motionState = extractEntityMotionState(entity)
  return buildDisplayPosition(motionState, source)
}

/**
 * 根据当前插值窗口采样实体姿态喵。
 */
export function sampleInterpolatedEntityDisplayPosition(params: {
  entityId: number
  fallbackEntity: EntitySnapshot | null
  window: SnapshotInterpolationWindow
  teleportThresholdGU: number
}): EntityDisplayPosition | null {
  const fallbackPose = params.fallbackEntity
    ? buildAuthoritativeDisplayPosition(params.fallbackEntity, 'fallback')
    : null

  const previousEntity = params.window.previousFrame
    ? getEntityFromFrame(params.window.previousFrame, params.entityId)
    : null
  const nextEntity = params.window.nextFrame
    ? getEntityFromFrame(params.window.nextFrame, params.entityId)
    : null

  if (params.window.didResetWindow) {
    return nextEntity
      ? buildAuthoritativeDisplayPosition(nextEntity, 'snapped')
      : previousEntity
        ? buildAuthoritativeDisplayPosition(previousEntity, 'snapped')
        : fallbackPose
  }

  if (params.window.mode === 'interpolate' && previousEntity && nextEntity) {
    return interpolateEntityStates(
      previousEntity,
      nextEntity,
      params.window.previousFrame,
      params.window.nextFrame,
      params.window.targetGameSeconds,
      params.teleportThresholdGU,
    )
  }

  if (params.window.mode === 'extrapolate' && previousEntity && params.window.previousFrame) {
    return extrapolateEntityState(
      previousEntity,
      params.window.previousFrame,
      params.window.targetGameSeconds,
    )
  }

  if (nextEntity) {
    return buildAuthoritativeDisplayPosition(nextEntity, 'authoritative')
  }
  if (previousEntity) {
    return buildAuthoritativeDisplayPosition(previousEntity, 'authoritative')
  }
  return fallbackPose
}

/**
 * 从某个高频帧里取出指定实体喵。
 */
function getEntityFromFrame(
  frame: HighFreqSnapshotFrame,
  entityId: number,
): EntitySnapshot | null {
  return frame.entitiesById.get(entityId) ?? null
}

/**
 * 对两个权威状态做位置和朝向插值喵。
 */
function interpolateEntityStates(
  previousEntity: EntitySnapshot,
  nextEntity: EntitySnapshot,
  previousFrame: HighFreqSnapshotFrame,
  nextFrame: HighFreqSnapshotFrame,
  targetGameSeconds: number,
  teleportThresholdGU: number,
): EntityDisplayPosition {
  const previousState = extractEntityMotionState(previousEntity)
  const nextState = extractEntityMotionState(nextEntity)
  const distance = getDistance(previousState.position, nextState.position)

  if (distance > teleportThresholdGU) {
    return buildDisplayPosition(nextState, 'snapped')
  }

  const totalDeltaSeconds = nextFrame.totalGameSecondsExact - previousFrame.totalGameSecondsExact
  if (!Number.isFinite(totalDeltaSeconds) || totalDeltaSeconds <= 0) {
    return buildDisplayPosition(nextState, 'snapped')
  }

  const t = clamp01(
    (targetGameSeconds - previousFrame.totalGameSecondsExact) / totalDeltaSeconds,
  )

  return buildDisplayPosition(
    {
      position: {
        x: lerp(previousState.position.x, nextState.position.x, t),
        y: lerp(previousState.position.y, nextState.position.y, t),
      },
      velocity: lerpVec2(previousState.velocity, nextState.velocity, t),
      headingDeg: lerpAngleDeg(previousState.headingDeg, nextState.headingDeg, t),
      isMoving: t < 0.5 ? previousState.isMoving : nextState.isMoving,
      movementTarget:
        t < 0.5
          ? cloneVec2(previousState.movementTarget ?? nextState.movementTarget)
          : cloneVec2(nextState.movementTarget ?? previousState.movementTarget),
    },
    'interpolated',
  )
}

/**
 * 在没有后一帧时做短时外推喵。
 */
function extrapolateEntityState(
  entity: EntitySnapshot,
  frame: HighFreqSnapshotFrame,
  targetGameSeconds: number,
): EntityDisplayPosition {
  const baseState = extractEntityMotionState(entity)
  const deltaSeconds = Math.max(0, targetGameSeconds - frame.totalGameSecondsExact)

  if (!baseState.velocity || !baseState.isMoving || deltaSeconds <= 0) {
    return buildDisplayPosition(baseState, 'authoritative')
  }

  const nextPosition = {
    x: baseState.position.x + baseState.velocity.x * deltaSeconds,
    y: baseState.position.y + baseState.velocity.y * deltaSeconds,
  }

  return buildDisplayPosition(
    {
      position: nextPosition,
      velocity: cloneVec2(baseState.velocity),
      headingDeg: getHeadingFromVelocity(baseState.velocity, baseState.headingDeg),
      isMoving: baseState.isMoving,
      movementTarget: cloneVec2(baseState.movementTarget),
    },
    'extrapolated',
  )
}

/**
 * 从实体快照中抽取插值层关心的最小运动状态喵。
 */
function extractEntityMotionState(entity: EntitySnapshot): EntityMotionState {
  const details = ((entity.details ?? {}) as ShipDetails & Record<string, unknown>) ?? {}
  const velocity = cloneVec2(details.velocity as { x: number; y: number } | null | undefined)
  const headingDeg = clampNumber(
    details.headingDeg,
    getHeadingFromVelocity(velocity, 0),
  )

  return {
    position: { x: entity.posWorldGU.x, y: entity.posWorldGU.y },
    velocity,
    headingDeg,
    isMoving: details.isMoving === true,
    movementTarget: cloneVec2(
      details.movementTarget as { x: number; y: number } | null | undefined,
    ),
  }
}

/**
 * 构造最终显示姿态喵。
 */
function buildDisplayPosition(
  motionState: EntityMotionState,
  source: EntityDisplayPosition['source'],
): EntityDisplayPosition {
  return {
    position: { x: motionState.position.x, y: motionState.position.y },
    velocity: cloneVec2(motionState.velocity),
    headingDeg: motionState.headingDeg,
    isMoving: motionState.isMoving,
    movementTarget: cloneVec2(motionState.movementTarget),
    usesCommandSeed: false,
    source,
  }
}

/**
 * 角度线性插值喵。
 */
function lerpAngleDeg(fromDeg: number, toDeg: number, t: number): number {
  const delta = ((((toDeg - fromDeg) % 360) + 540) % 360) - 180
  return normalizeAngleDeg(fromDeg + delta * t)
}

/**
 * 速度向量插值喵。
 */
function lerpVec2(
  from: { x: number; y: number } | null,
  to: { x: number; y: number } | null,
  t: number,
): { x: number; y: number } | null {
  if (!from && !to) {
    return null
  }
  if (!from) {
    return cloneVec2(to)
  }
  if (!to) {
    return cloneVec2(from)
  }

  return {
    x: lerp(from.x, to.x, t),
    y: lerp(from.y, to.y, t),
  }
}

/**
 * 计算两点距离喵。
 */
function getDistance(
  from: { x: number; y: number },
  to: { x: number; y: number },
): number {
  const dx = to.x - from.x
  const dy = to.y - from.y
  return Math.sqrt(dx * dx + dy * dy)
}

/**
 * 从速度向量推导朝向喵。
 */
function getHeadingFromVelocity(
  velocity: { x: number; y: number } | null,
  fallback: number,
): number {
  if (!velocity) {
    return normalizeAngleDeg(fallback)
  }

  const speedSq = velocity.x * velocity.x + velocity.y * velocity.y
  if (speedSq <= 0.0001) {
    return normalizeAngleDeg(fallback)
  }

  return normalizeAngleDeg((Math.atan2(velocity.y, velocity.x) * 180) / Math.PI)
}

/**
 * 归一化角度到 `[0, 360)` 区间喵。
 */
function normalizeAngleDeg(value: number): number {
  let angle = value % 360
  if (angle < 0) {
    angle += 360
  }
  return angle
}

/**
 * 数值线性插值喵。
 */
function lerp(from: number, to: number, t: number): number {
  return from + (to - from) * t
}

/**
 * 把数值夹到 `[0, 1]` 区间喵。
 */
function clamp01(value: number): number {
  if (value <= 0) {
    return 0
  }
  if (value >= 1) {
    return 1
  }
  return value
}

/**
 * 复制二维向量喵。
 */
function cloneVec2(
  value: { x: number; y: number } | null | undefined,
): { x: number; y: number } | null {
  if (!value) {
    return null
  }
  return {
    x: Number(value.x),
    y: Number(value.y),
  }
}

/**
 * 安全地读取数值喵。
 */
function clampNumber(value: unknown, fallback: number): number {
  return typeof value === 'number' && Number.isFinite(value) ? value : fallback
}

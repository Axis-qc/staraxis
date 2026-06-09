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

  const currentEntity = getEntityFromFrame(params.window.currentSnapshot, params.entityId)
  const nextEntity = params.window.nextSnapshotBuffer
    ? getEntityFromFrame(params.window.nextSnapshotBuffer, params.entityId)
    : null

  if (params.window.didResetWindow) {
    return currentEntity
      ? buildAuthoritativeDisplayPosition(currentEntity, 'snapped')
      : fallbackPose
  }

  if (params.window.mode === 'interpolate' && currentEntity && nextEntity) {
    return interpolateEntityStates(
      currentEntity,
      params.window.currentSnapshot,
      nextEntity,
      params.window.nextSnapshotBuffer,
      params.window.renderAlpha,
      params.teleportThresholdGU,
    )
  }

  if (params.window.mode === 'extrapolate' && currentEntity) {
    return extrapolateEntityState(
      currentEntity,
      params.window.currentSnapshot,
      params.window.renderGameSeconds,
    )
  }

  if (currentEntity) {
    return buildAuthoritativeDisplayPosition(currentEntity, 'authoritative')
  }
  if (nextEntity) {
    return buildAuthoritativeDisplayPosition(nextEntity, 'authoritative')
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
  currentEntity: EntitySnapshot,
  currentFrame: HighFreqSnapshotFrame,
  nextEntity: EntitySnapshot,
  nextFrame: HighFreqSnapshotFrame,
  renderAlpha: number,
  teleportThresholdGU: number,
): EntityDisplayPosition {
  const currentState = extractEntityMotionState(currentEntity)
  const nextState = extractEntityMotionState(nextEntity)
  const distance = getDistance(currentState.position, nextState.position)

  if (distance > teleportThresholdGU) {
    return buildDisplayPosition(nextState, 'snapped')
  }

  const t = clamp01(renderAlpha)
  const frameDeltaGameSeconds = Math.max(
    0,
    nextFrame.totalGameSecondsExact - currentFrame.totalGameSecondsExact,
  )

  return buildDisplayPosition(
    {
      // 使用快照里的速度向量做受限 Hermite 插值喵，
      // 这样能让逻辑段边界的速度变化更连续喵。
      // 同时继续做单调约束喵，避免之前样条过冲造成的回弹喵。
      position: interpolatePositionWithVelocity(
        currentState,
        nextState,
        frameDeltaGameSeconds,
        t,
      ),
      velocity: lerpVec2(currentState.velocity, nextState.velocity, t),
      headingDeg: lerpAngleDeg(currentState.headingDeg, nextState.headingDeg, t),
      isMoving: t < 0.5 ? currentState.isMoving : nextState.isMoving,
      movementTarget:
        t < 0.5
          ? cloneVec2(currentState.movementTarget ?? nextState.movementTarget)
          : cloneVec2(nextState.movementTarget ?? currentState.movementTarget),
    },
    'interpolated',
  )
}

/**
 * 使用前后快照速度做受限位置插值喵。
 *
 * 这里仍然要求同一逻辑段内的每个坐标分量保持单调喵，
 * 不能因为切线太大导致渲染帧在中间“回头”喵。
 */
function interpolatePositionWithVelocity(
  currentState: EntityMotionState,
  nextState: EntityMotionState,
  frameDeltaGameSeconds: number,
  t: number,
): { x: number; y: number } {
  if (
    frameDeltaGameSeconds <= 0.000001
    || !currentState.velocity
    || !nextState.velocity
  ) {
    return {
      x: lerp(currentState.position.x, nextState.position.x, t),
      y: lerp(currentState.position.y, nextState.position.y, t),
    }
  }

  return {
    x: interpolateMonotoneHermiteAxis(
      currentState.position.x,
      nextState.position.x,
      currentState.velocity.x * frameDeltaGameSeconds,
      nextState.velocity.x * frameDeltaGameSeconds,
      t,
    ),
    y: interpolateMonotoneHermiteAxis(
      currentState.position.y,
      nextState.position.y,
      currentState.velocity.y * frameDeltaGameSeconds,
      nextState.velocity.y * frameDeltaGameSeconds,
      t,
    ),
  }
}

/**
 * 单轴受限 Hermite 插值喵。
 *
 * 参考单调三次插值的切线限制喵：
 * - 端点切线若与段方向相反，则直接压到 0 喵。
 * - 两端切线总量若过大，则整体缩放到 `3 * 段位移` 以内喵。
 * 这样可以保住速度连续感喵，同时尽量避免过冲回弹喵。
 */
function interpolateMonotoneHermiteAxis(
  from: number,
  to: number,
  tangentFrom: number,
  tangentTo: number,
  t: number,
): number {
  const delta = to - from
  if (Math.abs(delta) <= 0.000001) {
    return lerp(from, to, t)
  }

  let m0 = sanitizeTangentForDelta(tangentFrom, delta)
  let m1 = sanitizeTangentForDelta(tangentTo, delta)

  const maxCombinedTangent = Math.abs(delta) * 3
  const combinedMagnitude = Math.abs(m0) + Math.abs(m1)
  if (combinedMagnitude > maxCombinedTangent && combinedMagnitude > 0.000001) {
    const scale = maxCombinedTangent / combinedMagnitude
    m0 *= scale
    m1 *= scale
  }

  return hermite(from, to, m0, m1, t)
}

/**
 * 将与段方向相反的切线压到 0 喵。
 */
function sanitizeTangentForDelta(tangent: number, delta: number): number {
  if (!Number.isFinite(tangent)) {
    return 0
  }
  if (delta > 0 && tangent < 0) {
    return 0
  }
  if (delta < 0 && tangent > 0) {
    return 0
  }
  return tangent
}

/**
 * 三次 Hermite 基函数喵。
 */
function hermite(
  from: number,
  to: number,
  tangentFrom: number,
  tangentTo: number,
  t: number,
): number {
  const t2 = t * t
  const t3 = t2 * t
  const h00 = 2 * t3 - 3 * t2 + 1
  const h10 = t3 - 2 * t2 + t
  const h01 = -2 * t3 + 3 * t2
  const h11 = t3 - t2
  return h00 * from + h10 * tangentFrom + h01 * to + h11 * tangentTo
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
      // 外推阶段只能继续推进位置喵，
      // 朝向必须保持后端权威快照里的旋转结果喵，
      // 不能在前端擅自把舰船朝向掰成速度方向喵。
      headingDeg: baseState.headingDeg,
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

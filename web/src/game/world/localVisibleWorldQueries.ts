/**
 * @file localVisibleWorldQueries.ts
 *
 * @description
 * 前端本地可见世界副本的查询接口喵。
 *
 * 提供统一的实体状态查询入口，替换原有的渲染层查询接口喵。
 * 所有UI、选择、聚焦、面板等模块都应通过此模块查询实体状态喵。
 */

import { getLocalVisibleWorld } from './localVisibleWorld'
import type { EntityDisplayPosition } from './localVisibleWorldTypes'
import type { DailySettlementState, EntitySnapshot, SectorCenter } from '../../net/snapshotWs'

/**
 * 获取实体显示位置喵
 *
 * 统一查询入口，用于所有需要实体位置的场景喵：
 * - 渲染器渲染
 * - UI面板显示
 * - 选择环绘制
 * - 镜头聚焦
 * - 框选命中检测
 *
 * @param entityId 实体ID喵
 * @returns 实体显示位置信息，如果实体不存在则返回null喵
 */
export function getEntityDisplayPosition(entityId: number): EntityDisplayPosition | null {
  const world = getLocalVisibleWorld()
  return world.getEntityDisplayPosition(entityId)
}

/**
 * 获取实体插值后显示位置喵。
 *
 * 该接口面向渲染层和需要平滑显示的位置读取喵。
 */
export function getInterpolatedEntityDisplayPosition(
  entityId: number,
): EntityDisplayPosition | null {
  const world = getLocalVisibleWorld()
  return world.getInterpolatedEntityDisplayPosition(entityId)
}

/**
 * 获取实体快照喵
 *
 * 返回实体的权威快照状态喵。
 *
 * @param entityId 实体ID喵
 * @returns 实体快照，如果不存在则返回null喵
 */
export function getEntitySnapshot(entityId: number): EntitySnapshot | null {
  const world = getLocalVisibleWorld()
  return world.getEntitySnapshot(entityId)
}

/**
 * 批量获取实体显示位置喵
 *
 * 优化批量查询性能喵。
 *
 * @param entityIds 实体ID数组喵
 * @returns 实体ID到显示位置的映射喵
 */
export function getEntityDisplayPositions(entityIds: number[]): Map<number, EntityDisplayPosition> {
  const world = getLocalVisibleWorld()
  const result = new Map<number, EntityDisplayPosition>()

  for (const entityId of entityIds) {
    const position = world.getEntityDisplayPosition(entityId)
    if (position) {
      result.set(entityId, position)
    }
  }

  return result
}

/**
 * 批量获取实体插值后显示位置喵。
 */
export function getInterpolatedEntityDisplayPositions(
  entityIds: number[],
): Map<number, EntityDisplayPosition> {
  const world = getLocalVisibleWorld()
  const result = new Map<number, EntityDisplayPosition>()

  for (const entityId of entityIds) {
    const position = world.getInterpolatedEntityDisplayPosition(entityId)
    if (position) {
      result.set(entityId, position)
    }
  }

  return result
}

/**
 * 获取所有可见实体快照列表喵
 *
 * 用于需要遍历所有实体的场景喵。
 *
 * @returns 所有可见实体快照数组喵
 */
export function getAllEntitySnapshots(): EntitySnapshot[] {
  const world = getLocalVisibleWorld()
  return world.getAllEntitySnapshots()
}

/**
 * 获取所有可见实体ID列表喵
 *
 * 轻量级查询，只返回ID喵。
 *
 * @returns 所有可见实体ID数组喵
 */
export function getAllVisibleEntityIds(): number[] {
  const world = getLocalVisibleWorld()
  const snapshots = world.getAllEntitySnapshots()
  return snapshots.map(s => s.entityId)
}

/**
 * 按实体类型过滤实体喵
 *
 * @param entityType 实体类型喵
 * @returns 符合条件的实体快照数组喵
 */
export function getEntitiesByType(entityType: string): EntitySnapshot[] {
  const world = getLocalVisibleWorld()
  const snapshots = world.getAllEntitySnapshots()
  return snapshots.filter(s => s.entityType === entityType)
}

/**
 * 获取舰船实体列表喵
 *
 * 快捷方法，用于常见场景喵。
 *
 * @returns 所有舰船实体快照喵
 */
export function getAllShipSnapshots(): EntitySnapshot[] {
  return getEntitiesByType('SHIP')
}

/**
 * 获取行星实体列表喵
 *
 * 快捷方法喵。
 *
 * @returns 所有行星实体快照喵
 */
export function getAllPlanetSnapshots(): EntitySnapshot[] {
  return getEntitiesByType('PLANET')
}

/**
 * 获取恒星实体列表喵
 *
 * 快捷方法喵。
 *
 * @returns 所有恒星实体快照喵
 */
export function getAllStarSnapshots(): EntitySnapshot[] {
  return getEntitiesByType('STAR')
}

/**
 * 获取空间站实体列表喵
 *
 * 快捷方法喵。
 *
 * @returns 所有空间站实体快照喵
 */
export function getAllStationSnapshots(): EntitySnapshot[] {
  return getEntitiesByType('STATION')
}

/**
 * 按所有者过滤实体喵
 *
 * @param nationId 国家ID喵
 * @returns 属于指定国家的实体快照数组喵
 */
export function getEntitiesByOwner(nationId: string): EntitySnapshot[] {
  const world = getLocalVisibleWorld()
  const snapshots = world.getAllEntitySnapshots()
  return snapshots.filter(s => s.ownerNationId === nationId)
}

/**
 * 获取玩家拥有的实体列表喵
 *
 * @param playerNationId 玩家国家ID喵
 * @returns 玩家拥有的实体快照数组喵
 */
export function getPlayerOwnedEntities(playerNationId: string): EntitySnapshot[] {
  return getEntitiesByOwner(playerNationId)
}

/**
 * 检查实体是否存在喵
 *
 * @param entityId 实体ID喵
 * @returns 实体是否存在喵
 */
export function hasEntity(entityId: number): boolean {
  const world = getLocalVisibleWorld()
  return world.getEntitySnapshot(entityId) !== null
}

/**
 * 检查实体是否对玩家可见喵
 *
 * 考虑情报可见性状态喵。
 *
 * @param entityId 实体ID喵
 * @returns 实体是否对玩家可见喵
 */
export function isEntityVisibleToPlayer(entityId: number): boolean {
  const world = getLocalVisibleWorld()
  return world.isEntityVisibleToPlayer(entityId)
}

/**
 * 获取世界状态统计喵
 *
 * 用于调试和监控喵。
 *
 * @returns 世界状态统计信息喵
 */
export function getWorldStats() {
  const world = getLocalVisibleWorld()
  return {
    visibleEntities: world.getVisibleEntityCount(),
    highFreqFrames: world.getHighFreqFrameCount(),
    lastAppliedVersion: world.getLastAppliedVersion(),
    pendingCommands: world.getPendingCommandCount(),
    lastAppliedTick: world.getLastAppliedTick(),
    focusedEntities: world.getAllFocusedEntityIds().length
  }
}

/**
 * 获取最新低频状态喵
 *
 * 供面板和低频 UI 查询喵。
 */
export function getLatestLowFreqState() {
  const world = getLocalVisibleWorld()
  return world.getLatestLowFreqState()
}

/**
 * 获取最新结算状态喵
 */
export function getLatestDailySettlementState(): DailySettlementState | null {
  return getLatestLowFreqState()?.dailySettlementState ?? null
}

/**
 * 获取最新星区中心喵
 */
export function getLatestSectorCenters(): SectorCenter[] {
  return getLatestLowFreqState()?.sectorCenters ?? []
}

/**
 * 获取最新星区归属喵
 */
export function getLatestSectorOwnerNationIdByCoord(): Record<string, string> {
  return getLatestLowFreqState()?.sectorOwnerNationIdByCoord ?? {}
}

/**
 * 获取实体世界坐标喵
 *
 * 权威缓存位置查询的便捷包装喵。
 *
 * @param entityId 实体ID喵
 * @returns 实体世界坐标（GU），如果不存在则返回null喵
 */
export function getEntityWorldPosGU(entityId: number): { x: number; y: number } | null {
  const position = getEntityDisplayPosition(entityId)
  return position ? position.position : null
}

/**
 * 获取实体插值后世界坐标喵。
 */
export function getInterpolatedEntityWorldPosGU(
  entityId: number,
): { x: number; y: number } | null {
  const position = getInterpolatedEntityDisplayPosition(entityId)
  return position ? position.position : null
}

/**
 * 获取实体航向角度喵
 *
 * @param entityId 实体ID喵
 * @returns 航向角度（度），如果不存在则返回0喵
 */
export function getEntityHeadingDeg(entityId: number): number {
  const position = getEntityDisplayPosition(entityId)
  return position ? position.headingDeg : 0
}

/**
 * 获取实体插值后航向角度喵。
 */
export function getInterpolatedEntityHeadingDeg(entityId: number): number {
  const position = getInterpolatedEntityDisplayPosition(entityId)
  return position ? position.headingDeg : 0
}

/**
 * 检查实体是否正在移动喵
 *
 * @param entityId 实体ID喵
 * @returns 是否正在移动喵
 */
export function isEntityMoving(entityId: number): boolean {
  const position = getEntityDisplayPosition(entityId)
  return position ? position.isMoving : false
}

/**
 * 检查实体插值显示是否处于移动中喵。
 */
export function isInterpolatedEntityMoving(entityId: number): boolean {
  const position = getInterpolatedEntityDisplayPosition(entityId)
  return position ? position.isMoving : false
}

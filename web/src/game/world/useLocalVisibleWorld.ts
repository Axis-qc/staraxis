/**
 * @file useLocalVisibleWorld.ts
 *
 * @description
 * 前端本地可见世界副本的Vue组合式函数喵。
 *
 * 提供响应式的世界状态访问，用于Vue组件中喵。
 * 将世界状态的变化映射到组件响应式系统喵。
 */

import { ref, computed, onUnmounted, type Ref, type ComputedRef } from 'vue'
import { getLocalVisibleWorld } from './localVisibleWorld'
import * as queries from './localVisibleWorldQueries'

/**
 * 本地可见世界组合式函数返回值类型喵
 */
export type UseLocalVisibleWorldReturn = {
  // 世界状态喵
  worldStats: ComputedRef<ReturnType<typeof queries.getWorldStats>>

  // 实体查询喵
  getAllEntitySnapshots: typeof queries.getAllEntitySnapshots
  getEntitySnapshot: typeof queries.getEntitySnapshot
  getEntityDisplayPosition: typeof queries.getEntityDisplayPosition
  getEntityWorldPosGU: typeof queries.getEntityWorldPosGU

  // 快捷查询喵
  getAllShipSnapshots: typeof queries.getAllShipSnapshots
  getAllPlanetSnapshots: typeof queries.getAllPlanetSnapshots
  getAllStarSnapshots: typeof queries.getAllStarSnapshots
  getAllStationSnapshots: typeof queries.getAllStationSnapshots

  // 响应式状态喵
  entityCount: Ref<number>
  shipCount: Ref<number>
  planetCount: Ref<number>
  starCount: Ref<number>
  stationCount: Ref<number>

  // 工具函数喵
  resetWorld: () => void
  hasEntity: typeof queries.hasEntity
  isEntityVisibleToPlayer: typeof queries.isEntityVisibleToPlayer
}

/**
 * 使用本地可见世界副本喵
 *
 * 在Vue组件中提供响应式的世界状态访问喵。
 * 组件卸载时会自动清理资源喵。
 *
 * @param options 配置选项喵
 * @returns 世界查询和状态接口喵
 */
export function useLocalVisibleWorld(): UseLocalVisibleWorldReturn {
  const world = getLocalVisibleWorld()

  // 响应式状态引用喵
  const entityCount = ref(world.getVisibleEntityCount())
  const shipCount = ref(queries.getAllShipSnapshots().length)
  const planetCount = ref(queries.getAllPlanetSnapshots().length)
  const starCount = ref(queries.getAllStarSnapshots().length)
  const stationCount = ref(queries.getAllStationSnapshots().length)

  // 更新统计的函数喵
  const updateStats = () => {
    entityCount.value = world.getVisibleEntityCount()
    shipCount.value = queries.getAllShipSnapshots().length
    planetCount.value = queries.getAllPlanetSnapshots().length
    starCount.value = queries.getAllStarSnapshots().length
    stationCount.value = queries.getAllStationSnapshots().length
  }

  // TODO: 监听世界状态变化的机制
  // 目前先提供一个手动更新函数，后续可以改为事件驱动喵
  const forceUpdate = () => {
    updateStats()
  }

  // 组件卸载时重置世界（可选，根据实际需求调整）喵
  onUnmounted(() => {
    // 注意：如果多个组件使用同一个世界实例，不应该在单个组件卸载时重置喵
    // 这里只是示例，实际使用时可能需要更精细的生命周期管理喵
    // resetLocalVisibleWorld()
  })

  // 计算属性：世界统计喵
  const worldStats = computed(() => queries.getWorldStats())

  return {
    // 世界状态喵
    worldStats,

    // 实体查询（直接暴露查询函数）喵
    getAllEntitySnapshots: queries.getAllEntitySnapshots,
    getEntitySnapshot: queries.getEntitySnapshot,
    getEntityDisplayPosition: queries.getEntityDisplayPosition,
    getEntityWorldPosGU: queries.getEntityWorldPosGU,

    // 快捷查询喵
    getAllShipSnapshots: queries.getAllShipSnapshots,
    getAllPlanetSnapshots: queries.getAllPlanetSnapshots,
    getAllStarSnapshots: queries.getAllStarSnapshots,
    getAllStationSnapshots: queries.getAllStationSnapshots,

    // 响应式状态喵
    entityCount,
    shipCount,
    planetCount,
    starCount,
    stationCount,

    // 工具函数喵
    resetWorld: () => {
      world.reset()
      updateStats()
    },
    hasEntity: queries.hasEntity,
    isEntityVisibleToPlayer: queries.isEntityVisibleToPlayer,

  }
}

/**
 * 创建世界状态响应式包装器喵
 *
 * 将世界状态变化映射到Vue响应式系统喵。
 * 这是一个高级API，用于需要深度响应式集成的场景喵。
 */
export function createWorldReactiveWrapper() {
  // 响应式实体列表喵
  const entities = ref(queries.getAllEntitySnapshots())

  // 响应式实体ID列表喵
  const entityIds = ref(queries.getAllVisibleEntityIds())

  // 响应式舰船列表喵
  const ships = ref(queries.getAllShipSnapshots())

  // 更新函数喵
  const updateAll = () => {
    entities.value = queries.getAllEntitySnapshots()
    entityIds.value = queries.getAllVisibleEntityIds()
    ships.value = queries.getAllShipSnapshots()
  }

  return {
    entities,
    entityIds,
    ships,
    updateAll
  }
}

/**
 * 快捷函数：获取实体世界坐标喵
 *
 * 用于需要快速获取坐标的场景喵。
 *
 * @param entityId 实体ID喵
 * @returns 实体世界坐标喵
 */
export function useEntityPosition(entityId: number) {
  const position = ref(queries.getEntityWorldPosGU(entityId))
  const heading = ref(queries.getEntityHeadingDeg(entityId))
  const isMoving = ref(queries.isEntityMoving(entityId))

  // TODO: 监听实体位置变化的机制

  return {
    position,
    heading,
    isMoving
  }
}
/**
 * @file localVisibleWorldTypes.ts
 *
 * @description
 * 前端本地可见世界副本的核心类型定义喵。
 *
 * 遵循“前端本地模拟与后端权威校验重构计划”中的类型设计喵：
 * - `visibleEntitiesById`（可见实体权威快照缓存）
 * - `predictedShipsById`（前端本地连续推进状态）
 * - `pendingCommandsByEntityId`（待确认指令状态）
 * - `lastSnapshotMeta`（最新快照元信息）
 * - `intelVisibilityState`（情报可见性状态）
 *
 * 类型分层喵：
 * 1. 权威快照态：从后端快照获得的权威数据
 * 2. 本地预测态：前端基于指令和时间轴连续推进的状态
 * 3. 待确认指令态：已发送但未获得后端确认的指令
 */

import type { EntitySnapshot, ShipDetails } from '../../net/snapshotWs'
import type { ShipState } from '../systems/ShipMovementSystemFrontend'

// ==================== 指令状态机类型 ====================

/**
 * 待确认指令状态喵
 *
 * 表示前端已发送但尚未获得后端最终确认的指令喵。
 * 指令生命周期：pending_send → predicting → confirmed/rejected → completed/correcting 喵
 */
export type PendingCommandStatus =
  | 'pending_send'   // 前端已发起，等待送达后端
  | 'predicting'     // 前端本地执行中
  | 'confirmed'      // 后端已接受
  | 'rejected'       // 后端拒绝
  | 'correcting'     // 进入纠偏阶段
  | 'completed'      // 完成并对账通过

/**
 * 待确认指令记录喵
 *
 * 包含指令元数据、状态和与权威数据的关联喵。
 */
export type PendingCommandRecord = {
  /** 实体ID喵 */
  entityId: number
  /** 前端指令唯一标识，用于后续对账喵 */
  clientCommandId: string
  /** 指令类型喵 */
  commandType: 'MOVE_TO' | 'STOP'
  /** 前端发出时间戳（毫秒）喵 */
  issuedAtClientMs: number
  /** 当前指令状态喵 */
  status: PendingCommandStatus
  /** 移动指令种子喵 */
  movementSeed: ShipDetails['movementCommand'] | null
  /** 最后确认的快照Tick喵 */
  lastAuthoritativeAckTick: number | null
  /** 拒绝原因喵 */
  rejectionReason: string | null
  /** 是否已经向后端发送过完成回报喵 */
  completionReportSentAtClientMs: number | null
}

// ==================== 预测状态类型 ====================

/**
 * 预测舰船状态喵
 *
 * 前端本地连续推进的舰船状态，包含时间戳用于增量推进喵。
 */
export type PredictedShipState = {
  /** 基础舰船状态喵 */
  shipState: ShipState
  /** 最后模拟的游戏时间秒数喵 */
  lastSimulatedGameSeconds: number
  /** 最后使用的指令键值喵 */
  lastCommandKey: string | null
  /** 最后更新时的权威快照Tick喵 */
  lastAuthoritativeSnapshotTick: number | null
  /** 是否正在纠偏中喵 */
  isCorrecting: boolean
  /** 纠偏目标位置喵 */
  correctionTargetPosition: { x: number; y: number } | null
  /** 纠偏开始时间（游戏秒数）喵 */
  correctionStartGameSeconds: number | null
  /** 纠偏插值持续时间（秒）喵 */
  correctionDurationSec: number | null
}

// ==================== 权威快照元信息 ====================

/**
 * 权威快照元信息喵
 *
 * 记录最新快照的关键元数据，用于排序和一致性检查喵。
 */
export type AuthoritativeSnapshotMeta = {
  /** 快照Tick喵 */
  simulationTick: number
  /** 游戏总秒数喵 */
  totalGameSeconds: number
  /** 精确游戏总秒数喵 */
  totalGameSecondsExact: number | null
  /** 收到时间戳（毫秒）喵 */
  receivedAtClientMs: number
}

// ==================== 可见性状态 ====================

/**
 * 情报可见性状态喵
 *
 * 记录玩家当前的情报可见性配置喵。
 */
export type IntelVisibilityState = {
  /** 当前玩家国家ID喵 */
  currentNationId: string | null
  /** 最后同步时间（毫秒）喵 */
  lastSyncAtMs: number
  /** 情报等级与可见实体ID的映射喵 */
  visiblePrivateEntitiesByLevel: Record<string, Set<number>>
}

// ==================== 核心世界类型 ====================

/**
 * 本地可见世界副本喵
 *
 * 前端维护的本地世界状态容器，包含三类状态喵：
 * 1. 权威快照态：`visibleEntitiesById`
 * 2. 本地预测态：`predictedShipsById`
 * 3. 待确认指令态：`pendingCommandsByEntityId`
 */
export type LocalVisibleWorld = {
  /** 可见实体权威快照缓存（实体ID → EntitySnapshot）喵 */
  visibleEntitiesById: Map<number, EntitySnapshot>
  /** 前端本地连续推进的舰船状态（实体ID → PredictedShipState）喵 */
  predictedShipsById: Map<number, PredictedShipState>
  /** 待确认指令状态（实体ID → PendingCommandRecord）喵 */
  pendingCommandsByEntityId: Map<number, PendingCommandRecord>
  /** 最新快照元信息喵 */
  lastSnapshotMeta: AuthoritativeSnapshotMeta | null
  /** 情报可见性状态喵 */
  intelVisibilityState: IntelVisibilityState
  /** 关注对象保留策略：选中实体、己方舰船、待确认指令对象、最近离开订阅范围的实体喵 */
  focusedEntityIds: Set<number>
  /** 最后应用快照的Tick，用于防止旧快照覆盖新状态喵 */
  lastAppliedSimulationTick: number | null
}

// ==================== 查询结果类型 ====================

/**
 * 实体显示位置查询结果喵
 *
 * 包含位置、速度和方向等信息，用于渲染和UI展示喵。
 */
export type EntityDisplayPosition = {
  /** 显示位置（GU坐标）喵 */
  position: { x: number; y: number }
  /** 速度向量喵 */
  velocity: { x: number; y: number } | null
  /** 航向角度（度）喵 */
  headingDeg: number
  /** 是否正在移动喵 */
  isMoving: boolean
  /** 移动目标位置喵 */
  movementTarget: { x: number; y: number } | null
  /** 是否使用指令种子喵 */
  usesCommandSeed: boolean
  /** 数据来源：'authoritative' | 'predicted' | 'fallback' 喵 */
  source: 'authoritative' | 'predicted' | 'fallback'
}

// ==================== 同步结果类型 ====================

/**
 * 世界同步结果喵
 *
 * 应用快照或指令后的同步结果，包含变更统计喵。
 */
export type WorldSyncResult = {
  /** 成功应用快照喵 */
  success: boolean
  /** 新增实体数量喵 */
  addedEntities: number
  /** 更新实体数量喵 */
  updatedEntities: number
  /** 移除实体数量喵 */
  removedEntities: number
  /** 应用快照的Tick喵 */
  appliedTick: number | null
  /** 是否触发了纠偏喵 */
  triggeredCorrection: boolean
  /** 纠偏实体数量喵 */
  correctedEntities: number
}

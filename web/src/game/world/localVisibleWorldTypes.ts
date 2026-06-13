/**
 * @file localVisibleWorldTypes.ts
 *
 * @description
 * 前端本地可见世界副本的核心类型定义喵。
 *
 * 遵循“纯快照缓存与插值渲染计划”中的缓存世界设计喵：
 * - `visibleEntitiesById`（当前最新权威实体缓存）
 * - `highFreqFrames`（高频帧缓存）
 * - `latestLowFreqState`（低频状态缓存）
 * - `pendingCommandsByEntityId`（命令 UI 状态缓存）
 * - `interestEntityIds`（兴趣集合与保活规则）
 *
 * 类型分层喵：
 * 1. 权威快照态：从后端快照获得的权威数据
 * 2. 低频静态态：面板/经济/星区中心等低频数据
 * 3. 待确认指令态：已发送但未获得后端最终结果的 UI 状态
 */

import type {
  DailySettlementState,
  EntitySnapshot,
  SectorCenter,
  ShipDetails,
  SnapshotHighFreqMessage,
  SnapshotLowFreqMessage,
} from '../../net/snapshotWs'

// ==================== 指令状态机类型 ====================

/**
 * 待确认指令状态喵
 *
 * 表示前端维护的命令 UI 状态喵。
 * `pending_send` 代表前端已创建命令记录，但尚未收到权威命令结果喵。
 * `confirmed/rejected/correcting/completed` 只能由 `command_result`（命令结果消息）推进喵。
 */
export type PendingCommandStatus =
  | 'pending_send'   // 前端已发起，等待命令结果消息
  | 'confirmed'      // 后端已接受
  | 'rejected'       // 后端拒绝
  | 'correcting'     // 进入纠偏阶段
  | 'completed'      // 完成并对账通过

/**
 * 指令传输状态喵
 *
 * 仅描述命令是否已通过 HTTP 送达后端接口喵，不表示权威命令结论喵。
 */
export type CommandTransportStatus =
  | 'pending'        // 前端已创建命令，尚未收到 HTTP 回执
  | 'submitted'      // HTTP 已确认收到请求
  | 'failed'         // HTTP 提交失败或接口内联拒绝

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
  /** 传输状态喵 */
  transportStatus: CommandTransportStatus
  /** 最近一次 HTTP 回执时间（毫秒）喵 */
  lastTransportAckAtClientMs: number | null
  /** HTTP 传输失败信息喵 */
  transportError: string | null
  /** 移动指令种子，用于立即显示目标点和路径标记喵 */
  movementSeed: ShipDetails['movementCommand'] | null
  /** 最后一次命令结果消息对应的权威 Tick 喵 */
  lastAuthoritativeAckTick: number | null
  /** 拒绝原因喵 */
  rejectionReason: string | null
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

// ==================== 高频/低频缓存类型 ====================

/**
 * 高频帧缓存喵
 *
 * 保存最近几帧权威高频快照，为后续插值层提供基础数据喵。
 */
export type HighFreqSnapshotFrame = {
  /** 快照 Tick 喵 */
  simulationTick: number
  /** 游戏总秒数喵 */
  totalGameSeconds: number
  /** 精确游戏总秒数喵 */
  totalGameSecondsExact: number
  /** 当前帧游戏秒增量喵 */
  deltaGameSeconds: number
  /** 当前帧 Tick 开销喵 */
  tickCostMs: number | null
  /** 同步模式喵 */
  syncMode: SnapshotHighFreqMessage['syncMode']
  /** 增量基线 Tick 喵 */
  baseTick: number | null
  /** 当前帧的实体缓存喵 */
  entitiesById: Map<number, EntitySnapshot>
  /** 当前帧的私有情报实体集合喵 */
  privateEntityIdsByLevel: Record<string, Set<number>>
  /** 收包时间喵 */
  receivedAtClientMs: number
}

/**
 * 低频状态缓存喵
 *
 * 保存最近一次权威低频状态，用于面板和低频 UI 查询喵。
 */
export type LowFreqWorldState = {
  /** 权威快照 Tick 喵 */
  simulationTick: number
  /** 低频版本号喵 */
  version: number
  /** 同步模式喵 */
  syncMode: SnapshotLowFreqMessage['syncMode']
  /** 增量基线版本号喵 */
  baseVersion: number | null
  /** 世界半径喵 */
  worldRadius: number | null
  /** 世界类型喵 */
  worldType: string | null
  /** 游戏秒/真实秒喵 */
  gameSecondsPerRealSecond: number | null
  /** 时间缩放喵 */
  timeScale: number | null
  /** 当前时间字段喵 */
  year: number | null
  month: number | null
  day: number | null
  hour: number | null
  minute: number | null
  second: number | null
  /** 星区中心缓存喵 */
  sectorCenters: SectorCenter[]
  /** 星区归属缓存喵 */
  sectorOwnerNationIdByCoord: Record<string, string>
  /** 低频结算缓存喵 */
  dailySettlementState: DailySettlementState | null
  /** 当前玩家国家喵 */
  playerNationId: string | null
  /** 收包时间喵 */
  receivedAtClientMs: number
  /** 公开实体基线（恒星/行星/重心），随低频快照下发喵 */
  entities?: EntitySnapshot[]
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
 * 前端维护的本地世界状态容器喵：
 * 1. `visibleEntitiesById` 是当前主权威实体缓存喵
 * 2. `highFreqFrames` 保存最近若干高频权威帧喵
 * 3. `latestLowFreqState` 保存最近一次低频权威状态喵
 * 4. `pendingCommandsByEntityId` 只负责命令 UI 状态喵
 */
export type LocalVisibleWorld = {
  /** 可见实体权威快照缓存（实体ID → EntitySnapshot）喵 */
  visibleEntitiesById: Map<number, EntitySnapshot>
  /** 最近若干高频权威帧缓存喵 */
  highFreqFrames: HighFreqSnapshotFrame[]
  /** 最近一次低频权威状态缓存喵 */
  latestLowFreqState: LowFreqWorldState | null
  /** 待确认指令状态（实体ID → PendingCommandRecord）喵 */
  pendingCommandsByEntityId: Map<number, PendingCommandRecord>
  /** 最新快照元信息喵 */
  lastSnapshotMeta: AuthoritativeSnapshotMeta | null
  /** 情报可见性状态喵 */
  intelVisibilityState: IntelVisibilityState
  /** 当前兴趣集合：选中对象、己方对象、命令对象等喵 */
  interestEntityIds: Set<number>
  /** 实体保活截止时间喵 */
  retainedUntilClientMsByEntityId: Map<number, number>
  /** 关注对象保留策略：选中实体、己方舰船、待确认指令对象、最近离开订阅范围的实体喵 */
  focusedEntityIds: Set<number>
  /** 最后应用高频快照的 Tick 喵 */
  lastAppliedHighFreqTick: number | null
  /** 最后应用低频状态的版本喵 */
  lastAppliedLowFreqVersion: number | null
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
  /** 数据来源：'authoritative' | 'fallback' | 'interpolated' | 'extrapolated' | 'snapped' 喵 */
  source:
    | 'authoritative'
    | 'fallback'
    | 'interpolated'
    | 'extrapolated'
    | 'snapped'
}

/**
 * 双逻辑帧插值调试状态喵。
 *
 * 用于调试面板解释当前渲染窗口喵。
 */
export type InterpolationDebugState = {
  currentTick: number
  nextTick: number | null
  latestTick: number
  renderAlpha: number
  renderGameSeconds: number
  mode: 'freeze' | 'interpolate' | 'extrapolate'
  didResetWindow: boolean
}

/**
 * 单个实体当前渲染帧的插值调试状态喵。
 *
 * 用于直接观察“上一渲染帧位置 / 本帧目标位置 / 本帧推进比例”喵。
 */
export type EntityInterpolationDebugState = {
  entityId: number
  previousRenderPosition: { x: number; y: number } | null
  targetRenderPosition: { x: number; y: number } | null
  presentedRenderPosition: { x: number; y: number } | null
  renderedMeshPosition: { x: number; y: number } | null
  previousRenderHeadingDeg: number | null
  targetRenderHeadingDeg: number | null
  presentedRenderHeadingDeg: number | null
  renderedMeshHeadingDeg: number | null
  frameBlendAlpha: number
  renderFrameDeltaMs: number
  currentTick: number
  nextTick: number | null
  renderGameSeconds: number
  targetSource: EntityDisplayPosition['source'] | 'none'
}

/**
 * 实体插值调试捕获状态喵。
 *
 * 支持对当前选中实体连续抓取若干渲染帧喵。
 */
export type EntityInterpolationCaptureState = {
  entityId: number | null
  remainingFrames: number
  frames: EntityInterpolationDebugState[]
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

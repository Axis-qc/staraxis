/**
 * @file localVisibleWorldCommands.ts
 *
 * @description
 * 前端本地可见世界的指令状态机管理喵。
 *
 * 实现“发指令 -> 本地执行 -> 后端验证 -> 对账结束”的完整状态机喵。
 * 核心状态：pending_send → predicting → confirmed/rejected → completed/correcting 喵。
 *
 * 遵循“前端本地模拟与后端权威校验重构计划”中的指令状态机设计要求喵。
 */

import type { PendingCommandRecord, PendingCommandStatus, LocalVisibleWorld } from './localVisibleWorldTypes'
import type { ShipDetails } from '../../net/snapshotWs'

// ==================== 指令结果消息协议 ====================

/**
 * 指令结果类型喵
 *
 * 后端通过 WebSocket 发送的指令处理结果喵。
 * 用于补充 HTTP 提交回执的不足，提供完整的指令生命周期喵。
 */
export type CommandResultType =
  | 'accepted'     // 指令已被后端接受并开始执行
  | 'rejected'     // 指令被后端拒绝
  | 'completed'    // 指令执行完成
  | 'corrected'    // 指令需要纠偏（前端预测与后端权威状态不一致）

/**
 * 指令结果消息喵
 *
 * 后端推送的指令处理结果，包含详细的处理信息和纠偏数据喵。
 */
export type CommandResultMessage = {
  /** 消息类型，固定为 'command_result' 喵 */
  type: 'command_result'
  /** 前端指令唯一标识喵 */
  clientCommandId: string
  /** 实体ID喵 */
  entityId: number
  /** 结果类型喵 */
  resultType: CommandResultType
  /** 结果发生时的权威模拟Tick喵 */
  authoritativeTick: number
  /** 结果时间戳（游戏秒数）喵 */
  gameSeconds: number
  /** 拒绝或纠偏原因喵 */
  reason?: string
  /** 纠偏数据：权威位置、速度、航向等喵 */
  correctionData?: {
    position: { x: number; y: number }
    velocity?: { x: number; y: number }
    headingDeg?: number
    movementCommand?: ShipDetails['movementCommand'] | null
  }
}

// ==================== 指令状态转换规则 ====================

/**
 * 指令状态转移映射喵
 *
 * 定义每个状态允许转移到哪些状态喵。
 */
const ALLOWED_TRANSITIONS: Record<PendingCommandStatus, PendingCommandStatus[]> = {
  pending_send: ['predicting', 'rejected'],
  predicting: ['confirmed', 'rejected', 'correcting', 'completed'],
  confirmed: ['completed', 'correcting'],
  rejected: [], // 拒绝是终态
  correcting: ['completed'],
  completed: [] // 完成是终态
}

/**
 * 检查是否允许状态转移喵
 */
export function canTransition(from: PendingCommandStatus, to: PendingCommandStatus): boolean {
  return ALLOWED_TRANSITIONS[from].includes(to)
}

// ==================== 指令创建与更新 ====================

/**
 * 生成客户端指令ID喵
 *
 * 使用时间戳和随机数生成唯一标识，用于后续对账喵。
 */
export function generateClientCommandId(): string {
  const timestamp = Date.now().toString(36)
  const random = Math.random().toString(36).substring(2, 8)
  return `cmd_${timestamp}_${random}`
}

/**
 * 创建待确认指令记录喵
 *
 * @param entityId 实体ID喵
 * @param commandType 指令类型喵
 * @param movementSeed 移动指令种子喵
 * @returns 待确认指令记录喵
 */
export function createPendingCommand(
  entityId: number,
  commandType: PendingCommandRecord['commandType'],
  movementSeed: ShipDetails['movementCommand'] | null
): PendingCommandRecord {
  const clientCommandId = generateClientCommandId()
  const normalizedMovementSeed =
    movementSeed && commandType === 'MOVE_TO'
      ? {
          ...movementSeed,
          clientCommandId: movementSeed.clientCommandId ?? clientCommandId,
        }
      : movementSeed

  return {
    entityId,
    clientCommandId,
    commandType,
    issuedAtClientMs: Date.now(),
    status: 'pending_send',
    movementSeed: normalizedMovementSeed,
    lastAuthoritativeAckTick: null,
    rejectionReason: null,
    completionReportSentAtClientMs: null,
  }
}

/**
 * 更新指令状态喵
 *
 * @param command 待更新指令喵
 * @param newStatus 新状态喵
 * @param options 更新选项喵
 * @returns 更新后的指令（新对象）喵
 */
export function updateCommandStatus(
  command: PendingCommandRecord,
  newStatus: PendingCommandStatus,
  options: {
    authoritativeTick?: number | null
    rejectionReason?: string | null
    movementSeed?: ShipDetails['movementCommand'] | null
  } = {}
): PendingCommandRecord {
  if (command.status === newStatus) {
    return {
      ...command,
      lastAuthoritativeAckTick: options.authoritativeTick ?? command.lastAuthoritativeAckTick,
      rejectionReason: options.rejectionReason ?? command.rejectionReason,
      movementSeed: options.movementSeed ?? command.movementSeed,
    }
  }

  if (!canTransition(command.status, newStatus)) {
    throw new Error(`Invalid command status transition: ${command.status} -> ${newStatus}`)
  }

  return {
    ...command,
    status: newStatus,
    lastAuthoritativeAckTick: options.authoritativeTick ?? command.lastAuthoritativeAckTick,
    rejectionReason: options.rejectionReason ?? command.rejectionReason,
    movementSeed: options.movementSeed ?? command.movementSeed
  }
}

/**
 * 处理指令结果消息喵
 *
 * 将后端推送的指令结果消息转换为前端指令状态更新喵。
 *
 * @param command 当前指令喵
 * @param result 指令结果消息喵
 * @returns 更新后的指令状态（可能为 null 表示指令已结束）喵
 */
export function processCommandResult(
  command: PendingCommandRecord,
  result: CommandResultMessage
): PendingCommandRecord | null {
  switch (result.resultType) {
    case 'accepted':
      return updateCommandStatus(command, 'confirmed', {
        authoritativeTick: result.authoritativeTick,
        movementSeed: result.correctionData?.movementCommand ?? command.movementSeed
      })

    case 'rejected':
      return updateCommandStatus(command, 'rejected', {
        authoritativeTick: result.authoritativeTick,
        rejectionReason: result.reason ?? '未知原因'
      })

    case 'completed':
      return updateCommandStatus(command, 'completed', {
        authoritativeTick: result.authoritativeTick
      })

    case 'corrected':
      return updateCommandStatus(command, 'correcting', {
        authoritativeTick: result.authoritativeTick,
        movementSeed: result.correctionData?.movementCommand ?? command.movementSeed
      })

    default:
      // 未知结果类型，保持原状态喵
      return command
  }
}

// ==================== 世界层指令管理 ====================

/**
 * 向世界层添加待确认指令喵
 *
 * @param world 本地可见世界喵
 * @param command 待确认指令喵
 * @returns 是否成功添加喵
 */
export function addPendingCommandToWorld(
  world: LocalVisibleWorld,
  command: PendingCommandRecord
): boolean {
  const existing = world.pendingCommandsByEntityId.get(command.entityId)
  if (existing && existing.status !== 'completed' && existing.status !== 'rejected') {
    // 实体已有未完成指令，需要先处理冲突喵
    return false
  }

  world.pendingCommandsByEntityId.set(command.entityId, command)
  return true
}

/**
 * 从世界层移除指令（完成或拒绝后）喵
 *
 * @param world 本地可见世界喵
 * @param entityId 实体ID喵
 * @param status 指令最终状态喵
 */
export function removeCommandFromWorld(
  world: LocalVisibleWorld,
  entityId: number,
  status: 'completed' | 'rejected'
): void {
  world.pendingCommandsByEntityId.delete(entityId)

  // 如果是拒绝状态，可能需要触发纠偏喵
  if (status === 'rejected') {
    const predictedState = world.predictedShipsById.get(entityId)
    if (predictedState) {
      // 标记为纠偏中喵
      predictedState.isCorrecting = true
    }
  }
}

/**
 * 根据快照更新指令状态喵
 *
 * 分析快照中的 movementCommand 字段，更新对应实体的指令状态喵。
 *
 * @param world 本地可见世界喵
 * @param snapshotTick 快照Tick喵
 * @param entityId 实体ID喵
 * @param movementCommand 移动指令种子喵
 * @returns 是否触发了状态更新喵
 */
export function updateCommandFromSnapshot(
  world: LocalVisibleWorld,
  snapshotTick: number,
  entityId: number,
  movementCommand: ShipDetails['movementCommand'] | null
): boolean {
  const command = world.pendingCommandsByEntityId.get(entityId)
  if (!command) {
    // 没有待确认指令，无需处理喵
    return false
  }

  if (movementCommand) {
    if (movementCommand.clientCommandId && movementCommand.clientCommandId !== command.clientCommandId) {
      return false
    }
    // 快照中包含移动指令，表示后端已接受并正在执行喵
    if (command.status === 'pending_send' || command.status === 'predicting') {
      const updated = updateCommandStatus(command, 'confirmed', {
        authoritativeTick: snapshotTick,
        movementSeed: command.movementSeed ?? movementCommand
      })
      world.pendingCommandsByEntityId.set(entityId, updated)
      return true
    }
  } else {
    // 快照中没有移动指令喵
    if (command.status === 'confirmed' || command.status === 'predicting') {
      // 指令可能已执行完成喵
      const updated = updateCommandStatus(command, 'completed', {
        authoritativeTick: snapshotTick
      })
      world.pendingCommandsByEntityId.set(entityId, updated)
      return true
    }
  }

  return false
}

/**
 * 获取实体当前指令状态喵
 *
 * @param world 本地可见世界喵
 * @param entityId 实体ID喵
 * @returns 指令状态，无指令时返回 null 喵
 */
export function getEntityCommandStatus(
  world: LocalVisibleWorld,
  entityId: number
): PendingCommandRecord | null {
  return world.pendingCommandsByEntityId.get(entityId) ?? null
}

/**
 * 检查实体是否有活跃指令喵
 *
 * @param world 本地可见世界喵
 * @param entityId 实体ID喵
 * @returns 是否有未完成的活跃指令喵
 */
export function hasActiveCommand(
  world: LocalVisibleWorld,
  entityId: number
): boolean {
  const command = world.pendingCommandsByEntityId.get(entityId)
  if (!command) return false

  return command.status !== 'completed' && command.status !== 'rejected'
}

// ==================== 指令冲突处理 ====================

/**
 * 指令冲突解决策略喵
 */
export type CommandConflictStrategy =
  | 'cancel_previous'   // 取消前一条指令
  | 'queue'             // 排队等待
  | 'reject_new'        // 拒绝新指令

/**
 * 解决指令冲突喵
 *
 * @param world 本地可见世界喵
 * @param entityId 实体ID喵
 * @param newCommand 新指令喵
 * @param strategy 冲突解决策略喵
 * @returns 是否允许新指令喵
 */
export function resolveCommandConflict(
  world: LocalVisibleWorld,
  entityId: number,
  _newCommand: PendingCommandRecord,
  strategy: CommandConflictStrategy = 'cancel_previous'
): boolean {
  const existing = world.pendingCommandsByEntityId.get(entityId)
  if (!existing) return true
  if (existing.status === 'completed' || existing.status === 'rejected') {
    world.pendingCommandsByEntityId.delete(entityId)
    return true
  }

  switch (strategy) {
    case 'cancel_previous':
      // 普通右键的新命令会直接覆盖旧命令，不把旧命令当作一次完成喵
      world.pendingCommandsByEntityId.delete(entityId)
      return true

    case 'queue':
      // 排队策略：当前不支持，需要队列数据结构喵
      return false

    case 'reject_new':
      return false

    default:
      return false
  }
}
// ==================== 优先级规则 ====================

/**
 * 指令更新来源喵
 */
export type CommandUpdateSource =
  | 'http_response'     // HTTP提交回执
  | 'websocket_result'  // WebSocket结果消息
  | 'snapshot_state'    // 快照状态

/**
 * 指令更新数据喵
 */
export type CommandUpdateData = {
  /** 来源喵 */
  source: CommandUpdateSource
  /** 更新时间戳（客户端毫秒）喵 */
  clientTimestamp: number
  /** 权威模拟Tick（如果可用）喵 */
  authoritativeTick?: number | null
  /** 更新内容喵 */
  data: {
    type: 'status_change'
    newStatus: PendingCommandStatus
    options?: {
      rejectionReason?: string | null
      movementSeed?: ShipDetails['movementCommand'] | null
    }
  } | {
    type: 'correction'
    correctionData: CommandResultMessage['correctionData']
  }
}

/**
 * 应用带优先级的指令更新喵
 *
 * 优先级规则喵：
 * 1. WebSocket结果消息优先级最高（明确的指令结果）喵
 * 2. 快照状态次之（反映权威世界状态）喵
 * 3. HTTP提交回执优先级最低（仅确认提交）喵
 *
 * 相同来源内，按权威Tick和时间戳排序喵。
 *
 * @param world 本地可见世界喵
 * @param entityId 实体ID喵
 * @param update 更新数据喵
 * @returns 是否应用了更新喵
 */
export function applyCommandUpdateWithPriority(
  world: LocalVisibleWorld,
  entityId: number,
  update: CommandUpdateData
): boolean {
  const existing = world.pendingCommandsByEntityId.get(entityId)
  if (!existing) {
    // 没有待确认指令，忽略更新喵
    return false
  }

  // 计算更新优先级分数喵
  const priorityScore = (source: CommandUpdateSource): number => {
    switch (source) {
      case 'websocket_result': return 3
      case 'snapshot_state': return 2
      case 'http_response': return 1
      default: return 0
    }
  }

  // 计算现有指令的优先级（基于最后确认来源）喵
  const existingPriority = existing.lastAuthoritativeAckTick !== null ? 2 : 1 // 简化逻辑喵

  const updatePriority = priorityScore(update.source)

  // 只有更高优先级的更新才应用喵
  if (updatePriority < existingPriority) {
    return false
  }

  // 相同优先级时，按权威Tick和时间戳判断喵
  if (updatePriority === existingPriority) {
    if (update.authoritativeTick !== undefined && update.authoritativeTick !== null) {
      if (existing.lastAuthoritativeAckTick !== null && update.authoritativeTick <= existing.lastAuthoritativeAckTick) {
        // 旧权威Tick，忽略喵
        return false
      }
    } else if (update.clientTimestamp <= existing.issuedAtClientMs) {
      // 旧时间戳，忽略喵
      return false
    }
  }

  // 应用更新喵
  if (update.data.type === 'status_change') {
    try {
      const updated = updateCommandStatus(existing, update.data.newStatus, {
        authoritativeTick: update.authoritativeTick ?? existing.lastAuthoritativeAckTick,
        rejectionReason: update.data.options?.rejectionReason,
        movementSeed: update.data.options?.movementSeed
      })
      world.pendingCommandsByEntityId.set(entityId, updated)
      return true
    } catch (error) {
      console.error('[CommandPriority] 状态更新失败:', error)
      return false
    }
  } else {
    // 纠偏数据更新，需要特殊处理喵
    console.warn('[CommandPriority] 纠偏数据更新暂未实现')
    return false
  }
}

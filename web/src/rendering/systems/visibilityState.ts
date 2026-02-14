/**
 * VisibilityState（前端可见性状态管理）
 *
 * 作用：管理前端可见性状态，包括战争迷雾、实体可见性级别、国家归属等。
 *
 * 可见性级别：
 * - FULL: 完全可见 - 显示所有细节
 * - PARTIAL: 部分可见 - 仅显示轮廓或基础信息
 * - NONE: 不可见 - 完全隐藏
 *
 * 状态分层：
 * 1. 记忆层：曾经探索过但当前不可见的区域（显示上次快照）
 * 2. 当前层：当前可见的区域（实时状态）
 * 3. 未探索：完全未知的区域
 */

import type { EntitySnapshot, SectorCenter } from '../../net/snapshotWs'

/** 可见性级别枚举 */
export type VisibilityLevel = 'FULL' | 'PARTIAL' | 'NONE'

/** 星区坐标键（用于集合操作） */
export type SectorCoordKey = string

/** 实体可见性状态 */
export interface EntityVisibility {
  entityId: number
  visibility: VisibilityLevel
  lastKnownSnapshot?: EntitySnapshot  // 记忆层：上次可见的快照
  ownerNationId?: string              // 实体所属国家ID
}

/** 星区可见性状态 */
export interface SectorVisibility {
  coordKey: SectorCoordKey
  visibility: VisibilityLevel
  explored: boolean                   // 是否已探索（记忆层）
  ownerNationId?: string              // 星区所属国家ID
  lastSeenTime?: number               // 上次可见时间（用于记忆衰减）
}

/**
 * 可见性状态管理器
 */
export class VisibilityStateManager {
  /** 当前国家ID（玩家所属国家） */
  private currentNationId: string | null = null

  /** 实体可见性状态映射 */
  private entityVisibilityMap = new Map<number, EntityVisibility>()

  /** 星区可见性状态映射 */
  private sectorVisibilityMap = new Map<SectorCoordKey, SectorVisibility>()

  /** 事件监听器：可见性变化 */
  private visibilityChangeListeners: Array<(changes: {
    entities: EntityVisibility[]
    sectors: SectorVisibility[]
  }) => void> = []

  /**
   * 设置当前国家ID
   */
  setCurrentNationId(nationId: string | null) {
    if (this.currentNationId !== nationId) {
      this.currentNationId = nationId
      // 国家变更时重置可见性状态
      this.clearVisibilityState()
    }
  }

  /**
   * 获取当前国家ID
   */
  getCurrentNationId(): string | null {
    return this.currentNationId
  }

  /**
   * 更新实体可见性状态（从快照数据）
   */
  updateFromSnapshot(
    entities: EntitySnapshot[],
    sectorCenters: SectorCenter[],
    currentTime: number
  ) {
    const entityChanges: EntityVisibility[] = []
    const sectorChanges: SectorVisibility[] = []

    // 处理星区可见性
    for (const sector of sectorCenters) {
      const coordKey = this.coordToKey(sector.q, sector.r)
      const existing = this.sectorVisibilityMap.get(coordKey)

      // 星区当前可见（在快照中）
      const sectorVisibility: SectorVisibility = {
        coordKey,
        visibility: 'FULL',  // 快照中的星区都是当前可见的
        explored: true,
        ownerNationId: undefined,  // TODO: 从后端获取星区归属
        lastSeenTime: currentTime
      }

      if (!existing || existing.visibility !== sectorVisibility.visibility) {
        this.sectorVisibilityMap.set(coordKey, sectorVisibility)
        sectorChanges.push(sectorVisibility)
      }
    }

    // 处理实体可见性
    for (const entity of entities) {
      const existing = this.entityVisibilityMap.get(entity.entityId)

      // 提取实体所属国家ID
      const ownerNationId = this.extractOwnerNationId(entity)

      // 计算可见性级别
      const visibility = this.calculateEntityVisibility(entity, ownerNationId)

      const entityVisibility: EntityVisibility = {
        entityId: entity.entityId,
        visibility,
        ownerNationId,
        lastKnownSnapshot: visibility === 'NONE' ? existing?.lastKnownSnapshot : entity
      }

      if (!existing || existing.visibility !== visibility) {
        this.entityVisibilityMap.set(entity.entityId, entityVisibility)
        entityChanges.push(entityVisibility)
      }
    }

    // 触发变化事件
    if (entityChanges.length > 0 || sectorChanges.length > 0) {
      this.notifyVisibilityChange(entityChanges, sectorChanges)
    }
  }

  /**
   * 计算实体可见性级别
   */
  private calculateEntityVisibility(
    _entity: EntitySnapshot,
    ownerNationId?: string
  ): VisibilityLevel {
    // 如果没有设置当前国家，则所有实体完全可见（兼容模式）
    if (!this.currentNationId) {
      return 'FULL'
    }

    // 实体无归属或归属未知，默认可见
    if (!ownerNationId) {
      return 'FULL'
    }

    // 实体属于当前国家：完全可见
    if (ownerNationId === this.currentNationId) {
      return 'FULL'
    }

    // TODO: 检查外交关系、传感器范围等
    // 暂时简化：非本国实体部分可见
    return 'PARTIAL'
  }

  /**
   * 从实体快照中提取所有者国家ID
   */
  private extractOwnerNationId(entity: EntitySnapshot): string | undefined {
    const details = entity.details as any
    return details?.ownerNationId
  }

  /**
   * 获取实体可见性状态
   */
  getEntityVisibility(entityId: number): EntityVisibility | undefined {
    return this.entityVisibilityMap.get(entityId)
  }

  /**
   * 获取星区可见性状态
   */
  getSectorVisibility(q: number, r: number): SectorVisibility | undefined {
    const coordKey = this.coordToKey(q, r)
    return this.sectorVisibilityMap.get(coordKey)
  }

  /**
   * 检查实体是否可见
   */
  isEntityVisible(entityId: number): boolean {
    const visibility = this.entityVisibilityMap.get(entityId)
    return visibility ? visibility.visibility !== 'NONE' : false
  }

  /**
   * 检查实体是否完全可见
   */
  isEntityFullyVisible(entityId: number): boolean {
    const visibility = this.entityVisibilityMap.get(entityId)
    return visibility ? visibility.visibility === 'FULL' : false
  }

  /**
   * 检查星区是否可见
   */
  isSectorVisible(q: number, r: number): boolean {
    const coordKey = this.coordToKey(q, r)
    const visibility = this.sectorVisibilityMap.get(coordKey)
    return visibility ? visibility.visibility !== 'NONE' : false
  }

  /**
   * 检查星区是否已探索
   */
  isSectorExplored(q: number, r: number): boolean {
    const coordKey = this.coordToKey(q, r)
    const visibility = this.sectorVisibilityMap.get(coordKey)
    return visibility ? visibility.explored : false
  }

  /**
   * 添加可见性变化监听器
   */
  addVisibilityChangeListener(
    listener: (changes: { entities: EntityVisibility[]; sectors: SectorVisibility[] }) => void
  ) {
    this.visibilityChangeListeners.push(listener)
  }

  /**
   * 移除可见性变化监听器
   */
  removeVisibilityChangeListener(
    listener: (changes: { entities: EntityVisibility[]; sectors: SectorVisibility[] }) => void
  ) {
    const index = this.visibilityChangeListeners.indexOf(listener)
    if (index !== -1) {
      this.visibilityChangeListeners.splice(index, 1)
    }
  }

  /**
   * 通知可见性变化
   */
  private notifyVisibilityChange(
    entityChanges: EntityVisibility[],
    sectorChanges: SectorVisibility[]
  ) {
    const changes = { entities: entityChanges, sectors: sectorChanges }
    for (const listener of this.visibilityChangeListeners) {
      try {
        listener(changes)
      } catch (error) {
        console.error('Visibility change listener error:', error)
      }
    }
  }

  /**
   * 清除所有可见性状态
   */
  clearVisibilityState() {
    const entityChanges = Array.from(this.entityVisibilityMap.values())
      .map(ev => ({ ...ev, visibility: 'NONE' as const }))

    const sectorChanges = Array.from(this.sectorVisibilityMap.values())
      .map(sv => ({ ...sv, visibility: 'NONE' as const }))

    this.entityVisibilityMap.clear()
    this.sectorVisibilityMap.clear()

    if (entityChanges.length > 0 || sectorChanges.length > 0) {
      this.notifyVisibilityChange(entityChanges, sectorChanges)
    }
  }

  /**
   * 将坐标转换为键
   */
  private coordToKey(q: number, r: number): SectorCoordKey {
    return `q:${q},r:${r}`
  }


  /**
   * 获取所有可见实体ID
   */
  getAllVisibleEntityIds(): number[] {
    return Array.from(this.entityVisibilityMap.entries())
      .filter(([_, visibility]) => visibility.visibility !== 'NONE')
      .map(([entityId]) => entityId)
  }

  /**
   * 获取所有可见星区坐标键
   */
  getAllVisibleSectorCoordKeys(): SectorCoordKey[] {
    return Array.from(this.sectorVisibilityMap.entries())
      .filter(([_, visibility]) => visibility.visibility !== 'NONE')
      .map(([coordKey]) => coordKey)
  }

  /**
   * 获取国家颜色（用于渲染）
   */
  getNationColor(nationId: string): string {
    // TODO: 从配置或后端获取国家颜色
    const colors: Record<string, string> = {
      'human_empire': '#3498db',     // 蓝色
      'human_republic': '#e74c3c',   // 红色
      // 默认颜色映射
    }
    return colors[nationId] || '#95a5a6'  // 默认灰色
  }
}
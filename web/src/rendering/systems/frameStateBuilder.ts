/**
 * @file frameStateBuilder.ts
 *
 * @description
 * 帧状态构建器 - 构建每帧渲染所需的状态数据。
 *
 * 作用：
 * - 基于屏幕像素计算剔除范围（cullingAabb）。
 * - 使用LOD系统计算各渲染类型的LOD状态。
 * - 整合快照数据、选择状态、时间信息。
 *
 * @usage
 * - 每帧调用 buildFrameState() 获取当前帧的完整状态。
 */
import * as THREE from 'three'
import type { EntitySnapshot, SnapshotMessage } from '../../net/snapshotWs'
import { computeLodState, type LodState, type LodOptions } from '../subsystems/lodSystem'
import type { VisibilityStateManager } from './visibilityState'

export type FrameState = {
    snapshot: SnapshotMessage | null
    entitiesById: Map<number, EntitySnapshot>
    sectorCenters: { q: number; r: number; x: number; y: number }[]
    selectedIds: Set<number>
    cullingAabb: { minX: number; maxX: number; minY: number; maxY: number }
    lod: LodState
    totalDays: number
    visibilityManager: VisibilityStateManager | null
}

export type FrameStateBuilder = {
    build: (snapshot: SnapshotMessage | null) => FrameState
    updateSectorCenters: (centers: { q: number; r: number; x: number; y: number }[]) => void
    updateEntities: (entities: EntitySnapshot[]) => void
    removeEntities: (entityIds: number[]) => void
    removeSectors: (sectorKeys: string[]) => void
    setSelectedIds: (ids: number[]) => void
}

export function createFrameStateBuilder(
    container: HTMLDivElement,
    cameraWorldPosGU: THREE.Vector2,
    zoom: { value: number },
    lodOptions?: LodOptions,
    visibilityManager?: VisibilityStateManager
): FrameStateBuilder {
    let sectorCenters: { q: number; r: number; x: number; y: number }[] = []
    const entitiesById = new Map<number, EntitySnapshot>()
    let selectedIds = new Set<number>()
    const visibilityMgr = visibilityManager || null

    const updateSectorCenters = (centers: { q: number; r: number; x: number; y: number }[]) => {
        // 增量更新星区中心喵
        for (const c of centers) {
            const key = `${c.q},${c.r}`
            const existing = sectorCenters.find(sc => sc.q === c.q && sc.r === c.r)
            if (!existing) {
                sectorCenters.push(c)
            }
        }
    }

    const updateEntities = (entities: EntitySnapshot[]) => {
        // 增量更新实体喵
        for (const e of entities) {
            entitiesById.set(e.entityId, e)
        }
    }

    const setSelectedIds = (ids: number[]) => {
        selectedIds = new Set(ids)
    }

    const removeEntities = (ids: number[]) => {
        for (const id of ids) {
            entitiesById.delete(id)
        }
    }

    const removeSectors = (keys: string[]) => {
        const keySet = new Set(keys)
        sectorCenters = sectorCenters.filter(sc => !keySet.has(`${sc.q},${sc.r}`))
    }

    const build = (snapshot: SnapshotMessage | null): FrameState => {
        // 基于屏幕像素计算世界范围（禁用视锥检测）
        // 使用屏幕宽高的 1.5 倍作为剔除范围
        const CULLING_SCALE = 1.5
        const screenWidthPx = container.clientWidth
        const screenHeightPx = container.clientHeight

        // 将屏幕像素转换为世界单位（GU）
        const viewWidthGU = screenWidthPx * zoom.value
        const viewHeightGU = screenHeightPx * zoom.value

        const cullingAabb = {
            minX: cameraWorldPosGU.x - (viewWidthGU * CULLING_SCALE) / 2,
            maxX: cameraWorldPosGU.x + (viewWidthGU * CULLING_SCALE) / 2,
            minY: cameraWorldPosGU.y - (viewHeightGU * CULLING_SCALE) / 2,
            maxY: cameraWorldPosGU.y + (viewHeightGU * CULLING_SCALE) / 2,
        }

        const totalDays =
            (snapshot?.realTimeWorldState?.gameDatetimeDay ?? 0) +
            (snapshot?.realTimeWorldState?.accGameHoursInDay ?? 0) / 24

        // 使用LOD系统统一计算所有渲染类型的LOD状态
        const lod = computeLodState(zoom.value, lodOptions)

        return {
            snapshot,
            entitiesById: new Map(entitiesById),
            sectorCenters: [...sectorCenters],
            selectedIds: new Set(selectedIds),
            cullingAabb,
            lod,
            totalDays,
            visibilityManager: visibilityMgr,
        }
    }

    return {
        build,
        updateSectorCenters,
        updateEntities,
        removeEntities,
        removeSectors,
        setSelectedIds,
    }
}

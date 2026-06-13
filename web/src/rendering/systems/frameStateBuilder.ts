/**
 * @file frameStateBuilder.ts
 *
 * @description
 * 帧状态构建器 - 构建每帧渲染所需的状态数据。
 *
 * 作用：
 * - 基于屏幕像素计算剔除范围（cullingAabb）。
 * - 使用LOD系统计算各渲染类型的LOD状态。
 * - 整合缓存实体、低频元数据、选择状态和渲染时间信息喵。
 *
 * @usage
 * - 每帧调用 buildFrameState() 获取当前帧的完整状态。
 */
import * as THREE from 'three'
import type { EntitySnapshot } from '../../net/snapshotWs'
import { computeLodState, type LodState, type LodOptions } from '../subsystems/lodSystem'
import type { VisibilityStateManager } from './visibilityState'
import { defaultGameTimeManager } from '../../game/time/GameTimeManager'

export type FrameState = {
    entitiesById: Map<number, EntitySnapshot>
    sectorCenters: { q: number; r: number; x: number; y: number }[]
    sectorOwnerNationIdByCoord: Record<string, string>
    selectedIds: Set<number>
    cullingAabb: { minX: number; maxX: number; minY: number; maxY: number }
    lod: LodState
    totalDays: number
    visibilityManager: VisibilityStateManager | null
}

export type FrameStateBuilder = {
    build: () => FrameState
    replaceSectorCenters: (centers: { q: number; r: number; x: number; y: number }[]) => void
    updateSectorOwnerNationIdByCoord: (ownerMap: Record<string, string>) => void
    updateEntities: (entities: EntitySnapshot[]) => void
    removeEntities: (entityIds: number[]) => void
    removeSectors: (sectorKeys: string[]) => void
    setSelectedIds: (ids: number[]) => void
    clearAllSectors: () => void
}

export function createFrameStateBuilder(
    container: HTMLDivElement,
    cameraWorldPosGU: THREE.Vector2,
    zoom: { value: number },
    lodOptions?: LodOptions,
    visibilityManager?: VisibilityStateManager,
    getViewSizeGU?: () => { widthGU: number; heightGU: number },
): FrameStateBuilder {
    let sectorCenters: { q: number; r: number; x: number; y: number }[] = []
    let sectorOwnerNationIdByCoord: Record<string, string> = {}
    const entitiesById = new Map<number, EntitySnapshot>()
    let selectedIds = new Set<number>()
    const visibilityMgr = visibilityManager || null

    const replaceSectorCenters = (centers: { q: number; r: number; x: number; y: number }[]) => {
        sectorCenters = [...centers]
    }

    const updateSectorOwnerNationIdByCoord = (ownerMap: Record<string, string>) => {
        sectorOwnerNationIdByCoord = { ...ownerMap }
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
        for (const key of keySet) {
            delete sectorOwnerNationIdByCoord[key]
        }
    }

    const clearAllSectors = () => {
        sectorCenters = []
        sectorOwnerNationIdByCoord = {}
    }

    const build = (): FrameState => {
        // 基于相机投影到 z=0 平面的可见范围计算世界包围盒喵。
        // 使用屏幕宽高的 1.2 倍作为剔除范围喵。
        const CULLING_SCALE = 1.2
        const fallbackWidthGU = container.clientWidth * zoom.value
        const fallbackHeightGU = container.clientHeight * zoom.value
        const viewSize = getViewSizeGU?.() ?? { widthGU: fallbackWidthGU, heightGU: fallbackHeightGU }
        const viewWidthGU = viewSize.widthGU
        const viewHeightGU = viewSize.heightGU

        const cullingAabb = {
            minX: cameraWorldPosGU.x - (viewWidthGU * CULLING_SCALE) / 2,
            maxX: cameraWorldPosGU.x + (viewWidthGU * CULLING_SCALE) / 2,
            minY: cameraWorldPosGU.y - (viewHeightGU * CULLING_SCALE) / 2,
            maxY: cameraWorldPosGU.y + (viewHeightGU * CULLING_SCALE) / 2,
        }

        const totalDays = defaultGameTimeManager.getCurrentGameSeconds() / 86400

        // 使用LOD系统统一计算所有渲染类型的LOD状态
        const lod = computeLodState(zoom.value, lodOptions)

        return {
            entitiesById,  // 直接引用，避免每帧复制 1935 条喵
            sectorCenters: [...sectorCenters],
            sectorOwnerNationIdByCoord: { ...sectorOwnerNationIdByCoord },
            selectedIds: new Set(selectedIds),
            cullingAabb,
            lod,
            totalDays,
            visibilityManager: visibilityMgr,
        }
    }

    return {
        build,
        replaceSectorCenters,
        updateSectorOwnerNationIdByCoord,
        updateEntities,
        removeEntities,
        removeSectors,
        setSelectedIds,
        clearAllSectors,
    }
}

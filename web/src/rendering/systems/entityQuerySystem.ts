/**
 * @file entityQuerySystem.ts
 *
 * @description
 * 实体查询系统 - 提供实体位置查询功能。
 *
 * 作用：
 * - 查询恒星、行星等实体的世界坐标位置。
 * - 计算行星的实时轨道位置。
 * - 供 SelectionRenderer 和外部 API 使用喵。
 *
 * @usage
 * - 传入实体数据后返回世界坐标喵。
 */
import type { EntitySnapshot, PlanetDetails } from '../../net/snapshotWs'
import { getInterpolatedEntityDisplayPosition } from '@/game/world'
import { defaultGameTimeManager } from '@/game/time/GameTimeManager'

export type EntityQuerySystem = {
    getEntityWorldPosGU: (entityId: number) => { x: number; y: number } | null
    updateEntities: (entities: EntitySnapshot[]) => void
}

export function createEntityQuerySystem(): EntityQuerySystem {
    const entitiesById = new Map<number, EntitySnapshot>()

    const updateEntities = (entities: EntitySnapshot[]) => {
        entitiesById.clear()
        for (const e of entities) {
            entitiesById.set(e.entityId, e)
        }
        // 实体缓存由高频快照链路统一刷新喵
    }

    const getEntityWorldPosGU = (entityId: number): { x: number; y: number } | null => {
        const e = entitiesById.get(entityId)
        if (!e) return null

        // 恒星和舰船直接返回位置喵
        if (e.entityType === 'STAR') {
            return e.posWorldGU ?? null
        }

        if (e.entityType === 'SHIP') {
            const displayPose = getInterpolatedEntityDisplayPosition(e.entityId)
            return displayPose?.position ?? e.posWorldGU ?? null
        }

        if (e.entityType === 'PLANET') {
            const details = e.details as PlanetDetails
            if (!details) return null

            const center = entitiesById.get(details.orbitCenterEntityId)
            if (!center) return null

            defaultGameTimeManager.update()
            const totalDays = defaultGameTimeManager.getCurrentGameSeconds() / 86400

            // 计算行星轨道位置
            const meanAnomaly = (Number(details.meanAnomalyDegAtEpoch ?? 0) * Math.PI) / 180
            const periodDays = Number(details.orbitalPeriodDays ?? 0)
            if (!Number.isFinite(periodDays) || periodDays <= 0) return null

            const angle = meanAnomaly + (totalDays / periodDays) * 2 * Math.PI
            const a = Number(details.semiMajorAxisGU ?? 0)
            const ecc = Number(details.eccentricity ?? 0)
            const b = a * Math.sqrt(Math.max(0, 1 - ecc ** 2))
            const periapsisArgRad = (Number(details.periapsisArgDeg ?? 0) * Math.PI) / 180

            const localX = a * Math.cos(angle)
            const localY = b * Math.sin(angle)
            const cosW = Math.cos(periapsisArgRad)
            const sinW = Math.sin(periapsisArgRad)

            return {
                x: center.posWorldGU!.x + localX * cosW - localY * sinW,
                y: center.posWorldGU!.y + localX * sinW + localY * cosW,
            }
        }

        return null
    }

    return {
        getEntityWorldPosGU,
        updateEntities,
    }
}

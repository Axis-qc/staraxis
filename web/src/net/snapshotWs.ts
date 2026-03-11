import { wsClient as sharedWsClient } from '../services/ws'

export type SectorCenter = { q: number; r: number; x: number; y: number }

export type EntityType = 'STAR' | 'PLANET' | 'SYSTEM_BARYCENTER' | 'SHIP' | 'STATION'

export type StarDetails = {
    starTypeId: string
    radiusGU: number
    massSolar: number
    temperatureK: number
    description: string
    surfaceTexturePath: string | null
}

export type PlanetDetails = {
    planetTypeId: string
    radiusGU: number
    rotationPeriodHours: number
    surfaceTexturePath: string | null
    ownerNationId: string | null
    isCapital: boolean
    orbitCenterEntityId: number
    semiMajorAxisGU: number
    eccentricity: number
    inclinationDeg: number
    periapsisArgDeg: number
    orbitalPeriodDays: number
    meanAnomalyDegAtEpoch: number
}

export type ShipDetails = {
    /** 舰船自定义标记集合（customFlags），例如 INITIAL_SPAWN_SHIP（初始出生舰船）喵。 */
    customFlags?: string[]
    /** 舰船朝向角（headingDeg，角度制）：0 度朝 +X，逆时针为正喵。 */
    headingDeg?: number
    /** 是否正在移动喵。 */
    isMoving?: boolean
    /** 移动目标位置（世界坐标 GU），仅当 isMoving=true 时有效喵。 */
    movementTarget?: { x: number; y: number }
    /** 当前速度矢量（GU/游戏秒）喵。 */
    velocity?: { x: number; y: number }
    /** 最大速度（GU/游戏秒）喵。 */
    maxSpeed?: number
    /** 基础加速度（GU/游戏秒²）喵。 */
    baseAcceleration?: number
    /** 舰首朝向加速度加成（GU/游戏秒²）喵。 */
    bowAccelerationBonus?: number
    /** 转向角速度（度/游戏秒）喵。 */
    turnRate?: number
    /** 侧向移动速度惩罚系数（0.0~1.0）喵。 */
    lateralSpeedPenalty?: number
    /** 反向移动速度惩罚系数（0.0~1.0）喵。 */
    reverseSpeedPenalty?: number
}

export type SystemBarycenterDetails = {}

export type EntitySnapshot = {
    entityId: number
    entityType: EntityType
    systemId: number
    parentEntityId: number
    sectorCoord: { q: number; r: number }
    posWorldGU: { x: number; y: number }
    ownerNationId?: string | null
    details: StarDetails | PlanetDetails | ShipDetails | SystemBarycenterDetails | null
}

export type SurfaceRegionSnapshot = {
    regionId: number
    regionType: string
    name: string
    surfacePercentage: number
    developableSpaceRatio: number
}

export type PlanetSurfaceSnapshot = {
    planetEntityId: number
    surfaceRegions: SurfaceRegionSnapshot[]
}

export type DailySettlementState = {
    settledDay: number
    sectorCount: number
    planetSurfaces: Record<string, PlanetSurfaceSnapshot>
}

export type SnapshotMessage = {
    type: 'snapshot'
    ok: boolean
    error?: string
    tickCostMs?: number
    realTimeWorldState?: {
        /** 权威模拟 tick。 */
        simulationTick: number
        /** 权威时间轴：累计游戏秒（向下取整）。 */
        totalGameSeconds: number
        /** 权威时间轴：本次快照对应 tick 的推进秒数（Δt）。 */
        deltaGameSeconds: number
        worldRadius: number
        worldType?: string
        gameSecondsPerRealSecond?: number
        timeScale?: number
        year?: number
        month?: number
        day?: number
        hour?: number
        minute?: number
        second?: number
        sectorCenters: SectorCenter[]
        sectorOwnerNationIdByCoord?: Record<string, string>
        entities: EntitySnapshot[]
        privateEntitiesByIntelLevel?: Record<string, EntitySnapshot[]>
    }
    dailySettlementState?: DailySettlementState
}

export type SnapshotWsOptions = {
    reconnectDelayMs?: number
    onStatus?: (s: { connected: boolean }) => void
    onSnapshot?: (snapshot: SnapshotMessage) => void
}

export type SnapshotWsClient = {
    close: () => void
    send: (data: any) => void
    updateVisibleSectors: (sectors: { q: number; r: number }[]) => void
    setNationId: (nationId: string) => void
}

export function connectSnapshotWs(options: SnapshotWsOptions = {}): SnapshotWsClient {
    const unsubs: Array<() => void> = []

    const applyStatus = () => {
        const s = sharedWsClient.getState()
        options.onStatus?.({ connected: s === 'connected' })
    }

    // 连接状态同步喵
    unsubs.push(sharedWsClient.onStateChange(() => applyStatus()))

    // 订阅 snapshot 消息喵
    if (options.onSnapshot) {
        unsubs.push(sharedWsClient.onMessage((text) => {
            try {
                const data = JSON.parse(text)
                if (data?.type === 'snapshot') {
                    options.onSnapshot?.(data)
                }
            } catch {
            }
        }))
    }

    applyStatus()

    return {
        close: () => {
            // 这里只解绑监听，不主动断开全局 WS，避免影响其他模块喵
            for (const u of unsubs) {
                try {
                    u()
                } catch {
                }
            }
            options.onStatus?.({ connected: false })
        },
        send: (data: any) => {
            try {
                sharedWsClient.sendText(typeof data === 'string' ? data : JSON.stringify(data))
            } catch {
            }
        },
        updateVisibleSectors: (sectors: { q: number; r: number }[]) => {
            sharedWsClient.updateVisibleSectors(sectors)
        },
        setNationId: (nationId: string) => {
            sharedWsClient.setNationId(nationId)
        }
    }
}

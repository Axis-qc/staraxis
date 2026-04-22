import { wsClient as sharedWsClient } from '../services/ws'

/**
 * 快照WebSocket协议规则喵：
 *
 * 消息类型喵：
 * 1. 高频快照：type = 'snapshot_high_freq'，用于实时实体状态同步喵。
 * 2. 低频快照：type = 'snapshot_low_freq'，用于经济/建筑/面板等低频数据喵。
 * 3. 命令结果：type = 'command_result'，用于命令处理结果通知喵。
 * 4. 兼容整包快照：type = 'snapshot'，仅保留兜底兼容入口，不再作为前端主链路喵。
 *
 * 同步模式喵：
 * - 全量同步（full）：包含完整状态，用于初始连接或恢复同步喵。
 * - 增量同步（delta）：仅包含自基线以来的变化，需要客户端有正确的基线状态喵。
 *
 * 恢复规则喵：
 * 1. 客户端重连时，应主动请求全量同步喵。
 * 2. 客户端收到delta包时，必须检查baseTick/baseVersion与本地最后应用的一致喵。
 * 3. 如果基线不连续，客户端应丢弃增量包并请求全量重同步喵。
 * 4. 客户端可主动发送 `{"type":"requestFullSync"}` 请求全量同步喵。
 */

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
    customFlags?: string[]
    headingDeg?: number
    isMoving?: boolean
    movementTarget?: { x: number; y: number }
    velocity?: { x: number; y: number }
    maxSpeed?: number
    baseAcceleration?: number
    bowAccelerationBonus?: number
    turnRate?: number
    lateralSpeedPenalty?: number
    reverseSpeedPenalty?: number
    movementCommand?: {
        commandType: string
        clientCommandId?: string
        targetPosition?: { x: number; y: number } | null
        startPosition?: { x: number; y: number } | null
        startVelocity?: { x: number; y: number } | null
        startHeadingDeg?: number
        startGameSeconds?: number
        startSimulationTick?: number
        maxSpeed?: number
        baseAcceleration?: number
        bowAccelerationBonus?: number
        turnRate?: number
        lateralSpeedPenalty?: number
        reverseSpeedPenalty?: number
    } | null
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
        simulationTick: number
        totalGameSeconds: number
        totalGameSecondsExact?: number
        deltaGameSeconds: number
        gameDatetimeDay?: number
        accGameHoursInDay?: number
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

export type SnapshotHighFreqMessage = {
    type: 'snapshot_high_freq'
    ok: boolean
    error?: string
    tickCostMs?: number
    simulationTick: number
    totalGameSeconds: number
    totalGameSecondsExact: number
    deltaGameSeconds: number
    syncMode: 'full' | 'delta'
    baseTick?: number
    entities: EntitySnapshot[]
    privateEntitiesByIntelLevel?: Record<string, EntitySnapshot[]>
    playerNationId?: string
}

export type SnapshotLowFreqMessage = {
    type: 'snapshot_low_freq'
    ok: boolean
    error?: string
    simulationTick: number
    version: number
    syncMode: 'full' | 'delta'
    baseVersion?: number
    worldRadius?: number
    worldType?: string
    gameSecondsPerRealSecond?: number
    timeScale?: number
    year?: number
    month?: number
    day?: number
    hour?: number
    minute?: number
    second?: number
    sectorCenters?: SectorCenter[]
    sectorOwnerNationIdByCoord?: Record<string, string>
    dailySettlementState?: DailySettlementState
    playerNationId?: string
}

export type CommandResultMessage = {
    type: 'command_result'
    clientCommandId: string
    entityId: number
    simulationTick: number
    // submitted 仅表示后端已收到命令，accepted 才表示权威接受喵
    resultType: 'submitted' | 'accepted' | 'rejected' | 'completed' | 'corrected'
    gameSeconds: number
    reason?: string
    correctionData?: {
        position: { x: number; y: number }
        velocity?: { x: number; y: number } | null
        headingDeg?: number
        movementCommand?: ShipDetails['movementCommand'] | null
    }
}

export type SnapshotWsOptions = {
    reconnectDelayMs?: number
    onStatus?: (s: { connected: boolean }) => void
    onSnapshot?: (snapshot: SnapshotMessage) => void
    onHighFreqSnapshot?: (snapshot: SnapshotHighFreqMessage) => void
    onLowFreqSnapshot?: (snapshot: SnapshotLowFreqMessage) => void
    onCommandResult?: (result: CommandResultMessage) => void
}

export type SnapshotWsClient = {
    close: () => void
    send: (data: any) => void
    updateVisibleSectors: (sectors: { q: number; r: number }[]) => void
    setNationId: (nationId: string) => void
    requestFullSync: () => void
}

export function connectSnapshotWs(options: SnapshotWsOptions = {}): SnapshotWsClient {
    const unsubs: Array<() => void> = []
    let subscribed = false

    const applyStatus = () => {
        const s = sharedWsClient.getState()
        if (s !== 'connected') {
            subscribed = false
        } else if (!subscribed) {
            sharedWsClient.subscribeSnapshot()
            subscribed = true
        }
        options.onStatus?.({ connected: s === 'connected' })
    }

    unsubs.push(sharedWsClient.onStateChange(() => applyStatus()))

    unsubs.push(sharedWsClient.onMessage((text) => {
        try {
            const data = JSON.parse(text)
            const type = data?.type

            if (type === 'snapshot') {
                options.onSnapshot?.(data)
            } else if (type === 'snapshot_high_freq') {
                options.onHighFreqSnapshot?.(data)
            } else if (type === 'snapshot_low_freq') {
                options.onLowFreqSnapshot?.(data)
            } else if (type === 'command_result') {
                options.onCommandResult?.(data)
            }
        } catch {
        }
    }))

    applyStatus()

    return {
        close: () => {
            if (subscribed) {
                try {
                    sharedWsClient.unsubscribeSnapshot()
                } catch {
                }
                subscribed = false
            }
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
        },
        requestFullSync: () => {
            sharedWsClient.sendText(JSON.stringify({ type: 'requestFullSync' }))
        }
    }
}

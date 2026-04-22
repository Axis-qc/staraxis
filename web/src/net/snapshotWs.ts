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
        }
    }
}

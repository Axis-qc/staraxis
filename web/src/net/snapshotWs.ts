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

export type SystemBarycenterDetails = {}

export type EntitySnapshot = {
    entityId: number
    entityType: EntityType
    systemId: number
    parentEntityId: number
    sectorCoord: { q: number; r: number }
    posWorldGU: { x: number; y: number }
    details: StarDetails | PlanetDetails | SystemBarycenterDetails | null
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
        gameDatetimeDay: number
        accGameHoursInDay: number
        worldRadius: number
        sectorCenters: SectorCenter[]
        entities: EntitySnapshot[]
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
    const reconnectDelayMs = options.reconnectDelayMs ?? 3000

    let ws: WebSocket | null = null
    let closed = false

    const connect = () => {
        if (closed) return

        const protocol = window.location.protocol === 'https:' ? 'wss:' : 'ws:'
        // 从 localStorage 获取 token 喵
        const token = localStorage.getItem('sa.token') || ''
        const wsUrl = `${protocol}//${window.location.host}/ws${token ? `?token=${encodeURIComponent(token)}` : ''}`
        ws = new WebSocket(wsUrl)

        ws.onopen = () => {
            options.onStatus?.({ connected: true })
            try {
                ws?.send(JSON.stringify({ type: 'subscribeSnapshot' }))
            } catch {
            }
        }

        ws.onmessage = (event) => {
            try {
                const data = JSON.parse(event.data)

                // 响应服务端应用层心跳喵
                if (data?.type === 'ping') {
                    try {
                        ws?.send(JSON.stringify({ type: 'pong' }))
                    } catch {
                    }
                    return
                }

                if (data?.type === 'snapshot') {
                    options.onSnapshot?.(data)
                }
            } catch {
            }
        }

        ws.onclose = () => {
            options.onStatus?.({ connected: false })
            ws = null
            if (!closed) {
                setTimeout(connect, reconnectDelayMs)
            }
        }

        ws.onerror = () => {
            try {
                ws?.close()
            } catch {
            }
        }
    }

    connect()

    return {
        close: () => {
            closed = true
            try {
                ws?.close()
            } catch {
            }
            ws = null
            options.onStatus?.({ connected: false })
        },
        send: (data: any) => {
            try {
                ws?.send(typeof data === 'string' ? data : JSON.stringify(data))
            } catch {
            }
        },
        updateVisibleSectors: (sectors: { q: number; r: number }[]) => {
            try {
                ws?.send(JSON.stringify({ type: 'updateVisibleSectors', sectors }))
            } catch {
            }
        },
        setNationId: (nationId: string) => {
            try {
                ws?.send(JSON.stringify({ type: 'setNationId', nationId }))
            } catch {
            }
        }
    }
}

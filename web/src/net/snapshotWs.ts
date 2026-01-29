export type SectorCenter = { q: number; r: number; x: number; y: number }

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
    }
    dailySettlementState?: {
        settledDay: number
        sectorCount: number
    }
}

export type SnapshotWsOptions = {
    reconnectDelayMs?: number
    onStatus?: (s: { connected: boolean }) => void
    onSnapshot?: (snapshot: SnapshotMessage) => void
}

export type SnapshotWsClient = {
    close: () => void
    send: (data: any) => void
}

export function connectSnapshotWs(options: SnapshotWsOptions = {}): SnapshotWsClient {
    const reconnectDelayMs = options.reconnectDelayMs ?? 3000

    let ws: WebSocket | null = null
    let closed = false

    const connect = () => {
        if (closed) return

        const protocol = window.location.protocol === 'https:' ? 'wss:' : 'ws:'
        const wsUrl = `${protocol}//${window.location.host}/ws`
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
    }
}

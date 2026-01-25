export type BackendStatus = {
    host: string
    port: number
    connections: number
    autoExitSeconds: number
    idleSeconds: number
}

export async function fetchStatus(): Promise<BackendStatus> {
    const resp = await fetch('/api/status')
    if (!resp.ok) {
        throw new Error(`HTTP ${resp.status}`)
    }
    return (await resp.json()) as BackendStatus
}

export async function requestQuit(): Promise<void> {
    const resp = await fetch('/api/quit', { method: 'POST' })
    if (!resp.ok) {
        throw new Error(`HTTP ${resp.status}`)
    }
}

export type WsState = 'disconnected' | 'connecting' | 'connected' | 'error'

export type WsClient = {
    getState: () => WsState
    getUrl: () => string
    connect: () => void
    disconnect: () => void
    sendText: (text: string) => void
    onStateChange: (cb: (s: WsState) => void) => () => void
    onMessage: (cb: (msg: string) => void) => () => void
    onError: (cb: (err: string) => void) => () => void
}

export function createWsClient(path: string = '/ws'): WsClient {
    let ws: WebSocket | null = null
    let state: WsState = 'disconnected'
    const stateListeners = new Set<(s: WsState) => void>()
    const msgListeners = new Set<(m: string) => void>()
    const errListeners = new Set<(e: string) => void>()

    function notifyState(s: WsState) {
        state = s
        for (const cb of stateListeners) cb(s)
    }

    function wsUrl(): string {
        const protocol = window.location.protocol === 'https:' ? 'wss:' : 'ws:'
        return `${protocol}//${window.location.host}${path}`
    }

    function connect() {
        if (state === 'connecting' || state === 'connected') return
        notifyState('connecting')
        try {
            ws = new WebSocket(wsUrl())
            ws.onopen = () => notifyState('connected')
            ws.onmessage = (evt) => {
                const text = String(evt.data)
                for (const cb of msgListeners) cb(text)
            }
            ws.onerror = () => {
                notifyState('error')
                for (const cb of errListeners) cb('WebSocket error')
            }
            ws.onclose = () => {
                ws = null
                notifyState('disconnected')
            }
        } catch (e) {
            ws = null
            notifyState('error')
            for (const cb of errListeners) cb((e as Error).message)
        }
    }

    function disconnect() {
        try {
            ws?.close()
        } catch {
        }
        ws = null
        notifyState('disconnected')
    }

    function sendText(text: string) {
        if (!ws || state !== 'connected') return
        ws.send(text)
    }

    return {
        getState: () => state,
        getUrl: wsUrl,
        connect,
        disconnect,
        sendText,
        onStateChange: (cb) => {
            stateListeners.add(cb)
            return () => stateListeners.delete(cb)
        },
        onMessage: (cb) => {
            msgListeners.add(cb)
            return () => msgListeners.delete(cb)
        },
        onError: (cb) => {
            errListeners.add(cb)
            return () => errListeners.delete(cb)
        },
    }
}

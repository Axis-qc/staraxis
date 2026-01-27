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

export type AuthMe = {
    ok: boolean
    playerId?: string
    username?: string
    gameId?: string
    error?: string
}

export type AuthLoginResponse = {
    ok: boolean
    playerId?: string
    username?: string
    token?: string
    error?: string
}

export type AuthRegisterResponse = {
    ok: boolean
    playerId?: string
    error?: string
}

function getToken(): string | null {
    try {
        return localStorage.getItem('sa.token')
    } catch {
        return null
    }
}

export async function authFetch(input: RequestInfo | URL, init: RequestInit = {}): Promise<Response> {
    const token = getToken()
    const headers = new Headers(init.headers || {})
    if (token) {
        headers.set('Authorization', `Bearer ${token}`)
    }
    return fetch(input, { ...init, headers })
}

export async function authMe(): Promise<AuthMe> {
    const resp = await authFetch('/api/auth/me')
    const data = (await resp.json()) as AuthMe
    if (!resp.ok) {
        throw new Error(data && data.error ? data.error : `HTTP ${resp.status}`)
    }
    return data
}

export async function authRegister(username: string, password: string): Promise<AuthRegisterResponse> {
    const resp = await fetch('/api/auth/register', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ username, password }),
    })
    const data = (await resp.json()) as AuthRegisterResponse
    if (!resp.ok) {
        throw new Error(data && data.error ? data.error : `HTTP ${resp.status}`)
    }
    return data
}

export async function authLogin(username: string, password: string): Promise<AuthLoginResponse> {
    const resp = await fetch('/api/auth/login', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ username, password }),
    })
    const data = (await resp.json()) as AuthLoginResponse
    if (!resp.ok) {
        throw new Error(data && data.error ? data.error : `HTTP ${resp.status}`)
    }
    return data
}

export async function authLogout(): Promise<void> {
    const resp = await authFetch('/api/auth/logout', { method: 'POST' })
    if (!resp.ok) {
        throw new Error(`HTTP ${resp.status}`)
    }
}

export async function authSetGameId(gameId: string): Promise<void> {
    const resp = await authFetch('/api/auth/gameId', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ gameId }),
    })
    if (!resp.ok) {
        const txt = await resp.text()
        throw new Error(txt || `HTTP ${resp.status}`)
    }
}

export type ModItem = {
    id: string
    enabled: boolean
    orderIndex: number
    name: string
    description: string
    version: string
    compatibleGameVersion: string
    author: string
}

export type ModsStatus = {
    ok: boolean
    mods: ModItem[]
    order: string[]
    disabled: string[]
    error?: string
}

export async function fetchMods(): Promise<ModsStatus> {
    const resp = await fetch('/api/mods', { cache: 'no-store' })
    const data = (await resp.json()) as ModsStatus
    if (!resp.ok) {
        throw new Error(data && data.error ? data.error : `HTTP ${resp.status}`)
    }
    return data
}

export async function saveMods(order: string[], disabled: string[]): Promise<void> {
    const resp = await fetch('/api/mods/order', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ order, disabled }),
    })
    const txt = await resp.text()
    if (!resp.ok) {
        try {
            const j = JSON.parse(txt)
            throw new Error(j && j.error ? j.error : `HTTP ${resp.status}`)
        } catch {
            throw new Error(txt || `HTTP ${resp.status}`)
        }
    }
}

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
    const resp = await authFetch('/api/quit', { method: 'POST' })
    if (!resp.ok) {
        const txt = await resp.text()
        throw new Error(txt || `HTTP ${resp.status}`)
    }
}



export type AuthMe = {
    ok: boolean
    playerId?: string
    username?: string
    gameId?: string
    role?: string
    error?: string
}

export type AuthLoginResponse = {
    ok: boolean
    playerId?: string
    username?: string
    role?: string
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

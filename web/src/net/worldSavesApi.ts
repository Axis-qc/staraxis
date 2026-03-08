export type TickPolicy = 'ALWAYS_RUN' | 'RUN_WHEN_ONLINE'

export type WorldSaveItem = {
  worldId: string
  worldName: string
  tickPolicy: TickPolicy
  createdAtEpochMs: number
  active: boolean
  worldRadius: number
  simulationTick: number
  totalGameSeconds: number
}

function authHeaders(): Record<string, string> {
  const token = localStorage.getItem('sa.token') || ''
  return token ? { Authorization: `Bearer ${token}` } : {}
}

async function parseJson<T>(resp: Response): Promise<T> {
  if (!resp.ok) {
    throw new Error(`http_${resp.status}`)
  }
  return (await resp.json()) as T
}

export async function listWorldSaves(): Promise<{ ok: boolean; worlds: WorldSaveItem[]; activeWorldId?: string }> {
  const resp = await fetch('/api/worlds', { headers: authHeaders() })
  return parseJson(resp)
}

export type SpawnMode = 'manual' | 'random'

export async function createWorldSave(req: {
  worldName: string
  worldRadius: number
  worldSeed?: string
  tickPolicy: TickPolicy
  spawnMode: SpawnMode
  creatorPlayerId?: string
}): Promise<{ ok: boolean; worldId: string; worldName: string; tickPolicy: TickPolicy; worldRadius: number }> {
  const resp = await fetch('/api/worlds', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json', ...authHeaders() },
    body: JSON.stringify(req),
  })
  return parseJson(resp)
}

export async function joinWorldSave(worldId: string, playerId: string): Promise<{ ok: boolean; worldId: string; playerState: string; nationId?: string; playerRole?: Record<string, unknown>; error?: string }> {
  const resp = await fetch(`/api/worlds/${encodeURIComponent(worldId)}/join`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json', ...authHeaders() },
    body: JSON.stringify({ playerId }),
  })
  return parseJson(resp)
}

export async function getWorldPlayerState(worldId: string, playerId: string): Promise<{ ok: boolean; worldId: string; playerId: string; playerState: string; playerRole?: Record<string, unknown>; error?: string }> {
  const resp = await fetch(`/api/worlds/${encodeURIComponent(worldId)}/player-state?playerId=${encodeURIComponent(playerId)}`, { headers: authHeaders() })
  return parseJson(resp)
}

export async function manualSaveWorld(worldId: string, saveId?: string): Promise<{ ok: boolean; worldId?: string; saveId?: string; error?: string }> {
  const resp = await fetch(`/api/worlds/${encodeURIComponent(worldId)}/save`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json', ...authHeaders() },
    body: JSON.stringify({ saveId: saveId || '' }),
  })
  return parseJson(resp)
}

export async function listWorldSaveFiles(worldId: string): Promise<{ ok: boolean; worldId?: string; saves: Array<{ fileName: string; saveType: 'latest' | 'auto' | 'manual'; path: string; lastModifiedEpochMs: number; sizeBytes: number }>; error?: string }> {
  const resp = await fetch(`/api/worlds/${encodeURIComponent(worldId)}/saves`, { headers: authHeaders() })
  return parseJson(resp)
}

export async function loadWorldFromSave(worldId: string, req: { loadType: 'latest' | 'auto' | 'manual'; fileName?: string }): Promise<{ ok: boolean; worldId?: string; loadType?: string; fileName?: string; error?: string }> {
  const resp = await fetch(`/api/worlds/${encodeURIComponent(worldId)}/load`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json', ...authHeaders() },
    body: JSON.stringify(req),
  })
  return parseJson(resp)
}

export async function listAvailableSpawns(worldId: string): Promise<{ ok: boolean; systems: Array<{ systemId: number; sectorQ: number; sectorR: number; centerX: number; centerY: number; starCount: number; planetCount: number }>; error?: string }> {
  const resp = await fetch(`/api/join-game/available-spawns?worldId=${encodeURIComponent(worldId)}`, { headers: authHeaders() })
  return parseJson(resp)
}

export async function confirmSpawn(req: { worldId: string; playerId: string; chosenSystemId: number }): Promise<{ ok: boolean; nationId?: string; spawnSystemId?: number; playerState?: string; error?: string }> {
  const resp = await fetch('/api/join-game/confirm-spawn', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json', ...authHeaders() },
    body: JSON.stringify(req),
  })
  return parseJson(resp)
}

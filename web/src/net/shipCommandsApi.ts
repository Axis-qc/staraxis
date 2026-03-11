/**
 * @file shipCommandsApi.ts
 *
 * @description
 * 舰船指令API - 发送移动等命令到后端喵。
 */

const API_BASE = '/api'

export type MoveShipCommand = {
  worldId: string
  nationId: string
  shipEntityId: number
  targetX: number
  targetY: number
}

export async function sendMoveShipCommand(cmd: MoveShipCommand): Promise<{ ok: boolean; error?: string }> {
  try {
    const resp = await fetch(`${API_BASE}/ship/move`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(cmd),
    })

    if (!resp.ok) {
      return { ok: false, error: `http_${resp.status}` }
    }

    return await resp.json()
  } catch (e) {
    return { ok: false, error: String(e) }
  }
}

import { logger, logMoveShip } from '../utils/logger'
import type { ShipDetails } from './snapshotWs'

const API_BASE = '/api'

export type MoveShipCommand = {
  worldId: string
  nationId: string
  clientCommandId: string
  shipEntityId: number
  targetX: number
  targetY: number
}

export type MoveShipCompletionReport = {
  worldId: string
  nationId: string
  clientCommandId: string
  shipEntityId: number
  reportedGameSeconds: number
  reportedPosition: { x: number; y: number }
}

export type MoveShipSubmitResponse = {
  ok: boolean
  status?: 'submitted'
  clientCommandId?: string
  shipEntityId?: number
  error?: string
}

export type MoveShipCompletionResponse = {
  ok: boolean
  status?: 'completed' | 'corrected'
  clientCommandId?: string
  shipEntityId?: number
  authoritativeTick?: number
  gameSeconds?: number
  reason?: string
  correctionData?: {
    position: { x: number; y: number }
    velocity?: { x: number; y: number } | null
    headingDeg?: number
    movementCommand?: ShipDetails['movementCommand'] | null
  } | null
  error?: string
}

export async function sendMoveShipCommand(cmd: MoveShipCommand): Promise<MoveShipSubmitResponse> {
  const startTime = performance.now()

  logMoveShip(cmd.shipEntityId, cmd.targetX, cmd.targetY, '前端发送移动命令喵')

  try {
    const resp = await fetch(`${API_BASE}/ship/move`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(cmd),
    })

    const duration = performance.now() - startTime
    logger.info('MoveShip-Trace', `移动命令提交 ship=${cmd.shipEntityId} 耗时=${duration.toFixed(0)}ms`)

    if (!resp.ok) {
      return { ok: false, error: `http_${resp.status}` }
    }

    return (await resp.json()) as MoveShipSubmitResponse
  } catch (e) {
    logger.error('MoveShip-Trace', `移动命令提交异常 ship=${cmd.shipEntityId} error=${String(e)}`)
    return { ok: false, error: String(e) }
  }
}

export async function sendMoveShipCompletionReport(
  report: MoveShipCompletionReport,
): Promise<MoveShipCompletionResponse> {
  try {
    const resp = await fetch(`${API_BASE}/ship/move/complete`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(report),
    })

    if (!resp.ok) {
      return { ok: false, error: `http_${resp.status}` }
    }

    return (await resp.json()) as MoveShipCompletionResponse
  } catch (e) {
    return { ok: false, error: String(e) }
  }
}

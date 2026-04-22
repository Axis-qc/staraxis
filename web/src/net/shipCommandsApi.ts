import { logger, logMoveShip } from '../utils/logger'

const API_BASE = '/api'

export type MoveShipCommand = {
  worldId: string
  nationId: string
  clientCommandId: string
  shipEntityId: number
  targetX: number
  targetY: number
}

export type MoveShipSubmitResponse = {
  ok: boolean
  /** HTTP 回包只表示 transport ack（传输确认），不代表权威命令状态喵 */
  status?: 'submitted' | 'rejected'
  clientCommandId?: string
  shipEntityId?: number
  authoritativeTick?: number
  gameSeconds?: number
  reason?: string
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

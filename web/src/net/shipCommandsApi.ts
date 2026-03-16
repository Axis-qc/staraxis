/**
 * @file shipCommandsApi.ts
 *
 * @description
 * 舰船指令API - 发送移动等命令到后端喵。
 */

import { logger, logMoveShip } from '../utils/logger'

const API_BASE = '/api'

export type MoveShipCommand = {
  worldId: string
  nationId: string
  shipEntityId: number
  targetX: number
  targetY: number
}

export async function sendMoveShipCommand(cmd: MoveShipCommand): Promise<{ ok: boolean; error?: string }> {
  const startTime = performance.now()

  // 使用 logger 记录，同时会输出到控制台和内存缓存喵
  logMoveShip(cmd.shipEntityId, cmd.targetX, cmd.targetY, '前端发送命令')

  try {
    const resp = await fetch(`${API_BASE}/ship/move`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(cmd),
    })

    const duration = performance.now() - startTime
    logger.info('MoveShip-Trace', `后端响应 ship=${cmd.shipEntityId} 耗时=${duration.toFixed(0)}ms`)

    if (!resp.ok) {
      return { ok: false, error: `http_${resp.status}` }
    }

    return await resp.json()
  } catch (e) {
    logger.error('MoveShip-Trace', `请求失败 ship=${cmd.shipEntityId} 错误=${String(e)}`)
    return { ok: false, error: String(e) }
  }
}

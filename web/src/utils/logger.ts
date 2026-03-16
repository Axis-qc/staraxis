/**
 * @file logger.ts
 *
 * @description
 * 前端日志工具 - 支持内存缓存和导出为文件喵。
 *
 * 说明：
 * - 浏览器无法直接写入本地文件，因此日志保存在内存中喵。
 * - 提供 exportLogs() 方法将日志导出为文件下载喵。
 * - 自动在页面卸载前导出日志（可选）喵。
 */

export type LogEntry = {
  timestamp: number
  level: 'debug' | 'info' | 'warn' | 'error'
  tag: string
  message: string
}

class Logger {
  private logs: LogEntry[] = []
  private maxLogs = 5000 // 最多保存 5000 条日志喵

  /**
   * 记录日志喵。
   */
  log(level: LogEntry['level'], tag: string, message: string) {
    const entry: LogEntry = {
      timestamp: Date.now(),
      level,
      tag,
      message,
    }

    this.logs.push(entry)

    // 保持日志数量在限制内喵
    if (this.logs.length > this.maxLogs) {
      this.logs = this.logs.slice(-this.maxLogs)
    }

    // 同时输出到控制台喵
    const date = new Date(entry.timestamp)
    const timeStr = `${date.getHours().toString().padStart(2, '0')}:${date.getMinutes().toString().padStart(2, '0')}:${date.getSeconds().toString().padStart(2, '0')}.${date.getMilliseconds().toString().padStart(3, '0')}`
    const formatted = `[${timeStr}] [${tag}] ${message}`

    switch (level) {
      case 'error':
        console.error(formatted)
        break
      case 'warn':
        console.warn(formatted)
        break
      case 'debug':
        console.debug(formatted)
        break
      default:
        console.log(formatted)
    }
  }

  debug(tag: string, message: string) {
    this.log('debug', tag, message)
  }

  info(tag: string, message: string) {
    this.log('info', tag, message)
  }

  warn(tag: string, message: string) {
    this.log('warn', tag, message)
  }

  error(tag: string, message: string) {
    this.log('error', tag, message)
  }

  /**
   * 获取所有日志喵。
   */
  getLogs(): LogEntry[] {
    return [...this.logs]
  }

  /**
   * 清空日志喵。
   */
  clear() {
    this.logs = []
  }

  /**
   * 导出日志为文件下载喵。
   */
  exportLogs(filename?: string) {
    if (this.logs.length === 0) {
      console.warn('[Logger] 没有日志可导出')
      return
    }

    const now = new Date()
    const defaultName = `staraxis-frontend-${now.getFullYear()}${String(now.getMonth() + 1).padStart(2, '0')}${String(now.getDate()).padStart(2, '0')}-${String(now.getHours()).padStart(2, '0')}${String(now.getMinutes()).padStart(2, '0')}.log`

    const content = this.logs
      .map((log) => {
        const d = new Date(log.timestamp)
        const timeStr = `${d.getFullYear()}-${(d.getMonth()+1).toString().padStart(2,'0')}-${d.getDate().toString().padStart(2,'0')} ${d.getHours().toString().padStart(2,'0')}:${d.getMinutes().toString().padStart(2,'0')}:${d.getSeconds().toString().padStart(2,'0')}.${d.getMilliseconds().toString().padStart(3,'0')}`
        return `[${timeStr}] [${log.level.toUpperCase()}] [${log.tag}] ${log.message}`
      })
      .join('\n')

    const blob = new Blob([content], { type: 'text/plain;charset=utf-8' })
    const url = URL.createObjectURL(blob)

    const a = document.createElement('a')
    a.href = url
    a.download = filename || defaultName
    document.body.appendChild(a)
    a.click()
    document.body.removeChild(a)

    URL.revokeObjectURL(url)

    console.log(`[Logger] 已导出 ${this.logs.length} 条日志到 ${filename || defaultName}`)
  }

  /**
   * 自动导出日志（页面卸载前）喵。
   */
  enableAutoExport() {
    window.addEventListener('beforeunload', () => {
      if (this.logs.length > 0) {
        this.exportLogs('staraxis-frontend-auto.log')
      }
    })
  }
}

export const logger = new Logger()

/**
 * 便捷函数：记录移动命令相关日志喵。
 */
export function logMoveShip(shipId: number, targetX: number, targetY: number, stage: string) {
  logger.info('MoveShip-Trace', `${stage} ship=${shipId} 目标=(${Math.round(targetX)},${Math.round(targetY)}) 时间=${performance.now().toFixed(0)}ms`)
}

/**
 * @file useRtsRightClickCommand.ts
 *
 * @description
 * RTS 右键命令入口（右键点击 → 命令意图）。
 *
 * 说明：
 * - 当前阶段只负责“右键触发”的输入逻辑与坐标换算，并在存在选中实体时产出命令意图喵。
 * - 命令发出后可立即显示目标点和路径标记，但不能在这里直接改实体权威位置喵。
 * - 真正命令状态由 `command_result`（命令结果消息）驱动，下层发送器只负责 transport ack（传输确认）记录喵。
 */

import type { WorldRenderer as ThreeWorldRenderer } from '../../../rendering/worldRenderManager'
import type { EntitySnapshot } from '../../../net/snapshotWs'

export type RtsCommandIntent =
    | {
        type: 'Move'
        unitIds: number[]
        targetWorldGU: { x: number; y: number }
    }

export type CommandSender = (intent: RtsCommandIntent, selectedEntities: EntitySnapshot[]) => Promise<void>

function clientToWorldGU(renderer: ThreeWorldRenderer, canvasRect: DOMRect, clientX: number, clientY: number) {
    const localX = clientX - canvasRect.left
    const localY = clientY - canvasRect.top
    const cx = canvasRect.width / 2
    const cy = canvasRect.height / 2

    const worldX = renderer.cameraWorldPosGU.x + (localX - cx) * renderer.zoom.value
    const worldY = renderer.cameraWorldPosGU.y - (localY - cy) * renderer.zoom.value

    return { x: worldX, y: worldY }
}

export function useRtsRightClickCommand(opts: {
    getRenderer: () => ThreeWorldRenderer | null
    getSelectedIds: () => number[]
    getSelectedEntities: () => EntitySnapshot[]
    onCommandIntent?: (intent: RtsCommandIntent) => void
    sendCommand?: CommandSender
}) {
    function onPointerDown(e: PointerEvent) {
        const eventTime = e.timeStamp
        const processStart = performance.now()

        if (e.button !== 2) return

        // 禁止浏览器/插件默认行为（尽力）
        e.preventDefault()

        const selected = opts.getSelectedIds()
        if (!selected || selected.length === 0) {
            console.log(`[RightClick-Delay] 无选中实体，跳过处理 延迟=${(processStart - eventTime).toFixed(2)}ms`)
            return
        }

        const r = opts.getRenderer()
        if (!r) return

        const rect = (e.currentTarget as HTMLElement).getBoundingClientRect()
        const targetWorldGU = clientToWorldGU(r, rect, e.clientX, e.clientY)

        const processEnd = performance.now()
        const totalDelay = processEnd - eventTime
        const processTime = processEnd - processStart

        console.log(`[RightClick-Delay] 坐标计算完成 目标=(${targetWorldGU.x.toFixed(0)},${targetWorldGU.y.toFixed(0)}) 总延迟=${totalDelay.toFixed(2)}ms 处理耗时=${processTime.toFixed(2)}ms`)

        const intent: RtsCommandIntent = {
            type: 'Move',
            unitIds: [...selected],
            targetWorldGU,
        }

        // 优先使用新的命令发送器喵
        const selectedEntities = opts.getSelectedEntities()
        if (opts.sendCommand) {
            const sendStart = performance.now()
            opts.sendCommand(intent, selectedEntities).then(() => {
                const sendEnd = performance.now()
                console.log(`[RightClick-Delay] 命令发送完成 总延迟=${(sendEnd - eventTime).toFixed(2)}ms 发送耗时=${(sendEnd - sendStart).toFixed(2)}ms`)
            }).catch(console.error)
        }

        opts.onCommandIntent?.(intent)
    }

    return { onPointerDown }
}

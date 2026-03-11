/**
 * @file useRtsRightClickCommand.ts
 *
 * @description
 * RTS 右键命令入口（右键点击 → 命令意图）。
 *
 * 说明：
 * - 当前阶段仅实现“右键触发”的输入逻辑与坐标换算，并在存在选中实体时产出命令意图。
 * - 由于当前项目尚未接入实体系统，通常不会有 `selectedIds`，因此不会触发命令。
 * - 未来接入后端 Command 时，可在 `issueMoveCommand` 处将命令通过 WS/HTTP 发往服务端。
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
        if (e.button !== 2) return

        // 禁止浏览器/插件默认行为（尽力）
        e.preventDefault()

        const selected = opts.getSelectedIds()
        if (!selected || selected.length === 0) {
            return
        }

        const r = opts.getRenderer()
        if (!r) return

        const rect = (e.currentTarget as HTMLElement).getBoundingClientRect()
        const targetWorldGU = clientToWorldGU(r, rect, e.clientX, e.clientY)

        const intent: RtsCommandIntent = {
            type: 'Move',
            unitIds: [...selected],
            targetWorldGU,
        }

        // 优先使用新的命令发送器喵
        const selectedEntities = opts.getSelectedEntities()
        if (opts.sendCommand) {
            opts.sendCommand(intent, selectedEntities).catch(console.error)
        }

        opts.onCommandIntent?.(intent)
    }

    return { onPointerDown }
}

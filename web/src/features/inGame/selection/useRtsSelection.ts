/**
 * @file useRtsSelection.ts
 *
 * @description
 * RTS 选择系统（左键点击/框选）。
 *
 * 说明：
 * - 当前阶段仅实现“选择框 UI + 选择集状态机”，不依赖真实实体数据。
 * - 右键指令只在存在选中实体时生效；由于当前没有实体，本模块会始终产生空选择集。
 * - 未来接入实体系统后，只需要在 `commitSelection` 阶段用 worldAabb 过滤实体即可。
 *
 * @usage
 * - 绑定画布容器的 pointerdown/move/up（左键）到 `onPointerDown/Move/Up`。
 * - 渲染层通过 `selectionRect` 显示框选矩形。
 */

import { computed, ref } from 'vue'

export type ScreenRect = {
    x: number
    y: number
    w: number
    h: number
}

export type SelectableEntity = {
    id: number
    type: 'STAR' | 'PLANET' | 'SHIP'
    worldPosGU: { x: number; y: number }
}

export function useRtsSelection(opts: {
    getEntities: () => SelectableEntity[]
    worldToClient: (world: { x: number; y: number }) => { x: number; y: number }
    onSelectionChange?: (selectedIds: number[]) => void
}) {
    const isSelecting = ref(false)
    const startClient = ref<{ x: number; y: number } | null>(null)
    const currentClient = ref<{ x: number; y: number } | null>(null)

    const selectedIds = ref<number[]>([])

    const selectionRect = computed<ScreenRect | null>(() => {
        if (!isSelecting.value) return null
        const a = startClient.value
        const b = currentClient.value
        if (!a || !b) return null

        const x1 = Math.min(a.x, b.x)
        const y1 = Math.min(a.y, b.y)
        const x2 = Math.max(a.x, b.x)
        const y2 = Math.max(a.y, b.y)

        return { x: x1, y: y1, w: x2 - x1, h: y2 - y1 }
    })

    function clearSelection() {
        selectedIds.value = []
    }

    function beginSelection(e: PointerEvent) {
        isSelecting.value = true
        startClient.value = { x: e.clientX, y: e.clientY }
        currentClient.value = { x: e.clientX, y: e.clientY }
    }

    function updateSelection(e: PointerEvent) {
        if (!isSelecting.value) return
        currentClient.value = { x: e.clientX, y: e.clientY }
    }

    function commitSelection() {
        const rect = selectionRect.value
        if (!rect) {
            selectedIds.value = []
        } else {
            const x1 = rect.x
            const y1 = rect.y
            const x2 = rect.x + rect.w
            const y2 = rect.y + rect.h

            // 检查框选范围大小，小于阈值视为点击（单选）
            const isClick = rect.w < 5 && rect.h < 5

            const hits: number[] = []
            for (const e of opts.getEntities()) {
                const p = opts.worldToClient(e.worldPosGU)
                if (isClick) {
                    // 点击模式：使用圆形碰撞检测（20像素半径）
                    const dx = p.x - (x1 + x2) / 2
                    const dy = p.y - (y1 + y2) / 2
                    if (dx * dx + dy * dy <= 400) { // 20^2 = 400
                        hits.push(e.id)
                        break // 点击只选第一个
                    }
                } else {
                    // 框选模式：矩形碰撞检测
                    if (p.x >= x1 && p.x <= x2 && p.y >= y1 && p.y <= y2) {
                        hits.push(e.id)
                    }
                }
            }
            selectedIds.value = hits
        }

        isSelecting.value = false
        startClient.value = null
        currentClient.value = null

        // 触发选择变化回调喵
        opts.onSelectionChange?.(selectedIds.value)
    }

    function cancelSelection() {
        isSelecting.value = false
        startClient.value = null
        currentClient.value = null
    }

    function onPointerDown(e: PointerEvent) {
        if (e.button !== 0) return
        beginSelection(e)
        try {
            ; (e.currentTarget as HTMLElement).setPointerCapture(e.pointerId)
        } catch {
        }
        e.preventDefault()
    }

    function onPointerMove(e: PointerEvent) {
        if (!isSelecting.value) return
        updateSelection(e)
        e.preventDefault()
    }

    function onPointerUp(e: PointerEvent) {
        if (e.button !== 0) return
        if (!isSelecting.value) return

        try {
            ; (e.currentTarget as HTMLElement).releasePointerCapture(e.pointerId)
        } catch {
        }

        commitSelection()
        e.preventDefault()
    }

    return {
        isSelecting,
        selectionRect,
        selectedIds,
        clearSelection,
        onPointerDown,
        onPointerMove,
        onPointerUp,
        cancelSelection,
    }
}

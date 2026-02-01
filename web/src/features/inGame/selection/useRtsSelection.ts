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

export function useRtsSelection(opts: {
    getEntities: () => Array<{ id: number; type: 'STAR' | 'PLANET'; worldPosGU: { x: number; y: number } }>
    worldToClient: (world: { x: number; y: number }) => { x: number; y: number }
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

            const hits: number[] = []
            for (const e of opts.getEntities()) {
                const p = opts.worldToClient(e.worldPosGU)
                if (p.x >= x1 && p.x <= x2 && p.y >= y1 && p.y <= y2) {
                    hits.push(e.id)
                }
            }
            selectedIds.value = hits
        }

        isSelecting.value = false
        startClient.value = null
        currentClient.value = null
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

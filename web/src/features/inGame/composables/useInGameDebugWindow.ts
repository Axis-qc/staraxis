import { ref } from 'vue'

/**
 * 判断调试模式是否开启喵。
 * 优先级：URL参数 ?debug=1 > localStorage sa.debugMode > import.meta.env.DEV
 */
function isDebugEnabled(): boolean {
    // URL 参数优先
    if (typeof window !== 'undefined') {
        try {
            const params = new URLSearchParams(window.location.search)
            if (params.has('debug')) {
                return params.get('debug') !== '0'
            }
        } catch { /* ignore */ }
        // localStorage 持久化开关
        try {
            if (localStorage.getItem('sa.debugMode') === 'true') return true
            if (localStorage.getItem('sa.debugMode') === 'false') return false
        } catch { /* ignore */ }
    }
    // 默认开启（URL ?debug=0 或 localStorage sa.debugMode=false 可关闭）
    return true
}

export function useInGameDebugWindow(hub: any) {
    const isDebugHudEnabled = isDebugEnabled()
    const isDebugWindowVisible = ref(false)
    const debugWindowPos = ref({ x: 0, y: 0 })
    const isDraggingDebugWindow = ref(false)
    const debugDragOffset = ref({ x: 0, y: 0 })

    function getDebugWindowSize() {
        return {
            w: Math.min(380, Math.max(320, window.innerWidth - 16)),
            h: Math.min(860, Math.max(360, window.innerHeight - 16)),
        }
    }

    function getClampedDebugWindowPos(next: { x: number; y: number }, size: { w: number; h: number }) {
        const margin = 8
        const maxX = Math.max(margin, window.innerWidth - size.w - margin)
        const maxY = Math.max(margin, window.innerHeight - size.h - margin)
        return {
            x: Math.min(maxX, Math.max(margin, next.x)),
            y: Math.min(maxY, Math.max(margin, next.y)),
        }
    }

    function centerDebugWindow() {
        const size = getDebugWindowSize()
        const x = (window.innerWidth - size.w) / 2
        const y = (window.innerHeight - size.h) / 2
        debugWindowPos.value = getClampedDebugWindowPos({ x, y }, size)
    }

    function toggleDebugWindow() {
        if (!isDebugHudEnabled) return
        const next = !isDebugWindowVisible.value
        isDebugWindowVisible.value = next
        hub.setPerformanceTracking(next)
        if (next && debugWindowPos.value.x === 0 && debugWindowPos.value.y === 0) {
            centerDebugWindow()
        }
    }

    function onDebugWindowHeaderPointerDown(e: PointerEvent, el: HTMLElement | null) {
        if (!isDebugWindowVisible.value || !el) return

        isDraggingDebugWindow.value = true
        const rect = el.getBoundingClientRect()
        debugDragOffset.value = { x: e.clientX - rect.left, y: e.clientY - rect.top }

        try {
            (e.currentTarget as HTMLElement).setPointerCapture(e.pointerId)
        } catch { }

        e.preventDefault()
    }

    function onDebugWindowHeaderPointerMove(e: PointerEvent) {
        if (!isDraggingDebugWindow.value) return

        const next = { x: e.clientX - debugDragOffset.value.x, y: e.clientY - debugDragOffset.value.y }
        debugWindowPos.value = getClampedDebugWindowPos(next, getDebugWindowSize())

        e.preventDefault()
    }

    function onDebugWindowHeaderPointerUp(e: PointerEvent) {
        if (!isDraggingDebugWindow.value) return
        isDraggingDebugWindow.value = false
        try {
            (e.currentTarget as HTMLElement).releasePointerCapture(e.pointerId)
        } catch { }
        e.preventDefault()
    }

    return {
        isDebugHudEnabled,
        isDebugWindowVisible,
        debugWindowPos,
        toggleDebugWindow,
        onDebugWindowHeaderPointerDown,
        onDebugWindowHeaderPointerMove,
        onDebugWindowHeaderPointerUp
    }
}

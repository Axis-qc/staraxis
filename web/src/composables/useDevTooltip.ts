/**
 * @file useDevTooltip.ts
 *
 * @description
 * 一个用于管理全局、非交互式“开发中”提示框的 Composable。
 * 它提供了一个集中的状态和方法，以便在应用的任何地方显示/隐藏提示框。
 *
 * @usage
 * ```ts
 * import { useDevTooltip } from '@/composables/useDevTooltip'
 *
 * const tooltip = useDevTooltip()
 *
 * function handleClick(event: MouseEvent) {
 *   tooltip.show('该功能正在开发中', event)
 * }
 * ```
 *
 * @provides
 * - 一个响应式的 `state` 对象，包含提示框的可见性、消息和坐标。
 * - 一个 `show(message, event)` 方法，用于在鼠标光标位置显示提示框。
 * - 一个 `hide()` 方法，用于手动隐藏提示框。
 * - 短暂延迟后自动隐藏提示框的功能。
 *
 * @api
 * - `state`: 一个只读的响应式对象 `{ visible: boolean, message: string, x: number, y: number }`。
 * - `show(message: string, event: MouseEvent)`: 显示提示框。
 * - `hide()`: 隐藏提示框。
 *
 * @resources
 * - 无。
 *
 * @potential_issues
 * - 由于这是一个全局单例，它一次只能管理一个提示框。
 */
import { reactive, readonly } from 'vue'

const state = reactive({
    visible: false,
    message: '',
    x: 0,
    y: 0,
})

let timeoutId: number | null = null

function show(message: string, event: MouseEvent) {
    if (timeoutId) {
        clearTimeout(timeoutId)
    }

    state.message = message
    state.x = event.clientX
    state.y = event.clientY
    state.visible = true

    timeoutId = window.setTimeout(() => {
        hide()
    }, 1500) // Auto-hide after 1.5 seconds
}

function hide() {
    state.visible = false
    if (timeoutId) {
        clearTimeout(timeoutId)
        timeoutId = null
    }
}

export function useDevTooltip() {
    return {
        state: readonly(state),
        show,
        hide,
    }
}

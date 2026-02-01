/**
 * @file useInGameInputController.ts
 *
 * @description
 * 游戏输入控制器（键盘输入 → GameAction）。
 *
 * 目标：
 * - 监听键盘事件，并根据可配置的按键映射表生成 `GameAction`。
 * - 将动作通过回调 `onAction(action)` 抛给上层（InGameView）执行，避免在控制器内产生副作用。
 *
 * 说明：
 * - 支持未来自定义按键：通过传入自定义 `keymap` 覆盖默认映射。
 * - 内置“输入框聚焦忽略”的规则，避免快捷键干扰文本输入。
 *
 * @usage
 * ```ts
 * const controller = useInGameInputController({ onAction })
 * controller.attach()
 * // ...
 * controller.detach()
 * ```
 */

import type { GameAction, InGameBottomTab } from './gameActions'

export type InGameKeymap = {
    toggleEscMenu?: string[]
    toggleDebugWindow?: string[]
    toggleBottomTab?: Partial<Record<InGameBottomTab, string[]>>
}

const DEFAULT_KEYMAP: Required<InGameKeymap> = {
    toggleEscMenu: ['Escape'],
    toggleDebugWindow: ['o', 'O'],
    toggleBottomTab: {},
}

function isEditableTarget(e: KeyboardEvent) {
    const t = e.target as HTMLElement | null
    if (!t) return false
    const tag = t.tagName
    if (tag === 'INPUT' || tag === 'TEXTAREA' || tag === 'SELECT') return true
    if ((t as any).isContentEditable) return true
    return false
}

export function useInGameInputController(opts: {
    onAction: (action: GameAction) => void
    keymap?: InGameKeymap
}) {
    const keymap: Required<InGameKeymap> = {
        ...DEFAULT_KEYMAP,
        ...opts.keymap,
        toggleBottomTab: {
            ...DEFAULT_KEYMAP.toggleBottomTab,
            ...(opts.keymap?.toggleBottomTab ?? {}),
        },
    }

    const onKeyDown = (e: KeyboardEvent) => {
        if (isEditableTarget(e)) return

        if (keymap.toggleEscMenu.includes(e.key)) {
            e.preventDefault()
            opts.onAction({ type: 'ToggleEscMenu' })
            return
        }

        if (keymap.toggleDebugWindow.includes(e.key)) {
            e.preventDefault()
            opts.onAction({ type: 'ToggleDebugWindow' })
            return
        }

        for (const tab of Object.keys(keymap.toggleBottomTab) as InGameBottomTab[]) {
            const keys = keymap.toggleBottomTab[tab]
            if (!keys) continue
            if (keys.includes(e.key)) {
                e.preventDefault()
                opts.onAction({ type: 'ToggleBottomTab', tab })
                return
            }
        }
    }

    function attach() {
        window.addEventListener('keydown', onKeyDown)
    }

    function detach() {
        window.removeEventListener('keydown', onKeyDown)
    }

    return { attach, detach, keymap }
}

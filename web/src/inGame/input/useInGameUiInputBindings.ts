/**
 * @file useInGameUiInputBindings.ts
 *
 * @description
 * 游戏 UI 输入绑定器（按钮点击 → GameAction）。
 *
 * 目标：
 * - 将 UI 组件的点击事件统一映射为 `GameAction`。
 * - 保持 InGameView 作为“动作执行者”，绑定器本身不产生副作用。
 * - 便于后续扩展：更多 UI（实体面板、舰船、特效等）复用同一套动作分发口径。
 *
 * @usage
 * ```ts
 * const ui = useInGameUiInputBindings({ onAction })
 * // ui.onClickSave(), ui.onSelectTab('tech')...
 * ```
 */

import type { GameAction, InGameBottomTab } from './gameActions'

export function useInGameUiInputBindings(opts: { onAction: (action: GameAction) => void }) {
    function onResume() {
        opts.onAction({ type: 'ToggleEscMenu' })
    }

    function onClickSave() {
        opts.onAction({ type: 'RequestSaveGame' })
    }

    function onClickLoad() {
        opts.onAction({ type: 'RequestLoadGame' })
    }

    function onClickQuit() {
        opts.onAction({ type: 'QuitToMainMenu' })
    }

    function onSelectBottomTab(tab: InGameBottomTab) {
        opts.onAction({ type: 'ToggleBottomTab', tab })
    }

    function onBuild(buildId: string) {
        void buildId
        opts.onAction({ type: 'ShowDevelopingHint' })
    }

    function onDeveloping() {
        opts.onAction({ type: 'ShowDevelopingHint' })
    }

    return {
        onResume,
        onClickSave,
        onClickLoad,
        onClickQuit,
        onSelectBottomTab,
        onBuild,
        onDeveloping,
    }
}

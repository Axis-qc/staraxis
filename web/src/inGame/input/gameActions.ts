/**
 * @file gameActions.ts
 *
 * @description
 * 游戏输入系统的统一动作定义（Game Actions）。
 *
 * 目标：
 * - 将“键盘输入/鼠标输入/按钮点击”等不同来源的输入，统一映射为一套可分发的动作（`GameAction`）。
 * - 便于后续实现自定义按键：只需替换/扩展“按键 → 动作”的映射表，而无需改动各 UI 或系统逻辑。
 *
 * 说明：
 * - 本模块只定义动作类型与相关辅助类型，不包含具体副作用。
 * - 动作的最终执行由 InGameView（或未来的 InGame 系统调度器）负责。
 */

export type InGameBottomTab = 'development' | 'military' | 'tech' | 'domestic' | 'diplomacy'

export type GameAction =
    | { type: 'ToggleEscMenu' }
    | { type: 'ToggleDebugWindow' }
    | { type: 'ToggleBottomTab'; tab: InGameBottomTab }
    | { type: 'RequestSaveGame' }
    | { type: 'RequestLoadGame' }
    | { type: 'QuitToMainMenu' }
    | { type: 'ShowDevelopingHint' }

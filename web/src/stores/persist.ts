import type { PiniaPluginContext } from 'pinia'

/**
 * Pinia 持久化插件（sessionStorage）
 *
 * @param context Pinia 插件上下文
 *
 * 说明：
 * - 自动将 store 的状态保存到 sessionStorage。
 * - 在应用启动时，从 sessionStorage 恢复状态。
 * - 仅对 ID 为 'auth' 的 store 生效。
 */
export function persistAuthSessionStorage({ store }: PiniaPluginContext) {
    // 只对 'auth' store 生效
    if (store.$id !== 'auth') {
        return
    }

    // 1. 从 sessionStorage 恢复状态（Hydrate）
    const storedState = sessionStorage.getItem(store.$id)
    if (storedState) {
        try {
            store.$patch(JSON.parse(storedState))
        } catch (e) {
            console.error('Failed to parse stored auth state', e)
            sessionStorage.removeItem(store.$id) // 解析失败则移除损坏的数据
        }
    }

    // 2. 订阅 store 变化，并保存到 sessionStorage
    store.$subscribe((_mutation, state) => {
        // 保存核心认证信息和游戏状态数据喵
        const stateToPersist = {
            username: state.username,
            playerId: state.playerId,
            token: state.token,
            selectedNationId: state.selectedNationId, // 保存玩家选择的国家ID喵
        }
        sessionStorage.setItem(store.$id, JSON.stringify(stateToPersist))
    })
}

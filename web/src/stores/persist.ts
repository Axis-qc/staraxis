import type { PiniaPluginContext } from 'pinia'

/**
 * 需要持久化的 store 配置喵。
 * 每个条目定义了 store ID 和需要保存的字段列表喵。
 */
const PERSISTENT_STORES: Record<string, string[]> = {
    auth: ['username', 'playerId', 'token', 'selectedNationId'],
    'world-session': ['selectedWorldId', 'selectedWorldName', 'playerWorldState', 'joinedAtEpochMs'],
}

/**
 * Pinia 持久化插件（sessionStorage）
 *
 * @param context Pinia 插件上下文
 *
 * 说明：
 * - 自动将指定 store 的状态保存到 sessionStorage。
 * - 在应用启动时，从 sessionStorage 恢复状态。
 * - 支持 auth 和 world-session 两个 store 喵。
 */
export function persistSessionStorage({ store }: PiniaPluginContext) {
    const fields = PERSISTENT_STORES[store.$id]
    if (!fields) {
        return
    }

    // 1. 从 sessionStorage 恢复状态（Hydrate）喵
    const storedState = sessionStorage.getItem(store.$id)
    if (storedState) {
        try {
            store.$patch(JSON.parse(storedState))
        } catch (e) {
            console.error(`Failed to parse stored state for ${store.$id}`, e)
            sessionStorage.removeItem(store.$id) // 解析失败则移除损坏的数据喵
        }
    }

    // 2. 订阅 store 变化，并保存到 sessionStorage 喵
    store.$subscribe((_mutation, state) => {
        const stateToPersist: Record<string, unknown> = {}
        for (const field of fields) {
            stateToPersist[field] = (state as Record<string, unknown>)[field]
        }
        sessionStorage.setItem(store.$id, JSON.stringify(stateToPersist))
    })
}

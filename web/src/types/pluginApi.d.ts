/**
 * StarAxis Plugin API 类型声明
 *
 * 使用方式：在浏览器控制台直接调用 window.StarAxisAPI.xxx
 */

import type { AuthState } from '../stores/auth'
import type { PlayerWorldState } from '../stores/worldSession'

interface StarAxisAPI {
    /** 前端状态存储 */
    stores: {
        /** 认证状态（username, playerId, token, role, selectedNationId） */
        auth: AuthState & {
            isLoggedIn: boolean
            setAuth(payload: {
                username: string
                playerId: string
                token?: string
                role?: string
                selectedNationId?: string
            }): void
            clear(): void
        }
        /** 世界会话状态 */
        worldSession: {
            selectedWorldId: string
            selectedWorldName: string
            playerWorldState: PlayerWorldState
            joinedAtEpochMs: number
            setSelectedWorld(worldId: string, worldName: string): void
            setPlayerWorldState(state: PlayerWorldState): void
            markJoinedNow(): void
            clear(): void
        }
    }
    /** 获取本地可见世界实例（包含所有实体数据） */
    getLocalVisibleWorld(): any
    /** API版本号 */
    version: string
}

declare global {
    interface Window {
        StarAxisAPI?: StarAxisAPI
    }
}

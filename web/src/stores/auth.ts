import { defineStore } from 'pinia'

export type AuthState = {
    username: string
    playerId: string
    token: string
}

/**
 * useAuthStore
 *
 * 说明：
 * - 用于“页面未关闭”的会话内全局状态。
 * - 通过 Pinia 插件持久化到 sessionStorage，刷新不丢。
 * - 退出登录时调用 clear() 清空。
 */
export const useAuthStore = defineStore('auth', {
    state: (): AuthState => ({
        username: '',
        playerId: '',
        token: '',
    }),
    getters: {
        isLoggedIn: (s) => !!s.username && !!s.playerId,
    },
    actions: {
        setAuth(payload: { username: string; playerId: string; token?: string }) {
            this.username = payload.username
            this.playerId = payload.playerId
            this.token = payload.token || ''
        },
        clear() {
            this.username = ''
            this.playerId = ''
            this.token = ''
        },
    },
})

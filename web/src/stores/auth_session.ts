import { defineStore } from 'pinia'

export type AuthSessionState = {
    username: string
    playerId: string
    token: string
}

/**
 * useAuthSessionStore
 *
 * 说明：
 * - 仅用于“页面未关闭”的会话内全局状态。
 * - 不做持久化（不写 localStorage）。
 * - 退出登录时调用 clear() 清空。
 */
export const useAuthSessionStore = defineStore('authSession', {
    state: (): AuthSessionState => ({
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

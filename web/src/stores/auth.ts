import { defineStore } from 'pinia'

export type AuthState = {
    username: string
    playerId: string
    token: string
    role: string
    selectedNationId: string  // 新增：玩家选择的国家ID
}

/**
 * useAuthStore（前端认证存储）
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
        role: 'USER',
        selectedNationId: '',  // 新增：默认空字符串
    }),
    getters: {
        isLoggedIn: (s) => !!s.username && !!s.playerId,
    },
    actions: {
        setAuth(payload: { username: string; playerId: string; token?: string; role?: string; selectedNationId?: string }) {
            this.username = payload.username
            this.playerId = payload.playerId
            this.token = payload.token || ''
            this.role = payload.role || 'USER'
            this.selectedNationId = payload.selectedNationId || ''  // 新增
        },
        clear() {
            this.username = ''
            this.playerId = ''
            this.token = ''
            this.role = 'USER'
            this.selectedNationId = ''  // 新增
        },
        setSelectedNationId(nationId: string) {
            this.selectedNationId = nationId
        },
    },
})

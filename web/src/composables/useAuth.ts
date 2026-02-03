import { reactive, ref, onMounted, watch } from 'vue'
import {
    authLogin,
    authLogout,
    authMe,
    authRegister,
    authSetGameId,
    type AuthMe,
} from '../services/backend'
import { useAuthStore } from '../stores/auth'

/**
 * @description 管理用户认证、登录、注册和 Game ID 的 Composable。
 */
export function useAuth() {
    const authStore = useAuthStore()

    // --- 响应式状态 ---
    const auth = reactive({
        isLoggedIn: false,
        token: localStorage.getItem('sa.token') || '',
        playerId: '',
        username: '',
        gameId: '',
        role: 'USER',
    })

    const loginForm = reactive({
        username: '',
        password: '',
        error: '',
        loading: false,
    })

    const gameIdInput = ref('')
    const gameIdSaveState = ref<'idle' | 'saving' | 'saved' | 'error'>('idle')

    // --- 私有方法 ---
    function storeToken(token: string) {
        auth.token = token
        localStorage.setItem('sa.token', token)
    }

    function clearToken() {
        auth.token = ''
        localStorage.removeItem('sa.token')
    }

    function updateAuthState(data: AuthMe) {
        auth.isLoggedIn = data.ok
        auth.playerId = data.playerId || ''
        auth.username = data.username || ''
        auth.gameId = data.gameId || ''
        auth.role = data.role || 'USER'
        gameIdInput.value = auth.gameId // 同步输入框
    }

    // --- 将 composable 状态同步到 Pinia（全站可读）---
    watch(
        () => ({
            isLoggedIn: auth.isLoggedIn,
            username: auth.username,
            playerId: auth.playerId,
            token: auth.token,
            role: auth.role,
        }),
        (s) => {
            if (s.isLoggedIn && s.username && s.playerId) {
                authStore.setAuth({ username: s.username, playerId: s.playerId, token: s.token, role: s.role })
            } else {
                authStore.clear()
            }
        },
        { immediate: true }
    )

    // --- 暴露给外部的方法 ---
    async function checkAuth() {
        if (!auth.token) {
            updateAuthState({ ok: false })
            return
        }
        loginForm.loading = true
        loginForm.error = ''
        try {
            const data = await authMe()
            updateAuthState(data)
        } catch {
            clearToken()
            updateAuthState({ ok: false })
        } finally {
            loginForm.loading = false
        }
    }

    async function doLogin() {
        loginForm.loading = true
        loginForm.error = ''
        try {
            const data = await authLogin(loginForm.username, loginForm.password)
            if (data.token) {
                storeToken(data.token)
                await checkAuth()
            } else {
                throw new Error('Login did not return a token.')
            }
        } catch (e) {
            loginForm.error = (e as Error).message
        } finally {
            loginForm.loading = false
        }
    }

    async function doRegister() {
        loginForm.loading = true
        loginForm.error = ''
        try {
            await authRegister(loginForm.username, loginForm.password)
            await doLogin() // 注册后自动登录
        } catch (e) {
            loginForm.error = (e as Error).message
        } finally {
            loginForm.loading = false
        }
    }

    async function doLogout() {
        loginForm.loading = true
        try {
            await authLogout()
        } catch (e) {
            console.error('Logout failed:', e)
        } finally {
            clearToken()
            updateAuthState({ ok: false })
            loginForm.loading = false
        }
    }

    async function saveGameId() {
        gameIdSaveState.value = 'saving'
        try {
            await authSetGameId(gameIdInput.value)
            auth.gameId = gameIdInput.value
            gameIdSaveState.value = 'saved'
        } catch {
            gameIdSaveState.value = 'error'
        }
        // 1.6秒后重置保存状态
        setTimeout(() => {
            if (gameIdSaveState.value !== 'saving') {
                gameIdSaveState.value = 'idle'
            }
        }, 1600)
    }

    // --- 生命周期钩子 ---
    onMounted(() => {
        checkAuth()
    })

    // --- 返回所有需要暴露的状态和方法 ---
    return {
        auth,
        loginForm,
        gameIdInput,
        gameIdSaveState,
        checkAuth,
        doLogin,
        doRegister,
        doLogout,
        saveGameId,
    }
}

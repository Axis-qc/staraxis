import { ref, onMounted, onUnmounted, computed } from 'vue'
import { useI18n } from 'vue-i18n'
import { fetchStatus, createWsClient, type BackendStatus, type WsState } from '../services/backend'

/**
 * @description 管理后端状态获取和 WebSocket 连接的 Composable。
 */
export function useServerStatus() {
    const { t } = useI18n()

    // --- 响应式状态 ---
    const status = ref<BackendStatus | null>(null)
    const statusError = ref<string | null>(null)
    const wsState = ref<WsState>('disconnected')
    const wsError = ref<string | null>(null)

    // --- WebSocket 客户端实例 ---
    const wsClient = createWsClient('/ws')
    let offState: (() => void) | null = null
    let offErr: (() => void) | null = null

    // --- 计算属性 ---
    const wsStateText = computed(() => {
        switch (wsState.value) {
            case 'connected':
                return t('mainMenu.status.connected')
            case 'connecting':
                return t('mainMenu.status.connecting')
            case 'disconnected':
                return t('mainMenu.status.disconnected')
            default:
                return t('mainMenu.status.error')
        }
    })

    const wsTagKind = computed(() => {
        if (wsState.value === 'connected') return 'ok'
        if (wsState.value === 'error') return 'error'
        return 'warn'
    })

    // --- 暴露给外部的方法 ---
    async function refreshStatus() {
        statusError.value = null
        try {
            status.value = await fetchStatus()
        } catch (e) {
            status.value = null
            statusError.value = (e as Error).message
        }
    }

    // --- 生命周期钩子 ---
    onMounted(() => {
        // 注册 WebSocket 事件监听器
        offState = wsClient.onStateChange((s) => (wsState.value = s))
        offErr = wsClient.onError((e) => (wsError.value = e))

        // 开始连接并定期刷新状态
        wsClient.connect()
        refreshStatus()
        const statusTimer = window.setInterval(refreshStatus, 3000)

        // 组件卸载时清理
        onUnmounted(() => {
            window.clearInterval(statusTimer)
            offState?.()
            offErr?.()
            wsClient.disconnect()
        })
    })

    return {
        status,
        statusError,
        wsState,
        wsError,
        wsStateText,
        wsTagKind,
        refreshStatus,
    }
}

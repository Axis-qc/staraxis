/**
 * @file ws.ts
 *
 * @description
 * 管理应用的全局 WebSocket 连接。
 * 该文件导出一个单例 `wsClient` 实例，以确保在应用的整个生命周期内保持持久连接，
 * 防止在视图之间导航时连接断开。
 *
 * @usage
 * ```ts
 * import { wsClient } from '@/services/ws'
 *
 * // 在 App.vue (或其他根组件) 中
 * onMounted(() => {
 *   wsClient.connect()
 * })
 *
 * // 在任何其他组件/Composable 中
 * wsClient.onMessage(message => {
 *   console.log('接收到:', message)
 * })
 * ```
 *
 * @provides
 * - 为整个应用提供一个单一、共享的 `WsClient` 实例。
 * - 提供连接、断开、发送消息以及监听状态变化、消息和错误的方法。
 *
 * @api
 * - `wsClient`: 导出的 `WsClient` 单例实例。
 *
 * @resources
 * - 浏览器原生的 `WebSocket` API。
 *
 * @potential_issues
 * - 由于这是一个全局单例，应用的所有部分共享同一个连接。消息处理逻辑
 *   在设计时应考虑到这一点。
 */
export type WsState = 'disconnected' | 'connecting' | 'connected' | 'error'

export type WsClient = {
    getState: () => WsState
    getUrl: () => string
    connect: () => void
    disconnect: () => void
    sendText: (text: string) => void
    updateVisibleSectors: (sectors: { q: number; r: number }[]) => void
    updateInterestEntities: (entityIds: number[]) => void
    startSnapshotTickTrace: (durationMs: number) => void
    setNationId: (nationId: string) => void
    subscribeSnapshot: () => void
    unsubscribeSnapshot: () => void
    onStateChange: (cb: (s: WsState) => void) => () => void
    onMessage: (cb: (msg: string) => void) => () => void
    onError: (cb: (err: string) => void) => () => void
}

function createWsClient(path: string = '/ws'): WsClient {
    let ws: WebSocket | null = null
    let state: WsState = 'disconnected'
    let reconnectTimerId = 0
    let shouldReconnect = true
    const stateListeners = new Set<(s: WsState) => void>()
    const msgListeners = new Set<(m: string) => void>()
    const errListeners = new Set<(e: string) => void>()
    const RECONNECT_DELAY_MS = 1500

    function notifyState(s: WsState) {
        state = s
        for (const cb of stateListeners) cb(s)
    }

    function clearReconnectTimer() {
        if (reconnectTimerId) {
            window.clearTimeout(reconnectTimerId)
            reconnectTimerId = 0
        }
    }

    function scheduleReconnect() {
        if (!shouldReconnect || reconnectTimerId || state === 'connected' || state === 'connecting') {
            return
        }

        reconnectTimerId = window.setTimeout(() => {
            reconnectTimerId = 0
            connect()
        }, RECONNECT_DELAY_MS)
    }

    function wsUrl(): string {
        const protocol = window.location.protocol === 'https:' ? 'wss:' : 'ws:'
        const token = localStorage.getItem('sa.token') || ''
        const url = `${protocol}//${window.location.host}${path}`
        return token ? `${url}?token=${encodeURIComponent(token)}` : url
    }

    function connect() {
        if (state === 'connecting' || state === 'connected') return
        shouldReconnect = true
        clearReconnectTimer()
        notifyState('connecting')
        try {
            ws = new WebSocket(wsUrl())
            ws.onopen = () => {
                clearReconnectTimer()
                notifyState('connected')
                // 连接成功后自动发送订阅快照请求喵
            }
            ws.onmessage = (evt) => {
                const text = String(evt.data)

                try {
                    const msg = JSON.parse(text)
                    // 响应服务端应用层心跳喵
                    if (msg.type === 'ping') {
                        ws?.send(JSON.stringify({ type: 'pong' }))
                        return // 心跳包不分发给业务监听器喵
                    }

                    // 处理挤号/被踢消息喵
                    if (msg.type === 'kick') {
                        console.warn('[WS] Kicked from server:', msg.reason)
                        const reasonMap: Record<string, string> = {
                            'new_login': '账号在别处登录，当前连接已断开喵！',
                            'player_disconnected': '玩家连接已断开，AI 会话同步结束喵！'
                        }
                        const alertMsg = reasonMap[msg.reason as string] || '您的账号已从服务器断开喵！'

                        // 延迟弹窗避免干扰正常刷新喵
                        setTimeout(() => {
                            if (state === 'connected') {
                                alert(alertMsg)
                            }
                        }, 100)

                        shouldReconnect = false
                        disconnect()
                        return
                    }
                } catch {
                    // 非 JSON 消息忽略喵
                }

                for (const cb of msgListeners) cb(text)
            }
            ws.onerror = () => {
                notifyState('error')
                for (const cb of errListeners) cb('WebSocket error')
                scheduleReconnect()
            }
            ws.onclose = () => {
                ws = null
                notifyState('disconnected')
                scheduleReconnect()
            }
        } catch (e) {
            ws = null
            notifyState('error')
            for (const cb of errListeners) cb((e as Error).message)
            scheduleReconnect()
        }
    }

    function disconnect() {
        shouldReconnect = false
        clearReconnectTimer()
        try {
            ws?.close()
        } catch {
        }
        ws = null
        notifyState('disconnected')
    }

    function sendText(text: string) {
        if (!ws || state !== 'connected') return
        ws.send(text)
    }

    /**
     * 订阅快照推送喵
     */
    function subscribeSnapshot() {
        sendText(JSON.stringify({ type: 'subscribeSnapshot' }))
    }

    /**
     * 取消订阅快照推送喵
     */
    function unsubscribeSnapshot() {
        sendText(JSON.stringify({ type: 'unsubscribeSnapshot' }))
    }

    /**
     * 更新可见星区喵
     */
    function updateVisibleSectors(sectors: { q: number; r: number }[]) {
        sendText(JSON.stringify({ type: 'updateVisibleSectors', sectors }))
    }

    /**
     * 更新当前快照订阅连接的兴趣实体集合喵。
     */
    function updateInterestEntities(entityIds: number[]) {
        sendText(JSON.stringify({ type: 'updateInterestEntities', entityIds }))
    }

    /**
     * 请求前后端同时开始一段限定时长的快照 Tick 对时录制喵。
     */
    function startSnapshotTickTrace(durationMs: number) {
        sendText(JSON.stringify({ type: 'startSnapshotTickTrace', durationMs }))
    }

    /**
     * 设置玩家所属国家 ID 喵
     */
    function setNationId(nationId: string) {
        sendText(JSON.stringify({ type: 'setNationId', nationId }))
    }

    return {
        getState: () => state,
        getUrl: wsUrl,
        connect,
        disconnect,
        sendText,
        updateVisibleSectors,
        updateInterestEntities,
        startSnapshotTickTrace,
        setNationId,
        subscribeSnapshot,
        unsubscribeSnapshot,
        onStateChange: (cb) => {
            stateListeners.add(cb)
            return () => stateListeners.delete(cb)
        },
        onMessage: (cb) => {
            msgListeners.add(cb)
            return () => msgListeners.delete(cb)
        },
        onError: (cb) => {
            errListeners.add(cb)
            return () => errListeners.delete(cb)
        },
    }
}

// Create and export a single, shared instance of the WebSocket client.
export const wsClient = createWsClient('/ws')

// 账户连接同步管理器喵
if (typeof window !== 'undefined') {
    window.addEventListener('beforeunload', () => {
        try {
            wsClient.disconnect()
        } catch {
        }
    })

    // 监听 storage 事件，当 token 或 auth 变更时自动重连喵
    window.addEventListener('storage', (e) => {
        if (e.key === 'sa.token' || e.key === 'auth') {
            console.log(`[WS] Account context changed (${e.key}), resetting connection喵`)
            wsClient.disconnect()
            // 延迟一会重连，确保新 token 已写入喵
            setTimeout(() => wsClient.connect(), 500)
        }
    })
}

<script setup lang="ts">
/**
 * @file AiAssistantFloatingBall.vue
 *
 * @description
 * 全局 AI 助手浮窗（圆形拖动球 + 鼠标靠近自动展开面板）喵！
 * 
 * @updates
 * - 支持玩家账户绑定，AI使用玩家权限访问数据喵！
 * - 支持自由调节窗口大小（拖拽右下角）喵！
 * - 显示思考过程和 Token 消耗统计喵！
 */

import { computed, onBeforeUnmount, onMounted, ref, nextTick } from 'vue'

const isAiEnabled = ref(localStorage.getItem('sa_settings_ai_enabled') !== 'false')

// 监听存储变化
function syncEnabledState() {
    isAiEnabled.value = localStorage.getItem('sa_settings_ai_enabled') !== 'false'
}

const TRIGGER_RADIUS_PX = 120
const CLOSE_RADIUS_PX = 220
const PANEL_CLOSE_MARGIN_PX = 24
const MIN_PANEL_WIDTH = 320
const MIN_PANEL_HEIGHT = 360
const MAX_PANEL_WIDTH = 800
const MAX_PANEL_HEIGHT = 900

type Point = { x: number; y: number }
type Size = { width: number; height: number }
type TokenUsage = {
    prompt_tokens: number
    completion_tokens: number
    total_tokens: number
}
type ToolCallInfo = {
    name: string
    arguments: Record<string, any>
    result: any
    duration_ms: number
}
type ThinkingStep = {
    type: 'llm_call' | 'tool_call' | 'reasoning'
    content: string
    tool_calls?: ToolCallInfo[]
    usage?: TokenUsage
    duration_ms: number
}
type Message = { 
    role: 'user' | 'assistant' | 'thinking'
    text: string 
    id?: string
    thinking?: ThinkingStep[]
    usage?: TokenUsage
    showThinking?: boolean
}
type SessionUsage = {
    total_prompt: number
    total_completion: number
    total_tokens: number
    request_count: number
    tool_call_count: number
}

const ballSize = 56

// 窗口大小状态
const panelSize = ref<Size>({ width: 400, height: 500 })
const isPanelSizeDirty = ref(false)

const isExpanded = ref(false)
const isDragging = ref(false)
const isResizing = ref(false)
const inputRef = ref<HTMLInputElement | null>(null)

const pos = ref<Point>({ x: 0, y: 0 })
const dragOffset = ref<Point>({ x: 0, y: 0 })
const resizeStart = ref<{ x: number; y: number; width: number; height: number }>({ x: 0, y: 0, width: 0, height: 0 })

const messages = ref<Message[]>([
    { role: 'assistant', text: 'AI 助手已就绪喵！请问有什么可以帮您的喵？', id: 'welcome' },
])

const inputText = ref('')
const isLoading = ref(false)
const sessionId = ref(generateSessionId())

// 玩家认证信息
const playerToken = ref(localStorage.getItem('sa.token') || '')
const playerUsername = ref('')
const playerId = ref('')

// Token 统计
const sessionUsage = ref<SessionUsage>({
    total_prompt: 0,
    total_completion: 0,
    total_tokens: 0,
    request_count: 0,
    tool_call_count: 0
})
const showUsagePanel = ref(false)

function generateSessionId(): string {
    return 'sess_' + Math.random().toString(36).substring(2, 15)
}

function clamp(n: number, min: number, max: number) {
    return Math.max(min, Math.min(n, max))
}

function layoutBounds() {
    const vw = window.innerWidth
    const vh = window.innerHeight
    const w = isExpanded.value ? panelSize.value.width : ballSize
    const h = isExpanded.value ? panelSize.value.height : ballSize

    return {
        minX: 8,
        minY: 8,
        maxX: Math.max(8, vw - w - 8),
        maxY: Math.max(8, vh - h - 8),
    }
}

function clampToViewport(next: Point) {
    const b = layoutBounds()
    return {
        x: clamp(next.x, b.minX, b.maxX),
        y: clamp(next.y, b.minY, b.maxY),
    }
}

// 延迟感应计时器
let expandTimer: number | null = null

async function setExpanded(next: boolean) {
    if (isDragging.value && next) return
    if (isExpanded.value === next) return

    isExpanded.value = next
    pos.value = clampToViewport(pos.value)

    if (next) {
        // 展开时更新玩家token
        playerToken.value = localStorage.getItem('sa.token') || ''
        await nextTick()
        inputRef.value?.focus()
    }
}

function distanceToBallCenter(e: MouseEvent) {
    const cx = pos.value.x + ballSize / 2
    const cy = pos.value.y + ballSize / 2
    const dx = e.clientX - cx
    const dy = e.clientY - cy
    return Math.sqrt(dx * dx + dy * dy)
}

function isMouseFarFromPanel(e: MouseEvent) {
    const left = pos.value.x
    const top = pos.value.y
    const right = pos.value.x + panelSize.value.width
    const bottom = pos.value.y + panelSize.value.height
    const x = e.clientX
    const y = e.clientY

    return (
        x < left - PANEL_CLOSE_MARGIN_PX ||
        x > right + PANEL_CLOSE_MARGIN_PX ||
        y < top - PANEL_CLOSE_MARGIN_PX ||
        y > bottom + PANEL_CLOSE_MARGIN_PX
    )
}

function onGlobalMouseMove(e: MouseEvent) {
    if (isDragging.value || isResizing.value) return

    const d = distanceToBallCenter(e)

    if (!isExpanded.value) {
        if (d <= TRIGGER_RADIUS_PX) {
            if (expandTimer === null) {
                expandTimer = window.setTimeout(() => {
                    setExpanded(true)
                    expandTimer = null
                }, 300)
            }
        } else {
            if (expandTimer !== null) {
                clearTimeout(expandTimer)
                expandTimer = null
            }
        }
        return
    }

    if (isMouseFarFromPanel(e)) {
        if (d >= CLOSE_RADIUS_PX) {
            setExpanded(false)
        }
    }
}

function onPointerDown(e: PointerEvent, isHeaderDrag = false) {
    if (isExpanded.value && !isHeaderDrag) return

    if (expandTimer !== null) {
        clearTimeout(expandTimer)
        expandTimer = null
    }

    const target = e.currentTarget as HTMLElement | null
    if (!target) return

    isDragging.value = true
    target.setPointerCapture(e.pointerId)

    dragOffset.value = {
        x: e.clientX - pos.value.x,
        y: e.clientY - pos.value.y,
    }
}

function onPointerMove(e: PointerEvent) {
    if (!isDragging.value) return
    pos.value = clampToViewport({
        x: e.clientX - dragOffset.value.x,
        y: e.clientY - dragOffset.value.y,
    })
}

function onPointerUp(e: PointerEvent) {
    const target = e.currentTarget as HTMLElement | null
    if (target) {
        try {
            target.releasePointerCapture(e.pointerId)
        } catch {
            // ignore
        }
    }
    isDragging.value = false
    savePosition()
}

// 窗口大小调节
function onResizeHandleDown(e: PointerEvent) {
    e.stopPropagation()
    e.preventDefault()
    
    const target = e.currentTarget as HTMLElement
    if (!target) return

    isResizing.value = true
    target.setPointerCapture(e.pointerId)

    resizeStart.value = {
        x: e.clientX,
        y: e.clientY,
        width: panelSize.value.width,
        height: panelSize.value.height,
    }
}

function onResizeHandleMove(e: PointerEvent) {
    if (!isResizing.value) return
    
    const dx = e.clientX - resizeStart.value.x
    const dy = e.clientY - resizeStart.value.y
    
    const newWidth = clamp(
        resizeStart.value.width + dx,
        MIN_PANEL_WIDTH,
        Math.min(MAX_PANEL_WIDTH, window.innerWidth - pos.value.x - 16)
    )
    const newHeight = clamp(
        resizeStart.value.height + dy,
        MIN_PANEL_HEIGHT,
        Math.min(MAX_PANEL_HEIGHT, window.innerHeight - pos.value.y - 16)
    )
    
    panelSize.value = { width: newWidth, height: newHeight }
    isPanelSizeDirty.value = true
}

function onResizeHandleUp(e: PointerEvent) {
    const target = e.currentTarget as HTMLElement
    if (target) {
        try {
            target.releasePointerCapture(e.pointerId)
        } catch {
            // ignore
        }
    }
    isResizing.value = false
    savePanelSize()
}

function savePosition() {
    localStorage.setItem('sa_ai_ball_pos', JSON.stringify(pos.value))
}

function loadPosition(): Point | null {
    const saved = localStorage.getItem('sa_ai_ball_pos')
    if (!saved) return null
    try {
        return JSON.parse(saved)
    } catch {
        return null
    }
}

function savePanelSize() {
    localStorage.setItem('sa_ai_panel_size', JSON.stringify(panelSize.value))
}

function loadPanelSize(): Size | null {
    const saved = localStorage.getItem('sa_ai_panel_size')
    if (!saved) return null
    try {
        const size = JSON.parse(saved)
        return {
            width: clamp(size.width || 400, MIN_PANEL_WIDTH, MAX_PANEL_WIDTH),
            height: clamp(size.height || 500, MIN_PANEL_HEIGHT, MAX_PANEL_HEIGHT)
        }
    } catch {
        return null
    }
}

/**
 * 发送消息到 AI 系统
 */
async function send() {
    const text = inputText.value.trim()
    if (!text || isLoading.value) return

    // 检查玩家是否已登录
    const token = localStorage.getItem('sa.token')
    if (!token) {
        messages.value.push({
            role: 'assistant',
            text: '请先登录游戏后再使用 AI 助手喵~',
            id: 'error_' + Date.now()
        })
        return
    }
    playerToken.value = token

    const userMsg: Message = { 
        role: 'user', 
        text,
        id: 'user_' + Date.now()
    }
    messages.value.push(userMsg)
    inputText.value = ''
    
    await sendToAi()
}

/**
 * 实际发送请求到 AI 系统
 */
async function sendToAi(retryCount = 0) {
    const MAX_RETRIES = 3
    const RETRY_DELAY_MS = 2000
    
    const thinkingId = 'thinking_' + Date.now()
    messages.value.push({ 
        role: 'thinking', 
        text: retryCount > 0 ? `AI 正在启动喵，请稍候... (${retryCount}/${MAX_RETRIES})` : '正在思考喵...',
        id: thinkingId
    })
    
    isLoading.value = true
    
    try {
        const history = messages.value
            .filter(m => m.role !== 'thinking')
            .slice(-10)
            .map(m => ({
                role: m.role,
                content: m.text
            }))
        
        // 调用 API，传递玩家token
        const response = await fetch('/api/ai/chat', {
            method: 'POST',
            headers: { 
                'Content-Type': 'application/json',
                'Authorization': `Bearer ${playerToken.value}`
            },
            body: JSON.stringify({
                messages: history,
                context: { 
                    sessionId: sessionId.value,
                    playerToken: playerToken.value  // 传递玩家token给AI系统
                },
                show_thinking: true
            })
        })
        
        const thinkingIndex = messages.value.findIndex(m => m.id === thinkingId)
        if (thinkingIndex !== -1) {
            messages.value.splice(thinkingIndex, 1)
        }
        
        if (!response.ok) {
            const errorData = await response.json().catch(() => ({ error: 'Unknown error' }))
            
            if (response.status === 401) {
                throw new Error('登录已过期，请重新登录')
            }
            
            const errorMsg = errorData.error || ''
            if ((errorMsg.includes('starting') || errorMsg.includes('Connection refused') || response.status === 503) && retryCount < MAX_RETRIES) {
                await delay(RETRY_DELAY_MS)
                return sendToAi(retryCount + 1)
            }
            
            throw new Error(errorData.error || `HTTP ${response.status}`)
        }
        
        const data = await response.json()
        
        if (data.ok) {
            if (data.usage) {
                sessionUsage.value.total_prompt += data.usage.prompt_tokens || 0
                sessionUsage.value.total_completion += data.usage.completion_tokens || 0
                sessionUsage.value.total_tokens += data.usage.total_tokens || 0
            }
            sessionUsage.value.request_count++
            if (data.tool_calls_count) {
                sessionUsage.value.tool_call_count += data.tool_calls_count
            }
            
            messages.value.push({
                role: 'assistant',
                text: data.message,
                id: 'ai_' + Date.now(),
                thinking: data.thinking,
                usage: data.usage,
                showThinking: false
            })
        } else {
            throw new Error(data.error || 'AI 返回错误')
        }
        
    } catch (e) {
        const thinkingIndex = messages.value.findIndex(m => m.id === thinkingId)
        if (thinkingIndex !== -1) {
            messages.value.splice(thinkingIndex, 1)
        }
        
        const errorMsg = e instanceof Error ? e.message : String(e)
        if ((errorMsg.includes('Connection refused') || errorMsg.includes('Failed to fetch')) && retryCount < MAX_RETRIES) {
            await delay(RETRY_DELAY_MS)
            return sendToAi(retryCount + 1)
        }
        
        messages.value.push({
            role: 'assistant',
            text: `抱歉，发生了错误喵: ${errorMsg}`,
            id: 'error_' + Date.now()
        })
    } finally {
        isLoading.value = false
        await nextTick()
        scrollToBottom()
    }
}

function delay(ms: number): Promise<void> {
    return new Promise(resolve => setTimeout(resolve, ms))
}

function scrollToBottom() {
    const msgList = document.querySelector('.msg-list')
    if (msgList) {
        msgList.scrollTop = msgList.scrollHeight
    }
}

function toggleThinking(msg: Message) {
    if (msg.thinking) {
        msg.showThinking = !msg.showThinking
    }
}

function formatToolName(name: string): string {
    const nameMap: Record<string, string> = {
        'snapshot.getEntity': '获取实体',
        'snapshot.getLatestSummary': '获取世界概要'
    }
    return nameMap[name] || name
}

function formatDuration(ms: number): string {
    if (ms < 1000) return `${ms}ms`
    return `${(ms / 1000).toFixed(1)}s`
}

const rootStyle = computed(() => {
    return {
        left: pos.value.x + 'px',
        top: pos.value.y + 'px',
        '--panel-width': panelSize.value.width + 'px',
        '--panel-height': panelSize.value.height + 'px',
        '--origin-x': (ballSize / 2) + 'px',
        '--origin-y': (ballSize / 2) + 'px',
    }
})

function onResize() {
    pos.value = clampToViewport(pos.value)
    
    const vw = window.innerWidth
    const vh = window.innerHeight
    const maxW = Math.min(MAX_PANEL_WIDTH, vw - pos.value.x - 16)
    const maxH = Math.min(MAX_PANEL_HEIGHT, vh - pos.value.y - 16)
    
    if (panelSize.value.width > maxW || panelSize.value.height > maxH) {
        panelSize.value = {
            width: Math.min(panelSize.value.width, maxW),
            height: Math.min(panelSize.value.height, maxH)
        }
        isPanelSizeDirty.value = true
    }
}

onMounted(() => {
    const savedPos = loadPosition()
    if (savedPos) {
        pos.value = clampToViewport(savedPos)
    } else {
        pos.value = clampToViewport({
            x: 32,
            y: window.innerHeight - ballSize - 32,
        })
    }
    
    const savedSize = loadPanelSize()
    if (savedSize) {
        panelSize.value = savedSize
    }
    
    // 加载玩家token
    playerToken.value = localStorage.getItem('sa.token') || ''
    
    window.addEventListener('resize', onResize)
    window.addEventListener('mousemove', onGlobalMouseMove)
    window.addEventListener('storage', syncEnabledState)
    const timer = setInterval(() => {
        syncEnabledState()
        // 定期同步token
        playerToken.value = localStorage.getItem('sa.token') || ''
    }, 1000)
    onBeforeUnmount(() => clearInterval(timer))
})

onBeforeUnmount(() => {
    window.removeEventListener('resize', onResize)
    window.removeEventListener('mousemove', onGlobalMouseMove)
    window.removeEventListener('storage', syncEnabledState)
    
    if (isPanelSizeDirty.value) {
        savePanelSize()
    }
})
</script>

<template>
    <div v-if="isAiEnabled" class="ai-float-root" :class="{ 'is-expanded': isExpanded, 'is-dragging': isDragging }"
        :style="rootStyle">
        <!-- 基础圆球 -->
        <div class="ai-ball" @pointerdown="onPointerDown" @pointermove="onPointerMove" @pointerup="onPointerUp"
            @pointercancel="onPointerUp">
            <div class="ball-inner">
                <span class="ball-label">AI</span>
            </div>
            <div class="ball-glow"></div>
        </div>

        <!-- 对话面板 -->
        <Transition name="spit-out">
            <div v-if="isExpanded" class="ai-panel" :class="{ 'is-resizing': isResizing }">
                <header class="panel-header" @pointerdown="onPointerDown($event, true)" @pointermove="onPointerMove"
                    @pointerup="onPointerUp">
                    <div class="panel-title">STARAXIS AI</div>
                    <div class="header-actions">
                        <button class="usage-btn" @click.prevent="showUsagePanel = !showUsagePanel" title="Token 统计">
                            <span class="token-icon">T</span>
                            <span class="token-count">{{ sessionUsage.total_tokens || 0 }}</span>
                        </button>
                        <button class="panel-close" type="button" @click.stop="setExpanded(false)">×</button>
                    </div>
                </header>

                <!-- Token 统计面板 -->
                <Transition name="slide-down">
                    <div v-if="showUsagePanel" class="usage-panel">
                        <div class="usage-title">本次会话 Token 统计</div>
                        <div class="usage-grid">
                            <div class="usage-item">
                                <div class="usage-value">{{ sessionUsage.total_prompt }}</div>
                                <div class="usage-label">输入</div>
                            </div>
                            <div class="usage-item">
                                <div class="usage-value">{{ sessionUsage.total_completion }}</div>
                                <div class="usage-label">输出</div>
                            </div>
                            <div class="usage-item">
                                <div class="usage-value">{{ sessionUsage.total_tokens }}</div>
                                <div class="usage-label">总计</div>
                            </div>
                            <div class="usage-item">
                                <div class="usage-value">{{ sessionUsage.request_count }}</div>
                                <div class="usage-label">请求数</div>
                            </div>
                            <div class="usage-item">
                                <div class="usage-value">{{ sessionUsage.tool_call_count }}</div>
                                <div class="usage-label">工具调用</div>
                            </div>
                        </div>
                    </div>
                </Transition>

                <!-- 消息列表 -->
                <div class="panel-body">
                    <div class="msg-list">
                        <div v-for="(m, idx) in messages" :key="m.id || idx" class="msg" :class="m.role">
                            <div class="bubble">
                                <div class="msg-text">{{ m.text }}</div>
                                <!-- 思考过程 -->
                                <div v-if="m.thinking && m.thinking.length > 0" class="thinking-section">
                                    <button class="thinking-toggle" @click="toggleThinking(m)">
                                        {{ m.showThinking ? '隐藏思考过程' : '查看思考过程' }}
                                        <span v-if="m.usage" class="thinking-tokens">
                                            ({{ m.usage.total_tokens }} tokens)
                                        </span>
                                    </button>
                                    <Transition name="fade">
                                        <div v-if="m.showThinking" class="thinking-content">
                                            <div v-for="(step, sidx) in m.thinking" :key="sidx" class="thinking-step">
                                                <div class="step-header">
                                                    <span class="step-type" :class="step.type">
                                                        {{ step.type === 'reasoning' ? '💭 推理' : '🔧 工具调用' }}
                                                    </span>
                                                    <span class="step-duration">{{ formatDuration(step.duration_ms) }}</span>
                                                </div>
                                                <div class="step-content">{{ step.content }}</div>
                                                <div v-if="step.tool_calls && step.tool_calls.length > 0" class="tool-calls">
                                                    <div v-for="(tc, tcidx) in step.tool_calls" :key="tcidx" class="tool-call">
                                                        <div class="tool-name">{{ formatToolName(tc.name) }}</div>
                                                        <div class="tool-args">参数: {{ JSON.stringify(tc.arguments) }}</div>
                                                        <div class="tool-result" v-if="tc.result">
                                                            结果: {{ typeof tc.result === 'object' ? JSON.stringify(tc.result).slice(0, 100) + '...' : tc.result }}
                                                        </div>
                                                        <div class="tool-duration">耗时: {{ formatDuration(tc.duration_ms) }}</div>
                                                    </div>
                                                </div>
                                                <div v-if="step.usage" class="step-usage">
                                                    Tokens: {{ step.usage.prompt_tokens }} 输入 + {{ step.usage.completion_tokens }} 输出 = {{ step.usage.total_tokens }} 总计
                                                </div>
                                            </div>
                                        </div>
                                    </Transition>
                                </div>
                            </div>
                        </div>
                    </div>
                </div>

                <!-- 输入框 -->
                <footer class="panel-footer">
                    <input 
                        ref="inputRef" 
                        v-model="inputText" 
                        class="msg-input" 
                        type="text"
                        :placeholder="isLoading ? 'AI 正在思考喵...' : (playerToken ? '在此输入回复内容喵...' : '请先登录后再使用喵...')" 
                        @keyup.enter="send"
                        :disabled="isLoading || !playerToken"
                    />
                    <button 
                        class="send-btn" 
                        type="button" 
                        @click="send"
                        :disabled="isLoading || !inputText.trim() || !playerToken"
                    >
                        {{ isLoading ? '...' : '发送' }}
                    </button>
                </footer>

                <!-- 大小调节手柄 -->
                <div 
                    class="resize-handle" 
                    @pointerdown="onResizeHandleDown"
                    @pointermove="onResizeHandleMove"
                    @pointerup="onResizeHandleUp"
                    @pointercancel="onResizeHandleUp"
                    title="拖拽调节窗口大小"
                >
                    <div class="resize-icon">
                        <span></span>
                        <span></span>
                        <span></span>
                    </div>
                </div>
            </div>
        </Transition>
    </div>
</template>

<style scoped>
.ai-float-root {
    position: fixed;
    z-index: 9999;
    pointer-events: none;
    transition: width 0.3s ease, height 0.3s ease;
}

.ai-float-root>* {
    pointer-events: auto;
}

/* AI 圆球 */
.ai-ball {
    position: absolute;
    left: 0;
    top: 0;
    width: 56px;
    height: 56px;
    border-radius: 999px;
    cursor: grab;
    z-index: 2;
    background: radial-gradient(circle at 30% 25%, var(--sa-accent2, #22d3ee), var(--sa-glow, #a855f7));
    border: 1px solid rgba(255, 255, 255, 0.2);
    box-shadow:
        0 8px 32px rgba(0, 0, 0, 0.5),
        inset 0 0 12px rgba(255, 255, 255, 0.2);
    backdrop-filter: blur(8px);
    display: grid;
    place-items: center;
    transition: transform 0.3s cubic-bezier(0.175, 0.885, 0.32, 1.275), opacity 0.2s;
}

.ai-ball:hover {
    transform: scale(1.05);
}

.is-dragging .ai-ball {
    cursor: grabbing;
    transform: scale(0.95);
    opacity: 0.8;
}

.is-expanded .ai-ball {
    opacity: 0;
    pointer-events: none;
    transform: scale(0.5);
}

.ball-label {
    font-size: 16px;
    font-weight: 900;
    color: #fff;
    text-shadow: 0 0 10px rgba(0, 0, 0, 0.5);
}

.ball-glow {
    position: absolute;
    inset: -4px;
    border-radius: 50%;
    background: var(--sa-glow, #a855f7);
    opacity: 0.3;
    filter: blur(8px);
    animation: pulse 2s infinite;
    z-index: -1;
}

@keyframes pulse {
    0%, 100% { transform: scale(1); opacity: 0.3; }
    50% { transform: scale(1.2); opacity: 0.5; }
}

/* AI 面板 */
.ai-panel {
    position: absolute;
    left: 0;
    top: 0;
    width: var(--panel-width, 400px);
    height: var(--panel-height, 500px);
    min-width: 320px;
    min-height: 360px;
    max-width: 800px;
    max-height: 900px;
    border-radius: 16px;
    background: var(--sa-panel, rgba(13, 14, 26, 0.95));
    border: 1px solid var(--sa-stroke, rgba(196, 181, 253, 0.2));
    box-shadow: 0 20px 50px rgba(0, 0, 0, 0.6);
    backdrop-filter: blur(20px);
    overflow: hidden;
    display: flex;
    flex-direction: column;
    transform-origin: var(--origin-x) var(--origin-y);
    z-index: 1;
    transition: box-shadow 0.2s;
}

.ai-panel.is-resizing {
    box-shadow: 0 30px 60px rgba(0, 0, 0, 0.8);
}

.ai-panel > * {
    flex-shrink: 0;
}

.ai-panel > .panel-body {
    flex: 1;
    flex-shrink: 1;
    min-height: 0;
}

/* 展开动画 */
.spit-out-enter-active {
    animation: spit-out-in 0.4s cubic-bezier(0.34, 1.56, 0.64, 1);
}

.spit-out-leave-active {
    animation: spit-out-in 0.3s cubic-bezier(0.36, 0, 0.66, -0.56) reverse;
}

@keyframes spit-out-in {
    0% {
        transform: scale(0);
        opacity: 0;
        clip-path: circle(0% at var(--origin-x) var(--origin-y));
    }
    100% {
        transform: scale(1);
        opacity: 1;
        clip-path: circle(150% at var(--origin-x) var(--origin-y));
    }
}

/* 面板头部 */
.panel-header {
    display: flex;
    align-items: center;
    justify-content: space-between;
    padding: 0 16px;
    height: 48px !important;
    min-height: 48px !important;
    max-height: 48px !important;
    box-sizing: border-box;
    background: rgba(255, 255, 255, 0.05);
    border-bottom: 1px solid rgba(255, 255, 255, 0.1);
    cursor: grab;
    flex: 0 0 48px;
}

.panel-header:active {
    cursor: grabbing;
}

.panel-title {
    font-size: 12px;
    font-weight: 800;
    letter-spacing: 2px;
    color: var(--sa-accent2, #22d3ee);
    text-shadow: 0 0 8px var(--sa-accent2);
}

.header-actions {
    display: flex;
    align-items: center;
    gap: 8px;
}

.usage-btn {
    display: flex;
    align-items: center;
    justify-content: center;
    gap: 4px;
    background: rgba(168, 85, 247, 0.2);
    border: 1px solid rgba(168, 85, 247, 0.4);
    border-radius: 12px;
    padding: 4px 10px;
    color: #fff;
    font-size: 11px;
    cursor: pointer;
    transition: all 0.2s;
    white-space: nowrap;
    user-select: none;
}

.usage-btn:hover {
    background: rgba(168, 85, 247, 0.4);
}

.usage-btn:active {
    background: rgba(168, 85, 247, 0.6);
}

.token-icon {
    font-size: 10px;
    font-weight: 700;
    color: var(--sa-accent2, #22d3ee);
}

.token-count {
    font-weight: 600;
    color: #fff;
}

.panel-close {
    background: none;
    border: none;
    color: #fff;
    font-size: 20px;
    cursor: pointer;
    opacity: 0.6;
    transition: all 0.2s;
    width: 28px;
    height: 28px;
    display: grid;
    place-items: center;
    border-radius: 4px;
}

.panel-close:hover {
    opacity: 1;
    background: rgba(255, 255, 255, 0.1);
}

/* Usage Panel */
.usage-panel {
    padding: 10px 16px;
    background: rgba(0, 0, 0, 0.3);
    border-bottom: 1px solid rgba(255, 255, 255, 0.1);
    flex: 0 0 auto;
    display: block;
}

.slide-down-enter-active,
.slide-down-leave-active {
    transition: opacity 0.3s ease, transform 0.3s ease;
}

.slide-down-enter-from {
    opacity: 0;
    transform: translateY(-10px);
}

.slide-down-leave-to {
    opacity: 0;
    transform: translateY(-10px);
}

.usage-title {
    font-size: 11px;
    font-weight: 600;
    color: rgba(255, 255, 255, 0.6);
    margin-bottom: 8px;
    text-transform: uppercase;
    letter-spacing: 1px;
}

.usage-grid {
    display: grid;
    grid-template-columns: repeat(5, 1fr);
    gap: 4px;
}

.usage-item {
    text-align: center;
    padding: 4px 2px;
    background: rgba(255, 255, 255, 0.05);
    border-radius: 6px;
    min-width: 0;
    overflow: hidden;
}

.usage-value {
    font-size: 12px;
    font-weight: 700;
    color: var(--sa-accent2, #22d3ee);
    line-height: 1.3;
}

.usage-label {
    font-size: 8px;
    color: rgba(255, 255, 255, 0.5);
    margin-top: 2px;
    line-height: 1.2;
    white-space: nowrap;
}

/* 面板主体 */
.panel-body {
    overflow-y: auto;
    overflow-x: hidden;
    padding: 16px;
    flex: 1 1 auto;
    min-height: 0;
    position: relative;
}

/* 自定义滚动条 */
.panel-body::-webkit-scrollbar {
    width: 6px;
}

.panel-body::-webkit-scrollbar-track {
    background: rgba(255, 255, 255, 0.05);
    border-radius: 3px;
}

.panel-body::-webkit-scrollbar-thumb {
    background: rgba(255, 255, 255, 0.2);
    border-radius: 3px;
}

.panel-body::-webkit-scrollbar-thumb:hover {
    background: rgba(255, 255, 255, 0.3);
}

.msg-list {
    display: flex;
    flex-direction: column;
    gap: 12px;
    width: 100%;
    min-height: 100%;
}

.msg {
    display: flex;
    max-width: 90%;
    width: fit-content;
}

.msg.assistant {
    align-self: flex-start;
}

.msg.user {
    align-self: flex-end;
}

.msg.thinking {
    align-self: flex-start;
    opacity: 0.6;
}

.bubble {
    padding: 10px 14px;
    border-radius: 12px;
    font-size: 13px;
    line-height: 1.5;
    background: rgba(255, 255, 255, 0.08);
    color: #eee;
    border: 1px solid rgba(255, 255, 255, 0.1);
    word-break: break-word;
    overflow-wrap: anywhere;
    min-width: 0;
    max-width: 100%;
}

.msg-text {
    white-space: pre-wrap;
}

.msg.user .bubble {
    background: var(--sa-accent2, #22d3ee);
    color: #000;
    font-weight: 500;
    border: none;
}

.msg.thinking .bubble {
    font-style: italic;
}

/* Thinking Section */
.thinking-section {
    margin-top: 8px;
    padding-top: 8px;
    border-top: 1px solid rgba(255, 255, 255, 0.1);
}

.thinking-toggle {
    background: rgba(168, 85, 247, 0.15);
    border: 1px solid rgba(168, 85, 247, 0.3);
    border-radius: 6px;
    padding: 4px 8px;
    font-size: 11px;
    color: var(--sa-glow, #a855f7);
    cursor: pointer;
    transition: all 0.2s;
}

.thinking-toggle:hover {
    background: rgba(168, 85, 247, 0.3);
}

.thinking-tokens {
    color: rgba(255, 255, 255, 0.5);
    margin-left: 4px;
}

.thinking-content {
    margin-top: 8px;
    padding: 10px;
    background: rgba(0, 0, 0, 0.3);
    border-radius: 8px;
    font-size: 11px;
    color: rgba(255, 255, 255, 0.7);
    max-height: 200px;
    overflow-y: auto;
}

.fade-enter-active,
.fade-leave-active {
    transition: all 0.3s ease;
}

.fade-enter-from,
.fade-leave-to {
    opacity: 0;
}

.thinking-step {
    margin-bottom: 10px;
    padding-bottom: 10px;
    border-bottom: 1px solid rgba(255, 255, 255, 0.05);
}

.thinking-step:last-child {
    margin-bottom: 0;
    padding-bottom: 0;
    border-bottom: none;
}

.step-header {
    display: flex;
    justify-content: space-between;
    align-items: center;
    margin-bottom: 4px;
}

.step-type {
    font-weight: 600;
    font-size: 11px;
}

.step-type.reasoning {
    color: var(--sa-accent2, #22d3ee);
}

.step-type.tool_call {
    color: #f59e0b;
}

.step-duration {
    font-size: 10px;
    color: rgba(255, 255, 255, 0.4);
}

.step-content {
    margin-bottom: 6px;
    line-height: 1.4;
}

.tool-calls {
    display: flex;
    flex-direction: column;
    gap: 6px;
}

.tool-call {
    padding: 6px 8px;
    background: rgba(255, 255, 255, 0.05);
    border-radius: 6px;
    border-left: 2px solid #f59e0b;
}

.tool-name {
    font-weight: 600;
    color: #f59e0b;
    font-size: 10px;
    margin-bottom: 2px;
}

.tool-args,
.tool-result {
    font-family: monospace;
    font-size: 10px;
    color: rgba(255, 255, 255, 0.5);
    white-space: nowrap;
    overflow: hidden;
    text-overflow: ellipsis;
}

.tool-duration {
    font-size: 9px;
    color: rgba(255, 255, 255, 0.3);
    margin-top: 2px;
}

.step-usage {
    margin-top: 6px;
    padding-top: 6px;
    border-top: 1px solid rgba(255, 255, 255, 0.05);
    font-size: 10px;
    color: rgba(255, 255, 255, 0.4);
    font-family: monospace;
}

/* 面板底部 */
.panel-footer {
    display: flex;
    align-items: center;
    gap: 8px;
    padding: 10px 12px;
    height: 56px !important;
    min-height: 56px !important;
    max-height: 56px !important;
    box-sizing: border-box;
    background: rgba(0, 0, 0, 0.2);
    border-top: 1px solid rgba(255, 255, 255, 0.05);
    flex: 0 0 56px;
}

.msg-input {
    flex: 1 1 auto;
    min-width: 0;
    height: 36px !important;
    min-height: 36px !important;
    max-height: 36px !important;
    box-sizing: border-box;
    background: rgba(255, 255, 255, 0.05);
    border: 1px solid rgba(255, 255, 255, 0.1);
    border-radius: 8px;
    padding: 0 12px;
    color: #fff;
    outline: none;
    font-size: 13px;
    line-height: 36px;
}

.msg-input:focus {
    border-color: var(--sa-accent2, #22d3ee);
}

.msg-input:disabled {
    opacity: 0.5;
    cursor: not-allowed;
}

.send-btn {
    padding: 0 16px;
    height: 36px !important;
    min-height: 36px !important;
    max-height: 36px !important;
    box-sizing: border-box;
    background: var(--sa-glow, #a855f7);
    color: #fff;
    border: none;
    border-radius: 8px;
    font-size: 13px;
    font-weight: 600;
    cursor: pointer;
    transition: filter 0.2s, opacity 0.2s;
    flex-shrink: 0;
    line-height: 36px;
}

.send-btn:hover:not(:disabled) {
    filter: brightness(1.2);
}

.send-btn:disabled {
    opacity: 0.5;
    cursor: not-allowed;
}

/* 大小调节手柄 */
.resize-handle {
    position: absolute;
    right: 0;
    bottom: 0;
    width: 24px;
    height: 24px;
    cursor: nwse-resize;
    display: grid;
    place-items: center;
    z-index: 10;
    border-radius: 0 0 16px 0;
}

.resize-handle::before {
    content: '';
    position: absolute;
    right: 4px;
    bottom: 4px;
    width: 12px;
    height: 12px;
    background: linear-gradient(
        135deg,
        transparent 40%,
        rgba(168, 85, 247, 0.6) 45%,
        rgba(168, 85, 247, 0.6) 55%,
        transparent 60%
    );
    pointer-events: none;
}

.resize-icon {
    display: flex;
    flex-direction: column;
    gap: 2px;
    opacity: 0.6;
    pointer-events: none;
}

.resize-icon span {
    display: block;
    width: 6px;
    height: 2px;
    background: rgba(168, 85, 247, 0.8);
    border-radius: 1px;
}

.resize-icon span:nth-child(1) { transform: translateX(4px); }
.resize-icon span:nth-child(2) { transform: translateX(2px); }

.resize-handle:hover::before {
    background: linear-gradient(
        135deg,
        transparent 40%,
        rgba(168, 85, 247, 1) 45%,
        rgba(168, 85, 247, 1) 55%,
        transparent 60%
    );
}

.resize-handle:hover .resize-icon {
    opacity: 1;
}

.ai-panel.is-resizing .resize-handle::before {
    background: linear-gradient(
        135deg,
        transparent 40%,
        var(--sa-accent2, #22d3ee) 45%,
        var(--sa-accent2, #22d3ee) 55%,
        transparent 60%
    );
}

/* 响应式布局适配 */
@media (max-width: 600px) {
    .ai-panel {
        width: calc(100vw - 32px) !important;
        height: calc(100vh - 100px) !important;
        max-width: none;
        max-height: none;
    }
}

@media (max-width: 450px) {
    .usage-grid {
        grid-template-columns: repeat(3, 1fr);
    }
}
</style>

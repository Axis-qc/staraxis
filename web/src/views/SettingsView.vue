<script setup lang="ts">
/**
 * @file SettingsView.vue
 *
 * @description
 * 游戏全局设置界面。提供画面效果（背景动画）、AI 系统配置、API 设置等基础项。
 * 从后端 ai_system/config/config.yaml 读取和保存配置。
 * 严格遵循 StarAxis 的数据驱动与美学规范喵！
 */

import { ref, onMounted } from 'vue'
import { useI18n } from 'vue-i18n'
import { useRouter } from 'vue-router'
import { useStarfield } from '../composables/useStarfield'

const { t } = useI18n()
const router = useRouter()

const canvasRef = ref<HTMLCanvasElement | null>(null)
const isReady = ref(false)
const isLoading = ref(true)
const loadError = ref('')

// 画面设置：背景动画
const disableBackgroundAnimation = ref(localStorage.getItem('sa_settings_disable_bg_anim') === 'true')

// AI 系统设置（默认值，会从后端加载）
const aiEnabled = ref(true)
const aiProvider = ref('openai')
const aiModel = ref('gpt-3.5-turbo')
const aiApiEndpoint = ref('https://api.openai.com/v1')
const aiApiKey = ref('')

// 厂商预设
const providerPresets: Record<string, { endpoint: string, model: string }> = {
    openai: { endpoint: 'https://api.openai.com/v1', model: 'gpt-3.5-turbo' },
    anthropic: { endpoint: 'https://api.anthropic.com/v1', model: 'claude-3-opus-20240229' },
    deepseek: { endpoint: 'https://api.deepseek.com/v1', model: 'deepseek-chat' },
    local: { endpoint: 'http://127.0.0.1:11434/v1', model: 'llama3' }
}

/**
 * 从后端加载 AI 配置
 */
async function loadAiConfig() {
    try {
        const resp = await fetch('/api/ai/config')
        if (!resp.ok) {
            throw new Error(`Failed to load config: ${resp.status}`)
        }
        const config = await resp.json()
        
        // 解析配置
        if (config.server) {
            aiEnabled.value = config.server.auto_start !== false
        }
        
        if (config.ai) {
            const ai = config.ai
            aiProvider.value = ai.active_provider || 'openai'
            
            // 获取当前厂商的配置
            const providers = ai.providers || {}
            const providerConfig = providers[aiProvider.value]
            
            if (providerConfig) {
                aiModel.value = providerConfig.model || providerPresets[aiProvider.value]?.model || ''
                aiApiEndpoint.value = providerConfig.base_url || providerPresets[aiProvider.value]?.endpoint || ''
                aiApiKey.value = providerConfig.api_key || ''
            } else {
                // 使用预设值
                const preset = providerPresets[aiProvider.value]
                if (preset) {
                    aiModel.value = preset.model
                    aiApiEndpoint.value = preset.endpoint
                }
            }
        }
        
        // 同时更新 localStorage 保持同步
        localStorage.setItem('sa_settings_ai_enabled', String(aiEnabled.value))
        localStorage.setItem('sa_settings_ai_provider', aiProvider.value)
        localStorage.setItem('sa_settings_ai_model', aiModel.value)
        localStorage.setItem('sa_settings_ai_endpoint', aiApiEndpoint.value)
        localStorage.setItem('sa_settings_ai_key', aiApiKey.value)
        
    } catch (e) {
        console.error('Failed to load AI config:', e)
        loadError.value = String(e)
        // 加载失败时回退到 localStorage
        loadFromLocalStorage()
    }
}

/**
 * 从 localStorage 加载配置（降级方案）
 */
function loadFromLocalStorage() {
    aiEnabled.value = localStorage.getItem('sa_settings_ai_enabled') !== 'false'
    aiProvider.value = localStorage.getItem('sa_settings_ai_provider') || 'openai'
    aiModel.value = localStorage.getItem('sa_settings_ai_model') || 'gpt-3.5-turbo'
    aiApiEndpoint.value = localStorage.getItem('sa_settings_ai_endpoint') || 'https://api.openai.com/v1'
    aiApiKey.value = localStorage.getItem('sa_settings_ai_key') || ''
}

function onProviderChange() {
    const preset = providerPresets[aiProvider.value]
    if (preset) {
        aiApiEndpoint.value = preset.endpoint
        aiModel.value = preset.model
    }
}

async function saveSettings() {
    localStorage.setItem('sa_settings_disable_bg_anim', String(disableBackgroundAnimation.value))
    localStorage.setItem('sa_settings_ai_enabled', String(aiEnabled.value))
    localStorage.setItem('sa_settings_ai_provider', aiProvider.value)
    localStorage.setItem('sa_settings_ai_model', aiModel.value)
    localStorage.setItem('sa_settings_ai_endpoint', aiApiEndpoint.value)
    localStorage.setItem('sa_settings_ai_key', aiApiKey.value)

    // 同步到后端 config.yaml
    try {
        const resp = await fetch('/api/ai/config', {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({
                enabled: aiEnabled.value,
                provider: aiProvider.value,
                model: aiModel.value,
                base_url: aiApiEndpoint.value,
                api_key: aiApiKey.value
            })
        })
        if (!resp.ok) throw new Error('Failed to sync AI config to server')
    } catch (e) {
        console.error(e)
    }

    alert(t('settings.saved'))
    router.back()
}

function goBack() {
    router.back()
}

onMounted(async () => {
    // 加载配置
    await loadAiConfig()
    isLoading.value = false
    
    setTimeout(() => {
        isReady.value = true
    }, 100)
})

// 只有在未禁用动画时启动星空喵
if (!disableBackgroundAnimation.value) {
    useStarfield(canvasRef)
}
</script>

<template>
    <div class="settings-view-root">
        <canvas ref="canvasRef" class="background-canvas"></canvas>

        <div class="layout-container" :class="{ 'is-ready': isReady }">
            <aside class="sidebar">
                <header class="sidebar-header">
                    <div class="title-group">
                        <h1 class="title">{{ t('settings.title') }}</h1>
                        <p class="sa-subtitle">{{ t('settings.subtitle') }}</p>
                    </div>
                </header>

                <nav class="sidebar-nav">
                    <button class="nav-btn active">
                        <span>{{ t('settings.tab.general') }}</span>
                    </button>
                    <button class="nav-btn" disabled>
                        <span>{{ t('settings.tab.audio') }} (Coming Soon)</span>
                    </button>
                </nav>

                <footer class="sidebar-footer">
                    <button class="sa-btn" @click="goBack">{{ t('common.back') }}</button>
                </footer>
            </aside>

            <main class="content-panel">
                <div class="content-body">
                    <div v-if="isLoading" class="loading-state">
                        <span>{{ t('common.loading') }}...</span>
                    </div>
                    <div v-else class="settings-content">
                        <section class="settings-section">
                            <h2 class="section-title">{{ t('settings.section.graphics') }}</h2>
                            <div class="setting-item">
                                <label class="checkbox-label">
                                    <input type="checkbox" v-model="disableBackgroundAnimation" />
                                    <span>{{ t('settings.graphics.disableBgAnim') }}</span>
                                </label>
                                <p class="setting-hint">{{ t('settings.graphics.disableBgAnimHint') }}</p>
                            </div>
                        </section>

                        <section class="settings-section">
                            <h2 class="section-title">{{ t('settings.section.ai') }}</h2>
                            
                            <div v-if="loadError" class="error-hint">
                                {{ t('settings.ai.loadError') }}: {{ loadError }}
                            </div>

                            <div class="setting-item">
                                <label class="checkbox-label">
                                    <input type="checkbox" v-model="aiEnabled" />
                                    <span>{{ t('settings.ai.enable') }}</span>
                                </label>
                            </div>

                            <div class="form-group" v-if="aiEnabled">
                                <label>{{ t('settings.ai.provider') }}</label>
                                <select v-model="aiProvider" class="sa-select" @change="onProviderChange">
                                    <option value="openai">OpenAI</option>
                                    <option value="anthropic">Anthropic</option>
                                    <option value="deepseek">DeepSeek</option>
                                    <option value="local">Local (Ollama/LM Studio)</option>
                                </select>
                            </div>

                            <div class="form-group" v-if="aiEnabled">
                                <label>{{ t('settings.ai.model') }}</label>
                                <input v-model="aiModel" class="input"
                                    :placeholder="t('settings.ai.model.placeholder')" />
                            </div>

                            <div class="form-group" v-if="aiEnabled">
                                <label>{{ t('settings.ai.endpoint') }}</label>
                                <input v-model="aiApiEndpoint" class="input" placeholder="https://..." />
                            </div>

                            <div class="form-group" v-if="aiEnabled">
                                <label>{{ t('settings.ai.key') }}</label>
                                <input v-model="aiApiKey" type="password" class="input" placeholder="sk-..." />
                            </div>
                        </section>

                        <div class="footer-actions">
                            <button class="sa-btn primary" @click="saveSettings">{{ t('common.save') }}</button>
                        </div>
                    </div>
                </div>
            </main>
        </div>
    </div>
</template>

<style scoped>
.settings-view-root {
    --bg0: var(--sa-bg0, #070712);
    --bg1: var(--sa-bg1, #0b0a19);
    --panel: var(--sa-panel, rgba(13, 14, 26, 0.62));
    --stroke: var(--sa-stroke, rgba(196, 181, 253, 0.16));
    --text: var(--text-color);
    --muted: rgba(255, 255, 255, 0.72);

    position: relative;
    height: 100%;
    width: 100%;
    overflow: hidden;
    display: grid;
    place-items: center;
}

.background-canvas {
    position: fixed;
    inset: 0;
    z-index: -1;
    background-color: var(--background-color);
}

.layout-container {
    position: relative;
    width: 80%;
    height: 70%;
    min-width: 980px;
    max-width: 1200px;
    display: grid;
    grid-template-columns: 280px 1fr;
    background: linear-gradient(180deg, var(--panel), rgba(13, 14, 26, 0.46));
    border: 1px solid var(--stroke);
    backdrop-filter: blur(22px);
    opacity: 0;
    transform: translateY(8px);
    transition: all 0.55s cubic-bezier(0.25, 1, 0.5, 1);
}

.layout-container.is-ready {
    opacity: 1;
    transform: translateY(0);
}

.sidebar {
    padding: 24px;
    border-right: 1px solid var(--stroke);
    display: flex;
    flex-direction: column;
    gap: 24px;
}

.title-group .title {
    margin: 0;
    font-size: 24px;
    font-weight: 750;
}

.sa-subtitle {
    margin: 4px 0 0;
    font-size: 12px;
    opacity: 0.7;
}

.sidebar-nav {
    display: flex;
    flex-direction: column;
    gap: 8px;
    flex: 1;
}

.nav-btn {
    appearance: none;
    background: rgba(255, 255, 255, 0.05);
    border: 1px solid transparent;
    color: var(--text);
    padding: 12px;
    text-align: left;
    cursor: pointer;
    clip-path: polygon(0 0, calc(100% - 10px) 0, 100% 10px, 100% 100%, 0 100%);
}

.nav-btn.active {
    border-color: var(--sa-glow, #a855f7);
    background: rgba(168, 85, 247, 0.1);
}

.nav-btn:disabled {
    opacity: 0.4;
    cursor: not-allowed;
}

.content-panel {
    padding: 32px;
    overflow-y: auto;
}

.settings-section {
    margin-bottom: 32px;
}

.section-title {
    font-size: 18px;
    font-weight: 700;
    margin-bottom: 20px;
    padding-bottom: 8px;
    border-bottom: 1px solid var(--stroke);
}

.setting-item {
    margin-bottom: 16px;
}

.checkbox-label {
    display: flex;
    align-items: center;
    gap: 12px;
    cursor: pointer;
}

.setting-hint {
    font-size: 12px;
    opacity: 0.6;
    margin: 4px 0 0 28px;
}

.form-group {
    margin-top: 16px;
    display: flex;
    flex-direction: column;
    gap: 8px;
}

.form-group label {
    font-size: 13px;
    opacity: 0.8;
}

.footer-actions {
    margin-top: 40px;
    display: flex;
    justify-content: flex-end;
}

.loading-state {
    display: flex;
    align-items: center;
    justify-content: center;
    height: 200px;
    opacity: 0.6;
}

.error-hint {
    color: #ff6b6b;
    font-size: 13px;
    margin-bottom: 12px;
    padding: 8px 12px;
    background: rgba(255, 107, 107, 0.1);
    border: 1px solid rgba(255, 107, 107, 0.3);
    border-radius: 4px;
}
</style>

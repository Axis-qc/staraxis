import { createApp } from 'vue'
import './styles/base.css'
import './styles/ui.css'
import './styles/theme.css'
import './styles/controls.css'

import { createPinia } from 'pinia'
import { persistSessionStorage } from './stores/persist'

import App from './App.vue'
import router from './router'
import { i18n, loadAvailableLanguages, loadLanguage } from './i18n'

async function bootstrap() {
    const app = createApp(App)

    const pinia = createPinia()
    pinia.use(persistSessionStorage)
    app.use(pinia)

    app.use(router)
    app.use(i18n)

    try {
        await loadAvailableLanguages()
    } catch {
    }

    try {
        await loadLanguage(i18n.global.locale.value)
    } catch {
    }

    app.mount('#app')

    // 暴露API供外部插件/魔改前端使用
    // 使用方式：在浏览器控制台直接调用 window.StarAxisAPI.xxx
    const { useAuthStore } = await import('./stores/auth')
    const { useWorldSessionStore } = await import('./stores/worldSession')
    const { getLocalVisibleWorld } = await import('./game/world')

    window.StarAxisAPI = {
        // stores - 直接暴露，插件可以直接读取状态
        stores: {
            auth: useAuthStore(),
            worldSession: useWorldSessionStore(),
        },
        // 获取本地可见世界实例（包含所有实体数据）
        getLocalVisibleWorld,
        // 版本号，方便插件判断兼容性
        version: '1.0.0',
    }
    console.log('[StarAxis] API exposed to window.StarAxisAPI')
}

bootstrap()

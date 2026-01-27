import { createApp } from 'vue'
import './styles/ui.css'


import App from './App.vue'
import router from './router'
import { i18n, loadAvailableLanguages, loadLanguage } from './i18n'

async function bootstrap() {
    const app = createApp(App)
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
}

bootstrap()

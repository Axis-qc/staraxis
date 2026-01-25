import { createI18n } from 'vue-i18n'

export type I18nState = {
    currentLang: string
    availableLangs: string[]
}

const STORAGE_KEY = 'staraxis.lang'

function getInitialLang(): string {
    try {
        const saved = localStorage.getItem(STORAGE_KEY)
        if (saved && saved.trim()) {
            return saved.trim()
        }
    } catch {
    }
    const nav = navigator.language || 'zh'
    if (nav.toLowerCase().startsWith('zh')) {
        return 'zh'
    }
    return 'en'
}

export const i18nState: I18nState = {
    currentLang: getInitialLang(),
    availableLangs: [],
}

export const i18n = createI18n({
    legacy: false,
    locale: i18nState.currentLang,
    fallbackLocale: 'zh',
    messages: {},
})

export async function loadAvailableLanguages(): Promise<string[]> {
    const resp = await fetch('/api/i18n/languages')
    if (!resp.ok) {
        throw new Error(`HTTP ${resp.status}`)
    }
    const langs = (await resp.json()) as string[]
    i18nState.availableLangs = langs
    return langs
}

export async function loadLanguage(lang: string): Promise<void> {
    const target = (lang || '').trim()
    if (!target) {
        return
    }

    const resp = await fetch(`/api/i18n/${encodeURIComponent(target)}`)
    if (!resp.ok) {
        throw new Error(`HTTP ${resp.status}`)
    }
    const messages = (await resp.json()) as Record<string, string>

    i18n.global.setLocaleMessage(target, messages)
    i18n.global.locale.value = target
    i18nState.currentLang = target

    try {
        localStorage.setItem(STORAGE_KEY, target)
    } catch {
    }
}

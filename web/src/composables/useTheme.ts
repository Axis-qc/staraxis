import { computed, onMounted, ref } from 'vue'

export type ThemeId = 'amethyst' | 'neonCyan' | 'ember' | 'forest'

export type ThemeTokens = {
    id: ThemeId
    label: string
    vars: Record<string, string>
}

const STORAGE_KEY = 'sa.theme'

const themes: ThemeTokens[] = [
    {
        id: 'amethyst',
        label: '紫晶',
        vars: {
            '--sa-bg0': '#070712',
            '--sa-bg1': '#0b0a19',
            '--sa-panel': 'rgba(13, 14, 26, 0.62)',
            '--sa-panel-strong': 'rgba(13, 14, 26, 0.78)',
            '--sa-stroke': 'rgba(196, 181, 253, 0.16)',
            '--sa-stroke-strong': 'rgba(196, 181, 253, 0.24)',
            '--sa-text': 'rgba(244, 244, 255, 0.92)',
            '--sa-muted': 'rgba(220, 216, 255, 0.72)',
            '--sa-glow': 'rgba(168, 85, 247, 0.55)',
            '--sa-glow-soft': 'rgba(168, 85, 247, 0.24)',
            '--sa-accent': 'rgba(168, 85, 247, 0.90)',
            '--sa-accent2': 'rgba(34, 211, 238, 0.22)',

            '--glow-color': 'rgba(168, 85, 247, 0.90)',
            '--border-color': 'rgba(196, 181, 253, 0.28)',
            '--panel-bg': 'rgba(13, 14, 26, 0.62)',
            '--background-color': '#070712',
            '--text-color': 'rgba(244, 244, 255, 0.92)',
            '--text-color-hover': '#ffffff',
            '--primary-color': 'rgba(168, 85, 247, 0.90)',
            '--danger-color': 'hsl(0, 80%, 55%)',
            '--selection-bg': 'rgba(168, 85, 247, 0.28)',
        },
    },
    {
        id: 'neonCyan',
        label: '霓蓝',
        vars: {
            '--sa-bg0': '#050a12',
            '--sa-bg1': '#061426',
            '--sa-panel': 'rgba(6, 18, 34, 0.62)',
            '--sa-panel-strong': 'rgba(6, 18, 34, 0.78)',
            '--sa-stroke': 'rgba(34, 211, 238, 0.18)',
            '--sa-stroke-strong': 'rgba(34, 211, 238, 0.28)',
            '--sa-text': 'rgba(236, 254, 255, 0.92)',
            '--sa-muted': 'rgba(203, 251, 255, 0.70)',
            '--sa-glow': 'rgba(34, 211, 238, 0.45)',
            '--sa-glow-soft': 'rgba(34, 211, 238, 0.18)',
            '--sa-accent': 'rgba(34, 211, 238, 0.92)',
            '--sa-accent2': 'rgba(168, 85, 247, 0.18)',

            '--glow-color': 'rgba(34, 211, 238, 0.92)',
            '--border-color': 'rgba(34, 211, 238, 0.28)',
            '--panel-bg': 'rgba(6, 18, 34, 0.62)',
            '--background-color': '#050a12',
            '--text-color': 'rgba(236, 254, 255, 0.92)',
            '--text-color-hover': '#ffffff',
            '--primary-color': 'rgba(34, 211, 238, 0.92)',
            '--danger-color': 'hsl(0, 80%, 55%)',
            '--selection-bg': 'rgba(34, 211, 238, 0.26)',
        },
    },
    {
        id: 'ember',
        label: '余烬',
        vars: {
            '--sa-bg0': '#0b0708',
            '--sa-bg1': '#12090c',
            '--sa-panel': 'rgba(20, 10, 12, 0.62)',
            '--sa-panel-strong': 'rgba(20, 10, 12, 0.78)',
            '--sa-stroke': 'rgba(251, 113, 133, 0.16)',
            '--sa-stroke-strong': 'rgba(251, 113, 133, 0.26)',
            '--sa-text': 'rgba(255, 245, 246, 0.92)',
            '--sa-muted': 'rgba(255, 214, 222, 0.72)',
            '--sa-glow': 'rgba(244, 63, 94, 0.42)',
            '--sa-glow-soft': 'rgba(244, 63, 94, 0.16)',
            '--sa-accent': 'rgba(244, 63, 94, 0.90)',
            '--sa-accent2': 'rgba(251, 191, 36, 0.16)',

            '--glow-color': 'rgba(244, 63, 94, 0.90)',
            '--border-color': 'rgba(251, 113, 133, 0.28)',
            '--panel-bg': 'rgba(20, 10, 12, 0.62)',
            '--background-color': '#0b0708',
            '--text-color': 'rgba(255, 245, 246, 0.92)',
            '--text-color-hover': '#ffffff',
            '--primary-color': 'rgba(244, 63, 94, 0.90)',
            '--danger-color': 'hsl(0, 80%, 55%)',
            '--selection-bg': 'rgba(244, 63, 94, 0.26)',
        },
    },
    {
        id: 'forest',
        label: '深林',
        vars: {
            '--sa-bg0': '#060a08',
            '--sa-bg1': '#07110d',
            '--sa-panel': 'rgba(7, 16, 13, 0.62)',
            '--sa-panel-strong': 'rgba(7, 16, 13, 0.78)',
            '--sa-stroke': 'rgba(52, 211, 153, 0.16)',
            '--sa-stroke-strong': 'rgba(52, 211, 153, 0.26)',
            '--sa-text': 'rgba(236, 253, 245, 0.92)',
            '--sa-muted': 'rgba(167, 243, 208, 0.70)',
            '--sa-glow': 'rgba(52, 211, 153, 0.38)',
            '--sa-glow-soft': 'rgba(52, 211, 153, 0.14)',
            '--sa-accent': 'rgba(52, 211, 153, 0.90)',
            '--sa-accent2': 'rgba(34, 211, 238, 0.14)',

            '--glow-color': 'rgba(52, 211, 153, 0.90)',
            '--border-color': 'rgba(52, 211, 153, 0.28)',
            '--panel-bg': 'rgba(7, 16, 13, 0.62)',
            '--background-color': '#060a08',
            '--text-color': 'rgba(236, 253, 245, 0.92)',
            '--text-color-hover': '#ffffff',
            '--primary-color': 'rgba(52, 211, 153, 0.90)',
            '--danger-color': 'hsl(0, 80%, 55%)',
            '--selection-bg': 'rgba(52, 211, 153, 0.24)',
        },
    },
]

const themeId = ref<ThemeId>('amethyst')

function applyThemeToRoot(id: ThemeId) {
    const t = themes.find(x => x.id === id)
    if (!t) return

    const el = document.documentElement
    for (const [k, v] of Object.entries(t.vars)) el.style.setProperty(k, v)
    themeId.value = t.id
    try {
        localStorage.setItem(STORAGE_KEY, t.id)
    } catch {
    }
}

function loadThemeFromStorage(): ThemeId | null {
    try {
        const v = localStorage.getItem(STORAGE_KEY) as ThemeId | null
        return v
    } catch {
        return null
    }
}

export function useTheme() {
    const list = computed(() => themes)

    onMounted(() => {
        const stored = loadThemeFromStorage()
        if (stored && themes.some(x => x.id === stored)) applyThemeToRoot(stored)
        else applyThemeToRoot(themeId.value)
    })

    return {
        themeId,
        themes: list,
        applyTheme: applyThemeToRoot,
    }
}

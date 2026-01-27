import { computed, onMounted, ref } from 'vue'

/**
 * @file useTheme.ts
 *
 * @description
 * 主题管理（组合式函数）。定义一组可切换的主题 token，并在运行时写入到 `document.documentElement`
 * 的 CSS 变量中，从而驱动全站 UI 配色（按钮/边框/高亮/选中文本等）。
 *
 * 本文件支持两种主题来源：
 * - 预设主题（amethyst/neonCyan/ember/forest）
 * - 自定义主题（用户通过 Color Picker 选择主色，自动派生高亮/描边/选中等）
 *
 * @usage
 * - 视图/组件通过 `useTheme()` 获取：
 *   - `themeId`：当前预设主题 id（响应式；自定义模式下保留为最近一次的预设 id）
 *   - `themes`：可选预设主题列表
 *   - `mode`：当前模式（'preset' | 'custom'）
 *   - `customPrimaryHex`：自定义主色（hex）
 *   - `applyTheme(id)`：切换到某个预设主题
 *   - `applyCustomTheme(hex)`：切换到自定义主题并应用
 *   - `setMode(mode)`：切换模式（会应用对应主题）
 *
 * @provides
 * - **主题变量写入**：遍历主题 `vars`，调用 `document.documentElement.style.setProperty` 写入。
 * - **持久化**：使用 `localStorage` 保存主题模式/预设主题/自定义主色。
 *
 * @api
 * - 本模块不调用后端 API。
 * - 使用浏览器 Web API：
 *   - `document.documentElement.style.setProperty`（应用主题）
 *   - `localStorage`（持久化主题选择）
 *
 * @resources
 * - CSS 变量约定：
 *   - `--sa-*`：主题源变量（背景/面板/描边/文本/主色等）
 *   - 兼容映射变量：`--glow-color/--border-color/--text-color/...` 供 `ui.css/theme.css` 使用
 *   - `--selection-bg`：文本选中高亮背景色
 *
 * @potential_issues
 * - **SSR/非浏览器环境**：如果将来引入 SSR，需要避免在服务端直接访问 `document/localStorage`。
 */

export type ThemeId = 'amethyst' | 'neonCyan' | 'ember' | 'forest'
export type ThemeMode = 'preset' | 'custom'

export type ThemeTokens = {
    id: ThemeId
    label: string
    vars: Record<string, string>
}

const STORAGE_KEY_PRESET = 'sa.theme'
const STORAGE_KEY_MODE = 'sa.theme.mode'
const STORAGE_KEY_CUSTOM_PRIMARY = 'sa.theme.custom.primary'

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
const mode = ref<ThemeMode>('preset')
const customPrimaryHex = ref<string>('#a855f7')

function safeGet(key: string): string | null {
    try {
        return localStorage.getItem(key)
    } catch {
        return null
    }
}

function safeSet(key: string, value: string): void {
    try {
        localStorage.setItem(key, value)
    } catch {
    }
}

function setCssVars(vars: Record<string, string>) {
    const el = document.documentElement
    for (const [k, v] of Object.entries(vars)) el.style.setProperty(k, v)
}

function clamp(n: number, min: number, max: number) {
    return Math.max(min, Math.min(max, n))
}

function hexToRgb(hex: string): { r: number; g: number; b: number } | null {
    const h = (hex || '').trim().replace('#', '')
    if (!/^[0-9a-fA-F]{6}$/.test(h)) return null
    const r = parseInt(h.slice(0, 2), 16)
    const g = parseInt(h.slice(2, 4), 16)
    const b = parseInt(h.slice(4, 6), 16)
    return { r, g, b }
}

function rgbToHsl(r: number, g: number, b: number): { h: number; s: number; l: number } {
    r /= 255
    g /= 255
    b /= 255
    const max = Math.max(r, g, b)
    const min = Math.min(r, g, b)
    let h = 0
    let s = 0
    const l = (max + min) / 2

    if (max !== min) {
        const d = max - min
        s = l > 0.5 ? d / (2 - max - min) : d / (max + min)
        switch (max) {
            case r:
                h = (g - b) / d + (g < b ? 6 : 0)
                break
            case g:
                h = (b - r) / d + 2
                break
            case b:
                h = (r - g) / d + 4
                break
        }
        h /= 6
    }

    return { h: h * 360, s, l }
}

function hslToRgb(h: number, s: number, l: number): { r: number; g: number; b: number } {
    h = ((h % 360) + 360) % 360
    h /= 360

    if (s === 0) {
        const v = Math.round(l * 255)
        return { r: v, g: v, b: v }
    }

    const hue2rgb = (p: number, q: number, t: number) => {
        if (t < 0) t += 1
        if (t > 1) t -= 1
        if (t < 1 / 6) return p + (q - p) * 6 * t
        if (t < 1 / 2) return q
        if (t < 2 / 3) return p + (q - p) * (2 / 3 - t) * 6
        return p
    }

    const q = l < 0.5 ? l * (1 + s) : l + s - l * s
    const p = 2 * l - q
    const r = hue2rgb(p, q, h + 1 / 3)
    const g = hue2rgb(p, q, h)
    const b = hue2rgb(p, q, h - 1 / 3)
    return { r: Math.round(r * 255), g: Math.round(g * 255), b: Math.round(b * 255) }
}

function rgba({ r, g, b }: { r: number; g: number; b: number }, a: number) {
    return `rgba(${r}, ${g}, ${b}, ${a})`
}

function buildCustomVars(primaryHex: string): Record<string, string> {
    const rgb = hexToRgb(primaryHex) || { r: 168, g: 85, b: 247 }
    const hsl = rgbToHsl(rgb.r, rgb.g, rgb.b)

    // 方案：背景固定为暗色（用户选择 1A）
    const bg0 = '#070712'
    const bg1 = '#0b0a19'

    // 次色：与主色互补/对比（用户选择 2B）
    const secondaryHue = (hsl.h + 180) % 360
    const secondaryRgb = hslToRgb(secondaryHue, clamp(hsl.s, 0.55, 0.95), clamp(hsl.l, 0.42, 0.62))

    // 面板与文字维持暗色基底
    const panel = 'rgba(13, 14, 26, 0.62)'
    const panelStrong = 'rgba(13, 14, 26, 0.78)'

    // 依据主色派生透明度层
    const glow = rgba(rgb, 0.55)
    const glowSoft = rgba(rgb, 0.22)
    const accent = rgba(rgb, 0.9)
    const border = rgba(rgb, 0.28)
    const stroke = rgba(rgb, 0.16)
    const strokeStrong = rgba(rgb, 0.24)
    const selection = rgba(rgb, 0.26)

    const text = 'rgba(244, 244, 255, 0.92)'
    const muted = 'rgba(220, 216, 255, 0.72)'

    const secondary = rgba(secondaryRgb, 0.18)

    return {
        '--sa-bg0': bg0,
        '--sa-bg1': bg1,
        '--sa-panel': panel,
        '--sa-panel-strong': panelStrong,
        '--sa-stroke': stroke,
        '--sa-stroke-strong': strokeStrong,
        '--sa-text': text,
        '--sa-muted': muted,
        '--sa-glow': glow,
        '--sa-glow-soft': glowSoft,
        '--sa-accent': accent,
        '--sa-accent2': secondary,

        '--glow-color': accent,
        '--border-color': border,
        '--panel-bg': panel,
        '--background-color': bg0,
        '--text-color': text,
        '--text-color-hover': '#ffffff',
        '--primary-color': accent,
        '--danger-color': 'hsl(0, 80%, 55%)',
        '--selection-bg': selection,
    }
}

function applyPresetTheme(id: ThemeId) {
    const t = themes.find(x => x.id === id)
    if (!t) return
    setCssVars(t.vars)
    themeId.value = t.id
    mode.value = 'preset'
    safeSet(STORAGE_KEY_PRESET, t.id)
    safeSet(STORAGE_KEY_MODE, 'preset')
}

function applyCustomTheme(primaryHex: string) {
    const hex = (primaryHex || '').trim()
    customPrimaryHex.value = hex
    mode.value = 'custom'
    setCssVars(buildCustomVars(hex))
    safeSet(STORAGE_KEY_MODE, 'custom')
    safeSet(STORAGE_KEY_CUSTOM_PRIMARY, hex)
}

function setMode(next: ThemeMode) {
    if (next === 'custom') {
        applyCustomTheme(customPrimaryHex.value)
        return
    }
    applyPresetTheme(themeId.value)
}

function loadPresetFromStorage(): ThemeId | null {
    try {
        const v = localStorage.getItem(STORAGE_KEY_PRESET) as ThemeId | null
        return v
    } catch {
        return null
    }
}

export function useTheme() {
    const list = computed(() => themes)

    onMounted(() => {
        const savedMode = safeGet(STORAGE_KEY_MODE) as ThemeMode | null
        const savedCustom = safeGet(STORAGE_KEY_CUSTOM_PRIMARY)

        const storedPreset = loadPresetFromStorage()
        if (storedPreset && themes.some(x => x.id === storedPreset)) {
            themeId.value = storedPreset
        }

        if (savedCustom && /^#?[0-9a-fA-F]{6}$/.test(savedCustom)) {
            customPrimaryHex.value = savedCustom.startsWith('#') ? savedCustom : `#${savedCustom}`
        }

        if (savedMode === 'custom') {
            applyCustomTheme(customPrimaryHex.value)
            return
        }

        applyPresetTheme(themeId.value)
    })

    return {
        themeId,
        themes: list,
        mode,
        customPrimaryHex,
        applyTheme: applyPresetTheme,
        applyCustomTheme,
        setMode,
    }
}

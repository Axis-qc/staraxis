<script setup lang="ts">
/**
 * @file ThemePicker.vue
 *
 * @description
 * 全局主题切换浮动组件（右下角）。用于在运行时切换主题色系，并即时写入 CSS 变量影响全站 UI。
 *
 * @usage
 * - 作为全局组件挂载在 `App.vue` 中，常驻页面。
 * - 依赖 `useTheme()` 进行主题列表读取与切换（预设主题 + 自定义主色）。
 * - 依赖 `vue-i18n` 获取本地化文案。
 *
 * @provides
 * - 预设主题列表切换。
 * - 自定义主色（Color Picker）切换：用户选择主色后自动派生高亮/描边/选中等变量。
 *
 * @api
 * - 本组件不直接调用后端 API。
 * - 间接使用浏览器 DOM API 与 localStorage（由 `useTheme` 内部完成）。
 *
 * @resources
 * - `../composables/useTheme`：主题定义与应用逻辑。
 * - `vue-i18n`：`t('theme.*')` 文案。
 * - 主题变量：通过 `useTheme` 写入 `document.documentElement` 的 CSS 变量（如 `--glow-color` 等）。
 *
 * @potential_issues
 * - **SSR**：若未来做 SSR，需要确保对 `document/localStorage` 的访问仅在客户端执行。
 * - **可访问性**：目前为轻量菜单实现，后续可增加键盘导航/焦点管理。
 */

import { computed, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { useTheme } from '../composables/useTheme'

const { t } = useI18n()
const { themeId, themes, mode, customPrimaryHex, applyTheme, applyCustomTheme, setMode } = useTheme()
const open = ref(false)

const currentPreset = computed(() => themes.value.find(x => x.id === themeId.value))

const colorInputRef = ref<HTMLInputElement | null>(null)

function onPickColor(e: Event) {
  const v = (e.target as HTMLInputElement).value
  applyCustomTheme(v)
}

function normalizeHex(v: string): string {
  const raw = (v || '').trim()
  const s = raw.startsWith('#') ? raw.slice(1) : raw
  if (/^[0-9a-fA-F]{6}$/.test(s)) return `#${s.toLowerCase()}`
  return customPrimaryHex.value
}

function onHexInput(e: Event) {
  const v = (e.target as HTMLInputElement).value
  const hex = normalizeHex(v)
  applyCustomTheme(hex)
}

function openSystemPicker() {
  colorInputRef.value?.click()
}
</script>

<template>
  <div class="theme-picker" :class="{ open }" @mouseenter="open = true" @mouseleave="open = false">
    <button class="theme-toggle" type="button" :aria-expanded="open">
      <span class="dot" :style="{ background: 'var(--sa-accent)' }" />
      <span class="label">{{ t('theme.label') }}</span>
      <span class="chev">{{ open ? '×' : '▾' }}</span>
    </button>

    <transition name="slide-fade">
      <div v-if="open" class="panel" role="menu" :aria-label="t('theme.ariaLabel')">
      <div class="section-title">{{ t('theme.section.presets') }}</div>
      <button
        v-for="preset in themes"
        :key="preset.id"
        class="theme-card"
        type="button"
        :class="{ active: mode === 'preset' && preset.id === themeId }"
        @click="applyTheme(preset.id)"
      >
        <span class="swatch" :style="{ background: preset.vars['--sa-accent'] }" />
        <span class="name">{{ t(`theme.name.${preset.id}`) }}</span>
        <span class="hint" v-if="mode === 'preset' && preset.id === (currentPreset?.id)">{{ t('theme.current') }}</span>
      </button>

      <div class="section-title">{{ t('theme.section.custom') }}</div>
      <div class="custom-row">
        <div class="custom-line">
          <span class="custom-text">{{ t('theme.colorPicker') }}</span>

          <button
            type="button"
            class="color-swatch-btn"
            :style="{ background: customPrimaryHex }"
            @click="openSystemPicker"
            :aria-label="t('theme.colorPicker')"
          />

          <input
            ref="colorInputRef"
            class="color-input"
            type="color"
            :value="customPrimaryHex"
            @input="onPickColor"
            aria-hidden="true"
            tabindex="-1"
          />

          <input
            class="hex-input"
            type="text"
            :value="customPrimaryHex"
            @input="onHexInput"
            spellcheck="false"
            inputmode="text"
            aria-label="HEX"
          />
        </div>

        <div class="custom-actions">
          <button
            class="sa-btn mini"
            type="button"
            :disabled="mode === 'custom'"
            @click="setMode('custom')"
          >
            {{ t('theme.useCustom') }}
          </button>
          <button
            class="sa-btn mini"
            type="button"
            :disabled="mode === 'preset'"
            @click="setMode('preset')"
          >
            {{ t('theme.usePreset') }}
          </button>
        </div>
      </div>
    </div>
  </transition>
</div>
</template>

<style scoped>
.theme-picker {
  position: fixed;
  right: 16px;
  bottom: 16px;
  z-index: 50;
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.theme-toggle {
  display: inline-flex;
  align-items: center;
  gap: 10px;
  padding: 10px 12px;
  border-radius: 999px;
  border: 1px solid rgba(196, 181, 253, 0.18);
  background: rgba(13, 14, 26, 0.55);
  color: rgba(244, 244, 255, 0.92);
  cursor: pointer;
  backdrop-filter: blur(16px);
  -webkit-backdrop-filter: blur(16px);
  box-shadow: 0 14px 34px rgba(0, 0, 0, 0.35);
}

.theme-toggle:hover {
  border-color: rgba(168, 85, 247, 0.34);
  box-shadow: 0 0 0 1px rgba(168, 85, 247, 0.10), 0 18px 40px rgba(0, 0, 0, 0.42);
}

.dot {
  width: 10px;
  height: 10px;
  border-radius: 999px;
  box-shadow: 0 0 18px rgba(168, 85, 247, 0.35);
}

.label {
  font-size: 13px;
  letter-spacing: 0.6px;
}

.chev {
  opacity: 0.8;
  width: 18px;
  text-align: center;
}

.panel {
  width: 240px;
  padding: 10px;
  border-radius: 16px;
  border: 1px solid rgba(196, 181, 253, 0.16);
  background: rgba(13, 14, 26, 0.52);
  backdrop-filter: blur(18px);
  -webkit-backdrop-filter: blur(18px);
  box-shadow: 0 22px 60px rgba(0, 0, 0, 0.52);
  display: grid;
  gap: 8px;
}

.section-title {
  font-size: 12px;
  opacity: 0.75;
  letter-spacing: 0.6px;
  padding: 6px 6px 2px;
}

.theme-card {
  display: grid;
  grid-template-columns: 14px 1fr auto;
  align-items: center;
  gap: 10px;
  padding: 10px 10px;
  border-radius: 12px;
  border: 1px solid rgba(196, 181, 253, 0.14);
  background: rgba(13, 14, 26, 0.28);
  color: rgba(244, 244, 255, 0.90);
  cursor: pointer;
  text-align: left;
}

.theme-card:hover {
  border-color: rgba(168, 85, 247, 0.30);
  box-shadow: 0 0 0 1px rgba(168, 85, 247, 0.08);
}

.theme-card.active {
  border-color: rgba(168, 85, 247, 0.42);
  box-shadow: 0 0 0 1px rgba(168, 85, 247, 0.14);
}

.swatch {
  width: 12px;
  height: 12px;
  border-radius: 999px;
}

.name {
  font-size: 13px;
}

.hint {
  font-size: 12px;
  opacity: 0.7;
}

.custom-row {
  display: grid;
  grid-template-columns: 1fr;
  gap: 10px;
  padding: 10px;
  border: 1px dashed rgba(255, 255, 255, 0.14);
  border-radius: 12px;
}

.custom-line {
  display: grid;
  grid-template-columns: 1fr auto auto;
  align-items: center;
  gap: 10px;
}

.custom-text {
  font-size: 12px;
  opacity: 0.85;
}

.color-swatch-btn {
  width: 30px;
  height: 30px;
  border-radius: 10px;
  border: 1px solid rgba(255, 255, 255, 0.22);
  box-shadow: 0 0 0 2px rgba(0, 0, 0, 0.18);
  cursor: pointer;
}

.color-swatch-btn:hover {
  border-color: var(--glow-color);
  box-shadow: 0 0 0 2px var(--selection-bg, rgba(0, 191, 255, 0.25));
}

.color-swatch-btn:focus {
  outline: none;
  border-color: var(--glow-color);
  box-shadow: 0 0 0 2px var(--selection-bg, rgba(0, 191, 255, 0.25));
}

.color-input {
  position: absolute;
  width: 1px;
  height: 1px;
  padding: 0;
  margin: -1px;
  overflow: hidden;
  clip: rect(0, 0, 0, 0);
  white-space: nowrap;
  border: 0;
}

.hex-input {
  width: 92px;
  height: 30px;
  padding: 0 10px;
  border-radius: 10px;
  background: rgba(0, 0, 0, 0.22);
  border: 1px solid var(--border-color);
  color: var(--text-color);
  outline: none;
  font-family: ui-monospace, SFMono-Regular, Menlo, Monaco, Consolas, 'Liberation Mono', 'Courier New', monospace;
  letter-spacing: 0.6px;
}

.hex-input:focus {
  border-color: var(--glow-color);
  box-shadow: 0 0 0 2px var(--selection-bg, rgba(0, 191, 255, 0.25));
}

.custom-actions {
  display: flex;
  gap: 8px;
  justify-content: flex-end;
}

.slide-fade-enter-active,
.slide-fade-leave-active {
  transition: all 0.2s ease-out;
}

.slide-fade-enter-from,
.slide-fade-leave-to {
  transform: translateY(10px);
  opacity: 0;
}
</style>

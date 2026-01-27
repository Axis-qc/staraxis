<script setup lang="ts">
import { computed, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { useTheme } from '../composables/useTheme'

const { t } = useI18n()
const { themeId, themes, applyTheme } = useTheme()
const open = ref(false)

const current = computed(() => themes.value.find(x => x.id === themeId.value))

const themeLabelKey = computed(() => {
  return `theme.name.${themeId.value}`
})
</script>

<template>
  <div class="theme-picker" :class="{ open }">
    <button class="theme-toggle" type="button" @click="open = !open" :aria-expanded="open">
      <span class="dot" :style="{ background: 'var(--sa-accent)' }" />
      <span class="label">{{ t('theme.label') }}</span>
      <span class="chev">{{ open ? '×' : '▾' }}</span>
    </button>

    <div v-if="open" class="panel" role="menu" :aria-label="t('theme.ariaLabel')">
      <button
        v-for="theme in themes"
        :key="theme.id"
        class="theme-card"
        type="button"
        :class="{ active: theme.id === themeId }"
        @click="applyTheme(theme.id)"
      >
        <span class="swatch" :style="{ background: theme.vars['--sa-accent'] }" />
        <span class="name">{{ t(`theme.name.${theme.id}`) }}</span>
        <span class="hint" v-if="theme.id === (current?.id)">{{ t('theme.current') }}</span>
      </button>
    </div>
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
  width: 180px;
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
</style>

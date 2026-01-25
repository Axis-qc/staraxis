<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { RouterLink } from 'vue-router'
import { useI18n } from 'vue-i18n'
import { i18nState, loadAvailableLanguages, loadLanguage } from '../i18n'

const { t } = useI18n()

const langs = ref<string[]>([])
const selected = ref<string>(i18nState.currentLang)
const err = ref<string | null>(null)

async function refreshLangs() {
  err.value = null
  try {
    langs.value = await loadAvailableLanguages()
  } catch (e) {
    err.value = (e as Error).message
  }
}

async function onChangeLang() {
  err.value = null
  try {
    await loadLanguage(selected.value)
  } catch (e) {
    err.value = (e as Error).message
  }
}

onMounted(() => {
  refreshLangs()
})
</script>

<template>
  <div class="sa-page">
    <div class="sa-shell">
      <div class="sa-topbar">
        <div>
          <div class="sa-title">{{ t('settings.title') }}</div>
          <div class="sa-subtitle">{{ t('lang.current') }}: {{ i18nState.currentLang }}</div>
        </div>
        <RouterLink class="sa-btn" to="/">{{ t('dialog.developing.confirm') }}</RouterLink>
      </div>

      <div class="sa-card">
        <div class="sa-card-header">
          <div class="sa-card-title">{{ t('lang.current') }}</div>
          <div>
            <button class="sa-btn" @click="refreshLangs">{{ t('mainMenu.action.refresh') }}</button>
          </div>
        </div>
        <div class="sa-card-body">
          <div style="display: flex; gap: 12px; align-items: center; flex-wrap: wrap">
            <select class="sa-select" v-model="selected" @change="onChangeLang">
              <option v-for="l in langs" :key="l" :value="l">{{ l }}</option>
            </select>
            <div class="sa-tag">{{ t('lang.selfName') }}</div>
            <div v-if="err" class="error">{{ err }}</div>
          </div>
        </div>
      </div>

      <div class="sa-card" style="margin-top: 16px">
        <div class="sa-card-header">
          <div class="sa-card-title">{{ t('settings.tab.other') }}</div>
        </div>
        <div class="sa-card-body">
          <div class="sa-tag warn">{{ t('mainMenu.tag.developing') }}</div>
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped>
.error {
  color: #ff9aa8;
}
</style>

<script setup lang="ts">
import { computed } from 'vue'
import { useRoute, RouterLink } from 'vue-router'
import { useI18n } from 'vue-i18n'

const route = useRoute()
const { t } = useI18n()

const title = computed(() => {
  const name = String(route.name || '')
  if (name === 'multiplayer') return t('mainMenu.multiplayer')
  if (name === 'ship-designer') return t('mainMenu.shipDesigner')
  if (name === 'load-game') return t('mainMenu.loadGame')
  return t('dialog.developing.title')
})
</script>

<template>
  <div class="sa-page">
    <div class="sa-shell">
      <div class="sa-topbar">
        <div>
          <div class="sa-title">{{ title }}</div>
          <div class="sa-subtitle">{{ t('dialog.developing.text') }}</div>
        </div>
        <RouterLink class="sa-btn" to="/">{{ t('dialog.developing.confirm') }}</RouterLink>
      </div>

      <div class="sa-card">
        <div class="sa-card-body">
          <div class="sa-tag warn">{{ t('mainMenu.tag.developing') }}</div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, onUnmounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { useI18n } from 'vue-i18n'
import { fetchStatus, requestQuit, createWsClient, type BackendStatus, type WsState } from '../services/backend'
import { i18nState, loadAvailableLanguages, loadLanguage } from '../i18n'

const { t } = useI18n()
const router = useRouter()

const status = ref<BackendStatus | null>(null)
const statusError = ref<string | null>(null)

const wsState = ref<WsState>('disconnected')
const wsError = ref<string | null>(null)
const lastWsMessage = ref<string | null>(null)

const quitting = ref(false)
const quitError = ref<string | null>(null)

const langs = ref<string[]>([])
const selectedLang = ref<string>(i18nState.currentLang)

const wsClient = createWsClient('/ws')
let offState: (() => void) | null = null
let offMsg: (() => void) | null = null
let offErr: (() => void) | null = null

const wsStateText = computed(() => {
  if (wsState.value === 'disconnected') return t('mainMenu.status.disconnected')
  if (wsState.value === 'connecting') return t('mainMenu.status.connecting')
  if (wsState.value === 'connected') return t('mainMenu.status.connected')
  return t('mainMenu.status.error')
})

async function refreshStatus() {
  statusError.value = null
  try {
    status.value = await fetchStatus()
  } catch (e) {
    status.value = null
    statusError.value = (e as Error).message
  }
}

function connectWs() {
  wsError.value = null
  lastWsMessage.value = null
  wsClient.connect()
}

function disconnectWs() {
  wsClient.disconnect()
}

function pingWs() {
  wsClient.sendText('ping')
}

async function refreshLangs() {
  try {
    langs.value = await loadAvailableLanguages()
  } catch {
    langs.value = []
  }
}

async function onChangeLang() {
  try {
    await loadLanguage(selectedLang.value)
  } catch {
  }
}

async function quitServer() {
  if (quitting.value) return
  quitting.value = true
  quitError.value = null
  try {
    await requestQuit()
  } catch (e) {
    quitError.value = (e as Error).message
  } finally {
    quitting.value = false
  }
}

function developing(label: string) {
  window.alert(t('mainMenu.log.developing', [label]))
}

onMounted(() => {
  refreshLangs()

  offState = wsClient.onStateChange((s) => (wsState.value = s))
  offMsg = wsClient.onMessage((m) => (lastWsMessage.value = m))
  offErr = wsClient.onError((e) => (wsError.value = e))

  refreshStatus()
  connectWs()
})

onUnmounted(() => {
  offState?.()
  offMsg?.()
  offErr?.()
  disconnectWs()
})
</script>

<template>
  <div class="sa-page">
    <div class="sa-shell">
      <div class="sa-topbar">
        <div>
          <div class="sa-title">{{ t('app.title') }}</div>
          <div class="sa-subtitle">{{ t('mainMenu.web.subtitle') }}</div>
        </div>

        <div style="display: flex; gap: 10px; align-items: center; flex-wrap: wrap">
          <label class="sa-tag">
            <span style="opacity: 0.9">{{ t('lang.current') }}</span>
            <select class="sa-select" v-model="selectedLang" @change="onChangeLang">
              <option v-for="l in langs" :key="l" :value="l">{{ l }}</option>
            </select>
          </label>
          <button class="sa-btn" @click="router.push('/settings')">{{ t('mainMenu.settings') }}</button>
        </div>
      </div>

      <div class="sa-grid">
        <div
          class="sa-menu-item"
          role="button"
          tabindex="0"
          @click="developing(t('mainMenu.newGame'))"
        >
          <div class="sa-menu-title">
            <div>{{ t('mainMenu.newGame') }}</div>
          </div>
          <div class="sa-menu-desc">{{ t('mainMenu.desc.newGame') }}</div>
        </div>

        <div class="sa-menu-item" role="button" tabindex="0" @click="router.push('/load-game')">
          <div class="sa-menu-title">
            <div>{{ t('mainMenu.loadGame') }}</div>
            <span class="sa-tag warn">{{ t('mainMenu.tag.developing') }}</span>
          </div>
          <div class="sa-menu-desc">{{ t('mainMenu.desc.loadGame') }}</div>
        </div>

        <div class="sa-menu-item" role="button" tabindex="0" @click="router.push('/multiplayer')">
          <div class="sa-menu-title">
            <div>{{ t('mainMenu.multiplayer') }}</div>
            <span class="sa-tag warn">{{ t('mainMenu.tag.developing') }}</span>
          </div>
          <div class="sa-menu-desc">{{ t('mainMenu.desc.multiplayer') }}</div>
        </div>

        <div class="sa-menu-item" role="button" tabindex="0" @click="router.push('/ship-designer')">
          <div class="sa-menu-title">
            <div>{{ t('mainMenu.shipDesigner') }}</div>
            <span class="sa-tag warn">{{ t('mainMenu.tag.developing') }}</span>
          </div>
          <div class="sa-menu-desc">{{ t('mainMenu.desc.shipDesigner') }}</div>
        </div>
      </div>

      <div style="margin-top: 16px" class="sa-card">
        <div class="sa-card-header">
          <div class="sa-card-title">{{ t('mainMenu.panel.localConnection') }}</div>
          <div style="display: flex; gap: 10px; align-items: center; flex-wrap: wrap">
            <button class="sa-btn" @click="refreshStatus">{{ t('mainMenu.action.refresh') }}</button>
            <button class="sa-btn" @click="connectWs" :disabled="wsState === 'connecting' || wsState === 'connected'">
              {{ t('mainMenu.action.connect') }}
            </button>
            <button class="sa-btn" @click="pingWs" :disabled="wsState !== 'connected'">{{ t('mainMenu.action.ping') }}</button>
            <button class="sa-btn danger" @click="quitServer" :disabled="quitting">
              {{ t('mainMenu.exit') }}
            </button>
          </div>
        </div>

        <div class="sa-card-body">
          <div class="sa-kv">
            <div class="k">{{ t('mainMenu.label.http') }}</div>
            <div class="v">/api/status</div>

            <div class="k">{{ t('mainMenu.label.ws') }}</div>
            <div class="v">{{ wsClient.getUrl() }}</div>

            <div class="k">{{ t('mainMenu.label.connections') }}</div>
            <div class="v">{{ status?.connections ?? '-' }}</div>

            <div class="k">{{ t('mainMenu.label.autoExit') }}</div>
            <div class="v">{{ status?.autoExitSeconds ?? '-' }}s / idle={{ status?.idleSeconds ?? '-' }}s</div>

            <div class="k">{{ t('mainMenu.status.local') }}</div>
            <div class="v">
              <span :class="['sa-tag', wsState === 'connected' ? 'ok' : wsState === 'error' ? 'error' : 'warn']">
                {{ wsStateText }}
              </span>
              <span v-if="statusError" class="sa-tag error">{{ statusError }}</span>
              <span v-if="wsError" class="sa-tag error">{{ wsError }}</span>
              <span v-if="quitError" class="sa-tag error">{{ quitError }}</span>
            </div>

            <div class="k">{{ t('mainMenu.log.recv', ['']) }}</div>
            <div class="v">{{ lastWsMessage ?? '-' }}</div>
          </div>

          <div style="margin-top: 12px" class="muted">{{ t('mainMenu.footer.hint') }}</div>
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped>
.muted {
  color: rgba(255, 255, 255, 0.65);
  font-size: 13px;
}
</style>

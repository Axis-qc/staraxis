<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { useRouter } from 'vue-router'
import { requestQuit } from '../services/backend'
import { i18nState, loadAvailableLanguages, loadLanguage } from '../i18n'
import { useAuth } from '../composables/useAuth'
import { useMods } from '../composables/useMods'
import { useServerStatus } from '../composables/useServerStatus'
import { useStarfield } from '../composables/useStarfield'

// --- Core Composables ---
const { t } = useI18n()
const router = useRouter()
const { auth, loginForm, gameIdInput, gameIdSaveState, doLogin, doRegister, doLogout, saveGameId } = useAuth()
const { mods, modsLoading, modsError, modsSaveState, expandedModId, loadMods, moveMod, toggleModEnabled, saveModsToServer } = useMods()
const { wsStateText, wsTagKind } = useServerStatus()
const canvasRef = ref<HTMLCanvasElement | null>(null)
useStarfield(canvasRef)

const isReady = ref(false)

// --- Component-Specific State ---
type TabKey = 'status' | 'mods' | 'quit'
const activeTab = ref<TabKey>('status')
const showPassword = ref(false) // This state remains as it's purely for UI toggling

// --- Quit Logic (Component-specific) ---
const quitting = ref(false)
const quitHint = ref<string | null>(null)
const quitError = ref<string | null>(null)
async function quitServer() {
  if (quitting.value) return
  quitting.value = true
  quitError.value = null
  quitHint.value = null
  try {
    await requestQuit()
    quitHint.value = t('mainMenu.log.quitRequested')
  } catch (e) {
    quitError.value = (e as Error).message
  } finally {
    quitting.value = false
  }
}

// --- Language Switcher (Component-specific) ---
const langs = ref<string[]>(i18nState.availableLangs || [])
const selectedLang = ref<string>(i18nState.currentLang)
const langLoading = ref(false)
async function refreshLangs() {
  langLoading.value = true
  try {
    langs.value = await loadAvailableLanguages()
  } catch {
    langs.value = []
  } finally {
    langLoading.value = false
  }
}
async function onChangeLang() {
  langLoading.value = true
  try {
    await loadLanguage(selectedLang.value)
  } finally {
    langLoading.value = false
  }
}

// --- Navigation ---
function enterGame() {
  router.push('/main-menu')
}

onMounted(() => {
  refreshLangs()
  // Add a small delay to ensure the initial render is complete
  setTimeout(() => {
    isReady.value = true
  }, 100)
})

</script>

<template>
  <div class="home-page">
    <!-- Starfield Background -->
    <canvas ref="canvasRef" class="background-canvas"></canvas>

    <!-- Main Content Layout -->
    <div class="layout-container" :class="{ 'is-ready': isReady }">

      <!-- Left Sidebar -->
      <aside class="sidebar">
        <header class="sidebar-header">
          <div class="title-group">
            <h1 class="title">{{ t('home.title') }}</h1>
            <p class="sa-subtitle">{{ t('home.subtitle') }}</p>
          </div>
          <div class="status-tags">
            <span v-if="auth.isLoggedIn" class="sa-tag ok">{{ auth.username }}</span>
            <span v-else class="sa-tag warn">{{ t('home.notLoggedIn') }}</span>
            <span class="sa-tag" :class="wsTagKind">WS: {{ wsStateText }}</span>
          </div>
        </header>

        <!-- Logged In Navigation -->
        <nav v-if="auth.isLoggedIn" class="sidebar-nav">
          <button class="nav-btn" :class="{ active: activeTab === 'status' }" @click="activeTab = 'status'">
            <span>{{ t('home.tab.status') }}</span>
          </button>
          <button class="nav-btn" :class="{ active: activeTab === 'mods' }" @click="activeTab = 'mods'">
            <span>{{ t('home.tab.mods') }}</span>
          </button>
          <button class="nav-btn" :class="{ active: activeTab === 'quit' }" @click="activeTab = 'quit'">
            <span>{{ t('home.tab.quit') }}</span>
          </button>
        </nav>

        <footer class="sidebar-footer">
          <!-- Language selector can be placed here if needed -->
        </footer>
      </aside>

      <!-- Right Content Panel -->
      <main class="content-panel">
        <div class="content-body">
          <!-- Logged Out View -->
          <div v-if="!auth.isLoggedIn" class="login-view">
            <div class="form-group">
              <label for="username">{{ t('home.login.username') }}</label>
              <input id="username" v-model="loginForm.username" class="input" :placeholder="t('home.login.usernamePlaceholder')" />
            </div>
            <div class="form-group">
              <label for="password">{{ t('home.login.password') }}</label>
              <input id="password" v-model="loginForm.password" :type="showPassword ? 'text' : 'password'" class="input" :placeholder="t('home.login.passwordPlaceholder')" @keyup.enter="doLogin" />
            </div>
            <div class="actions">
              <button class="sa-btn" @click="doLogin" :disabled="loginForm.loading">{{ t('home.login.actionLogin') }}</button>
              <button class="sa-btn" @click="doRegister" :disabled="loginForm.loading">{{ t('home.login.actionRegister') }}</button>
            </div>
            <div v-if="loginForm.error" class="sa-tag error form-error">{{ loginForm.error }}</div>
          </div>

          <!-- Logged In View -->
          <div v-else class="hub-content">
            <transition name="fade-slide" mode="out-in">
              <section v-if="activeTab === 'status'" key="status" class="tab-section">
                <h2 class="tab-title">{{ t('home.tab.status') }}</h2>
                <div class="form-group">
                  <label for="gameId">{{ t('home.login.gameId') }}</label>
                  <input id="gameId" class="input" v-model="gameIdInput" :placeholder="t('home.login.gameIdPlaceholder')" />
                </div>
                <div class="actions">
                  <button class="sa-btn" @click="saveGameId" :disabled="gameIdSaveState === 'saving'">{{ t('home.login.saveId') }}</button>
                  <button class="sa-btn primary" @click="enterGame">{{ t('home.enterGame') }}</button>
                  <button class="sa-btn danger" @click="doLogout" :disabled="loginForm.loading">{{ t('home.login.logout') }}</button>
                </div>
              </section>

              <section v-else-if="activeTab === 'mods'" key="mods" class="tab-section mods-section">
                <div class="mods-header">
                  <h2 class="tab-title">{{ t('home.tab.mods') }}</h2>
                  <div class="header-actions">
                    <button class="sa-btn" @click="loadMods" :disabled="modsLoading">{{ t('mods.refresh') }}</button>
                    <button class="sa-btn primary" @click="saveModsToServer" :disabled="modsSaveState === 'saving' || modsLoading">{{ t('mods.save') }}</button>
                  </div>
                </div>
                <div class="header-status">
                  <span v-if="modsLoading" class="sa-tag warn">{{ t('mods.loading') }}</span>
                  <span v-if="modsSaveState === 'saved'" class="sa-tag ok">{{ t('mods.saved') }}</span>
                  <span v-if="modsSaveState === 'error'" class="sa-tag error">{{ t('mods.saveFailed') }}</span>
                </div>

                <div v-if="modsError" class="sa-tag error">{{ modsError }}</div>
                <div v-if="!modsLoading && mods.length === 0" class="mods-empty">{{ t('mods.empty') }}</div>

                <TransitionGroup v-else tag="div" name="mod-list" class="mods-list">
                  <div class="mods-list-header">
                    <span>{{ t('mods.column.enabled') }}</span>
                    <span>{{ t('mods.column.id') }}</span>
                    <span class="text-right">{{ t('mods.column.order') }}</span>
                  </div>
                  <div v-for="(m, idx) in mods" :key="m.id" class="mod-item sa-list-item">
                    <div class="mod-row">
                      <input type="checkbox" :checked="m.enabled" @change="toggleModEnabled(m.id, ($event.target as HTMLInputElement).checked)" />
                      <span class="mod-name">{{ m.name }}</span>
                      <div class="mod-actions">
                        <button class="sa-btn" @click="expandedModId = expandedModId === m.id ? null : m.id">{{ t('mods.action.details') }}</button>
                        <button class="sa-btn icon-btn" :disabled="idx === 0" @click="moveMod(idx, -1)">↑</button>
                        <button class="sa-btn icon-btn" :disabled="idx === mods.length - 1" @click="moveMod(idx, 1)">↓</button>
                      </div>
                    </div>
                    <div v-if="expandedModId === m.id" class="mod-details">
                      <div class="details-kv">
                        <div class="k">{{ t('mods.field.id') }}</div><div class="v mono">{{ m.id }}</div>
                        <div class="k">{{ t('mods.field.author') }}</div><div class="v">{{ m.author }}</div>
                        <div class="k">{{ t('mods.field.version') }}</div><div class="v">{{ m.version }}</div>
                        <div class="k">{{ t('mods.field.gameVersion') }}</div><div class="v">{{ m.compatibleGameVersion }}</div>
                        <div class="k">{{ t('mods.field.description') }}</div><div class="v description">{{ m.description }}</div>
                      </div>
                    </div>
                  </div>
                </TransitionGroup>
              </section>

              <section v-else key="quit" class="tab-section">
                <h2 class="tab-title">{{ t('home.tab.quit') }}</h2>
                <p class="quit-desc">{{ t('home.quit.desc') }}</p>
                <button class="sa-btn danger" @click="quitServer" :disabled="quitting">{{ t('home.quit.action') }}</button>
              </section>
            </transition>
          </div>
        </div>
      </main>
    </div>
  </div>
</template>

<style scoped>
.home-page {
  position: relative;
  min-height: 100vh;
  width: 100%;
  overflow: hidden;
  display: grid;
  place-items: center;
}

.background-canvas {
  position: fixed;
  inset: 0;
  width: 100%;
  height: 100%;
  z-index: -1;
  display: block;
  background: transparent;
}

.layout-container {
  position: relative;
  width: min(1200px, calc(100% - 32px));
  min-height: min(720px, calc(100vh - 48px));
  max-height: calc(100vh - 48px);
  display: grid;
  grid-template-columns: 280px 1fr;
  background: rgba(10, 25, 47, 0.5);
  border: 1px solid rgba(0, 191, 255, 0.2);
  backdrop-filter: blur(20px);
  box-shadow: 0 10px 30px rgba(0, 0, 0, 0.2), 0 0 0 1px rgba(0, 191, 255, 0.25);

  /* Add loading transition */
  opacity: 0;
  transform: scale(0.98);
  transition: opacity 0.5s cubic-bezier(0.25, 1, 0.5, 1), transform 0.5s cubic-bezier(0.25, 1, 0.5, 1);
}

.layout-container::before, .layout-container::after {
    content: '';
    position: absolute;
    width: 20px;
    height: 20px;
    border: 2px solid var(--glow-color);
    z-index: 1;
}

.layout-container::before {
    top: -2px;
    left: -2px;
    border-right: none;
    border-bottom: none;
}

.layout-container::after {
    bottom: -2px;
    right: -2px;
    border-left: none;
    border-top: none;
}

.layout-container.is-ready {
  opacity: 1;
  transform: scale(1);
}

.sidebar {
  display: flex;
  flex-direction: column;
  gap: 16px;
  padding: 20px;
  border-right: 1px solid rgba(0, 191, 255, 0.18);
  min-width: 0;
}

.sidebar-header {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.title-group .title {
  margin: 0;
  font-size: 28px;
  line-height: 1.1;
  font-weight: 700;
  color: var(--text-color, #d1d5db);
}

.status-tags {
  display: flex;
  flex-direction: column;
  align-items: flex-start;
  gap: 8px;
}

.sidebar-nav {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.nav-btn {
  position: relative;
  appearance: none;
  background: transparent;
  border: 1px solid transparent;
  color: var(--text-color, #d1d5db);
  padding: 12px 16px;
  cursor: pointer;
  text-align: left;
  transition: background-color 0.2s ease, color 0.2s ease;
  clip-path: polygon(0 0, calc(100% - 10px) 0, 100% 10px, 100% 100%, 0 100%);
  background-color: rgba(0, 191, 255, 0.1);
  border: 1px solid rgba(0, 191, 255, 0.2);
}

.nav-btn:hover {
  background-color: rgba(0, 191, 255, 0.2);
  color: #fff;
}

.nav-btn.active {
  background-color: rgba(0, 191, 255, 0.3);
  color: #fff;
  text-shadow: 0 0 5px var(--glow-color);
}

.nav-btn.active::before {
    content: '';
    position: absolute;
    left: 0;
    top: 0;
    bottom: 0;
    width: 4px;
    background-color: var(--glow-color);
    box-shadow: 0 0 10px var(--glow-color);
}

.sidebar-footer {
  margin-top: auto;
  opacity: 0.8;
}

.content-panel {
  min-width: 0;
  display: flex;
  padding: 24px;
  overflow: hidden;
}

.content-body {
  width: 100%;
  min-width: 0;
  overflow: auto;
}

.login-view,
.hub-content {
  width: 100%;
}

.tab-section {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.tab-title {
  position: relative;
  margin: 0;
  padding-bottom: 12px;
  font-size: 20px;
  font-weight: 700;
  color: var(--text-color, #d1d5db);
  text-transform: uppercase;
  letter-spacing: 2px;
}

.tab-title::after {
    content: '';
    position: absolute;
    left: 0;
    bottom: 0;
    width: 100%;
    height: 2px;
    background: linear-gradient(to right, var(--glow-color), transparent);
}

.form-group {
  display: grid;
  gap: 8px;
}

.form-group label {
  font-size: 12px;
  opacity: 0.9;
  color: var(--text-color, #d1d5db);
}

.actions {
  display: flex;
  flex-wrap: wrap;
  gap: 12px;
}

.form-error {
  width: 100%;
  text-align: center;
}

.quit-desc {
  color: var(--text-color, #d1d5db);
  opacity: 0.9;
  line-height: 1.6;
}

.mods-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  flex-wrap: wrap;
  gap: 12px;
}

.header-actions {
  display: flex;
  gap: 12px;
  flex-wrap: wrap;
}

.header-status {
  margin-top: 8px;
}

.mods-list {
  display: flex;
  flex-direction: column;
  gap: 8px;
  overflow: auto;
  padding-right: 8px;
}

.mods-list-header {
  display: grid;
  grid-template-columns: 60px 1fr auto;
  gap: 12px;
  padding: 0 12px 8px 12px;
  font-size: 12px;
  opacity: 0.85;
  text-transform: uppercase;
  letter-spacing: 1px;
  border-bottom: 1px solid rgba(0, 191, 255, 0.2);
}

.mod-actions .sa-btn {
    padding: 4px 8px;
    font-size: 14px;
}

.text-right {
  text-align: right;
}

.mod-item {
  background-color: rgba(0, 191, 255, 0.05);
  border: 1px solid rgba(0, 191, 255, 0.2);
  clip-path: polygon(0 0, calc(100% - 8px) 0, 100% 8px, 100% 100%, 0 100%);
}

.mod-row {
  display: grid;
  grid-template-columns: 60px 1fr auto;
  gap: 12px;
  align-items: center;
  padding: 10px 12px;
}

.mod-actions {
  display: flex;
  justify-content: flex-end;
  gap: 8px;
  flex-wrap: wrap;
}

.mod-details {
  padding: 12px;
}

.details-kv {
  display: grid;
  grid-template-columns: 120px 1fr;
  gap: 8px 12px;
}

.mono {
  font-family: ui-monospace, SFMono-Regular, Menlo, Monaco, Consolas, "Liberation Mono", "Courier New", monospace;
}

@media (max-width: 960px) {
  .layout-container {
    grid-template-columns: 1fr;
    min-height: calc(100vh - 32px);
    max-height: calc(100vh - 32px);
  }

  .sidebar {
    border-right: none;
    border-bottom: 1px solid rgba(0, 191, 255, 0.18);
  }
}

.fade-slide-enter-active,
.fade-slide-leave-active {
  transition: opacity 0.25s ease, transform 0.25s ease;
}

.fade-slide-enter-from {
  opacity: 0;
  transform: translateX(20px);
}

.fade-slide-leave-to {
  opacity: 0;
  transform: translateX(-20px);
}

.mod-list-move,
.mod-list-enter-active,
.mod-list-leave-active {
  transition: all 0.2s ease;
}

.mod-list-enter-from,
.mod-list-leave-to {
  opacity: 0;
  transform: translateY(6px);
}

.mod-list-leave-active {
  position: absolute;
}
</style>

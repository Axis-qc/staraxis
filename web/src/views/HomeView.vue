<script setup lang="ts">
/**
 * @file HomeView.vue
 *
 * @description
 * StarAxis 的 Web 启动台/控制台视图（/）。
 * 提供登录、服务器状态概览、MOD 管理（表格 + 展开详情）、退出本地服务端等入口。
 * 右上角提供语言切换，右下角通过全局 ThemePicker 提供主题切换。
 *
 * @usage
 * - 通过 Vue Router 进入（根路由 `/`）。
 * - 本视图大量依赖 i18n 文案：所有可见文字使用 `t('...')`。
 *
 * @provides
 * - **动态背景**：`useStarfield(canvasRef)` 在 `<canvas>` 绘制星空。
 * - **登录/注册/退出**：通过 `useAuth()` 管理状态与请求。
 * - **服务器状态**：`useServerStatus()` 提供 WS 状态展示。
 * - **MOD 管理**：`useMods()` 提供列表加载、启用/禁用、顺序调整、保存。
 * - **退出服务端**：调用 `requestQuit()` 请求后端退出。
 * - **语言切换**：调用 `loadAvailableLanguages()` / `loadLanguage()` 拉取并切换语言包。
 *
 * @api
 * - `/api/i18n/languages`：拉取可用语言列表（via `loadAvailableLanguages`）。
 * - `/api/i18n/{lang}`：拉取语言包（via `loadLanguage`）。
 * - `/api/auth/*`、`/api/mods`、`/api/status`：由 `useAuth/useMods/useServerStatus` 间接调用。
 * - `/api/quit`：由 `requestQuit()` 调用。
 *
 * @resources
 * - `../composables/useStarfield`：星空动画。
 * - `../composables/useAuth`：认证/登录表单。
 * - `../composables/useMods`：MOD 管理。
 * - `../composables/useServerStatus`：WS 状态。
 * - 全局样式：`ui.css/theme.css/controls.css` 提供主题变量与通用控件样式。
 *
 * @potential_issues
 * - **性能**：Canvas 动画在低端设备可能消耗较多 CPU/GPU。
 * - **网络/后端依赖**：语言包、登录、MOD 等请求依赖本地后端可用；后端不可用时会出现请求失败（如 ECONNREFUSED）。
 * - **布局约束**：本视图禁用窗口滚动，内部区域（如 MOD 表体）通过 `overflow:auto` 滚动；修改布局时需注意 `min-height: 0` 等 flex 收缩约束。
 */

import { onMounted, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { useRouter } from 'vue-router'
import { requestQuit } from '../services/backend'
import { i18nState, loadAvailableLanguages, loadLanguage } from '../i18n'
import { useAuth } from '../composables/useAuth'
import { useMods } from '../composables/useMods'
import { useServerStatus } from '../composables/useServerStatus'
import { useStarfield } from '../composables/useStarfield'

const { t } = useI18n()
const router = useRouter()
const { auth, loginForm, gameIdInput, gameIdSaveState, doLogin, doRegister, doLogout, saveGameId } = useAuth()
const { mods, modsLoading, modsError, modsSaveState, expandedModId, loadMods, moveMod, toggleModEnabled, saveModsToServer } = useMods()
const { wsStateText, wsTagKind } = useServerStatus()

const canvasRef = ref<HTMLCanvasElement | null>(null)
useStarfield(canvasRef)

const isReady = ref(false)

type TabKey = 'status' | 'mods' | 'quit'
const activeTab = ref<TabKey>('status')

function onClickTab(tab: TabKey) {
  const canQuit = auth.isLoggedIn && auth.role === 'ADMIN'
  if (tab === 'quit' && !canQuit) return
  activeTab.value = tab
}
const showPassword = ref(false)

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

function enterGame() {
  router.push('/main-menu')
}

onMounted(() => {
  refreshLangs()
  setTimeout(() => {
    isReady.value = true
  }, 100)
})
</script>

<template>
  <div class="home-view-root">
    <canvas ref="canvasRef" class="background-canvas"></canvas>

    <div class="lang-switcher" v-if="langs.length">
      <label class="lang-label">
        <span>{{ t('lang.current') }}：</span>
        <select v-model="selectedLang" class="sa-select" :disabled="langLoading" @change="onChangeLang">
          <option v-for="l in langs" :key="l" :value="l">{{ l }}</option>
        </select>
      </label>
    </div>

    <div class="layout-container" :class="{ 'is-ready': isReady }">
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

        <nav v-if="auth.isLoggedIn" class="sidebar-nav">
          <button class="nav-btn" :class="{ active: activeTab === 'status' }" @click="onClickTab('status')">
            <span>{{ t('home.tab.status') }}</span>
          </button>
          <button class="nav-btn" :class="{ active: activeTab === 'mods' }" @click="onClickTab('mods')">
            <span>{{ t('home.tab.mods') }}</span>
          </button>
          <button v-if="auth.isLoggedIn && auth.role === 'ADMIN'" class="nav-btn" :class="{ active: activeTab === 'quit' }" @click="onClickTab('quit')">
            <span>{{ t('home.tab.quit') }}</span>
          </button>
        </nav>

        <footer class="sidebar-footer"></footer>
      </aside>

      <main class="content-panel">
        <div class="content-body">
          <div v-if="!auth.isLoggedIn" class="login-view">
            <div class="form-group">
              <label for="username">{{ t('home.login.username') }}</label>
              <input id="username" v-model="loginForm.username" class="input" :placeholder="t('home.login.usernamePlaceholder')" />
            </div>
            <div class="form-group">
              <label for="password">{{ t('home.login.password') }}</label>
              <input
                id="password"
                v-model="loginForm.password"
                :type="showPassword ? 'text' : 'password'"
                class="input"
                :placeholder="t('home.login.passwordPlaceholder')"
                @keyup.enter="doLogin"
              />
            </div>
            <div class="actions">
              <button class="sa-btn" @click="doLogin" :disabled="loginForm.loading">{{ t('home.login.actionLogin') }}</button>
              <button class="sa-btn" @click="doRegister" :disabled="loginForm.loading">{{ t('home.login.actionRegister') }}</button>
            </div>
            <div v-if="loginForm.error" class="sa-tag error form-error">{{ loginForm.error }}</div>
          </div>

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

                <div v-else class="mods-table">
                  <div class="mods-table-head">
                    <div class="th enabled">{{ t('mods.column.enabled') }}</div>
                    <div class="th name">{{ t('mods.column.id') }}</div>
                    <div class="th actions">{{ t('mods.column.order') }}</div>
                  </div>

                  <div class="mods-table-body" role="table">
                    <div v-for="(m, idx) in mods" :key="m.id" class="row-group">
                      <div class="tr" role="row">
                        <div class="td enabled">
                          <input
                            type="checkbox"
                            :checked="m.enabled"
                            @change="toggleModEnabled(m.id, ($event.target as HTMLInputElement).checked)"
                          />
                        </div>
                        <div class="td name">
                          <div class="mod-name">{{ m.name }}</div>
                          <div class="mod-id mono">{{ m.id }}</div>
                        </div>
                        <div class="td actions">
                          <button class="sa-btn" @click="expandedModId = expandedModId === m.id ? null : m.id">
                            {{ t('mods.action.details') }}
                          </button>
                        <button class="sa-btn icon-btn" :disabled="idx === 0" @click="moveMod(idx, -1)">↑</button>
                        <button class="sa-btn icon-btn" :disabled="idx === mods.length - 1" @click="moveMod(idx, 1)">↓</button>
                      </div>
                    </div>

                      <div v-if="expandedModId === m.id" class="tr child" role="row">
                        <div class="td child-cell" role="cell">
                      <div class="details-kv">
                        <div class="k">{{ t('mods.field.id') }}</div><div class="v mono">{{ m.id }}</div>
                        <div class="k">{{ t('mods.field.author') }}</div><div class="v">{{ m.author }}</div>
                        <div class="k">{{ t('mods.field.version') }}</div><div class="v">{{ m.version }}</div>
                        <div class="k">{{ t('mods.field.gameVersion') }}</div><div class="v">{{ m.compatibleGameVersion }}</div>
                        <div class="k">{{ t('mods.field.description') }}</div><div class="v description">{{ m.description }}</div>
                      </div>
                    </div>
                  </div>
                    </div>
                  </div>
                </div>
              </section>

              <section v-else-if="activeTab === 'quit' && auth.isLoggedIn && auth.role === 'ADMIN'" key="quit" class="tab-section">
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
.home-view-root {
  /* 主题 token：从 useTheme 写入的 --sa-* 取值，并在本视图内做别名，方便复用 */
  --bg0: var(--sa-bg0, #070712);
  --bg1: var(--sa-bg1, #0b0a19);
  --panel: var(--sa-panel, rgba(13, 14, 26, 0.62));
  --panel-strong: var(--sa-panel-strong, rgba(13, 14, 26, 0.78));
  --stroke: var(--sa-stroke, rgba(196, 181, 253, 0.16));
  --stroke-strong: var(--sa-stroke-strong, rgba(196, 181, 253, 0.24));
  --text: var(--text-color);
  --muted: rgba(255, 255, 255, 0.72);
  --glow: var(--sa-glow, rgba(168, 85, 247, 0.55));
  --glow-soft: var(--sa-glow-soft, rgba(168, 85, 247, 0.24));
  --cyan: var(--sa-accent2, rgba(34, 211, 238, 0.22));

  position: relative;
  height: 100%;
  min-height: 0;
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
  background-color: var(--background-color);
  opacity: 1;
  filter: saturate(1.1) contrast(1.05);
}

.layout-container {
  position: relative;
  width: 80%;
  height: 70%;
  min-width: 980px;
  max-width: 1200px;
  min-height: 480px;
  max-height: 860px;
  display: grid;
  grid-template-columns: 280px 1fr;

  background: linear-gradient(180deg, var(--panel), rgba(13, 14, 26, 0.46));
  border: 1px solid var(--stroke);
  backdrop-filter: blur(22px);
  -webkit-backdrop-filter: blur(22px);
  box-shadow:
    0 18px 60px rgba(0, 0, 0, 0.55),
    0 0 0 1px rgba(168, 85, 247, 0.10),
    0 0 80px rgba(168, 85, 247, 0.12);

  opacity: 0;
  transform: translateY(8px) scale(0.985);
  transition: opacity 0.55s cubic-bezier(0.25, 1, 0.5, 1), transform 0.55s cubic-bezier(0.25, 1, 0.5, 1);
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
  border-right: 1px solid var(--stroke);
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
  font-weight: 750;
  color: var(--text);
  letter-spacing: 0.4px;
}

.sa-subtitle {
  margin: 6px 0 0;
  color: rgba(255, 255, 255, 0.72);
  letter-spacing: 1px;
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
  background: rgba(0, 0, 0, 0.18);
  border: 1px solid var(--border-color);
  color: var(--text-color);
  padding: 12px 16px;
  cursor: pointer;
  text-align: left;
  transition: background-color 0.22s ease, color 0.22s ease, border-color 0.22s ease, box-shadow 0.22s ease, transform 0.22s ease;
  clip-path: polygon(0 0, calc(100% - 10px) 0, 100% 10px, 100% 100%, 0 100%);
}

.nav-btn:hover {
  border-color: var(--glow-color);
  color: var(--text-color-hover);
  transform: translateY(-1px);
}

.nav-btn.active {
  border-color: var(--glow-color);
  color: var(--text-color-hover);
  text-shadow: 0 0 10px var(--glow-color);
}

.nav-btn.active::before {
    content: '';
    position: absolute;
    left: 0;
    top: 0;
    bottom: 0;
    width: 4px;
  background: var(--glow-color);
  box-shadow: 0 0 18px var(--glow-color);
}

.content-panel {
  min-width: 0;
  min-height: 0;
  display: flex;
  padding: 24px;
  overflow: hidden;
}

.content-body {
  width: 100%;
  min-width: 0;
  min-height: 0;
  display: flex;
  flex-direction: column;
  overflow: hidden;
}

.login-view,
.hub-content {
  width: 100%;
  min-height: 0;
}

.tab-section {
  display: flex;
  flex-direction: column;
  gap: 16px;
  min-height: 0;
}

.tab-title {
  position: relative;
  margin: 0;
  padding-bottom: 12px;
  font-size: 20px;
  font-weight: 750;
  color: var(--text);
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
  background: linear-gradient(to right, var(--glow-color), rgba(255, 255, 255, 0.10), transparent);
  opacity: 0.9;
}

.form-group {
  display: grid;
  gap: 8px;
}

.form-group label {
  font-size: 12px;
  opacity: 0.92;
  color: var(--muted);
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
  color: var(--text-color);
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

.mods-empty {
  padding: 18px 12px;
  opacity: 0.85;
  color: var(--text-color);
}

.mods-table {
  display: flex;
  flex-direction: column;
  min-height: 0;
  height: calc(100vh - 320px);
  border: 1px solid var(--border-color);
  background: rgba(0, 0, 0, 0.16);
}

.mods-table-head {
  display: grid;
  grid-template-columns: 80px 1fr 240px;
  gap: 12px;
  padding: 10px 12px;
  border-bottom: 1px solid rgba(255, 255, 255, 0.10);
  color: var(--text-color);
  font-size: 12px;
  letter-spacing: 1px;
  text-transform: uppercase;
}

.mods-table-body {
  min-height: 0;
  overflow: auto;
}

.lang-switcher {
  position: fixed;
  top: 12px;
  right: 12px;
  z-index: 60;
}

.lang-label {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  padding: 8px 10px;
  border-radius: 999px;
  border: 1px solid rgba(255, 255, 255, 0.12);
  background: rgba(0, 0, 0, 0.18);
  color: var(--text-color);
}

.lang-label > span {
  opacity: 0.9;
  font-size: 12px;
  letter-spacing: 0.6px;
}

.row-group {
  border-bottom: 1px solid rgba(255, 255, 255, 0.08);
}

.tr {
  display: grid;
  grid-template-columns: 80px 1fr 240px;
  gap: 12px;
  align-items: center;
  padding: 10px 12px;
}

.tr:hover {
  background: rgba(0, 0, 0, 0.10);
}

.td.actions {
  display: flex;
  justify-content: flex-end;
  gap: 8px;
  flex-wrap: wrap;
}

.mod-name {
  color: var(--text-color);
}

.mod-id {
  opacity: 0.75;
  color: var(--text-color);
}

.tr.child {
  grid-template-columns: 1fr;
  padding-top: 0;
}

.child-cell {
  grid-column: 1 / -1;
  padding: 0 12px 12px 12px;
}

.details-kv {
  display: grid;
  grid-template-columns: 120px 1fr;
  gap: 8px 12px;
}

.details-kv .k {
  opacity: 0.9;
  color: var(--text-color);
}

.details-kv .v {
  color: var(--text-color);
}

.details-kv .description {
  opacity: 0.9;
  line-height: 1.5;
}

.mono {
  font-family: ui-monospace, SFMono-Regular, Menlo, Monaco, Consolas, 'Liberation Mono', 'Courier New', monospace;
}

@media (max-width: 960px) {
  .layout-container {
    width: calc(100% - 24px);
    height: calc(100% - 24px);
    min-width: 0;
    max-width: none;
    min-height: 0;
    max-height: none;
    grid-template-columns: 1fr;
  }

  .lang-switcher {
    top: 10px;
    right: 10px;
  }

  .sidebar {
    border-right: none;
    border-bottom: 1px solid var(--stroke);
  }

  .mods-table {
    height: calc(100vh - 380px);
  }

  .mods-table-head,
  .tr {
    grid-template-columns: 70px 1fr 200px;
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
</style>

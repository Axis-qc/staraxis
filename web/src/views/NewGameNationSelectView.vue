<script setup lang="ts">
/**
 * @file NewGameNationSelectView.vue
 *
 * @description
 * 新游戏 - 国家选择页面（/new-game/nation）。
 * UI 风格对齐 MainMenuView：HUD + 星空背景。
 *
 * 本页面接入：
 * - 预设国家：GET /api/game/nations
 * - 玩家自定义国家：
 *   - GET  /api/nations/players/list?username=...&playerId=...
 *   - GET  /api/nations/players/get?username=...&playerId=...&nationId=...
 *   - POST /api/nations/players/save
 */
import { computed, onMounted, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { useRouter } from 'vue-router'

import { useStarfield } from '../composables/useStarfield'
import { useAuthStore } from '../stores/auth'

type NationItem = {
  id: string
  nameKey: string
  descriptionKey: string
  governmentId: string
  speciesIds: string[]
  startingTechIds: string[]
}

type PlayerNationDesignDto = {
  schemaVersion: number
  username: string
  playerId: string
  updatedAtUnixMs: number
  nation: NationItem
}

const { t } = useI18n()
const router = useRouter()

// --- Canvas Background ---
const canvasRef = ref<HTMLCanvasElement | null>(null)
useStarfield(canvasRef)

// --- Animation state ---
const entered = ref(false)

// --- Global identity (session) ---
// 说明：身份来源改为全局状态（Pinia store）。
// 只要登录后不关闭页面且不退出登录，所有页面都可读取。
const auth = useAuthStore()

const username = computed(() => auth.username)
const playerId = computed(() => auth.playerId)

// --- Data ---
const presetNations = ref<NationItem[]>([])
const playerNationIds = ref<string[]>([])
const playerNations = ref<Record<string, PlayerNationDesignDto>>({})

const selectedNationId = ref<string>('')
const selectedNationSource = ref<'preset' | 'player' | ''>('')

const loading = ref(false)
const errorMsg = ref('')

const selectedNation = computed<NationItem | null>(() => {
  if (!selectedNationId.value || !selectedNationSource.value) {
    return null
  }
  if (selectedNationSource.value === 'preset') {
    return presetNations.value.find(n => n.id === selectedNationId.value) || null
  }
  const d = playerNations.value[selectedNationId.value]
  return d ? d.nation : null
})

function setError(msg: string) {
  errorMsg.value = msg
}

async function apiGetJson<T>(url: string): Promise<T> {
  const resp = await fetch(url, { method: 'GET' })
  if (!resp.ok) {
    throw new Error(`http_${resp.status}`)
  }
  return (await resp.json()) as T
}

async function apiPostJson<T>(url: string, body: unknown): Promise<T> {
  const resp = await fetch(url, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(body),
  })
  if (!resp.ok) {
    throw new Error(`http_${resp.status}`)
  }
  return (await resp.json()) as T
}

async function loadPresetNations() {
  const data = await apiGetJson<{ ok: boolean; nations: NationItem[]; error?: string }>('/api/game/nations')
  if (!data.ok) {
    throw new Error(data.error || 'preset_nations_failed')
  }
  presetNations.value = Array.isArray(data.nations) ? data.nations : []
}

async function loadPlayerNationIds() {
  const url = `/api/nations/players/list?username=${encodeURIComponent(username.value)}&playerId=${encodeURIComponent(playerId.value)}`
  const data = await apiGetJson<{ ok: boolean; nationIds: string[]; error?: string }>(url)
  if (!data.ok) {
    throw new Error(data.error || 'player_nations_list_failed')
  }
  playerNationIds.value = Array.isArray(data.nationIds) ? data.nationIds : []
}

async function loadPlayerNation(nationId: string) {
  const url = `/api/nations/players/get?username=${encodeURIComponent(username.value)}&playerId=${encodeURIComponent(playerId.value)}&nationId=${encodeURIComponent(nationId)}`
  const data = await apiGetJson<{ ok: boolean; design?: PlayerNationDesignDto; error?: string }>(url)
  if (!data.ok || !data.design) {
    return
  }
  playerNations.value = { ...playerNations.value, [nationId]: data.design }
}

async function refreshAll() {
  loading.value = true
  errorMsg.value = ''
  try {
    await loadPresetNations()
    await loadPlayerNationIds()

    // 拉取玩家国家详情（用于展示名称等）
    for (const id of playerNationIds.value) {
      if (!playerNations.value[id]) {
        await loadPlayerNation(id)
      }
    }
  } catch (e: any) {
    setError(String(e?.message || e))
  } finally {
    loading.value = false
  }
}

function choosePreset(nationId: string) {
  selectedNationSource.value = 'preset'
  selectedNationId.value = nationId
}

function choosePlayer(nationId: string) {
  selectedNationSource.value = 'player'
  selectedNationId.value = nationId
}

async function handleNextStep() {
  if (!selectedNation.value) {
    return
  }
  if (!username.value || !playerId.value) {
    setError('not_logged_in')
    return
  }

  loading.value = true
  errorMsg.value = ''
  try {
    const resp = await apiPostJson<{ ok: boolean; newGameDraftId?: string; error?: string }>(
      '/api/newgame/step1/selectNation',
      {
        username: username.value,
        playerId: playerId.value,
        nationId: selectedNation.value.id,
      },
    )
    if (!resp.ok) {
      throw new Error(resp.error || 'step1_failed')
    }
    router.push('/new-game/world-settings')
  } catch (e: any) {
    setError(String(e?.message || e))
  } finally {
    loading.value = false
  }
}

async function createSamplePlayerNation() {
  // 用于验证 save 流程的最小样例
  const nationId = `custom_${Date.now()}`
  const body = {
    schemaVersion: 1,
    username: username.value,
    playerId: playerId.value,
    nation: {
      id: nationId,
      nameKey: `nation.${nationId}.name`,
      descriptionKey: `nation.${nationId}.desc`,
      governmentId: 'gov_republic',
      speciesIds: ['species_human'],
      startingTechIds: ['tech_basic_mining_1'],
    },
  }

  loading.value = true
  errorMsg.value = ''
  try {
    const data = await apiPostJson<{ ok: boolean; nationId?: string; error?: string }>('/api/nations/players/save', body)
    if (!data.ok) {
      throw new Error(data.error || 'player_nation_save_failed')
    }
    await loadPlayerNationIds()
    await loadPlayerNation(nationId)
    choosePlayer(nationId)
  } catch (e: any) {
    setError(String(e?.message || e))
  } finally {
    loading.value = false
  }
}

onMounted(() => {
  refreshAll()
  // 触发入场动画
  setTimeout(() => {
    entered.value = true
  }, 100)
})

</script>

<template>
  <div class="page">
    <canvas ref="canvasRef" class="background-canvas"></canvas>

    <div class="hud" :class="{ entered: entered }">
      <header class="card card-top">
        <div class="header-left">
          <h2 class="panel-title">{{ t('newGame.nationSelect.title') }}</h2>
          <p class="panel-desc">{{ t('newGame.nationSelect.desc') }}</p>
        </div>
        <div class="header-right">
          <div class="meta-row">
            <div class="meta-item">
              <span class="meta-label">username</span>
              <span class="meta-value">{{ username }}</span>
            </div>
            <div class="meta-item">
              <span class="meta-label">playerId</span>
              <span class="meta-value">{{ playerId }}</span>
            </div>
            <button class="btn small" @click="refreshAll" :disabled="loading">
              {{ loading ? '...' : t('common.refresh') }}
            </button>
          </div>
        </div>
      </header>

      <div v-if="errorMsg" class="error">
        {{ errorMsg }}
      </div>

      <main class="main-row">
        <section class="card card-left nation-list">
          <div class="nation-list-section">
            <div class="section-header">
              <h3 class="section-title">{{ t('newGame.nationSelect.presets') }}</h3>
            </div>

            <div v-if="loading" class="placeholder">{{ t('common.loading') }}</div>
            <div v-else class="list">
              <button v-for="n in presetNations" :key="n.id" class="list-item"
                :class="{ selected: selectedNationSource === 'preset' && selectedNationId === n.id }"
                @click="choosePreset(n.id)">
                <div class="item-title">{{ t(n.nameKey) || n.id }}</div>
                <div class="item-sub">{{ n.id }}</div>
              </button>
            </div>
          </div>

          <div class="nation-list-divider"></div>

          <div class="nation-list-section">
            <div class="section-header">
              <h3 class="section-title">{{ t('newGame.nationSelect.playerDesigns') }}</h3>
              <button class="btn small" @click="createSamplePlayerNation" :disabled="loading">
                {{ t('newGame.nationSelect.createSample') }}
              </button>
            </div>

            <div v-if="loading" class="placeholder">{{ t('common.loading') }}</div>
            <div v-else class="list">
              <button v-for="id in playerNationIds" :key="id" class="list-item"
                :class="{ selected: selectedNationSource === 'player' && selectedNationId === id }"
                @click="choosePlayer(id)">
                <div class="item-title">
                  {{ playerNations[id] ? (t(playerNations[id].nation.nameKey) || id) : id }}
                </div>
                <div class="item-sub">{{ id }}</div>
              </button>

              <div v-if="playerNationIds.length === 0" class="placeholder">
                {{ t('newGame.nationSelect.noPlayerDesigns') }}
              </div>
            </div>
          </div>
        </section>

        <section class="card card-right nation-detail">
          <div class="detail-content" v-if="selectedNation">
            <h3 class="detail-title">{{ t(selectedNation.nameKey) || selectedNation.id }}</h3>
            <div class="detail-row">
              <span class="detail-label">id</span>
              <span class="detail-value">{{ selectedNation.id }}</span>
            </div>
            <div class="detail-row">
              <span class="detail-label">governmentId</span>
              <span class="detail-value">{{ selectedNation.governmentId }}</span>
            </div>
            <div class="detail-row">
              <span class="detail-label">speciesIds</span>
              <span class="detail-value">{{ (selectedNation.speciesIds || []).join(', ') }}</span>
            </div>
            <div class="detail-row">
              <span class="detail-label">startingTechIds</span>
              <span class="detail-value">{{ (selectedNation.startingTechIds || []).join(', ') }}</span>
            </div>
          </div>

          <div class="detail-content" v-else>
            <div class="placeholder">{{ t('newGame.nationSelect.pickOne') }}</div>
          </div>
        </section>
      </main>

      <footer class="card card-bottom">
        <button class="btn" @click="router.push('/main-menu')">
          {{ t('common.back') }}
        </button>
        <button class="btn primary" :disabled="!selectedNation || loading" @click="handleNextStep">
          {{ loading ? '...' : t('common.next') }}
        </button>
      </footer>
    </div>
  </div>
</template>

<style scoped>
.page {
  display: flex;
  height: 100vh;
  width: 100vw;
  background-color: transparent;
  color: var(--text-color);
  font-family: 'Orbitron', sans-serif;
  overflow: hidden;
  position: relative;
  padding: 2.5rem;
  box-sizing: border-box;
  justify-content: center;
}

.background-canvas {
  position: fixed;
  top: 0;
  left: 0;
  width: 100%;
  height: 100%;
  z-index: -1;
  background-color: var(--background-color);
}

.hud {
  position: relative;
  z-index: 1;
  width: min(1200px, 100%);
  height: 100%;
  display: grid;
  grid-template-rows: auto 1fr auto;
  gap: 1rem;
  min-height: 0;
}

.card {
  background: color-mix(in srgb, var(--background-color) 68%, rgba(255, 255, 255, 0.05));
  border: 1px solid color-mix(in srgb, var(--glow-color) 32%, transparent);
  box-shadow: 0 0 24px color-mix(in srgb, var(--glow-color) 12%, transparent);
  border-radius: 12px;
  padding: 1.25rem;
  min-height: 0;
}

.main-row {
  display: grid;
  grid-template-columns: 360px 1fr;
  gap: 1rem;
  min-height: 0;
}

.card-top {
  transform: translateY(-24px);
  opacity: 0;
  transition: transform 420ms ease, opacity 420ms ease;
}

.card-left {
  transform: translateX(-24px);
  opacity: 0;
  transition: transform 420ms ease, opacity 420ms ease;
}

.card-right {
  transform: translateX(24px);
  opacity: 0;
  transition: transform 420ms ease, opacity 420ms ease;
}

.card-bottom {
  transform: translateY(24px);
  opacity: 0;
  transition: transform 420ms ease, opacity 420ms ease;
  display: flex;
  justify-content: space-between;
  gap: 1rem;
}

.hud.entered .card-top,
.hud.entered .card-left,
.hud.entered .card-right,
.hud.entered .card-bottom {
  transform: translate(0, 0);
  opacity: 1;
}

.panel-title {
  margin: 0;
  font-size: 1.2rem;
  color: var(--text-color-hover);
}

.panel-desc {
  margin: 0.75rem 0 0 0;
  font-size: 0.9rem;
  color: color-mix(in srgb, var(--text-color) 70%, transparent);
}

.meta-row {
  display: flex;
  align-items: center;
  gap: 1rem;
  margin-top: 1rem;
}

.meta-item {
  display: grid;
  gap: 0.25rem;
}

.meta-label {
  font-size: 0.7rem;
  color: color-mix(in srgb, var(--text-color) 60%, transparent);
}

.meta-value {
  font-size: 0.85rem;
  color: var(--text-color);
}

.error {
  margin-top: 0.75rem;
  padding: 0.75rem;
  border-radius: 10px;
  border: 1px solid color-mix(in srgb, #ff4d4f 40%, transparent);
  background: color-mix(in srgb, #ff4d4f 12%, transparent);
  color: color-mix(in srgb, #ffb3b3 80%, white);
  font-size: 0.85rem;
}

.panel-header {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  gap: 1.25rem;
  padding-bottom: 1rem;
  border-bottom: 1px solid color-mix(in srgb, var(--glow-color) 18%, transparent);
}

.header-left {
  min-width: 0;
}

.header-right {
  min-width: 0;
}

.panel-main {
  flex: 1;
  min-height: 0;
  display: flex;
  flex-direction: column;
  margin-top: 1rem;
}

.panel-footer {
  display: flex;
  justify-content: space-between;
  gap: 1rem;
  margin-top: 1rem;
  padding-top: 1rem;
  border-top: 1px solid color-mix(in srgb, var(--glow-color) 18%, transparent);
}


.two-pane {
  display: grid;
  grid-template-columns: 320px 1fr;
  gap: 1rem;
  flex: 1;
  min-height: 0;
}

.nation-list {
  border: 1px solid color-mix(in srgb, var(--glow-color) 20%, transparent);
  border-radius: 12px;
  padding: 0.75rem;
  overflow: auto;
  height: 100%;
  min-height: 0;
}

.nation-list-section+.nation-list-section {
  margin-top: 1rem;
}

.nation-list-divider {
  height: 1px;
  background: color-mix(in srgb, var(--glow-color) 18%, transparent);
  margin: 0.75rem 0;
}

.section-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 0.75rem;
}

.section-title {
  margin: 0;
  font-size: 0.95rem;
  color: var(--text-color-hover);
}

.nation-detail {
  min-width: 0;
  border: 1px solid color-mix(in srgb, var(--glow-color) 20%, transparent);
  border-radius: 12px;
  padding: 0.9rem;
  overflow: auto;
  height: 100%;
  min-height: 0;
}

.list {
  margin-top: 0.75rem;
  display: grid;
  gap: 0.5rem;
}

@media (max-width: 880px) {
  .two-pane {
    grid-template-columns: 1fr;
  }

  .nation-list {
    height: 280px;
  }
}

.list-item {
  text-align: left;
  background: transparent;
  border: 1px solid color-mix(in srgb, var(--glow-color) 25%, transparent);
  color: var(--text-color);
  padding: 0.75rem;
  font-family: 'Orbitron', sans-serif;
  cursor: pointer;
  border-radius: 10px;
  transition: border-color 0.15s ease, transform 0.15s ease, color 0.15s ease;
}

.list-item:hover {
  transform: translateY(-1px);
  border-color: color-mix(in srgb, var(--glow-color) 55%, transparent);
  color: var(--text-color-hover);
}

.list-item.selected {
  border-color: color-mix(in srgb, var(--glow-color) 75%, transparent);
  box-shadow: 0 0 12px color-mix(in srgb, var(--glow-color) 12%, transparent);
}

.item-title {
  font-size: 0.95rem;
}

.item-sub {
  margin-top: 0.2rem;
  font-size: 0.75rem;
  color: color-mix(in srgb, var(--text-color) 60%, transparent);
}

.detail-content {
  min-width: 0;
}

.detail-title {
  margin: 0 0 1rem 0;
  color: var(--text-color-hover);
  font-size: 1.05rem;
}

.detail-row {
  margin-top: 0.5rem;
  display: grid;
  grid-template-columns: 140px 1fr;
  gap: 0.75rem;
  font-size: 0.85rem;
}

.detail-label {
  color: color-mix(in srgb, var(--text-color) 65%, transparent);
}

.detail-value {
  color: var(--text-color);
}

.placeholder {
  padding: 0.75rem;
  border-radius: 10px;
  border: 1px dashed color-mix(in srgb, var(--glow-color) 35%, transparent);
  color: color-mix(in srgb, var(--text-color) 70%, transparent);
  font-size: 0.85rem;
}



.btn {
  background: transparent;
  border: 1px solid color-mix(in srgb, var(--glow-color) 32%, transparent);
  color: var(--text-color);
  padding: 0.75rem 1rem;
  font-family: 'Orbitron', sans-serif;
  font-size: 0.95rem;
  cursor: pointer;
  border-radius: 10px;
  transition: transform 0.15s ease, color 0.15s ease, border-color 0.15s ease;
}

.btn:hover {
  transform: translateY(-1px);
  color: var(--text-color-hover);
  border-color: color-mix(in srgb, var(--glow-color) 58%, transparent);
}

.btn.primary {
  background: color-mix(in srgb, var(--glow-color) 18%, rgba(255, 255, 255, 0.04));
}

.btn:disabled {
  opacity: 0.6;
  cursor: not-allowed;
}

.btn.small {
  padding: 0.4rem 0.6rem;
  font-size: 0.75rem;
}
</style>

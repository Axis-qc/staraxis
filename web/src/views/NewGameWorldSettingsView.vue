<script setup lang="ts">
/**
 * @file NewGameWorldSettingsView.vue
 *
 * @description
 * 新游戏 - 世界设置页面（/new-game/world-settings）。
 * UI 风格对齐 MainMenuView：HUD + 星空背景。
 *
 * 本期改动：
 * - 将“星区数量”改为“世界半径（worldRadius）”：大地图由六边形星区拼合而成，以 (0,0) 为中心，根据半径生成。
 * - 新增“星系形状（galaxyShape）”：控制大地图星系生成形状（双/三/四螺旋、圆、椭圆、不规则）。
 *
 * 注意：
 * - 具体保存到新游戏草稿与后端读取，会在接入 newgame step2/step3 时完成。
 */
import { computed, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { useRouter } from 'vue-router'

import { useStarfield } from '../composables/useStarfield'
import { useAuthStore } from '../stores/auth'

const { t } = useI18n()
const router = useRouter()

const auth = useAuthStore()
const username = computed(() => auth.username)
const playerId = computed(() => auth.playerId)
const newGameDraftId = computed(() => auth.username)

const loading = ref(false)
const errorMsg = ref('')

// --- Canvas Background ---
const canvasRef = ref<HTMLCanvasElement | null>(null)
useStarfield(canvasRef)

const worldSeed = ref('')

// worldRadius：六边形半径（0,0 为中心），半径为 R 时总格子数约为 1 + 3R(R+1)
const worldRadius = ref(12)

type GalaxyShape = 'doubleSpiral' | 'tripleSpiral' | 'quadSpiral' | 'circle' | 'ellipse' | 'irregular'
const galaxyShape = ref<GalaxyShape>('doubleSpiral')

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

async function handleStartGame() {
  if (loading.value) {
    return
  }
  if (!username.value || !playerId.value || !newGameDraftId.value) {
    errorMsg.value = 'not_logged_in'
    return
  }

  loading.value = true
  errorMsg.value = ''
  try {
    const worldGenConfig = {
      worldSeed: worldSeed.value,
      worldRadius: worldRadius.value,
      galaxyShape: galaxyShape.value,
    }

    const resp2 = await apiPostJson<{ ok: boolean; error?: string }>('/api/newgame/step2/worldSettings', {
      username: username.value,
      playerId: playerId.value,
      newGameDraftId: newGameDraftId.value,
      worldGenConfig,
    })

    if (!resp2.ok) {
      throw new Error(resp2.error || 'step2_failed')
    }

    const resp3 = await apiPostJson<{ ok: boolean; gameSessionId?: string; error?: string }>(
      '/api/newgame/step3/confirm',
      {
        username: username.value,
        playerId: playerId.value,
        newGameDraftId: newGameDraftId.value,
      },
    )

    if (!resp3.ok) {
      throw new Error(resp3.error || 'step3_failed')
    }

    router.push('/in-game')
  } catch (e: any) {
    errorMsg.value = String(e?.message || e)
  } finally {
    loading.value = false
  }
}

</script>

<template>
  <div class="page">
    <canvas ref="canvasRef" class="background-canvas"></canvas>

    <div class="hud">
      <header class="hud-header">
        <h1 class="title">{{ t('mainMenu.newGame') }}</h1>
        <p class="subtitle">{{ t('mainMenu.web.subtitle') }}</p>
      </header>

      <section class="panel">
        <h2 class="panel-title">{{ t('newGame.worldSettings.title') }}</h2>
        <p class="panel-desc">{{ t('newGame.worldSettings.desc') }}</p>

        <div class="form">
          <label class="field">
            <span class="label">{{ t('newGame.worldSettings.seed') }}</span>
            <input class="input" v-model="worldSeed" placeholder="seed" />
          </label>

          <label class="field">
            <span class="label">{{ t('newGame.worldSettings.worldRadius') }}</span>
            <input class="input" type="number" v-model.number="worldRadius" min="1" max="512" />
          </label>

          <label class="field">
            <span class="label">{{ t('newGame.worldSettings.galaxyShape') }}</span>
            <select class="input" v-model="galaxyShape">
              <option value="doubleSpiral">{{ t('newGame.worldSettings.galaxyShape.doubleSpiral') }}</option>
              <option value="tripleSpiral">{{ t('newGame.worldSettings.galaxyShape.tripleSpiral') }}</option>
              <option value="quadSpiral">{{ t('newGame.worldSettings.galaxyShape.quadSpiral') }}</option>
              <option value="circle">{{ t('newGame.worldSettings.galaxyShape.circle') }}</option>
              <option value="ellipse">{{ t('newGame.worldSettings.galaxyShape.ellipse') }}</option>
              <option value="irregular">{{ t('newGame.worldSettings.galaxyShape.irregular') }}</option>
            </select>
          </label>
        </div>

        <div class="actions">
          <button class="btn" @click="router.push('/new-game/nation')">
            {{ t('common.back') }}
          </button>
          <button class="btn primary" :disabled="loading || !newGameDraftId" @click="handleStartGame">
            {{ loading ? '...' : t('newGame.worldSettings.startGame') }}
          </button>
        </div>
      </section>
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
  padding: 4rem;
  box-sizing: border-box;
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
  width: 760px;
  max-width: 100%;
  height: 100%;
}

.hud-header {
  margin-bottom: 2rem;
}

.title {
  font-size: 2.6rem;
  font-weight: 700;
  color: var(--text-color-hover);
  text-shadow: 0 0 8px var(--glow-color), 0 0 15px var(--glow-color);
  margin: 0;
}

.subtitle {
  font-size: 1rem;
  color: var(--glow-color);
  margin: 0.25rem 0 0 0;
  letter-spacing: 0.1875rem;
  text-transform: uppercase;
}

.panel {
  background: color-mix(in srgb, var(--background-color) 68%, rgba(255, 255, 255, 0.05));
  border: 1px solid color-mix(in srgb, var(--glow-color) 32%, transparent);
  box-shadow: 0 0 24px color-mix(in srgb, var(--glow-color) 12%, transparent);
  border-radius: 12px;
  padding: 1.5rem;
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

.form {
  margin-top: 1.25rem;
  display: grid;
  grid-template-columns: 1fr;
  gap: 1rem;
}

.field {
  display: grid;
  gap: 0.5rem;
}

.label {
  font-size: 0.85rem;
  color: color-mix(in srgb, var(--text-color) 75%, transparent);
}

.input {
  background: transparent;
  border: 1px solid color-mix(in srgb, var(--glow-color) 32%, transparent);
  color: var(--text-color);
  padding: 0.75rem 0.9rem;
  border-radius: 10px;
  font-family: 'Orbitron', sans-serif;
}

.input:focus {
  outline: none;
  border-color: color-mix(in srgb, var(--glow-color) 60%, transparent);
}

.actions {
  display: flex;
  justify-content: space-between;
  gap: 1rem;
  margin-top: 1.5rem;
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
</style>

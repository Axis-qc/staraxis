<script setup lang="ts">
import { computed, onMounted, onUnmounted, reactive, ref, watch } from 'vue'
import {
  authLogin,
  authLogout,
  authMe,
  authRegister,
  authSetGameId,
  createWsClient,
  fetchStatus,
  requestQuit,
  type AuthMe,
  type BackendStatus,
  type WsState,
} from '../services/backend'

// --- Tabs ---
type TabKey = 'status' | 'mods' | 'quit'
const activeTab = ref<TabKey>('status')

// --- Auth State ---
const auth = reactive({
  isLoggedIn: false,
  token: localStorage.getItem('sa.token') || '',
  playerId: '',
  username: '',
  gameId: '',
})

const loginForm = reactive({
  username: '',
  password: '',
  error: '',
  loading: false,
})

const gameIdInput = ref('')
const gameIdSaveState = ref<'idle' | 'saving' | 'saved' | 'error'>('idle')

// --- Server Status ---
const status = ref<BackendStatus | null>(null)
const statusError = ref<string | null>(null)

// --- WebSocket ---
const wsState = ref<WsState>('disconnected')
const wsError = ref<string | null>(null)
const wsClient = createWsClient('/ws')
let offState: (() => void) | null = null
let offErr: (() => void) | null = null

// --- Quit ---
const quitting = ref(false)
const quitHint = ref<string | null>(null)
const quitError = ref<string | null>(null)

// --- Computed ---
const wsStateText = computed(() => {
  if (wsState.value === 'disconnected') return '未连接'
  if (wsState.value === 'connecting') return '连接中'
  if (wsState.value === 'connected') return '已连接'
  return '错误'
})

// --- Methods ---
function storeToken(token: string) {
  auth.token = token
  localStorage.setItem('sa.token', token)
}

function clearToken() {
  auth.token = ''
  localStorage.removeItem('sa.token')
}

function updateAuthState(data: AuthMe) {
  auth.isLoggedIn = data.ok
  auth.playerId = data.playerId || ''
  auth.username = data.username || ''
  auth.gameId = data.gameId || ''
  gameIdInput.value = auth.gameId
}

async function checkAuth() {
  if (!auth.token) {
    updateAuthState({ ok: false })
    return
  }
  loginForm.loading = true
  loginForm.error = ''
  try {
    const data = await authMe()
    updateAuthState(data)
  } catch (e) {
    clearToken()
    updateAuthState({ ok: false })
  } finally {
    loginForm.loading = false
  }
}

async function doRegister() {
  loginForm.loading = true
  loginForm.error = ''
  try {
    await authRegister(loginForm.username, loginForm.password)
    await doLogin()
  } catch (e) {
    loginForm.error = (e as Error).message
  } finally {
    loginForm.loading = false
  }
}

async function doLogin() {
  loginForm.loading = true
  loginForm.error = ''
  try {
    const data = await authLogin(loginForm.username, loginForm.password)
    if (data.token) {
      storeToken(data.token)
      await checkAuth()
    } else {
      throw new Error('Login did not return a token.')
    }
  } catch (e) {
    loginForm.error = (e as Error).message
  } finally {
    loginForm.loading = false
  }
}

async function doLogout() {
  loginForm.loading = true
  try {
    await authLogout()
  } catch (e) {
    console.error('Logout failed:', e)
  } finally {
    clearToken()
    updateAuthState({ ok: false })
    loginForm.loading = false
  }
}

async function saveGameId() {
  gameIdSaveState.value = 'saving'
  try {
    await authSetGameId(gameIdInput.value)
    auth.gameId = gameIdInput.value
    gameIdSaveState.value = 'saved'
  } catch (e) {
    gameIdSaveState.value = 'error'
  }
  setTimeout(() => {
    if (gameIdSaveState.value !== 'saving') {
      gameIdSaveState.value = 'idle'
    }
  }, 2000)
}

async function refreshStatus() {
  statusError.value = null
  try {
    status.value = await fetchStatus()
  } catch (e) {
    status.value = null
    statusError.value = (e as Error).message
  }
}

async function quitServer() {
  if (quitting.value) return
  quitting.value = true
  quitError.value = null
  quitHint.value = null
  try {
    await requestQuit()
    quitHint.value = '已请求关闭，本地服务端将很快退出。'
  } catch (e) {
    quitError.value = (e as Error).message
  } finally {
    quitting.value = false
  }
}

// --- Lifecycle ---
onMounted(() => {
  checkAuth()

  offState = wsClient.onStateChange((s) => (wsState.value = s))
  offErr = wsClient.onError((e) => (wsError.value = e))

  refreshStatus()
  wsClient.connect()

  const statusTimer = window.setInterval(refreshStatus, 3000)
  onUnmounted(() => window.clearInterval(statusTimer))
})

onUnmounted(() => {
  offState?.()
  offErr?.()
  wsClient.disconnect()
})
</script>

<template>
  <div class="sa-page">
    <div class="sa-shell" style="max-width: 1200px">
      <div class="sa-topbar">
        <div>
          <div class="sa-title">StarAxis 启动台</div>
          <div class="sa-subtitle">本地服务器状态 / 登录 / MOD管理</div>
        </div>
      </div>

      <div class="sa-layout">
        <div class="sa-side">
          <div class="sa-card" style="overflow: hidden">
            <div class="sa-card-header">
              <div class="sa-card-title">菜单</div>
            </div>
            <div class="sa-card-body" style="display: grid; gap: 10px">
              <button class="sa-tab" :class="{ active: activeTab === 'status' }" @click="activeTab = 'status'">
                游戏状态
              </button>
              <button class="sa-tab" :class="{ active: activeTab === 'mods' }" @click="activeTab = 'mods'">
                MOD管理
              </button>
              <button class="sa-tab" :class="{ active: activeTab === 'quit' }" @click="activeTab = 'quit'">
                关闭游戏
              </button>
            </div>
          </div>
        </div>

        <div class="sa-main">
          <!-- Game Status Tab -->
          <div v-if="activeTab === 'status'" class="sa-content-grid">
            <div class="sa-card">
              <div class="sa-card-header">
                <div class="sa-card-title">登录</div>
              </div>
              <div class="sa-card-body">
                <div v-if="auth.isLoggedIn">
                  <div class="sa-kv">
                    <div class="k">当前用户</div>
                    <div class="v">{{ auth.username }} ({{ auth.playerId }})</div>
                    <div class="k">玩家游戏ID</div>
                    <div class="v">
                      <input class="sa-input" v-model="gameIdInput" placeholder="用于游戏内识别的ID" />
                    </div>
                  </div>
                  <div style="margin-top: 12px" class="row">
                    <button class="sa-btn" @click="saveGameId" :disabled="gameIdSaveState === 'saving'">保存ID</button>
                    <button class="sa-btn danger" @click="doLogout">退出登录</button>
                    <span v-if="gameIdSaveState === 'saved'" class="sa-tag ok">已保存</span>
                    <span v-if="gameIdSaveState === 'error'" class="sa-tag error">保存失败</span>
                  </div>
                </div>
                <div v-else>
                  <div class="sa-kv">
                    <div class="k">账号</div>
                    <div class="v"><input v-model="loginForm.username" class="sa-input" /></div>
                    <div class="k">密码</div>
                    <div class="v"><input v-model="loginForm.password" type="password" class="sa-input" /></div>
                  </div>
                  <div style="margin-top: 12px" class="row">
                    <button class="sa-btn" @click="doLogin" :disabled="loginForm.loading">登录</button>
                    <button class="sa-btn" @click="doRegister" :disabled="loginForm.loading">注册</button>
                  </div>
                  <div v-if="loginForm.error" class="sa-tag error" style="margin-top: 10px">
                    {{ loginForm.error }}
                  </div>
                </div>
              </div>
            </div>

            <div class="sa-card">
              <div class="sa-card-header">
                <div class="sa-card-title">服务器状态</div>
                <div class="row">
                  <button class="sa-btn" @click="refreshStatus">刷新</button>
                  <button class="sa-btn" @click="wsClient.connect()">连接WS</button>
                </div>
              </div>
              <div class="sa-card-body">
                <div class="sa-kv">
                  <div class="k">HTTP</div>
                  <div class="v">/api/status</div>
                  <div class="k">WS</div>
                  <div class="v">{{ wsClient.getUrl() }}</div>
                  <div class="k">连接数</div>
                  <div class="v">{{ status?.connections ?? '-' }}</div>
                  <div class="k">状态</div>
                  <div class="v">
                    <span :class="['sa-tag', wsState === 'connected' ? 'ok' : 'warn']">{{ wsStateText }}</span>
                    <span v-if="statusError" class="sa-tag error">HTTP: {{ statusError }}</span>
                    <span v-if="wsError" class="sa-tag error">WS: {{ wsError }}</span>
                  </div>
                </div>
              </div>
            </div>
          </div>

          <!-- Mods Tab -->
          <div v-else-if="activeTab === 'mods'" class="sa-card">
            <div class="sa-card-header">
              <div class="sa-card-title">MOD管理</div>
            </div>
            <div class="sa-card-body">
              <div class="muted">占位：后续将接入 mod 列表（例如 /api/mods）并支持启用/禁用与顺序调整。</div>
            </div>
          </div>

          <!-- Quit Tab -->
          <div v-else class="sa-card">
            <div class="sa-card-header">
              <div class="sa-card-title">关闭游戏</div>
            </div>
            <div class="sa-card-body">
              <div class="muted">将请求关闭本地服务端（StarAxis.jar 进程）。</div>
              <div style="margin-top: 12px" class="row">
                <button class="sa-btn danger" @click="quitServer" :disabled="quitting">关闭本地游戏进程</button>
                <span v-if="quitHint" class="sa-tag ok">{{ quitHint }}</span>
                <span v-if="quitError" class="sa-tag error">{{ quitError }}</span>
              </div>
            </div>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped>
.sa-layout {
  display: grid;
  grid-template-columns: 260px 1fr;
  gap: 14px;
  align-items: start;
}

.sa-content-grid {
  display: grid;
  gap: 14px;
}

@media (max-width: 920px) {
  .sa-layout {
    grid-template-columns: 1fr;
  }
}

.sa-tab {
  width: 100%;
  text-align: left;
  border-radius: 12px;
  border: 1px solid rgba(255, 255, 255, 0.14);
  padding: 10px 12px;
  font-size: 13px;
  font-weight: 800;
  background: rgba(0, 0, 0, 0.14);
  color: rgba(255, 255, 255, 0.92);
  cursor: pointer;
}

.sa-tab:hover {
  border-color: rgba(106, 168, 255, 0.55);
  background: rgba(0, 0, 0, 0.2);
}

.sa-tab.active {
  border-color: rgba(106, 168, 255, 0.8);
  background: rgba(106, 168, 255, 0.12);
}

.sa-input {
  width: 100%;
  border-radius: 10px;
  border: 1px solid rgba(255, 255, 255, 0.14);
  padding: 8px 10px;
  font-size: 13px;
  background: rgba(0, 0, 0, 0.16);
  color: rgba(255, 255, 255, 0.92);
  outline: none;
}

.sa-input::placeholder {
  color: rgba(255, 255, 255, 0.45);
}

.muted {
  color: rgba(255, 255, 255, 0.65);
  font-size: 13px;
}

.row {
  display: flex;
  gap: 10px;
  flex-wrap: wrap;
  align-items: center;
}
</style>

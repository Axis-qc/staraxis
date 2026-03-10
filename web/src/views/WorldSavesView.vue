<script setup lang="ts">
/**
 * @file WorldSavesView.vue
 *
 * @description
 * 世界/存档一体视图：
 * - /worlds：新游戏入口（世界界面），强调“创建世界 + 加入世界”喵。
 * - /load-game：加载游戏入口（存档界面），强调“浏览存档 + 按存档加载”喵。
 *
 * 说明：
 * - 该视图通过 route.meta.viewMode 区分界面模式，避免重复维护两套页面逻辑喵。
 */
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useAuthStore } from '../stores/auth'
import { useWorldSessionStore } from '../stores/worldSession'
import { useEnvironment } from '../utils/environment'
import {
  listWorldSaves,
  createWorldSave,
  joinWorldSave,
  getWorldPlayerState,
  listWorldSaveFiles,
  loadWorldFromSave,
  type TickPolicy,
  type WorldSaveItem,
} from '../net/worldSavesApi'

const route = useRoute()
const router = useRouter()
const auth = useAuthStore()
const worldSession = useWorldSessionStore()

const loading = ref(false)
const errorMsg = ref('')
const worlds = ref<WorldSaveItem[]>([])

const createForm = ref({
  worldName: '',
  worldRadius: 12,
  worldSeed: '',
  tickPolicy: 'RUN_WHEN_ONLINE' as TickPolicy,
  spawnMode: 'manual' as 'manual' | 'random',
})


/**
 * 环境检测：判断当前运行环境喵。
 */
const env = useEnvironment()

/**
 * 角色权限：仅管理员可创建/加载控制世界；普通用户仅可加入运行中世界喵。
 * 在本地开发环境（端口5173）自动启用管理员权限，方便开发测试喵。
 */
const isAdmin = computed(() => {
  // 正常的管理员权限检查
  const isNormalAdmin = String(auth.role || '').toUpperCase() === 'ADMIN'

  // 本地开发环境自动获得管理员权限（仅前端UI层面）
  const isDevAdmin = env.shouldAutoAdmin && env.isLocalDev

  return isNormalAdmin || isDevAdmin
})

const saveFilesByWorldId = ref<Record<string, Array<{ fileName: string; saveType: 'latest' | 'auto' | 'manual'; path: string; lastModifiedEpochMs: number; sizeBytes: number }>>>({})

/**
 * 页面模式：
 * - worlds：新游戏（世界界面）
 * - saves：加载游戏（存档界面）喵。
 */
const viewMode = computed<'worlds' | 'saves'>(() => {
  const mode = route.meta?.viewMode
  return mode === 'saves' ? 'saves' : 'worlds'
})

async function refreshWorlds() {
  loading.value = true
  errorMsg.value = ''
  try {
    const resp = await listWorldSaves()
    worlds.value = resp.worlds || []
  } catch (e: any) {
    errorMsg.value = String(e?.message || e)
  } finally {
    loading.value = false
  }
}

async function onCreateWorld() {
  loading.value = true
  errorMsg.value = ''
  try {
    await createWorldSave({
      worldName: createForm.value.worldName,
      worldRadius: createForm.value.worldRadius,
      worldSeed: createForm.value.worldSeed,
      tickPolicy: createForm.value.tickPolicy,
      spawnMode: createForm.value.spawnMode,
      creatorPlayerId: auth.playerId || '',
    })
    await refreshWorlds()
  } catch (e: any) {
    errorMsg.value = String(e?.message || e)
  } finally {
    loading.value = false
  }
}

async function onJoinWorld(world: WorldSaveItem) {
  if (!auth.playerId) {
    errorMsg.value = 'playerId_required'
    return
  }

  loading.value = true
  errorMsg.value = ''
  try {
    const joinResp = await joinWorldSave(world.worldId, auth.playerId)
    if (!joinResp.ok) {
      throw new Error(joinResp.error || 'join_failed')
    }

    worldSession.setSelectedWorld(world.worldId, world.worldName)
    worldSession.setPlayerWorldState((joinResp.playerState as any) || 'SPAWN_PENDING')
    worldSession.markJoinedNow()

    // SPAWNED 玩家进入时，从 players.json 同步 nationId 到前端会话喵。
    if (joinResp.nationId && String(joinResp.nationId).trim()) {
      auth.setSelectedNationId(String(joinResp.nationId))
    }

    // 加入后立即请求世界数据（通过进入 in-game 建立快照流）喵
    const stateResp = await getWorldPlayerState(world.worldId, auth.playerId)
    if (stateResp.ok) {
      worldSession.setPlayerWorldState((stateResp.playerState as any) || 'SPAWN_PENDING')
      const stateNationId = (stateResp.playerRole as any)?.nationId
      if (stateNationId && String(stateNationId).trim()) {
        auth.setSelectedNationId(String(stateNationId))
      }
    }

    // 出生流程统一并入 in-game 页面喵（创建世界时仅设置策略）
    router.push('/in-game')
  } catch (e: any) {
    errorMsg.value = String(e?.message || e)
  } finally {
    loading.value = false
  }
}

async function onRefreshSaves(worldId: string) {
  loading.value = true
  errorMsg.value = ''
  try {
    const resp = await listWorldSaveFiles(worldId)
    if (!resp.ok) {
      throw new Error(resp.error || 'list_saves_failed')
    }
    saveFilesByWorldId.value = {
      ...saveFilesByWorldId.value,
      [worldId]: resp.saves || [],
    }
  } catch (e: any) {
    errorMsg.value = String(e?.message || e)
  } finally {
    loading.value = false
  }
}

async function onLoadSave(worldId: string, fileName: string, saveType: 'latest' | 'auto' | 'manual') {
  loading.value = true
  errorMsg.value = ''
  try {
    const loadType = saveType === 'latest' ? 'latest' : (saveType === 'auto' ? 'auto' : 'manual')
    const resp = await loadWorldFromSave(worldId, { loadType, fileName })
    if (!resp.ok) {
      throw new Error(resp.error || 'load_world_failed')
    }
    await refreshWorlds()
  } catch (e: any) {
    errorMsg.value = String(e?.message || e)
  } finally {
    loading.value = false
  }
}

function goToMainMenu() {
  router.push('/main-menu')
}

async function onDeleteWorld(worldId: string, worldName: string) {
  if (!confirm(`确定要删除世界 "${worldName}" 吗？此操作不可恢复。`)) {
    return
  }

  // TODO: 实现删除世界API
  console.log(`TODO: 删除世界 ${worldId} (${worldName})`)
  errorMsg.value = '删除世界功能开发中喵 (TODO)'
}


onMounted(() => {
  void refreshWorlds()
})
</script>

<template>
  <div class="page">
    <section class="panel">
      <div class="panel-header">
        <div class="header-left">
          <button class="sa-btn mini back-btn" @click="goToMainMenu">
            <svg class="icon" viewBox="0 0 24 24" width="16" height="16">
              <path fill="currentColor" d="M20 11H7.83l5.59-5.59L12 4l-8 8 8 8 1.41-1.41L7.83 13H20v-2z"/>
            </svg>
            返回主菜单
          </button>
          <h1>{{ viewMode === 'worlds' ? '世界管理' : '存档管理' }}</h1>
        </div>
        <div class="header-right">
          <div class="env-badge" :class="env.badgeClass" :title="env.displayName">
            {{ env.badgeText }}
            <span v-if="env.shouldAutoAdmin" class="admin-badge" title="开发环境自动管理员权限">⚡</span>
          </div>
          <div class="mode-switcher">
            <router-link :to="{ path: '/worlds' }" class="mode-link" :class="{ active: viewMode === 'worlds' }">
              世界界面
            </router-link>
            <span class="mode-separator">|</span>
            <router-link :to="{ path: '/load-game' }" class="mode-link" :class="{ active: viewMode === 'saves' }">
              存档界面
            </router-link>
          </div>
        </div>
      </div>

      <div class="panel-description">
        <p v-if="viewMode === 'worlds'">创建新世界并加入，进入出生流程喵。</p>
        <p v-else>浏览并加载现有存档，再加入世界喵。</p>
      </div>

      <div v-if="loading" class="loading-overlay">
        <div class="loading-spinner"></div>
        <div class="loading-text">处理中...</div>
      </div>

      <div v-if="viewMode === 'worlds' && isAdmin" class="create-world-form">
        <div class="form-row">
          <input class="input" v-model="createForm.worldName" placeholder="世界名称" />
          <input class="input" v-model.number="createForm.worldRadius" type="number" min="1" max="512" placeholder="半径" />
          <input class="input" v-model="createForm.worldSeed" placeholder="种子（可选）" />
          <select class="input" v-model="createForm.tickPolicy">
            <option value="RUN_WHEN_ONLINE">有玩家在线才推进</option>
            <option value="ALWAYS_RUN">无视在线持续推进</option>
          </select>
          <select class="input" v-model="createForm.spawnMode">
            <option value="manual">进入游戏后手动选出生点</option>
            <option value="random">创建世界时随机出生</option>
          </select>
          <button class="sa-btn primary" :disabled="loading" @click="onCreateWorld">创建世界</button>
        </div>
      </div>

      <div class="worlds-list" :class="{ 'has-scroll': worlds.length > 5 }">
        <div v-if="worlds.length === 0" class="empty-state">
          暂无世界喵。{{ isAdmin ? '请创建新世界。' : '请等待管理员创建世界。' }}
        </div>

        <div v-for="w in worlds" :key="w.worldId" class="world-item">
          <div class="world-item-main">
            <div class="world-info">
              <div class="world-name-row">
                <div class="world-name">{{ w.worldName }}</div>
                <div class="world-status-tag" :class="w.tickPolicy === 'RUN_WHEN_ONLINE' ? 'online' : 'always'">
                  {{ w.tickPolicy === 'RUN_WHEN_ONLINE' ? '在线' : '持续' }}
                </div>
              </div>
              <div class="world-meta">
                <div class="meta-item">
                  <span class="meta-label">ID:</span>
                  <span class="meta-value">{{ w.worldId }}</span>
                </div>
                <div class="meta-item">
                  <span class="meta-label">半径:</span>
                  <span class="meta-value">{{ w.worldRadius }}</span>
                </div>
                <div class="meta-item">
                  <span class="meta-label">刻:</span>
                  <span class="meta-value">{{ w.simulationTick }}</span>
                </div>
                <div class="meta-item">
                  <span class="meta-label">创建:</span>
                  <span class="meta-value">{{ new Date(w.createdAtEpochMs).toLocaleDateString() }}</span>
                </div>
                <div class="meta-item">
                  <span class="meta-label">状态:</span>
                  <span class="meta-value">{{ w.active ? '运行中' : '已停止' }}</span>
                </div>
              </div>
            </div>

            <div class="world-actions">
              <button class="sa-btn mini primary" :disabled="loading" @click="onJoinWorld(w)" title="加入世界">
                加入
              </button>
              <button class="sa-btn mini" v-if="isAdmin" :disabled="loading" @click="onRefreshSaves(w.worldId)" title="刷新存档">
                刷新存档
              </button>
              <button class="sa-btn mini danger" v-if="isAdmin" :disabled="loading" @click="onDeleteWorld(w.worldId, w.worldName)" title="删除世界">
                删除
              </button>
            </div>
          </div>

          <div v-if="viewMode === 'saves' && isAdmin && saveFilesByWorldId[w.worldId]?.length" class="saves-list">
            <div v-for="s in saveFilesByWorldId[w.worldId]" :key="`${w.worldId}:${s.fileName}`" class="save-item">
              <div class="save-info">
                <div class="save-name">{{ s.fileName }}</div>
                <div class="save-meta">
                  <span class="save-size">{{ (s.sizeBytes / 1024).toFixed(1) }} KB</span>
                  <span class="save-time">{{ new Date(s.lastModifiedEpochMs).toLocaleString() }}</span>
                </div>
              </div>
              <div class="save-actions">
                <div class="save-type-tag" :class="s.saveType">
                  {{ s.saveType === 'latest' ? '最新' : s.saveType === 'auto' ? '自动' : '手动' }}
                </div>
                <button class="sa-btn mini" :disabled="loading" @click="onLoadSave(w.worldId, s.fileName, s.saveType)" title="加载存档">
                  加载
                </button>
              </div>
            </div>
          </div>
        </div>
      </div>


      <div v-if="errorMsg" class="error">{{ errorMsg }}</div>
    </section>
  </div>
</template>

<style scoped>
/* 页面基础布局 */
.page {
  padding: 16px;
  color: var(--text-color);
  font-family: 'Orbitron', sans-serif;
  min-height: 100vh;
  background: var(--background-color);
  display: flex;
  flex-direction: column;
}

.panel {
  border: 1px solid var(--border-color);
  border-radius: 16px;
  padding: 20px;
  background: color-mix(in srgb, var(--panel-bg) 65%, rgba(0, 0, 0, 0.35));
  backdrop-filter: blur(12px);
  -webkit-backdrop-filter: blur(12px);
  box-shadow: 0 0 24px color-mix(in srgb, var(--glow-color) 12%, transparent);
  max-width: 1200px;
  margin: 0 auto;
  width: 100%;
  display: flex;
  flex-direction: column;
  flex: 1;
  max-height: calc(100vh - 32px);
  overflow: hidden;
}

/* 面板头部 */
.panel-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 16px;
  padding-bottom: 16px;
  border-bottom: 1px solid color-mix(in srgb, var(--border-color) 40%, transparent);
}

.header-left {
  display: flex;
  align-items: center;
  gap: 16px;
}

.back-btn {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 6px 12px;
}

.back-btn .icon {
  margin-right: 4px;
}

.panel-header h1 {
  font-size: 1.5rem;
  margin: 0;
  color: var(--text-color-hover);
  text-shadow: 0 0 8px var(--glow-color);
}

.header-right {
  display: flex;
  align-items: center;
  gap: 12px;
}

/* 环境徽章 */
.env-badge {
  font-size: 0.7rem;
  padding: 4px 8px;
  border-radius: 10px;
  font-weight: bold;
  text-transform: uppercase;
  letter-spacing: 0.5px;
  border: 1px solid transparent;
  background: color-mix(in srgb, var(--sa-bg1) 70%, transparent);
  color: var(--text-color);
  display: flex;
  align-items: center;
  gap: 4px;
  white-space: nowrap;
}

.env-badge.env-badge-local-dev {
  background: color-mix(in srgb, var(--glow-color) 25%, transparent);
  color: var(--glow-color);
  border-color: color-mix(in srgb, var(--glow-color) 50%, transparent);
  box-shadow: 0 0 8px color-mix(in srgb, var(--glow-color) 30%, transparent);
}

.env-badge.env-badge-lan {
  background: color-mix(in srgb, #4a9eff 25%, transparent);
  color: #a8d0ff;
  border-color: color-mix(in srgb, #4a9eff 50%, transparent);
}

.env-badge.env-badge-production {
  background: color-mix(in srgb, #ff6b6b 25%, transparent);
  color: #ffb8b8;
  border-color: color-mix(in srgb, #ff6b6b 50%, transparent);
}

.env-badge.env-badge-unknown {
  background: color-mix(in srgb, var(--sa-muted) 25%, transparent);
  color: var(--sa-muted);
  border-color: color-mix(in srgb, var(--sa-muted) 50%, transparent);
}

.env-badge .admin-badge {
  color: #ffd700;
  font-size: 0.8rem;
  margin-left: 2px;
  text-shadow: 0 0 4px #ffd700;
  animation: pulse 2s infinite;
}

@keyframes pulse {
  0%, 100% { opacity: 1; }
  50% { opacity: 0.6; }
}

.mode-switcher {
  display: flex;
  align-items: center;
  gap: 8px;
  background: color-mix(in srgb, var(--sa-bg1) 60%, transparent);
  border-radius: 10px;
  padding: 4px;
  border: 1px solid var(--border-color);
}

.mode-link {
  padding: 6px 12px;
  border-radius: 8px;
  font-size: 0.85rem;
  color: var(--sa-muted);
  text-decoration: none;
  transition: all 0.2s ease;
}

.mode-link:hover {
  color: var(--text-color);
  background: color-mix(in srgb, var(--sa-bg1) 80%, transparent);
}

.mode-link.active {
  color: var(--text-color-hover);
  background: color-mix(in srgb, var(--glow-color) 20%, transparent);
  border: 1px solid color-mix(in srgb, var(--glow-color) 40%, transparent);
  text-shadow: 0 0 4px var(--glow-color);
}

.mode-separator {
  color: var(--border-color);
  opacity: 0.5;
}

/* 面板描述 */
.panel-description {
  margin-bottom: 20px;
  color: var(--sa-muted);
  font-size: 0.9rem;
}

/* 创建世界表单 */
.create-world-form {
  margin-bottom: 24px;
  padding: 16px;
  border: 1px solid var(--border-color);
  border-radius: 12px;
  background: color-mix(in srgb, var(--panel-bg) 30%, transparent);
}

.form-row {
  display: flex;
  gap: 12px;
  margin-bottom: 12px;
  flex-wrap: wrap;
  align-items: center;
}

.form-row .input {
  flex: 1;
  min-width: 150px;
}

.form-row .sa-btn.primary {
  flex-shrink: 0;
}

/* 世界列表容器 */
.worlds-list {
  flex: 1;
  overflow-y: auto;
  padding-right: 4px;
  margin-bottom: 16px;
}

.worlds-list.has-scroll {
  padding-right: 8px;
}

.worlds-list::-webkit-scrollbar {
  width: 8px;
}

.worlds-list::-webkit-scrollbar-track {
  background: color-mix(in srgb, var(--sa-bg1) 30%, transparent);
  border-radius: 4px;
}

.worlds-list::-webkit-scrollbar-thumb {
  background: color-mix(in srgb, var(--border-color) 60%, transparent);
  border-radius: 4px;
}

.worlds-list::-webkit-scrollbar-thumb:hover {
  background: color-mix(in srgb, var(--glow-color) 60%, transparent);
}

/* 空状态 */
.empty-state {
  text-align: center;
  padding: 40px 20px;
  color: var(--sa-muted);
  font-size: 0.95rem;
  background: color-mix(in srgb, var(--sa-bg1) 20%, transparent);
  border-radius: 12px;
  border: 1px dashed var(--border-color);
}

/* 世界项目 */
.world-item {
  margin-bottom: 12px;
  border: 1px solid var(--border-color);
  border-radius: 12px;
  background: color-mix(in srgb, var(--panel-bg) 40%, rgba(0, 0, 0, 0.2));
  overflow: hidden;
  transition: all 0.2s ease;
}

.world-item:hover {
  border-color: var(--glow-color);
  box-shadow: 0 0 12px color-mix(in srgb, var(--glow-color) 15%, transparent);
  transform: translateY(-1px);
}

.world-item-main {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 12px 16px;
  min-height: 60px;
}

.world-info {
  flex: 1;
  min-width: 0;
}

.world-name-row {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 6px;
}

.world-name {
  font-weight: 700;
  font-size: 1rem;
  color: var(--text-color-hover);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.world-status-tag {
  font-size: 0.65rem;
  padding: 2px 6px;
  border-radius: 8px;
  font-weight: bold;
  text-transform: uppercase;
  letter-spacing: 0.3px;
  flex-shrink: 0;
}

.world-status-tag.online {
  background: color-mix(in srgb, var(--glow-color) 20%, transparent);
  color: var(--glow-color);
  border: 1px solid color-mix(in srgb, var(--glow-color) 40%, transparent);
}

.world-status-tag.always {
  background: color-mix(in srgb, var(--sa-muted) 20%, transparent);
  color: var(--sa-muted);
  border: 1px solid color-mix(in srgb, var(--sa-muted) 40%, transparent);
}

.world-meta {
  display: flex;
  flex-wrap: wrap;
  gap: 12px;
  font-size: 0.8rem;
}

.meta-item {
  display: flex;
  align-items: center;
  gap: 4px;
}

.meta-label {
  color: var(--sa-muted);
  font-weight: 500;
}

.meta-value {
  color: var(--text-color);
  font-family: monospace;
  font-size: 0.85rem;
}

.world-actions {
  display: flex;
  gap: 8px;
  flex-shrink: 0;
  margin-left: 12px;
}

/* 存档列表 */
.saves-list {
  border-top: 1px solid color-mix(in srgb, var(--border-color) 30%, transparent);
  background: color-mix(in srgb, var(--sa-bg1) 20%, transparent);
  padding: 8px 16px;
}

.save-item {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 8px 0;
  border-bottom: 1px dashed color-mix(in srgb, var(--border-color) 20%, transparent);
}

.save-item:last-child {
  border-bottom: none;
}

.save-info {
  flex: 1;
  min-width: 0;
}

.save-name {
  font-size: 0.85rem;
  color: var(--text-color);
  margin-bottom: 2px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.save-meta {
  display: flex;
  gap: 12px;
  font-size: 0.75rem;
  color: var(--sa-muted);
}

.save-actions {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-shrink: 0;
  margin-left: 12px;
}

.save-type-tag {
  font-size: 0.65rem;
  padding: 2px 6px;
  border-radius: 6px;
  font-weight: bold;
  text-transform: uppercase;
  letter-spacing: 0.3px;
}

.save-type-tag.latest {
  background: color-mix(in srgb, var(--glow-color) 20%, transparent);
  color: var(--glow-color);
  border: 1px solid color-mix(in srgb, var(--glow-color) 40%, transparent);
}

.save-type-tag.auto {
  background: color-mix(in srgb, var(--sa-muted) 20%, transparent);
  color: var(--sa-muted);
  border: 1px solid color-mix(in srgb, var(--sa-muted) 40%, transparent);
}

.save-type-tag.manual {
  background: color-mix(in srgb, var(--glow-color) 20%, transparent);
  color: var(--glow-color);
  border: 1px solid color-mix(in srgb, var(--glow-color) 40%, transparent);
}

/* 错误消息 */
.error {
  color: var(--danger-color);
  margin-top: 12px;
  padding: 10px 14px;
  border-radius: 8px;
  background: color-mix(in srgb, var(--danger-color) 10%, transparent);
  border: 1px solid color-mix(in srgb, var(--danger-color) 30%, transparent);
  font-size: 0.9rem;
}

/* 加载覆盖层 */
.loading-overlay {
  position: fixed;
  top: 0;
  left: 0;
  right: 0;
  bottom: 0;
  background: rgba(0, 0, 0, 0.7);
  backdrop-filter: blur(4px);
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  z-index: 1000;
}

.loading-spinner {
  width: 48px;
  height: 48px;
  border: 3px solid var(--border-color);
  border-top: 3px solid var(--glow-color);
  border-radius: 50%;
  animation: spin 1s linear infinite;
  margin-bottom: 16px;
}

.loading-text {
  color: var(--text-color);
  font-size: 1.1rem;
  text-shadow: 0 0 8px var(--glow-color);
}

@keyframes spin {
  0% { transform: rotate(0deg); }
  100% { transform: rotate(360deg); }
}

/* 响应式设计 */
@media (max-width: 768px) {
  .page {
    padding: 12px;
  }

  .panel {
    padding: 16px;
    border-radius: 12px;
    max-height: calc(100vh - 24px);
  }

  .panel-header {
    flex-direction: column;
    align-items: flex-start;
    gap: 12px;
  }

  .header-left, .header-right {
    width: 100%;
  }

  .header-right {
    flex-direction: column;
    align-items: flex-start;
    gap: 8px;
  }

  .mode-switcher {
    justify-content: center;
  }

  .world-item-main {
    flex-direction: column;
    align-items: flex-start;
    gap: 12px;
  }

  .world-actions {
    width: 100%;
    justify-content: flex-end;
    margin-left: 0;
  }

  .world-meta {
    gap: 8px;
  }

  .meta-item {
    flex: 1 0 calc(50% - 8px);
  }

  .save-item {
    flex-direction: column;
    align-items: flex-start;
    gap: 8px;
  }

  .save-actions {
    width: 100%;
    justify-content: space-between;
    margin-left: 0;
  }

  .form-row .input {
    min-width: 100%;
  }

  /* 环境徽章响应式 */
  .env-badge {
    font-size: 0.65rem;
    padding: 3px 6px;
    order: -1; /* 移动到最前面 */
    margin-bottom: 4px;
  }
}

/* 按钮样式优化 */
.sa-btn.mini {
  padding: 4px 10px;
  font-size: 0.85rem;
  height: 28px;
}

.sa-btn.mini.danger {
  border-color: var(--danger-color);
  color: var(--danger-color);
}

.sa-btn.mini.danger:hover {
  background: color-mix(in srgb, var(--danger-color) 10%, transparent);
  border-color: var(--danger-color);
  color: var(--text-color-hover);
}
</style>
